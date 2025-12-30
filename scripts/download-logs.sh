#!/bin/bash

set -euo pipefail

DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"
APP_NAME="ytchatinteraction"

LOCAL_DIR="./logs-analysis"
DATE=$(date +%Y%m%d_%H%M%S)
ARCHIVE_NAME="logs_${APP_NAME}_${DATE}.tar.gz"

mkdir -p "$LOCAL_DIR"

echo "Creating logs archive inside container..."

ssh -i "$SSH_KEY" "$DOKKU_SERVER" '
  cd /var/lib/dokku/data/storage/ytchat-logs &&
  find . -maxdepth 1 -type f \
    \( -name "access.json.*.gz" \
       -o -name "application.json.*.gz" \
       -o -name "user-activity.json.*.gz" \) \
  | tar -czf /tmp/'"$ARCHIVE_NAME"' --files-from=-
'

echo "Downloading logs archive..."

scp -i $SSH_KEY \
  "$DOKKU_SERVER:/tmp/${ARCHIVE_NAME}" \
  "${LOCAL_DIR}/"

echo "Cleaning up remote archive..."

ssh -i $SSH_KEY $DOKKU_SERVER \
  "rm -f /tmp/${ARCHIVE_NAME}"

echo "Logs archived at ${LOCAL_DIR}/${ARCHIVE_NAME}"

