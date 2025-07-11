#!/bin/bash

echo "🗄️ Setting up local file storage for development testing..."

# Create the uploads directory in your project root
UPLOAD_DIR="uploads"
mkdir -p "$UPLOAD_DIR"

# Set permissions (equivalent to what Dokku does)
chmod 755 "$UPLOAD_DIR"

echo "✅ Created local uploads directory at: $(pwd)/$UPLOAD_DIR"
echo ""
echo "📋 Testing Setup Complete!"
echo ""
echo "🔧 How to test your storage interaction:"
echo ""
echo "1. 📂 Local Development (Recommended):"
echo "   - Files will be stored in: $(pwd)/$UPLOAD_DIR"
echo "   - Your app is already configured to use this path in development"
echo "   - Start your app with: sbt run"
echo "   - Upload files through your web interface"
echo ""
echo "2. 🐳 Docker Testing (Mimics Dokku exactly):"
echo "   - Run: docker run -v $(pwd)/$UPLOAD_DIR:/app/uploads ..."
echo ""
echo "3. 🔍 Test the FileStorageService directly:"
echo "   - Use sbt console and test the service methods"
echo "   - Check getStorageStats to verify directory creation"
echo ""
echo "4. 📊 Storage Stats Endpoint:"
echo "   - Add a route to expose storage stats for debugging"
echo ""
echo "🚀 Ready to test! Your storage is now configured for:"
echo "   • Development: $(pwd)/$UPLOAD_DIR"
echo "   • Production: /app/uploads (Dokku mounted)"
