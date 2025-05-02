#!/bin/bash
# Script to deploy the application to Dokku

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting deployment to Dokku...${NC}"

# Ensure we're on the main branch
git checkout main

# Create a production build
echo -e "${YELLOW}Creating production build...${NC}"
sbt clean stage

# Add all files to git
echo -e "${YELLOW}Adding files to git...${NC}"
git add .

# Commit changes
echo -e "${YELLOW}Committing changes...${NC}"
git commit -m "Deployment $(date)"

# Push to Dokku
echo -e "${YELLOW}Pushing to Dokku...${NC}"
git push dokku main

# Check deployment status
echo -e "${YELLOW}Checking deployment status...${NC}"
ssh -i ~/.ssh/id_hetzner root@91.99.13.219 "dokku logs ytchatinteraction --tail 50"

echo -e "${GREEN}Deployment process completed!${NC}"
echo -e "${YELLOW}If the deployment was successful, your app should be available at evolutioncomplete.com${NC}"
echo -e "${YELLOW}If there were errors, check the logs for more details.${NC}"
