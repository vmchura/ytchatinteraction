#!/bin/bash
# Script to deploy the application to Dokku

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting deployment to Dokku...${NC}"

# Ensure we're on the main branch
git checkout main

# Create a production build
echo -e "${YELLOW}Creating production build...${NC}"
sbt clean stage

# Push to Dokku
echo -e "${YELLOW}Pushing to Dokku...${NC}"
git push dokku main

echo -e "${GREEN}Deployment complete!${NC}"
