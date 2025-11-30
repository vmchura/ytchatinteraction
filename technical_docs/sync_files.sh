#!/bin/bash

# Download file storage from Dokku server to local machine
# This script downloads all files from the persistent storage directory

DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"
REMOTE_PATH="/var/lib/dokku/data/storage/file-uploads"
LOCAL_PATH="/home/vmchura/DataspellProjects/ScrepUsage/input"  # Change this to your preferred local destination

echo "📥 Downloading files from Dokku server..."

# STEP 1: Create local directory if it doesn't exist
mkdir -p "$LOCAL_PATH"

# STEP 2: Use rsync to download all files
# Options explained:
# -a: archive mode (preserves permissions, timestamps, etc.)
# -v: verbose (shows progress)
# -z: compress during transfer (faster over network)
# -h: human-readable numbers
# --progress: show progress during transfer
# -e: specify SSH command with your key
rsync -avzh --progress \
  -e "ssh -i $SSH_KEY" \
  "$DOKKU_SERVER:$REMOTE_PATH/" \
  "$LOCAL_PATH/"

echo "✅ Download complete! Files saved to: $LOCAL_PATH"
echo "📊 Summary:"
du -sh "$LOCAL_PATH"
