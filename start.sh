#!/bin/bash

# MedAid Asviror POC - Quick Start Script

set -e

echo "🦝 MedAid Asviror POC - Starting up..."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    echo "   macOS: brew install --cask docker"
    exit 1
fi

# Check if Docker Compose is available
if ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not available."
    exit 1
fi

# Start Docker services
echo "📦 Starting PostgreSQL and Ollama..."
docker compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
until docker exec medaid-postgres pg_isready -U postgres &> /dev/null; do
    sleep 2
done

# Create pgvector extension
echo "🔧 Setting up pgvector extension..."
docker exec medaid-postgres psql -U postgres -d medaid_poc -c "CREATE EXTENSION IF NOT EXISTS vector;" || true

# Wait for Ollama to be ready
echo "⏳ Waiting for Ollama to be ready (this may take a few minutes on first run)..."
until curl -s http://localhost:11434/api/tags > /dev/null 2>&1; do
    sleep 5
    echo "   Ollama still starting..."
done

echo "✅ Services are ready!"

# Check if Maven wrapper exists, otherwise download it
if [ ! -f "mvnw" ]; then
    echo "📥 Downloading Maven wrapper..."
    curl -o mvnw https://raw.githubusercontent.com/takari/maven-wrapper/master/mvnw
    chmod +x mvnw
fi

# Build and run the application
echo "🚀 Building and starting the application..."
./mvnw spring-boot:run

echo ""
echo "🎉 MedAid Asviror POC is now running!"
echo ""
echo "📍 API is available at: http://localhost:8080"
echo ""
echo "🧪 Try these commands:"
echo "   # Ingest documents"
echo "   curl -X POST http://localhost:8080/api/v1/rag/ingest-directory \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"directoryPath\": \"~/Documents/medaids\"}'"
echo ""
echo "   # Generate recommendations"
echo "   curl -X POST http://localhost:8080/api/v1/recommendations \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"employeeProfile\": {\"age\": 32, \"dependents\": 1, \"maxMonthlyBudget\": 4000.0, \"riskTolerance\": \"MEDIUM\"}}'"
echo ""
echo "   # Search documents"
echo "   curl 'http://localhost:8080/api/v1/rag/search?query=chronic disease coverage&topK=3'"
echo ""
echo "📚 Check README.md for full API documentation"
echo ""
