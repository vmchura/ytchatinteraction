# AGENTS.md

This file provides guidance for AI agents working with this codebase.

## Project Overview

YouTube Chat Interaction Application - A full-stack Scala 3 application for managing YouTube live chat interactions, tournaments, and StarCraft replay analysis. Built with Play Framework (server), ScalaJS (client), and PostgreSQL database.

## Build Commands

```bash
# Compile entire project (server + client + shared)
sbt compile

# Run in local development mode
./local-dev.sh
# Or: sbt "server/run -Dconfig.file=server/conf/local.conf"

# Run all tests
sbt test

# Run a single test class
sbt "server/testOnly controllers.AuthControllerSpec"

# Run tests with coverage
sbt coverage test coverageReport

# Format code
sbt scalafmt

# Create production distribution
sbt server/dist
```

## Code Style Guidelines

### Scala 3 Syntax
- Use `given`/`using` instead of `implicit`
- Use `derives` for automatic typeclass derivation (e.g., `derives ReadWriter`)
- Use `enum` for ADTs where appropriate
- Prefer indentation-based syntax over braces where readable

### Imports
- Group imports: java.*, scala.*, third-party libs, project packages
- Use wildcard imports for Play Forms: `import play.api.data.Forms.*`
- Use explicit imports for models: `import models.{User, Tournament}`
- Import profile.api.* inside repository classes after getting dbConfig

### Naming Conventions
- Classes/objects: PascalCase (e.g., `TournamentRepository`)
- Methods/values: camelCase (e.g., `findById`, `createAction`)
- Constants: camelCase (e.g., `tournamentsTable`)
- Database tables: plural snake_case (e.g., `tournaments`)
- Case classes: singular (e.g., `case class Tournament(...)`)

### Types
- Use sealed traits for enums (e.g., `TournamentStatus`)
- Use `Option[T]` for nullable fields
- Use `Future[T]` for async operations
- Use `DBIO[T]` for composable database actions
- Use `Long` for IDs (auto-increment primary keys)
- Use `Instant` for timestamps

### Error Handling
- Use `Future.successful()` and `Future.failed()` for async results
- Return `Option[T]` for "not found" scenarios
- Use for-comprehensions for sequential async operations
- Handle errors in controllers with appropriate HTTP status codes

### Repository Pattern
Every repository method has two variants:
```scala
def findById(id: Long): Future[Option[T]]              // For direct use
def findByIdAction(id: Long): DBIO[Option[T]]          // For composition
```

Use `db.run(action.transactionally)` for transactions.

### Architecture Layers
1. **Models** (`models/`): Case classes + sealed trait enums
2. **Components** (`models/component/`): Slick table definitions
3. **Repositories** (`models/repository/`): Data access with dual API
4. **Services** (`services/`): Business logic
5. **Controllers** (`controllers/`): HTTP handlers, extend `SilhouetteController`

### Testing
- Use `PlaySpec` with `MockitoSugar` and `ScalaFutures`
- Mock external dependencies (repositories, services)
- Use `FakeRequest()` and `stubControllerComponents()`
- Tests run sequentially (configured in build.sbt)

### Database
- Evolutions in `server/conf/evolutions/default/*.sql`
- Use `# --- !Ups` and `# --- !Downs` sections
- Custom types: define `given BaseTypedType[T]` with `MappedColumnType.base`

### Formatting
- Scalafmt config in `.scalafmt.conf`
- Runner dialect: scala3
- Version: 3.10.1
