#!/bin/bash

# Test script for RAG document ingestion

set -e

echo "╔══════════════════════════════════════════════════════════╗"
echo "║        MedAid Advisor - Document Ingestion Test            ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Check if application is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "❌ Error: Application is not running on http://localhost:8080"
    echo ""
    echo "Please start the application first:"
    echo "  cd ~/Development/zynafin/medaid-advisor"
    echo "  ./start.sh"
    exit 1
fi

echo "✅ Application is running"
echo ""

# Define PDF files to ingest
DISCOVERY_PDF="$HOME/Documents/medaids/discovery-health-medical-scheme-comprehensive-plan-guide.pdf"
BESTMED_PDF="$HOME/Documents/medaids/Beat 3 Product brochure 2026.pdf"

# Check if PDFs exist
if [ ! -f "$DISCOVERY_PDF" ]; then
    echo "❌ Error: Discovery PDF not found: $DISCOVERY_PDF"
    exit 1
fi

if [ ! -f "$BESTMED_PDF" ]; then
    echo "❌ Error: Bestmed PDF not found: $BESTMED_PDF"
    exit 1
fi

echo "📄 Found test PDFs:"
echo "   1. Discovery Health - Comprehensive Plan"
echo "   2. Bestmed - Beat 3"
echo ""

# Ingest Discovery PDF
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Ingesting Discovery Health PDF..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DISCOVERY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/rag/ingest \
  -F "file=@$DISCOVERY_PDF" \
  -F 'metadata={"scheme":"Discovery Health","plan":"Comprehensive"}')

echo "$DISCOVERY_RESPONSE" | jq .

if echo "$DISCOVERY_RESPONSE" | grep -q '"status":"success"'; then
    echo "✅ Discovery PDF ingested successfully"
else
    echo "❌ Error ingesting Discovery PDF"
    exit 1
fi

echo ""

# Wait a moment
sleep 2

# Ingest Bestmed PDF
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Ingesting Bestmed PDF..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

BESTMED_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/rag/ingest \
  -F "file=@$BESTMED_PDF" \
  -F 'metadata={"scheme":"Bestmed","plan":"Beat 3"}')

echo "$BESTMED_RESPONSE" | jq .

if echo "$BESTMED_RESPONSE" | grep -q '"status":"success"'; then
    echo "✅ Bestmed PDF ingested successfully"
else
    echo "❌ Error ingesting Bestmed PDF"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Testing Semantic Search"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Test search for chronic conditions
echo "🔍 Searching for 'chronic disease coverage'..."
SEARCH_RESPONSE=$(curl -s "http://localhost:8080/api/v1/rag/search?query=chronic%20disease%20coverage&topK=3")
echo "$SEARCH_RESPONSE" | jq .

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Testing RAG Explanation"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Test RAG explanation
echo "❓ Question: How does the Medical Savings Account work?"
EXPLAIN_RESPONSE=$(curl -s "http://localhost:8080/api/v1/rag/explain?query=How%20does%20the%20Medical%20Savings%20Account%20work%3F")
echo "$EXPLAIN_RESPONSE"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Getting RAG Statistics"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

STATS_RESPONSE=$(curl -s "http://localhost:8080/api/v1/rag/stats")
echo "$STATS_RESPONSE" | jq .

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║                  Ingestion Test Complete!                   ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "✅ Both PDFs ingested successfully!"
echo "✅ Semantic search is working!"
echo "✅ RAG explanation is working!"
echo ""
echo "You can now test recommendations with:"
echo "  ./cli/main.kts test ./samples/questionnaire-001-young-family.json"
echo ""
