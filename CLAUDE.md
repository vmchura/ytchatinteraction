# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YouTube Chat Interaction Application - A full-stack Scala 3 application for managing YouTube live chat interactions, tournaments, and StarCraft replay analysis. Built with Play Framework (server), ScalaJS (client), and PostgreSQL database.

## Build Commands

### Development
```bash
# Run in local development mode
./local-dev.sh
# Or manually:
sbt "server/run -Dconfig.file=server/conf/local.conf"

# Compile the entire project (server + client + shared)
sbt compile

# Continuous compilation
sbt ~compile
```

### Testing
```bash
# Run all tests
sbt test

# Run specific test suite
sbt "server/testOnly controllers.AuthControllerSpec"

# Run tests with coverage
sbt coverage test coverageReport
```

### Database
```bash
# Apply database evolutions (auto-applied in dev)
# Evolutions are in server/conf/evolutions/default/*.sql

# Clean and reset database (local development)
# Drop and recreate the database, then restart the app
```

### Production
```bash
# Create production distribution
sbt server/dist

# Run with production config
sbt "server/run -Dconfig.file=server/conf/production.conf"
```

## Architecture

### Multi-Project Build Structure
- **server/** - Play Framework backend (Scala 3.3.7)
- **client/** - ScalaJS frontend
- **shared/** - Cross-compiled models used by both server and client
- **build.sbt** - Multi-project SBT configuration with cross-platform setup

### Server Architecture Layers

#### 1. Models (`server/app/models/`)
Domain models follow a three-tier pattern:

**Component** → **Repository** → **DAO/Service**

- **Component traits** (`component/`): Slick table definitions with schema mappings
- **Repository classes** (`repository/`): Data access with dual API (Future + DBIO)
- **Model case classes**: Domain objects (User, Tournament, YtStreamer, etc.)

Example:
```scala
// Component defines schema
trait UserComponent {
  class UsersTable(tag: Tag) extends Table[User](tag, "users")
  val usersTable = TableQuery[UsersTable]
}

// Repository provides queries
class UserRepository extends UserComponent {
  def getById(id: Long): Future[Option[User]]       // Async
  def getByIdAction(id: Long): DBIO[Option[User]]   // Composable
}
```

#### 2. Controllers (`server/app/controllers/`)
All controllers extend `SilhouetteController` for authentication:
- **SecuredAction**: Requires authenticated user
- **UnsecuredAction**: Only for non-authenticated users
- **UserAwareAction**: Optional authentication

Key controllers:
- `AuthController` - YouTube OAuth2 authentication
- `TournamentController` - Tournament CRUD and lifecycle
- `FileUploadController` - Replay file uploads
- `AdminController` - Admin operations with role checks

#### 3. Services (`server/app/services/`)
Business logic layer with Future-based async operations:
- `ParseReplayFileService` - Integrates with external replay-parser microservice
- `TournamentService` - Tournament management
- `TournamentChallongeService` - Challonge API integration
- `UserService` - User management with alias generation
- `YoutubeLiveChatServiceTyped` - YouTube Live Chat polling coordination

#### 4. Actors (`server/app/actors/`)
Pekko Typed actors for concurrent operations:
- **YoutubeLiveChatPollingActor**: Polls YouTube Live Chat API, processes messages, matches with active polls
- **ChatRoomActor**: WebSocket chat room with client connection management
- **RootBehavior**: Top-level supervisor (empty, extensible)

Message protocols use sealed traits for type safety.

### Database (Slick + PostgreSQL)

#### Dual API Pattern
Every repository method has two variants:
```scala
// Returns Future[T] - for direct async use
def create(entity: T): Future[Long]

// Returns DBIO[T] - for composition in transactions
def createAction(entity: T): DBIO[Long]

// Use in transactions:
db.run(DBIO.seq(
  createAction(entity1),
  updateAction(entity2)
).transactionally)
```

#### Custom Type Mappings
Sealed traits map to database strings:
```scala
given BaseTypedType[TournamentStatus] = MappedColumnType.base[TournamentStatus, String](
  { case TournamentStatus.RegistrationOpen => "RegistrationOpen" },
  { case "RegistrationOpen" => TournamentStatus.RegistrationOpen }
)
```

#### Evolutions
Database migrations are in `server/conf/evolutions/default/*.sql`. Auto-applied in development. Each file has "# --- !Ups" and "# --- !Downs" sections.

### Authentication (Silhouette)

OAuth2 flow with YouTube provider:
1. User redirects to YouTube OAuth2 consent
2. Callback at `/authenticate/youtube` with authorization code
3. Exchange code for access token + refresh token
4. Store OAuth2Info in database
5. Create CookieAuthenticator for session

Key classes:
- `DefaultEnv` - Environment trait (User + CookieAuthenticator)
- `SilhouetteModule` - DI configuration
- `YouTubeProvider` - OAuth2 provider implementation
- `OAuth2TokenRefreshService` - Token refresh logic

### External Services

#### Replay Parser Microservice
Go service that parses StarCraft replay files. Deployed separately on Dokku.

**Communication**:
```scala
// Default: http://localhost:5000 (dev)
// Production: http://replay-parser.dokku-internal:5000
POST /parse-replay
Body: { "file": "<base64>" }
Response: GameInfo JSON
```

Health check endpoint: `GET /api/replay-parser/health`

#### Challonge API
Integration for tournament bracket management:
- Create tournaments
- Add participants
- Report match results
- Sync bracket state

### Shared Models

Cross-compiled models in `shared/src/main/scala/evolutioncomplete/`:
- `ParticipantShared`, `WinnerShared`, `GameStateShared`
- `StarCraftModelsShared` - SCRace, Team, SCPlayer
- Serialization with uPickle (`derives ReadWriter`)

Both server and client import these for type-safe communication.

## Configuration

Environment-specific configs in `server/conf/`:
- `application.conf` - Base defaults with env var placeholders
- `local.conf` - Local dev (git-ignored, copy from `local.conf.template`)
- `production.conf` - Production (git-ignored)
- `test.conf` - Test environment (H2 in-memory)

Key settings:
- `play.http.secret.key` - Session encryption
- `silhouette.*` - Authentication config
- `slick.dbs.default.*` - Database connection
- `replayparser.url` - Replay parser service URL

## Development Workflow

### Adding a New Model

1. **Create domain model** in `server/app/models/YourModel.scala`
2. **Create component** in `server/app/models/component/YourModelComponent.scala`:
   ```scala
   trait YourModelComponent {
     class YourModelsTable(tag: Tag) extends Table[YourModel](tag, "your_models") {
       def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
       // ... other columns
       def * = (id, ...) <> ((YourModel.apply _).tupled, YourModel.unapply)
     }
     val yourModelsTable = TableQuery[YourModelsTable]
   }
   ```
3. **Create repository** in `server/app/models/repository/YourModelRepository.scala`:
   ```scala
   @Singleton
   class YourModelRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)
       extends YourModelComponent {
     // Implement CRUD methods with Future + DBIO variants
   }
   ```
4. **Create evolution** in `server/conf/evolutions/default/N.sql`:
   ```sql
   # --- !Ups
   CREATE TABLE your_models (
     id SERIAL PRIMARY KEY,
     -- ... columns
   );

   # --- !Downs
   DROP TABLE your_models;
   ```

### Adding a New Controller

1. **Create controller** extending `SilhouetteController`
2. **Inject dependencies** via constructor
3. **Use SecuredAction** for authenticated endpoints
4. **Add routes** in `server/conf/routes`

### Adding a New Actor

1. **Define message protocol** as sealed trait
2. **Create behavior factory** with `Behaviors.setup`
3. **Register in ActorModule** if top-level
4. **Spawn child actors** from services or other actors

### Working with Transactions

Compose DBIO actions for atomic operations:
```scala
val transaction = for {
  userId <- userRepo.createAction(user)
  _ <- ytUserRepo.createAction(ytUser.copy(userId = userId))
  _ <- streamerRepo.createAction(streamer.copy(userId = userId))
} yield userId

db.run(transaction.transactionally)
```

## Important Patterns

### Sealed Trait Enumerations
Use sealed traits for type-safe enums:
```scala
sealed trait TournamentStatus
object TournamentStatus {
  case object RegistrationOpen extends TournamentStatus
  case object InProgress extends TournamentStatus
  case object Completed extends TournamentStatus
}
```

### Repository Composition
Repositories mix in multiple components for cross-table queries:
```scala
class TournamentMatchRepository
    extends TournamentMatchComponent
    with UserComponent
    with TournamentComponent {
  // Can join across all three tables
}
```

### Service Layer Pattern
Services coordinate multiple repositories:
```scala
@Singleton
class TournamentService @Inject()(
  tournamentRepo: TournamentRepository,
  participantRepo: TournamentRegistrationRepository,
  challongeService: TournamentChallongeService
) {
  def createTournament(data: TournamentData): Future[Tournament] = {
    for {
      tournament <- tournamentRepo.create(data)
      challongeTournament <- challongeService.create(tournament)
      updated <- tournamentRepo.update(tournament.copy(
        challongeTournamentId = Some(challongeTournament.id)
      ))
    } yield updated
  }
}
```

## Common Issues

### Scala 3 Migration
This project uses Scala 3. Key differences from Scala 2:
- `given`/`using` instead of `implicit`
- `derives` for automatic typeclass derivation
- `enum` for ADTs
- Indentation-based syntax supported

### Slick Custom Types
When adding custom column types, define `given` instances:
```scala
given BaseTypedType[CustomType] = MappedColumnType.base[CustomType, String](
  encode = _.toString,
  decode = CustomType.fromString
)
```

### Actor Message Serialization
Pekko Typed actors require serializable messages. Keep messages as simple case classes/objects.

### Play Evolution Conflicts
If evolutions fail, check:
1. Database state matches expected schema
2. Evolution files are numbered sequentially
3. No manual schema changes outside evolutions

## Deployment (Dokku)

See `technical_docs/dokku-deployment.md` for full guide.

Key environment variables for production:
- `PLAY_APPLICATION_SECRET` - Session key
- `SILHOUETTE_SIGNER_KEY` - JWT signing key
- `SILHOUETTE_CRYPTER_KEY` - Encryption key
- `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET` - OAuth2 credentials
- `DATABASE_URL` - PostgreSQL connection string
- `REPLAY_PARSER_URL` - Replay parser service endpoint

Network setup requires shared Docker network for service-to-service communication (see POST_DEPLOY_SCRIPT).
