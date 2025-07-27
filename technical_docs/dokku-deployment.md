# Dokku Deployment Guide

This document describes how to deploy both the Play Framework application and the Go replay-parser service on Dokku.

## Services Architecture

1. **Play Framework App** (`ytchatinteraction`) - Main web application
2. **Go Replay Parser** (`replay-parser`) - Microservice for parsing StarCraft replay files

## Environment Variables

### Play Framework App

```bash
# Set these environment variables on your Dokku app
dokku config:set ytchatinteraction \
  PLAY_APPLICATION_SECRET="your-secret-key-here" \
  SILHOUETTE_SIGNER_KEY="your-signer-key-here" \
  SILHOUETTE_CRYPTER_KEY="your-crypter-key-here" \
  YOUTUBE_REDIRECT_URL="https://your-domain.com/authenticate/youtube" \
  YOUTUBE_CLIENT_ID="your-youtube-client-id" \
  YOUTUBE_CLIENT_SECRET="your-youtube-client-secret" \
  YOUTUBE_API_KEY="your-youtube-api-key" \
  DATABASE_URL="postgres://user:pass@host:port/dbname" \
  REPLAY_PARSER_URL="http://replay-parser.dokku-internal:5000"
```

### Go Replay Parser App

```bash
# Set port for the replay parser (Dokku will set this automatically)
dokku config:set replay-parser \
  PORT=5000
```

## Internal Service Communication

When both services are deployed on the same Dokku instance, they can communicate using Dokku's internal networking:

- **From Play App to Parser**: `http://replay-parser.dokku-internal:5000`
- **External Access**: `https://your-parser-domain.com`

## Deployment Steps

### 1. Deploy the Replay Parser Service

```bash
# Create the app
dokku apps:create replay-parser

# Add your Go code and deploy
git push dokku-replay-parser main

# The service will be available at http://replay-parser.dokku-internal:5000 internally
```

### 2. Deploy the Play Framework App

```bash
# Create the app
dokku apps:create ytchatinteraction

# Set environment variables (see above)
dokku config:set ytchatinteraction REPLAY_PARSER_URL="http://replay-parser.dokku-internal:5000"

# Deploy
git push dokku-main main
```

## Health Check Endpoints

### Replay Parser Service

- **Health Check**: `GET /health`
- **Service Info**: `GET /`
- **Parse Replay**: `POST /parse-replay`

### Play Framework App

- **Replay Parser Health**: `GET /replay-parser/health` (UI)
- **Health API**: `GET /api/replay-parser/health` (JSON)
- **Status API**: `GET /api/replay-parser/status` (JSON)

## Troubleshooting

### Check Service Status

```bash
# Check if replay-parser is running
dokku ps:report replay-parser

# Check if main app is running
dokku ps:report ytchatinteraction

# Check logs
dokku logs replay-parser --tail
dokku logs ytchatinteraction --tail
```

### Test Internal Communication

```bash
# SSH into the main app and test connectivity
dokku run ytchatinteraction curl http://replay-parser.dokku-internal:5000/health
```

### External Testing

```bash
# Test the parser service externally (if exposed)
curl https://your-parser-domain.com/health

# Test the main app's health check
curl https://your-main-domain.com/api/replay-parser/health
```

## Security Considerations

1. **Internal Services**: The replay-parser service should ideally only be accessible internally unless external access is specifically needed.

2. **API Keys**: Store all sensitive configuration in environment variables, never in code.

3. **HTTPS**: Ensure all external communication uses HTTPS in production.

## Example Dokku Configuration

```bash
# Example complete setup
dokku config:set ytchatinteraction \
  PLAY_APPLICATION_SECRET="$(openssl rand -base64 32)" \
  SILHOUETTE_SIGNER_KEY="$(openssl rand -base64 32)" \
  SILHOUETTE_CRYPTER_KEY="$(openssl rand -base64 32)" \
  REPLAY_PARSER_URL="http://replay-parser.dokku-internal:5000" \
  DATABASE_URL="postgres://user:pass@host:port/dbname"
```
