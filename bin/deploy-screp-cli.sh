#!/bin/bash
# Script to deploy SCREP CLI to DOKKU server and make it available for other applications

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
DOKKU_SERVER="root@evolutioncomplete.com"
DOKKU_APP_NAME="screp-cli"
SCREP_REPO="https://github.com/icza/screp.git"
SSH_KEY="~/.ssh/id_hetzner"
DOKKU_DOMAIN="evolutioncomplete.com"

echo -e "${BLUE}Starting SCREP CLI deployment to DOKKU...${NC}"

# Function to run commands on DOKKU server
run_on_dokku() {
    ssh -i $SSH_KEY $DOKKU_SERVER "$1"
}

# Function to check if app exists
check_app_exists() {
    run_on_dokku "dokku apps:exists $DOKKU_APP_NAME" 2>/dev/null
    return $?
}

# Step 1: Create DOKKU app if it doesn't exist
echo -e "${YELLOW}Checking if DOKKU app exists...${NC}"
if ! check_app_exists; then
    echo -e "${YELLOW}Creating DOKKU app: $DOKKU_APP_NAME${NC}"
    run_on_dokku "dokku apps:create $DOKKU_APP_NAME"
else
    echo -e "${GREEN}DOKKU app $DOKKU_APP_NAME already exists${NC}"
fi

# Step 2: Set up buildpack for Go application
echo -e "${YELLOW}Setting up Go buildpack...${NC}"
run_on_dokku "dokku buildpacks:set $DOKKU_APP_NAME https://github.com/heroku/heroku-buildpack-go.git"

# Step 3: Create temporary directory and clone SCREP
echo -e "${YELLOW}Preparing SCREP source code...${NC}"
TEMP_DIR=$(mktemp -d)
cd $TEMP_DIR

echo -e "${YELLOW}Cloning SCREP repository...${NC}"
git clone $SCREP_REPO screp
cd screp

# Step 4: Create necessary files for DOKKU deployment
echo -e "${YELLOW}Creating DOKKU deployment files...${NC}"

# Create go.mod file if it doesn't exist
if [ ! -f go.mod ]; then
    echo -e "${YELLOW}Creating go.mod file...${NC}"
    cat > go.mod << 'EOF'
module github.com/icza/screp

go 1.21

require (
    github.com/icza/bitio v1.1.0
)
EOF
fi

# Create Procfile for DOKKU
cat > Procfile << 'EOF'
web: ./screp-server
EOF

# Create a simple HTTP server wrapper for SCREP
cat > screp-server.go << 'EOF'
package main

import (
    "encoding/json"
    "fmt"
    "io"
    "log"
    "net/http"
    "os"
    "os/exec"
    "path/filepath"
    "strings"
    "time"
)

type ScrepResponse struct {
    Success bool        `json:"success"`
    Data    interface{} `json:"data,omitempty"`
    Error   string      `json:"error,omitempty"`
}

type HealthResponse struct {
    Status    string    `json:"status"`
    Timestamp time.Time `json:"timestamp"`
    Version   string    `json:"version"`
}

func main() {
    port := os.Getenv("PORT")
    if port == "" {
        port = "8080"
    }

    // Create uploads directory
    os.MkdirAll("uploads", 0755)

    http.HandleFunc("/", homeHandler)
    http.HandleFunc("/health", healthHandler)
    http.HandleFunc("/api/parse", parseHandler)
    http.HandleFunc("/api/overview", overviewHandler)
    
    log.Printf("SCREP CLI server starting on port %s", port)
    if err := http.ListenAndServe(":"+port, nil); err != nil {
        log.Fatal("Server failed to start:", err)
    }
}

func homeHandler(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    response := map[string]interface{}{
        "service": "SCREP CLI API",
        "version": "1.0.0",
        "endpoints": map[string]string{
            "/health":       "GET - Service health check",
            "/api/parse":    "POST - Parse replay file (multipart/form-data with 'replay' field)",
            "/api/overview": "POST - Get replay overview (multipart/form-data with 'replay' field)",
        },
    }
    json.NewEncoder(w).Encode(response)
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    
    response := HealthResponse{
        Status:    "healthy",
        Timestamp: time.Now(),
        Version:   "1.0.0",
    }
    
    json.NewEncoder(w).Encode(response)
}

func parseHandler(w http.ResponseWriter, r *http.Request) {
    if r.Method != "POST" {
        http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
        return
    }

    w.Header().Set("Content-Type", "application/json")

    // Parse multipart form
    if err := r.ParseMultipartForm(10 << 20); err != nil { // 10MB limit
        sendError(w, "Failed to parse multipart form", http.StatusBadRequest)
        return
    }

    file, header, err := r.FormFile("replay")
    if err != nil {
        sendError(w, "No replay file provided", http.StatusBadRequest)
        return
    }
    defer file.Close()

    // Validate file extension
    if !strings.HasSuffix(strings.ToLower(header.Filename), ".rep") {
        sendError(w, "Invalid file type. Only .rep files are supported", http.StatusBadRequest)
        return
    }

    // Save uploaded file
    filename := fmt.Sprintf("uploads/%d_%s", time.Now().Unix(), header.Filename)
    dst, err := os.Create(filename)
    if err != nil {
        sendError(w, "Failed to save file", http.StatusInternalServerError)
        return
    }
    defer dst.Close()
    defer os.Remove(filename) // Clean up after processing

    if _, err := io.Copy(dst, file); err != nil {
        sendError(w, "Failed to save file", http.StatusInternalServerError)
        return
    }

    // Get additional flags from query parameters
    flags := []string{}
    if r.URL.Query().Get("map") == "true" {
        flags = append(flags, "-map")
    }
    if r.URL.Query().Get("cmds") == "true" {
        flags = append(flags, "-cmds")
    }
    if r.URL.Query().Get("computed") == "true" {
        flags = append(flags, "-computed")
    }

    // Execute screp command
    args := append(flags, filename)
    cmd := exec.Command("./screp", args...)
    output, err := cmd.CombinedOutput()
    
    if err != nil {
        sendError(w, fmt.Sprintf("SCREP execution failed: %s\nOutput: %s", err.Error(), string(output)), http.StatusInternalServerError)
        return
    }

    // Try to parse JSON output
    var result interface{}
    if err := json.Unmarshal(output, &result); err != nil {
        // If not JSON, return as string
        result = string(output)
    }

    sendSuccess(w, result)
}

func overviewHandler(w http.ResponseWriter, r *http.Request) {
    if r.Method != "POST" {
        http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
        return
    }

    w.Header().Set("Content-Type", "application/json")

    // Parse multipart form
    if err := r.ParseMultipartForm(10 << 20); err != nil { // 10MB limit
        sendError(w, "Failed to parse multipart form", http.StatusBadRequest)
        return
    }

    file, header, err := r.FormFile("replay")
    if err != nil {
        sendError(w, "No replay file provided", http.StatusBadRequest)
        return
    }
    defer file.Close()

    // Validate file extension
    if !strings.HasSuffix(strings.ToLower(header.Filename), ".rep") {
        sendError(w, "Invalid file type. Only .rep files are supported", http.StatusBadRequest)
        return
    }

    // Save uploaded file
    filename := fmt.Sprintf("uploads/%d_%s", time.Now().Unix(), header.Filename)
    dst, err := os.Create(filename)
    if err != nil {
        sendError(w, "Failed to save file", http.StatusInternalServerError)
        return
    }
    defer dst.Close()
    defer os.Remove(filename) // Clean up after processing

    if _, err := io.Copy(dst, file); err != nil {
        sendError(w, "Failed to save file", http.StatusInternalServerError)
        return
    }

    // Execute screp command with overview flag
    cmd := exec.Command("./screp", "-overview", filename)
    output, err := cmd.CombinedOutput()
    
    if err != nil {
        sendError(w, fmt.Sprintf("SCREP execution failed: %s\nOutput: %s", err.Error(), string(output)), http.StatusInternalServerError)
        return
    }

    sendSuccess(w, string(output))
}

func sendSuccess(w http.ResponseWriter, data interface{}) {
    response := ScrepResponse{
        Success: true,
        Data:    data,
    }
    json.NewEncoder(w).Encode(response)
}

func sendError(w http.ResponseWriter, message string, statusCode int) {
    w.WriteHeader(statusCode)
    response := ScrepResponse{
        Success: false,
        Error:   message,
    }
    json.NewEncoder(w).Encode(response)
}
EOF

# Create main.go that builds both screp CLI and server
cat > main.go << 'EOF'
package main

import (
    "log"
    "os"
    "os/exec"
)

func main() {
    // Build the screp CLI first
    log.Println("Building SCREP CLI...")
    cmd := exec.Command("go", "build", "-o", "screp", "./cmd/screp")
    cmd.Stdout = os.Stdout
    cmd.Stderr = os.Stderr
    if err := cmd.Run(); err != nil {
        log.Fatal("Failed to build SCREP CLI:", err)
    }

    // Build the server
    log.Println("Building SCREP server...")
    cmd = exec.Command("go", "build", "-o", "screp-server", "screp-server.go")
    cmd.Stdout = os.Stdout
    cmd.Stderr = os.Stderr
    if err := cmd.Run(); err != nil {
        log.Fatal("Failed to build SCREP server:", err)
    }

    log.Println("Build completed successfully!")
}
EOF

# Step 5: Set up git repository for deployment
echo -e "${YELLOW}Setting up git repository...${NC}"
git add .
git commit -m "Add DOKKU deployment configuration and HTTP server wrapper"

# Step 6: Add DOKKU remote and deploy
echo -e "${YELLOW}Adding DOKKU remote...${NC}"
git remote add dokku dokku@$(echo $DOKKU_SERVER | cut -d'@' -f2):$DOKKU_APP_NAME

echo -e "${YELLOW}Deploying to DOKKU...${NC}"
git push dokku main

# Step 7: Configure app settings
echo -e "${YELLOW}Configuring application settings...${NC}"

# Set environment variables
run_on_dokku "dokku config:set $DOKKU_APP_NAME GO_INSTALL_PACKAGE_SPEC='.'"

# Set up domain (optional)
echo -e "${YELLOW}Setting up domain configuration...${NC}"
run_on_dokku "dokku domains:add $DOKKU_APP_NAME screp.$DOKKU_DOMAIN"

# Step 8: Create service configuration for internal communication
echo -e "${YELLOW}Setting up internal service configuration...${NC}"
run_on_dokku "dokku network:create screp-network" 2>/dev/null || true
run_on_dokku "dokku network:set $DOKKU_APP_NAME attach-post-create screp-network"

# Step 9: Check deployment status
echo -e "${YELLOW}Checking deployment status...${NC}"
run_on_dokku "dokku apps:info $DOKKU_APP_NAME"

# Step 10: Test the deployment
echo -e "${YELLOW}Testing deployment...${NC}"
sleep 10
if curl -s "http://screp.$DOKKU_DOMAIN/health" > /dev/null; then
    echo -e "${GREEN}✓ SCREP CLI service is accessible via HTTP${NC}"
else
    echo -e "${RED}✗ SCREP CLI service is not accessible${NC}"
    echo -e "${YELLOW}Check logs: ssh -i $SSH_KEY $DOKKU_SERVER 'dokku logs $DOKKU_APP_NAME'${NC}"
fi

# Clean up
echo -e "${YELLOW}Cleaning up temporary files...${NC}"
cd /
rm -rf $TEMP_DIR

echo -e "${GREEN}SCREP CLI deployment completed!${NC}"
echo -e "${BLUE}Service Details:${NC}"
echo -e "  - App Name: $DOKKU_APP_NAME"
echo -e "  - Domain: screp.$DOKKU_DOMAIN"
echo -e "  - Health Check: http://screp.$DOKKU_DOMAIN/health"
echo -e "  - Parse Endpoint: http://screp.$DOKKU_DOMAIN/api/parse"
echo -e "  - Overview Endpoint: http://screp.$DOKKU_DOMAIN/api/overview"
echo -e ""
echo -e "${YELLOW}Internal Docker Network Access:${NC}"
echo -e "  - Network: screp-network"
echo -e "  - Internal URL: http://screp-cli.web:8080"
echo -e ""
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "1. Test the API endpoints"
echo -e "2. Integrate with your Play Framework application"
echo -e "3. Configure internal networking for your main app"
