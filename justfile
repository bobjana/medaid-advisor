# MedAid Advisor - Justfile
# Run 'just' to see available commands
# Install just: https://github.com/casey/just

set shell := ["bash", "-c"]

# Default recipe - shows all available commands
@default:
	echo "🏥 MedAid Advisor - Available Commands"
	echo ""
	just --list

# =============================================================================
# 🚀 Development Commands
# =============================================================================

# Start all services (PostgreSQL, Ollama, and the app)
@dev:
	echo "🚀 Starting MedAid Advisor development environment..."
	./infra/scripts/start.sh

# Build the project
@build:
	echo "🔨 Building MedAid Advisor..."
	mvn clean install -DskipTests

# Run tests
@test:
	echo "🧪 Running tests..."
	mvn test

# Clean build artifacts
@clean:
	echo "🧹 Cleaning build artifacts..."
	mvn clean

# =============================================================================
# 📦 Database Commands
# =============================================================================

# Start PostgreSQL and Ollama via Docker Compose
@db-up:
	echo "📦 Starting PostgreSQL and Ollama..."
	docker compose up -d
	echo "⏳ Waiting for PostgreSQL to be ready..."
	until docker exec medaid-postgres pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
	echo "✅ Database is ready"

# Stop database services
@db-down:
	echo "🛑 Stopping database services..."
	docker compose down

# Reset database (drop and recreate)
@db-reset: db-down
	echo "🔄 Resetting database..."
	docker compose up -d
	sleep 2
	docker exec medaid-postgres psql -U postgres -c "DROP DATABASE IF EXISTS medaid_poc;" || true
	docker exec medaid-postgres psql -U postgres -c "CREATE DATABASE medaid_poc;"
	docker exec medaid-postgres psql -U postgres -d medaid_poc -c "CREATE EXTENSION IF NOT EXISTS vector;"
	echo "✅ Database reset complete"

# Run Flyway migrations manually
@db-migrate:
	echo "🔄 Running database migrations..."
	mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/medaid_poc -Dflyway.user=postgres -Dflyway.password=postgres

# =============================================================================
# 📄 Ingestion Commands
# =============================================================================

# Ingest plan data from PDFs (creates/updates plans in database)
@ingest-plans:
	echo "📄 Ingesting plan data from PDFs..."
	java -jar target/medaid-advisor-*.jar --ingest-plan-data

# Ingest RAG documents (creates vector embeddings)
@ingest-rag:
	echo "🧠 Ingesting RAG documents..."
	java -jar target/medaid-advisor-*.jar --ingest-data

# Ingest both plans and RAG data
@ingest-all: build
	echo "📄 Ingesting plans and RAG data..."
	java -jar target/medaid-advisor-*.jar --ingest-plan-data --ingest-data

# =============================================================================
# 🧪 Testing Commands
# =============================================================================

# Run ingestion tests
@test-ingestion:
	echo "🧪 Running ingestion tests..."
	./infra/scripts/test-ingestion.sh

# Quick API health check
@health:
	echo "🏥 Checking API health..."
	curl -s http://localhost:8080/actuator/health | jq . || echo "❌ API not responding"

# =============================================================================
# 🏗️ Build & Package Commands
# =============================================================================

# Build runnable JAR
@package: build
	echo "📦 Creating runnable JAR..."
	ls -lh target/medaid-advisor-*.jar

# Run the application (requires database)
@run: build
	echo "🏃 Running application..."
	java -jar target/medaid-advisor-*.jar

# Run with plan ingestion
@run-with-ingest: build
	echo "🏃 Running with plan ingestion..."
	java -jar target/medaid-advisor-*.jar --ingest-plan-data

# =============================================================================
# 🛠️ Utility Commands
# =============================================================================

# View application logs
@logs:
	docker compose logs -f

# View PostgreSQL logs
@db-logs:
	docker compose logs -f postgres

# Open PostgreSQL console
@db-console:
	docker exec -it medaid-postgres psql -U postgres -d medaid_poc

# Format Kotlin code (requires ktlint)
@format:
	echo "🎨 Formatting Kotlin code..."
	ktlint -F "src/**/*.kt" || echo "Install ktlint: brew install ktlint"

# Check code style (requires ktlint)
@lint:
	echo "🔍 Checking code style..."
	ktlint "src/**/*.kt" || echo "Install ktlint: brew install ktlint"

# =============================================================================
# 📚 Documentation
# =============================================================================

# Show API documentation
@docs:
	echo "📚 API Documentation"
	echo ""
	echo "🏥 Health Check:"
	echo "  curl http://localhost:8080/actuator/health"
	echo ""
	echo "📄 List Plans:"
	echo "  curl http://localhost:8080/api/v1/plans"
	echo ""
	echo "🔍 Search RAG:"
	echo "  curl 'http://localhost:8080/api/v1/rag/search?query=chronic coverage&topK=3'"
	echo ""
	echo "🎯 Get Recommendations:"
	echo "  curl -X POST http://localhost:8080/api/v1/recommendations \\"
	echo "    -H 'Content-Type: application/json' \\"
	echo "    -d '{\"employeeProfile\": {\"age\": 32, \"dependents\": 1, \"maxMonthlyBudget\": 4000}}'"
