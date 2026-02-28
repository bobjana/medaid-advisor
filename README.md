# MedAid Advisor - RAG + Recommendation Engine POC

A Spring Boot + Kotlin proof-of-concept for medical aid recommendations using Retrieval-Augmented Generation (RAG) and a structured scoring algorithm.

## 🎯 Project Overview

This POC demonstrates:
- **Document Ingestion**: Parse and index medical aid PDF documents
- **RAG Pipeline**: Semantic search over ingested documents
- **Recommendation Engine**: Multi-factor scoring (cost, coverage, convenience, risk)
- **Local & Cost-Effective**: Uses local resources (Ollama, pgvector)

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Spring Boot API                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Recommendation  │  │  RAG Service    │  │  LLM Service │ │
│  │      Engine     │  │                 │  │              │ │
│  └────────┬────────┘  └────────┬────────┘  └──────┬───────┘ │
│           │                    │                    │         │
│           ▼                    ▼                    ▼         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  PostgreSQL     │  │  pgvector       │  │   Ollama     │ │
│  │  (Plans Data)   │  │  (Vector Store) │  │   (LLM)      │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

1. **Java 21+**
2. **Maven 3.8+**
3. **[just](https://github.com/casey/just)** task runner (`brew install just`)
4. **Docker & Docker Compose** (for PostgreSQL and Ollama)

### Option 1: Using Just (Recommended!)

```bash
# See all available commands
just

# Start everything (DB + Ollama + App)
just dev

# Or step by step:
just db-up       # Start PostgreSQL and Ollama
just build       # Build the project
just ingest-all  # Ingest plans and RAG data
just run         # Run the application
```

### Option 2: Manual Setup

#### 1. Start Infrastructure

```bash
# Start PostgreSQL and Ollama
docker compose up -d

# Wait for PostgreSQL and create database
docker exec medaid-postgres psql -U postgres -c "CREATE DATABASE medaid_poc;"
docker exec medaid-postgres psql -U postgres -d medaid_poc -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

#### 2. Build and Run

```bash
# Build
mvn clean install -DskipTests

# Ingest plan data from PDFs (idempotent - safe to run multiple times)
java -jar target/medaid-advisor-*.jar --ingest-plan-data

# Run the application
java -jar target/medaid-advisor-*.jar
```

The API will be available at `http://localhost:8080`

## 📋 Available Just Commands

### Development
| Command | Description |
|---------|-------------|
| `just dev` | Start full dev environment |
| `just build` | Build the project |
| `just test` | Run all tests |
| `just run` | Run the application |

### Database
| Command | Description |
|---------|-------------|
| `just db-up` | Start PostgreSQL and Ollama |
| `just db-down` | Stop database services |
| `just db-reset` | Reset database (drops and recreates) |

### Ingestion
| Command | Description |
|---------|-------------|
| `just ingest-plans` | Ingest plan data from PDFs |
| `just ingest-rag` | Ingest RAG documents |
| `just ingest-all` | Ingest both plans and RAG |

### Testing & Utilities
| Command | Description |
|---------|-------------|
| `just health` | Check API health |
| `just docs` | Show API documentation |
| `just db-console` | Open PostgreSQL console |

## 📚 API Endpoints

### Recommendation Engine

#### Generate Recommendations
```bash
POST /api/v1/recommendations
Content-Type: application/json

{
  "employeeProfile": {
    "age": 35,
    "dependents": 2,
    "chronicConditions": ["Type 2 Diabetes"],
    "planningPregnancy": false,
    "maxMonthlyBudget": 5000.0,
    "riskTolerance": "MEDIUM"
  },
  "schemeFilter": ["Discovery Health", "Bonitas"],
  "maxRecommendations": 3
}
```

### RAG (Document Ingestion & Search)

#### Ingest a PDF Document
```bash
POST /api/v1/rag/ingest
Content-Type: multipart/form-data

file: <your-medical-aid-pdf.pdf>
```

#### Ingest All PDFs from Directory
```bash
POST /api/v1/rag/ingest-directory
Content-Type: application/json

{
  "directoryPath": "~/Documents/medaids"
}
```

#### Semantic Search
```bash
GET /api/v1/rag/search?query=What is covered in hospital&topK=5
```

#### Get LLM Explanation (RAG-powered)
```bash
GET /api/v1/rag/explain?query=How does the Medical Savings Account work?
```

### Plans

#### List All Plans
```bash
GET /api/v1/plans
GET /api/v1/plans?scheme=Discovery Health&planYear=2026
```

#### Get Plan Details
```bash
GET /api/v1/plans/{id}
```

#### Available Schemes
```bash
GET /api/v1/plans/schemes
```

## 🗄️ Database Schema

The database schema is managed by **Flyway** migrations:

- Migrations are in `src/main/resources/db/migration/`
- Schema is version controlled via SQL files
- On startup, Flyway automatically applies pending migrations
- Hibernate validates schema (`ddl-auto: validate`)

### Key Tables

- **plans** - Medical aid plan information
- **contributions** - Detailed contribution data by member type
- **hospital_benefits** - Hospital benefit coverage details
- **plan_benefits** - Key-value benefit information
- **plan_copayments** - Copayment structures

## 🧪 Testing

### Run Tests
```bash
just test
```

### Test Ingestion
```bash
just test-ingestion
```

### Quick API Test
```bash
just health
```

## 📄 Data Ingestion Workflow

The ingestion pipeline is **idempotent** - you can run it multiple times safely.

### Plan Data Ingestion

Plan data is extracted directly from PDFs in `data/plans/`:

```bash
# Ingest all plans from PDFs
just ingest-plans

# Or manually:
java -jar target/medaid-advisor-*.jar --ingest-plan-data
```

This will:
1. Scan all PDFs in `data/plans/`
2. Extract scheme, plan name, and year from filenames
3. Create new plans or update existing ones (based on scheme+name+year)
4. Extract contribution tables and hospital benefits from PDF content

### RAG Document Ingestion

For semantic search capabilities:

```bash
# Ingest RAG documents
just ingest-rag

# Or manually:
java -jar target/medaid-advisor-*.jar --ingest-data
```

This will:
1. Parse PDFs and extract text
2. Split into chunks (800 chars with 100 overlap)
3. Generate embeddings using nomic-embed-text
4. Store in pgvector for semantic search

## 🔧 Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medaid_poc
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: validate  # Schema managed by Flyway

  flyway:
    enabled: true
    locations: classpath:db/migration

  ai:
    openai:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}/v1
      chat:
        options:
          model: ${OLLAMA_MODEL:llama3.2}
      embedding:
        options:
          model: nomic-embed-text

medaid:
  documents:
    chunk-size: 800
    chunk-overlap: 100
  scoring:
    weights:
      cost: 0.30
      coverage: 0.40
      convenience: 0.15
      risk: 0.15
```

## 💡 Key Features

### 1. Recommendation Scoring

The engine scores plans based on 4 factors:

| Factor | Weight | Description |
|--------|--------|-------------|
| Cost | 30% | Fit within budget, contribution levels |
| Coverage | 40% | Chronic conditions, planned procedures, network |
| Convenience | 15% | Provider proximity, digital services |
| Risk | 15% | Scheme stability, claims history |

### 2. RAG Pipeline

- **Document Parsing**: Extract text from PDFs
- **Chunking**: Split into 800-character chunks with 100 overlap
- **Embedding**: Convert to vectors (768 dimensions)
- **Storage**: Store in pgvector with metadata
- **Retrieval**: Semantic search for relevant chunks

### 3. Local & Cost-Effective

| Component | Tool | Cost |
|-----------|------|------|
| LLM | Ollama (llama3.2) | Free (local) |
| Embeddings | nomic-embed-text | Free (local) |
| Vector Store | pgvector (PostgreSQL) | Free (local) |
| Database | PostgreSQL | Free (local) |

## 📂 Project Structure

```
src/main/kotlin/cc/zynafin/medaid/
├── MedAidAsvirorApplication.kt      # Main application
├── controller/
│   ├── RecommendationController.kt   # Recommendation API
│   ├── RagController.kt              # RAG API
│   └── PlanController.kt             # Plans API
├── domain/
│   ├── Plan.kt                       # Medical aid plan entity
│   ├── Contribution.kt               # Contribution data
│   ├── EmployeeProfile.kt            # Employee profile entity
│   └── Recommendation.kt            # Recommendation DTOs
├── repository/
│   ├── PlanRepository.kt            # Plan data access
│   ├── ContributionRepository.kt    # Contribution data access
│   └── EmployeeProfileRepository.kt # Profile data access
└── service/
    ├── RecommendationEngine.kt       # Scoring algorithm
    ├── BatchPlanIngestionService.kt  # Plan data ingestion
    ├── PlanDataService.kt           # PDF data extraction
    ├── RagService.kt                # RAG pipeline
    └── LlmService.kt                 # LLM integration

src/main/resources/db/migration/
└── V1__Initial_schema.sql           # Database schema
```

## 🔮 Next Steps

To move from POC to production:

1. **Enhanced Document Parsing**
   - Add structured data extraction from tables
   - Extract contribution grids
   - Parse benefit limits and copayments

2. **Improved Scoring**
   - Add geospatial network proximity
   - Integrate scheme financial stability data
   - Custom weight configurations per client

3. **Questionnaire System**
   - Employee-facing web form
   - Profile builder from responses
   - Progressive disclosure logic

4. **Broker Dashboard**
   - Review and approve recommendations
   - Generate PDF reports
   - Manual override capabilities

5. **Compliance & Audit**
   - FAIS-compliant logging
   - Broker approval workflow
   - Regulatory record keeping

## 🐛 Troubleshooting

### PostgreSQL Connection Issues

```bash
# Check PostgreSQL is running
docker compose ps

# View PostgreSQL logs
just db-logs

# Reset database
just db-reset
```

### Ollama Not Responding

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Restart Ollama
docker compose restart ollama
```

### pgvector Extension Not Found

```bash
# Connect to database and create extension
docker exec -it medaid-postgres psql -U postgres -d medaid_poc
db> CREATE EXTENSION IF NOT EXISTS vector;
db> \dx
```

### Ingestion Issues

```bash
# Check if plans were created
curl http://localhost:8080/api/v1/plans

# Re-run ingestion (idempotent)
just ingest-plans
```

## 📄 License

Proprietary - MedAidAdvisor Project

## 👥 Related Projects

- [MedAidAdvisor Obsidian Vault](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/)
- [Technical Architecture](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/Technical Architecture.md)
- [MVP Scope](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/MVP Scope.md)
