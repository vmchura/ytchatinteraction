# SCREP CLI Integration

This document explains how to integrate the SCREP CLI (StarCraft: Brood War replay parser) with your Play Framework application deployed on DOKKU.

## Overview

The SCREP CLI is deployed as a separate microservice on the same DOKKU server and exposed as an HTTP API. Your Play Framework application can communicate with it through internal Docker networking for optimal performance.

## Architecture

```
┌─────────────────────┐    ┌─────────────────────┐
│  Play Framework     │    │   SCREP CLI         │
│  (ytchatinteraction)│◄──►│   (screp-cli)       │
│                     │    │                     │
│  - Web Interface    │    │  - HTTP API Wrapper │
│  - File Upload      │    │  - Go CLI Binary    │
│  - Results Display  │    │  - Replay Parser    │
└─────────────────────┘    └─────────────────────┘
           │                          │
           └──────────────────────────┘
                 screp-network
```

## Deployment

### 1. Deploy SCREP CLI Service

Run the deployment script:

```bash
./bin/deploy-screp-cli.sh
```

This will:
- Clone the SCREP repository
- Create an HTTP API wrapper
- Deploy to DOKKU as `screp-cli` app
- Configure networking
- Set up domain at `screp.evolutioncomplete.com`

### 2. Configure Network Integration

Run the network configuration script:

```bash
./bin/configure-screp-network.sh
```

This will:
- Create a Docker network for inter-service communication
- Connect both applications to the network
- Set environment variables for internal communication
- Restart services to apply changes

### 3. Deploy Main Application

Deploy your Play Framework application with the updated configuration:

```bash
./deploy-dokku.sh
```

## Configuration

### Environment Variables

The following environment variables are configured for the main application:

- `SCREP_BASE_URL`: External URL for SCREP service (default: `http://screp.evolutioncomplete.com`)
- `SCREP_INTERNAL_URL`: Internal Docker network URL (default: `http://screp-cli.web:8080`)
- `SCREP_TIMEOUT`: Request timeout in seconds (default: 30)

### Application Configuration

In `conf/application.conf`:

```hocon
screp {
  base-url = "http://screp.evolutioncomplete.com"
  base-url = ${?SCREP_BASE_URL}
  
  timeout = 30
  timeout = ${?SCREP_TIMEOUT}
  
  internal-url = "http://screp-cli.web:8080"
  internal-url = ${?SCREP_INTERNAL_URL}
}
```

## API Endpoints

### SCREP CLI Service

- `GET /health` - Health check
- `GET /` - Service information
- `POST /api/parse` - Parse replay file
- `POST /api/overview` - Get replay overview

### Play Framework Integration

- `GET /screp` - Web interface for file upload
- `POST /screp/parse` - Parse replay and show results
- `POST /screp/overview` - Get overview and show results
- `GET /screp/health` - Health check (proxied)
- `POST /screp/api/parse` - API endpoint for parsing
- `POST /screp/api/overview` - API endpoint for overview

## Usage

### Web Interface

1. Navigate to `http://evolutioncomplete.com/screp`
2. Upload a StarCraft: Brood War replay file (.rep)
3. Configure parsing options:
   - Include Map Data
   - Include Commands
   - Include Computed Data
4. Choose "Parse Replay" for full JSON output or "Get Overview" for summary

### API Usage

#### Parse Replay

```bash
curl -X POST \
  http://evolutioncomplete.com/screp/api/parse \
  -F "replay=@sample.rep" \
  -G \
  -d "map=true" \
  -d "cmds=true" \
  -d "computed=true"
```

#### Get Overview

```bash
curl -X POST \
  http://evolutioncomplete.com/screp/api/overview \
  -F "replay=@sample.rep"
```

### Programmatic Usage (Scala)

```scala
import services.ScrepService
import javax.inject.Inject

class MyController @Inject()(screpService: ScrepService) {
  
  def parseReplay() = Action.async(parse.multipartFormData) { request =>
    request.body.file("replay") match {
      case Some(replayFile) =>
        screpService.parseReplayFromUpload(
          replayFile.ref,
          replayFile.filename,
          includeMap = true,
          includeCommands = true,
          includeComputed = true
        ).map { response =>
          if (response.success) {
            Ok(Json.toJson(response.data))
          } else {
            BadRequest(Json.obj("error" -> response.error))
          }
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "No file provided")))
    }
  }
}
```

## Service Classes

### ScrepService

The `ScrepService` class provides methods for:

- `healthCheck()`: Check if SCREP service is available
- `parseReplay()`: Parse replay file with options
- `getReplayOverview()`: Get human-readable overview
- `parseReplayFromUpload()`: Parse from uploaded temporary file
- `getReplayOverviewFromUpload()`: Get overview from uploaded file
- `isValidReplayFile()`: Validate file extension
- `getServiceInfo()`: Get service information

### ScrepController

The `ScrepController` provides web endpoints for:

- File upload interface
- Replay parsing and display
- API endpoints for external integration
- Health checks and service info

## File Support

The SCREP CLI supports:
- StarCraft: Brood War replay files (.rep)
- Both modern (1.18+) and legacy (pre-1.18) formats
- File size limit: 10MB per upload

## Troubleshooting

### Check Service Health

```bash
curl http://screp.evolutioncomplete.com/health
```

### Check Network Connectivity

```bash
# On DOKKU server
dokku run ytchatinteraction curl -s http://screp-cli.web:8080/health
```

### View Logs

```bash
# SCREP CLI logs
ssh -i ~/.ssh/id_hetzner root@91.99.13.219 "dokku logs screp-cli"

# Main app logs
ssh -i ~/.ssh/id_hetzner root@91.99.13.219 "dokku logs ytchatinteraction"
```

### Common Issues

1. **Service Unavailable**: Check if SCREP CLI is running
2. **Network Issues**: Ensure both apps are on the same network
3. **File Upload Errors**: Check file size and format
4. **Parse Errors**: Verify replay file is valid

## Security Considerations

- File uploads are limited to 10MB
- Only .rep files are accepted
- Uploaded files are automatically cleaned up after processing
- Internal network communication uses Docker networking
- External access is controlled by DOKKU proxy

## Performance Notes

- Internal network communication is faster than external HTTP calls
- Files are processed synchronously with configurable timeout
- Concurrent uploads are supported
- Parsed results can be cached if needed

## Development

### Local Development

For local development, you can run SCREP CLI separately:

```bash
# Clone and build SCREP
git clone https://github.com/icza/screp.git
cd screp/cmd/screp
go build

# Run locally
./screp sample.rep
```

Update your `conf/local.conf`:

```hocon
screp {
  base-url = "http://localhost:8080"
  timeout = 30
}
```

### Testing

The integration includes comprehensive error handling and validation:

- File type validation
- Size limits
- Timeout handling
- Network error recovery
- Service availability checks

## Future Enhancements

Potential improvements:
- Batch processing of multiple replays
- Caching of parsed results
- WebSocket for real-time parsing progress
- Integration with database for replay storage
- Advanced filtering and search capabilities
