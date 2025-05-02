#!/bin/bash
# Production environment launcher script

# Set the necessary environment variables for production
# Alternatively, you can source these from a .env file (not in git)

# Application secret (should be a long, random string)
export PLAY_APPLICATION_SECRET="your-production-secret-here"

# Silhouette authentication keys (should be at least 16 characters)
export SILHOUETTE_SIGNER_KEY="your-production-signer-key"
export SILHOUETTE_CRYPTER_KEY="your-production-crypter-key"

# YouTube API credentials
export YOUTUBE_CLIENT_ID="your-production-client-id"
export YOUTUBE_CLIENT_SECRET="your-production-client-secret"
export YOUTUBE_REDIRECT_URL="https://your-production-domain.com/authenticate/youtube"
export YOUTUBE_API_KEY="your-production-api-key"

# Database configuration
export DATABASE_URL="jdbc:postgresql://your-production-db-host:5432/your-production-db-name"
export DATABASE_USER="your-production-db-user"
export DATABASE_PASSWORD="your-production-db-password"

# Allowed hosts
export ALLOWED_HOSTS="your-production-domain.com"

# Run the Play application with production config
sbt "run -Dconfig.file=conf/production.conf"

# Alternatively, for a production deployment
# sbt dist
# And then run the packaged application with:
# ./target/universal/stage/bin/your-app-name -Dconfig.file=conf/production.conf
