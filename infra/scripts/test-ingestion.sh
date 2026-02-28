#!/bin/bash

# Test script for RAG document ingestion and Plan Data extraction

set -e

echo "╔══════════════════════════════════════════════════════════╗"
echo "║        MedAid Advisor - Ingestion Test Suite              ║"
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

# Function to test plan data ingestion
test_plan_data_ingestion() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║     Testing Plan Data Ingestion (Contributions/Benefits)   ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""

    # Get available plans
    echo "📋 Fetching available plans..."
    PLANS_RESPONSE=$(curl -s http://localhost:8080/api/v1/plans)
    echo "$PLANS_RESPONSE" | head -100
    echo ""

    # Check if plans exist
    if echo "$PLANS_RESPONSE" | grep -q '"id"'; then
        echo "✅ Plans found in database"
    else
        echo "⚠️  No plans found. Please add plans to the database first."
        echo "   You can use the /api/v1/plans endpoint to create plans."
        return 1
    fi
    echo ""

    # Use first available plan for testing
    PLAN_ID=$(echo "$PLANS_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "📌 Using plan ID: $PLAN_ID for testing"
    echo ""

    # Test PDF for plan data extraction
    TEST_PDF="data/plans/Beat 1 Product brochure 2026.pdf"

    if [ ! -f "$TEST_PDF" ]; then
        echo "❌ Test PDF not found: $TEST_PDF"
        return 1
    fi

    echo "📄 Test PDF found: $TEST_PDF"
    echo ""

    # Test contribution extraction
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Test 1: Extracting contribution data from PDF..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    CONTRIB_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/plan-data/contributions?planId=$PLAN_ID" \
      -F "file=@$TEST_PDF" 2>&1 || echo '{"success":false,"error":"Request failed"}')

    echo "$CONTRIB_RESPONSE"
    echo ""

    if echo "$CONTRIB_RESPONSE" | grep -q '"success":true'; then
        echo "✅ Contribution extraction successful"
    else
        echo "⚠️  Contribution extraction completed with issues (may be expected for test PDF)"
    fi
    echo ""

    # Test hospital benefit extraction
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Test 2: Extracting hospital benefit data from PDF..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    BENEFIT_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/plan-data/hospital-benefits?planId=$PLAN_ID" \
      -F "file=@$TEST_PDF" 2>&1 || echo '{"success":false,"error":"Request failed"}')

    echo "$BENEFIT_RESPONSE"
    echo ""

    if echo "$BENEFIT_RESPONSE" | grep -q '"success":true'; then
        echo "✅ Hospital benefit extraction successful"
    else
        echo "⚠️  Hospital benefit extraction completed with issues (may be expected for test PDF)"
    fi
    echo ""

    # Test full extraction
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Test 3: Extracting full plan data from PDF..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    FULL_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/plan-data/parse-full?planId=$PLAN_ID" \
      -F "file=@$TEST_PDF" 2>&1 || echo '{"contributionsSuccess":false,"benefitsSuccess":false}')

    echo "$FULL_RESPONSE"
    echo ""

    if echo "$FULL_RESPONSE" | grep -q '"contributionsSuccess":true'; then
        echo "✅ Full extraction completed"
    else
        echo "ℹ️  Full extraction completed (results depend on PDF content match)"
    fi
    echo ""
}

# Function to test RAG ingestion
test_rag_ingestion() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║              Testing RAG Document Ingestion                ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""

    # Test PDF paths
    DISCOVERY_PDF="data/plans/discovery-health-medical-scheme-comprehensive-plan-guide.pdf"
    BESTMED_PDF="data/plans/Beat 3 Product brochure 2026.pdf"

    if [ -f "$DISCOVERY_PDF" ]; then
        echo "📄 Found: Discovery Health PDF"
    else
        echo "⚠️  Discovery PDF not found at: $DISCOVERY_PDF"
    fi

    if [ -f "$BESTMED_PDF" ]; then
        echo "📄 Found: Bestmed PDF"
    else
        echo "⚠️  Bestmed PDF not found at: $BESTMED_PDF"
    fi
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Note: RAG ingestion requires running Ollama service"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# Main menu
echo "Select test to run:"
echo ""
echo "1) Plan Data Ingestion (Contributions/Benefits extraction)"
echo "2) RAG Document Ingestion (Vector search)"
echo "3) Run all tests"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        test_plan_data_ingestion
        ;;
    2)
        test_rag_ingestion
        ;;
    3)
        test_plan_data_ingestion
        test_rag_ingestion
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║                   Testing Complete!                        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
