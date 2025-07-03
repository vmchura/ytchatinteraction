#!/bin/bash
# Master script to deploy SCREP CLI integration completely

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== SCREP CLI Complete Deployment ===${NC}"
echo -e "${YELLOW}This script will deploy the SCREP CLI and configure integration with your Play Framework app.${NC}"
echo -e ""

# Step 1: Deploy SCREP CLI
echo -e "${BLUE}Step 1: Deploying SCREP CLI service...${NC}"
if ./bin/deploy-screp-cli.sh; then
    echo -e "${GREEN}✓ SCREP CLI deployment completed successfully${NC}"
else
    echo -e "${RED}✗ SCREP CLI deployment failed${NC}"
    exit 1
fi

echo -e ""

# Step 2: Configure network integration
echo -e "${BLUE}Step 2: Configuring network integration...${NC}"
if ./bin/configure-screp-network.sh; then
    echo -e "${GREEN}✓ Network configuration completed successfully${NC}"
else
    echo -e "${RED}✗ Network configuration failed${NC}"
    exit 1
fi

echo -e ""

# Step 3: Deploy main application
echo -e "${BLUE}Step 3: Deploying main application with SCREP integration...${NC}"
if ./deploy-dokku.sh; then
    echo -e "${GREEN}✓ Main application deployment completed successfully${NC}"
else
    echo -e "${RED}✗ Main application deployment failed${NC}"
    exit 1
fi

echo -e ""

# Step 4: Final verification
echo -e "${BLUE}Step 4: Final verification...${NC}"
echo -e "${YELLOW}Waiting for services to stabilize...${NC}"
sleep 30

# Test external access
echo -e "${YELLOW}Testing external SCREP access...${NC}"
if curl -s "http://screp.evolutioncomplete.com/health" > /dev/null; then
    echo -e "${GREEN}✓ External SCREP access working${NC}"
else
    echo -e "${RED}✗ External SCREP access failed${NC}"
fi

# Test main app access
echo -e "${YELLOW}Testing main application access...${NC}"
if curl -s "http://evolutioncomplete.com/screp/health" > /dev/null; then
    echo -e "${GREEN}✓ Main application SCREP integration working${NC}"
else
    echo -e "${RED}✗ Main application SCREP integration failed${NC}"
fi

echo -e ""
echo -e "${GREEN}=== SCREP CLI Integration Deployment Complete ===${NC}"
echo -e ""
echo -e "${BLUE}Service URLs:${NC}"
echo -e "  - SCREP CLI: http://screp.evolutioncomplete.com"
echo -e "  - Main App SCREP: http://evolutioncomplete.com/screp"
echo -e "  - Health Check: http://evolutioncomplete.com/screp/health"
echo -e ""
echo -e "${BLUE}API Endpoints:${NC}"
echo -e "  - Parse: POST http://evolutioncomplete.com/screp/api/parse"
echo -e "  - Overview: POST http://evolutioncomplete.com/screp/api/overview"
echo -e ""
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "1. Test the web interface at http://evolutioncomplete.com/screp"
echo -e "2. Upload a StarCraft replay file to verify functionality"
echo -e "3. Check the API documentation in docs/SCREP_INTEGRATION.md"
echo -e "4. Monitor logs if needed: dokku logs ytchatinteraction"
echo -e ""
echo -e "${GREEN}Deployment completed successfully!${NC}"
