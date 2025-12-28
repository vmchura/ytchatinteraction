#!/bin/bash
set -euo pipefail

DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"
APP_NAME="ytchatinteraction"
LOCAL_DIR="./logs-analysis"

mkdir -p "$LOCAL_DIR"

echo "Downloading access.json..."
ssh -i $SSH_KEY $DOKKU_SERVER "dokku run $APP_NAME cat /logs/access.json" > "$LOCAL_DIR/access.json"

echo "Downloading application.json..."
ssh -i $SSH_KEY $DOKKU_SERVER "dokku run $APP_NAME cat /logs/application.json" > "$LOCAL_DIR/application.json"

echo "Downloading user-activity.json..."
ssh -i $SSH_KEY $DOKKU_SERVER "dokku run $APP_NAME cat /logs/user-activity.json" > "$LOCAL_DIR/user-activity.json"

echo "Done. Files in $LOCAL_DIR"
