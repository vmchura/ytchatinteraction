#!/bin/bash

# Setup Minio on Dokku
APP_NAME="minio-storage"
DOKKU_SERVER="root@evolutioncomplete.com"
SSH_KEY="~/.ssh/id_hetzner"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🗄️  Setting up Minio on Dokku...${NC}"

# Function to run commands on Dokku server
run_on_dokku() {
    ssh -i $SSH_KEY $DOKKU_SERVER "$1"
}

# 1. Create Minio app
echo -e "${YELLOW}📦 Creating Minio app...${NC}"
run_on_dokku "dokku apps:create $APP_NAME"

# 2. Set up Docker image deployment
echo -e "${YELLOW}🐳 Setting up Docker deployment...${NC}"
run_on_dokku "dokku git:from-image $APP_NAME minio/minio:latest"

# 3. Create persistent storage for Minio data
echo -e "${YELLOW}💾 Setting up persistent storage...${NC}"
run_on_dokku "mkdir -p /var/lib/dokku/data/storage/$APP_NAME"
run_on_dokku "dokku storage:mount $APP_NAME /var/lib/dokku/data/storage/$APP_NAME:/data"

# 4. Set environment variables
echo -e "${YELLOW}🔧 Configuring environment variables...${NC}"
run_on_dokku "dokku config:set $APP_NAME MINIO_ROOT_USER=admin"
run_on_dokku "dokku config:set $APP_NAME MINIO_ROOT_PASSWORD=minioadmin123"
run_on_dokku "dokku config:set $APP_NAME MINIO_BROWSER_REDIRECT_URL=http://$APP_NAME.evolutioncomplete.com"

# 5. Set up the command to run Minio
echo -e "${YELLOW}⚙️  Setting up Minio command...${NC}"
run_on_dokku "dokku docker-options:add $APP_NAME run '--entrypoint=\"\"'"
run_on_dokku "dokku config:set $APP_NAME DOKKU_DOCKERFILE_CMD='server /data --console-address \":9001\"'"

# 6. Deploy using Docker image
echo -e "${YELLOW}🚀 Deploying Minio...${NC}"
run_on_dokku "dokku git:from-image $APP_NAME minio/minio:latest"

# 7. Set up domain
echo -e "${YELLOW}🌐 Setting up domain...${NC}"
run_on_dokku "dokku domains:add $APP_NAME $APP_NAME.evolutioncomplete.com"

# 8. Expose ports
echo -e "${YELLOW}🔌 Configuring ports...${NC}"
run_on_dokku "dokku ports:add $APP_NAME http:80:9000"
run_on_dokku "dokku ports:add $APP_NAME http:9001:9001"

echo -e "${GREEN}✅ Minio setup completed!${NC}"
echo -e "${BLUE}Access Minio at: http://$APP_NAME.evolutioncomplete.com${NC}"
echo -e "${BLUE}Console at: http://$APP_NAME.evolutioncomplete.com:9001${NC}"
echo -e "${YELLOW}Username: admin${NC}"
echo -e "${YELLOW}Password: minioadmin123${NC}"