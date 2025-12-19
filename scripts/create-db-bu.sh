#!/bin/bash
set -euo pipefail
DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"
DB_SERVICE="ytchat-db"
APP_NAME="ytchatinteraction"
LOCAL_DIR="./backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$LOCAL_DIR/${APP_NAME}_${DATE}.sql.gz"
mkdir -p "$LOCAL_DIR"
echo "Exporting database..."
ssh -i "$SSH_KEY" "$DOKKU_SERVER" "dokku postgres:export $DB_SERVICE" | gzip > "$BACKUP_FILE"
echo "Backup created: $BACKUP_FILE"
echo "Backup size: $(du -h "$BACKUP_FILE" | cut -f1)"

# Create db
# createdb -h localhost -U postgres ytchat_local
# Import
# gunzip -c ytchatinteraction_20251219_203708.sql.gz | pg_restore -h localhost -U postgres -d ytchat_local
