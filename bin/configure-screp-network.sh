#!/bin/bash
# Script to configure the main application to connect to SCREP CLI network

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
DOKKU_SERVER="root@91.99.13.219"
MAIN_APP_NAME="ytchatinteraction"
SCREP_APP_NAME="screp-cli"
SSH_KEY="~/.ssh/id_hetzner"
NETWORK_NAME="screp-network"

echo -e "${BLUE}Configuring network connection between apps...${NC}"

# Function to run commands on DOKKU server
run_on_dokku() {
    ssh -i $SSH_KEY $DOKKU_SERVER "$1"
}

# Step 1: Ensure the network exists
echo -e "${YELLOW}Ensuring network exists...${NC}"
run_on_dokku "dokku network:create $NETWORK_NAME" 2>/dev/null || true

# Step 2: Connect main app to SCREP network
echo -e "${YELLOW}Connecting main app to SCREP network...${NC}"
run_on_dokku "dokku network:set $MAIN_APP_NAME attach-post-create $NETWORK_NAME"

# Step 3: Connect SCREP app to network (in case it's not already connected)
echo -e "${YELLOW}Ensuring SCREP app is connected to network...${NC}"
run_on_dokku "dokku network:set $SCREP_APP_NAME attach-post-create $NETWORK_NAME"

# Step 4: Set internal URL configuration for main app
echo -e "${YELLOW}Setting SCREP internal URL configuration...${NC}"
run_on_dokku "dokku config:set $MAIN_APP_NAME SCREP_INTERNAL_URL=http://screp-cli.web:8080"
run_on_dokku "dokku config:set $MAIN_APP_NAME SCREP_BASE_URL=http://screp-cli.web:8080"

# Step 5: Restart both apps to apply network changes
echo -e "${YELLOW}Restarting applications...${NC}"
run_on_dokku "dokku ps:restart $MAIN_APP_NAME"
run_on_dokku "dokku ps:restart $SCREP_APP_NAME"

# Step 6: Test connectivity
echo -e "${YELLOW}Testing connectivity...${NC}"
sleep 15

# Test if main app can reach SCREP service
echo -e "${YELLOW}Testing internal network connectivity...${NC}"
run_on_dokku "dokku run $MAIN_APP_NAME curl -s http://screp-cli.web:8080/health" || echo -e "${RED}Internal connectivity test failed${NC}"

# Step 7: Show network information
echo -e "${YELLOW}Network configuration:${NC}"
run_on_dokku "dokku network:info $NETWORK_NAME"

echo -e "${GREEN}Network configuration completed!${NC}"
echo -e "${BLUE}Configuration Summary:${NC}"
echo -e "  - Network: $NETWORK_NAME"
echo -e "  - Main App: $MAIN_APP_NAME"
echo -e "  - SCREP App: $SCREP_APP_NAME"
echo -e "  - Internal URL: http://screp-cli.web:8080"
echo -e ""
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "1. Deploy your main application with the updated configuration"
echo -e "2. Test the SCREP integration at: http://evolutioncomplete.com/screp"
echo -e "3. Check logs if there are any issues: dokku logs $MAIN_APP_NAME"
