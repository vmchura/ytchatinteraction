#!/bin/bash

# Enhanced Dokku deployment script for Scala Play Framework application
# Usage: ./deploy-dokku.sh [app-name] [dokku-server]

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
APP_NAME=${1:-"ytchatinteraction"}
DOKKU_SERVER=${2:-"root@evolutioncomplete.com"}
SSH_KEY="~/.ssh/id_hetzner"
DOKKU_DOMAIN="evolutioncomplete.com"

echo -e "${BLUE}🚀 Deploying Scala Play Application to Dokku...${NC}"
echo -e "${BLUE}===============================================${NC}"
echo -e "App Name: ${YELLOW}$APP_NAME${NC}"
echo -e "Server: ${YELLOW}$DOKKU_SERVER${NC}"
echo -e "Domain: ${YELLOW}$APP_NAME.$DOKKU_DOMAIN${NC}"
echo ""

# Function to run commands on Dokku server
run_on_dokku() {
    ssh -i $SSH_KEY $DOKKU_SERVER "$1"
}

# Step 1: Create Dokku app if it doesn't exist
echo -e "${YELLOW}📋 Checking if Dokku app exists...${NC}"
if ! run_on_dokku "dokku apps:exists $APP_NAME" 2>/dev/null; then
    echo -e "${YELLOW}🆕 Creating Dokku app: $APP_NAME${NC}"
    run_on_dokku "dokku apps:create $APP_NAME"
    echo -e "${GREEN}✅ App created successfully${NC}"
else
    echo -e "${GREEN}✅ App $APP_NAME already exists${NC}"
fi

# Step 2: Set up Play Framework buildpack
echo -e "${YELLOW}🔧 Setting up Play Framework buildpack...${NC}"
run_on_dokku "dokku buildpacks:set $APP_NAME https://github.com/heroku/heroku-buildpack-scala.git"
echo -e "${GREEN}✅ Buildpack configured${NC}"

# Step 3: Configure Java version and SBT settings
echo -e "${YELLOW}🔧 Configuring Java and SBT settings...${NC}"
run_on_dokku "dokku config:set $APP_NAME JAVA_OPTS='-Xmx2g -Xms1g'"
run_on_dokku "dokku config:set $APP_NAME SBT_OPTS='-Xmx2g -Xms1g'"
echo -e "${GREEN}✅ Java and SBT settings configured${NC}"

# Step 4: Add/Update Dokku git remote
echo -e "${YELLOW}🔗 Setting up git remote...${NC}"
if git remote | grep -q dokku; then
    echo -e "${YELLOW}🔄 Updating existing Dokku remote...${NC}"
    git remote set-url dokku dokku@$(echo $DOKKU_SERVER | cut -d'@' -f2):$APP_NAME
else
    echo -e "${YELLOW}➕ Adding new Dokku remote...${NC}"
    git remote add dokku dokku@$(echo $DOKKU_SERVER | cut -d'@' -f2):$APP_NAME
fi
echo -e "${GREEN}✅ Dokku remote configured${NC}"

# Step 5: Build the application locally first
echo -e "${YELLOW}🏗️  Building application locally...${NC}"
echo -e "${BLUE}This may take a few minutes...${NC}"

# Clean and stage the server project
sbt server/stage

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Local build failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Local build successful${NC}"

# Step 6: Prepare git for deployment
echo -e "${YELLOW}📦 Preparing git for deployment...${NC}"

# Ensure we're on the main branch
git checkout main 2>/dev/null || git checkout master 2>/dev/null || echo "Using current branch"

# Add all files to git
git add .

# Commit changes
git commit -m "Deployment $(date)" || echo "No changes to commit"

# Step 7: Deploy to Dokku
echo -e "${YELLOW}🚀 Deploying to Dokku...${NC}"
echo -e "${BLUE}This may take several minutes...${NC}"

# Fetch the latest from dokku remote to check status
echo -e "${YELLOW}📥 Checking remote repository status...${NC}"
GIT_SSH_COMMAND="ssh -i $SSH_KEY" git fetch dokku || echo "Could not fetch from remote (this is normal for first deployment)"

# Push to Dokku using SSH key - force push to handle non-fast-forward issues
echo -e "${YELLOW}🔄 Pushing to Dokku (force push to handle any conflicts)...${NC}"
GIT_SSH_COMMAND="ssh -i $SSH_KEY" git push dokku HEAD:main --force

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Deployment failed${NC}"
    echo -e "${YELLOW}💡 Check logs: ssh -i $SSH_KEY $DOKKU_SERVER 'dokku logs $APP_NAME --tail'${NC}"
    exit 1
fi

# Step 8: Set up domain
echo -e "${YELLOW}🌐 Setting up domain...${NC}"
if ! run_on_dokku "dokku domains:report $APP_NAME | grep -q '$APP_NAME.$DOKKU_DOMAIN'"; then
    run_on_dokku "dokku domains:add $APP_NAME $APP_NAME.$DOKKU_DOMAIN"
    echo -e "${GREEN}✅ Domain added: $APP_NAME.$DOKKU_DOMAIN${NC}"
else
    echo -e "${GREEN}✅ Domain already configured${NC}"
fi

# Step 9: Configure database (if needed)
echo -e "${YELLOW}🗄️  Checking database configuration...${NC}"
if run_on_dokku "dokku postgres:exists postgres" 2>/dev/null; then
    if ! run_on_dokku "dokku postgres:linked postgres $APP_NAME" 2>/dev/null; then
        echo -e "${YELLOW}🔗 Linking PostgreSQL database...${NC}"
        run_on_dokku "dokku postgres:link postgres $APP_NAME"
        echo -e "${GREEN}✅ Database linked${NC}"
    else
        echo -e "${GREEN}✅ Database already linked${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  No PostgreSQL service found. You may need to create one:${NC}"
    echo -e "${YELLOW}   dokku postgres:create postgres${NC}"
fi

# Step 10: Check deployment status
echo -e "${YELLOW}📊 Checking deployment status...${NC}"
sleep 10

if run_on_dokku "dokku ps:inspect $APP_NAME" | grep -q "running"; then
    echo -e "${GREEN}✅ Application is running${NC}"
else
    echo -e "${RED}❌ Application may not be running properly${NC}"
    echo -e "${YELLOW}💡 Check logs: ssh -i $SSH_KEY $DOKKU_SERVER 'dokku logs $APP_NAME --tail'${NC}"
fi

# Step 11: Test the deployment
echo -e "${YELLOW}🧪 Testing deployment...${NC}"
echo -e "${BLUE}Waiting for app to start...${NC}"
sleep 15

# Test if the app is responding
if curl -s --max-time 10 "http://$APP_NAME.$DOKKU_DOMAIN/" | grep -q -i "html\|ytchatinteraction\|play"; then
    echo -e "${GREEN}✅ Application is responding${NC}"
else
    echo -e "${YELLOW}⚠️  Application may need more time to start${NC}"
    echo -e "${YELLOW}💡 Try again in a few minutes or check logs${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Deployment completed!${NC}"
echo -e "${BLUE}===============================================${NC}"
echo -e "🌍 Your app is available at:"
echo -e "   ${YELLOW}http://$APP_NAME.$DOKKU_DOMAIN${NC}"
echo ""
echo -e "🛠️  Useful commands:"
echo -e "   View logs:    ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku logs $APP_NAME --tail'${NC}"
echo -e "   Restart app:  ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku ps:restart $APP_NAME'${NC}"
echo -e "   App status:   ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku ps:report $APP_NAME'${NC}"
echo -e "   App config:   ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku config $APP_NAME'${NC}"
echo ""
echo -e "📝 Test your application:"
echo -e "   ${YELLOW}curl http://$APP_NAME.$DOKKU_DOMAIN/${NC}"
echo ""
echo -e "🔄 To redeploy:"
echo -e "   ${YELLOW}./deploy-dokku.sh${NC}"
echo ""
echo -e "🗄️  Database commands (if needed):"
echo -e "   Connect to DB: ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku postgres:connect postgres'${NC}"
echo -e "   DB info:       ${YELLOW}ssh -i $SSH_KEY $DOKKU_SERVER 'dokku postgres:info postgres'${NC}"
