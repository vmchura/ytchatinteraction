# YouTube Chat Interaction Application

## Configuration Guide

This application uses Play Framework's configuration approach to handle different environments securely. Here's how to set up and run the application in different environments.

### Configuration Files

The application uses the following configuration files:

- `conf/application.conf`: The base configuration file that defines default values and structure.
- `conf/youtube.conf`: Contains YouTube API-specific configuration.
- `conf/local.conf`: Local development configuration (not in git).
- `conf/production.conf`: Production environment configuration (not in git).
- `conf/test.conf`: Test environment configuration.

### Local Development Setup

1. Create your `local.conf` file based on the template:
   ```bash
   cp conf/local.conf.template conf/local.conf
   ```

2. Edit `conf/local.conf` and fill in your development values:
   - YouTube API credentials
   - Database connection details
   - Security keys

3. Run the application with your local configuration:
   ```bash
   ./local-dev.sh
   ```
   
   Or manually:
   ```bash
   sbt "run -Dconfig.file=conf/local.conf"
   ```

### Production Deployment

For production deployment, you have two options:

#### Option 1: Environment Variables

Set all required environment variables and run the application with the production configuration:

```bash
# Set environment variables (see production.sh for the complete list)
export PLAY_APPLICATION_SECRET="your-secret"
export SILHOUETTE_SIGNER_KEY="your-signer-key"
# ... and other required variables

# Run with production config
sbt "run -Dconfig.file=conf/production.conf"
```

#### Option 2: Production Config File

1. Create a `production.conf` file (not tracked by git):
   ```
   include "application.conf"
   
   # Override production values here
   play.http.secret.key="your-production-secret"
   # ... other overrides
   ```

2. Run the application with:
   ```bash
   sbt "run -Dconfig.file=conf/production.conf"
   ```

### Testing Environment

The test environment uses its own configuration in `conf/test.conf`, which is set up to use H2 in-memory database.

To run tests:

```bash
sbt test
```

### Security Notes

1. Never commit sensitive values to git. Always use environment variables or files excluded from git for secret keys, passwords, and API credentials.
2. In production, always set:
   - Strong secret keys
   - Secure cookies (HTTPS only)
   - Proper allowed hosts
   - Database credentials via environment variables

### Available Scripts

- `local-dev.sh`: Run the application in local development mode
- `production.sh`: Template for running in production with environment variables

### Configuration Structure

The configuration follows a hierarchical approach where:
1. `application.conf` provides defaults and environment variable placeholders
2. Environment-specific configs (local.conf, production.conf) include application.conf and override values
3. Environment variables can override any setting at runtime

This structure ensures your application can run securely in any environment while keeping sensitive information protected.


## Installation

...
### Connection ytchatinteraciton with replay-parser

#### On your Dokku host:
dokku plugin:install https://github.com/baikunz/dokku-post-deploy-script.git post-deploy-script

#### Copy the POST_DEPLOY_SCRIPT file to the correct location
sudo cp POST_DEPLOY_SCRIPT /home/dokku/ytchatinteraction/POST_DEPLOY_SCRIPT
sudo chmod +x /home/dokku/ytchatinteraction/POST_DEPLOY_SCRIPT
sudo chown dokku:dokku /home/dokku/ytchatinteraction/POST_DEPLOY_SCRIPT

### After deploying, output expected:

```
-----> Executing post deploy script...
       Running post-deploy network setup for ytchatinteraction...
Found containers:
YTCHAT_CONTAINER: ytchatinteraction.web.1
REPLAY_CONTAINER: replay-parser.web.1
Connecting ytchatinteraction.web.1 to dokku-shared-network...
Connecting replay-parser.web.1 to dokku-shared-network...
Already connected or connection failed
Post-deploy network setup completed
-----> Updated schedule file
```