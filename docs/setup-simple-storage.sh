#!/bin/bash

# Simple file storage setup on your Dokku server
# This script creates a persistent file storage solution that survives app deployments
DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"

echo "🗄️ Setting up simple file storage on your server..."

# STEP 1: Create a persistent storage directory on the host server
# This directory lives outside of your app container, so files persist across deployments
# /var/lib/dokku/data/storage/ is Dokku's standard location for persistent data
ssh -i $SSH_KEY $DOKKU_SERVER "mkdir -p /var/lib/dokku/data/storage/file-uploads"

# STEP 2: Set proper ownership
# dokku:dokku ensures the Dokku system can read/write to this directory
# This is important for container access permissions
ssh -i $SSH_KEY $DOKKU_SERVER "chown -R dokku:dokku /var/lib/dokku/data/storage/file-uploads"

# STEP 3: Set proper permissions
# 755 means: owner (dokku) can read/write/execute, group and others can read/execute
# This allows your app containers to read and write files
ssh -i $SSH_KEY $DOKKU_SERVER "chmod 755 /var/lib/dokku/data/storage/file-uploads"

# STEP 4: Mount the host directory into your app container
# This creates a "bind mount" - the host directory appears inside your container
# Format: dokku storage:mount APP_NAME HOST_PATH:CONTAINER_PATH
# - HOST_PATH: /var/lib/dokku/data/storage/file-uploads (on server)
# - CONTAINER_PATH: /app/uploads (inside your Play app container)
ssh -i $SSH_KEY $DOKKU_SERVER "dokku storage:mount ytchatinteraction /var/lib/dokku/data/storage/file-uploads:/app/uploads"

# STEP 5: Restart your app to pick up the new mount
# This restarts your container with the new volume mount active
# After this, your Play app can read/write files to /app/uploads
ssh -i $SSH_KEY $DOKKU_SERVER "dokku ps:restart ytchatinteraction"
