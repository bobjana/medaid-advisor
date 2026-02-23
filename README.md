# MedAid Asviror - RAG + Recommendation Engine POC

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
3. **PostgreSQL 15+** with pgvector extension
4. **Ollama** (optional, for local LLM)

### Setup

#### 1. Install PostgreSQL with pgvector

```bash
# macOS
brew install postgresql@16
brew install pgvector

# Start PostgreSQL
brew services start postgresql@16

# Create database
createdb medaid_poc

# Enable pgvector extension
psql medaid_poc
psql> CREATE EXTENSION vector;
psql> \q
```

#### 2. Install Ollama (for local LLM)

```bash
# macOS
brew install ollama

# Pull models
ollama pull llama3.2
ollama pull nomic-embed-text

# Start Ollama
ollama serve
```

#### 3. Run the Application

```bash
# Navigate to project directory
cd ~/Development/zynafin/med-aid-asviror

# Build and run
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`

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

## 🧪 Testing

### 1. Ingest Documents First

```bash
# Start the app
./mvnw spring-boot:run

# Ingest your medical aid PDFs
curl -X POST http://localhost:8080/api/v1/rag/ingest-directory \
  -H "Content-Type: application/json" \
  -d '{"directoryPath": "~/Documents/medaids"}'
```

### 2. Test RAG Search

```bash
# Search for benefits
curl "http://localhost:8080/api/v1/rag/search?query=chronic disease coverage&topK=3"
```

### 3. Test Recommendations

```bash
# Generate recommendations for a sample profile
curl -X POST http://localhost:8080/api/v1/recommendations \
  -H "Content-Type: application/json" \
  -d '{
    "employeeProfile": {
      "age": 32,
      "dependents": 1,
      "chronicConditions": ["Hypertension"],
      "planningPregnancy": false,
      "maxMonthlyBudget": 4000.0,
      "riskTolerance": "MEDIUM"
    },
    "maxRecommendations": 3
  }'
```

## 🔧 Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medaid_poc
    username: postgres
    password: postgres

  ai:
    openai:
      base-url: http://localhost:11434/v1  # Ollama
      chat:
        options:
          model: llama3.2
      embedding:
        options:
          model: nomic-embed-text

medaid:
  documents:
    source-path: ~/Documents/medaids
    chunk-size: 1000
    chunk-overlap: 200
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
- **Chunking**: Split into 1000-character chunks with 200 overlap
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
│   ├── EmployeeProfile.kt            # Employee profile entity
│   └── Recommendation.kt            # Recommendation DTOs
├── repository/
│   ├── PlanRepository.kt            # Plan data access
│   └── EmployeeProfileRepository.kt # Profile data access
└── service/
    ├── RecommendationEngine.kt       # Scoring algorithm
    ├── RagService.kt                # RAG pipeline
    └── LlmService.kt                 # LLM integration
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
brew services list | grep postgres

# View PostgreSQL logs
tail -f /usr/local/var/log/postgresql@16.log
```

### Ollama Not Responding

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# View Ollama logs
ollama logs
```

### pgvector Extension Not Found

```bash
# Connect to database
psql medaid_poc

# Install extension
CREATE EXTENSION IF NOT EXISTS vector;

# Verify
\dx
```

## 📄 License

Proprietary - MedAidAdvisor Project

## 👥 Related Projects

- [MedAidAdvisor Obsidian Vault](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/)
- [Technical Architecture](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/Technical Architecture.md)
- [MVP Scope](~/Library/CloudStorage/GoogleDrive-dylan@zynafin.co.za/My Drive/notes/Projects/MedAidAdvisor/MVP Scope.md)
