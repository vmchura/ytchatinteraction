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

ssh -i $SSH_KEY $DOKKU_SERVER <<EOF
  dokku enter $APP_NAME bash -c '
    cd /app/logs || exit 1
    tar -czf /tmp/${ARCHIVE_NAME} *.gz
  '
EOF

echo "Downloading logs archive..."

scp -i $SSH_KEY \
  "$DOKKU_SERVER:/tmp/${ARCHIVE_NAME}" \
  "${LOCAL_DIR}/"

echo "Cleaning up remote archive..."

ssh -i $SSH_KEY $DOKKU_SERVER \
  "rm -f /tmp/${ARCHIVE_NAME}"

echo "Logs archived at ${LOCAL_DIR}/${ARCHIVE_NAME}"

