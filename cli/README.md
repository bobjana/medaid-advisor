# MedAid Advisor CLI

A command-line interface for testing the MedAid Advisor recommendation engine with questionnaire responses.

## Prerequisites

- Application running on `http://localhost:8080`
- Kotlin scripting support (comes with Kotlin 1.3+)
- Java 11+ installed

## Installation

The CLI is a Kotlin script located at `cli/main.kts`. No installation required - just run it!

## Usage

### Test a Single Questionnaire

```bash
./cli/main.kts test ./samples/questionnaire-001-young-family.json
```

This will:
1. Load the questionnaire JSON file
2. Convert it to an employee profile
3. Call the recommendation API
4. Display the top 3 recommendations with explanations

### Batch Process Multiple Questionnaires

```bash
./cli/main.kts batch ./samples/
```

This will process all JSON files in the specified directory.

### Show Help

```bash
./cli/main.kts help
```

## Sample Questionnaires

The `samples/` directory contains example questionnaire responses:

### questionnaire-001-young-family.json
- **Profile:** 32-year-old with spouse and child
- **Needs:** Planning pregnancy
- **Budget:** R5,500/month
- **Risk Tolerance:** Medium

### questionnaire-002-chronic-conditions.json
- **Profile:** 45-year-old with 2 children
- **Conditions:** Type 2 Diabetes (CDL), Hypertension (CDL)
- **Planned Procedure:** Colonoscopy
- **Budget:** R6,500/month
- **Risk Tolerance:** Low

### questionnaire-003-budget-conscious.json
- **Profile:** 28-year-old single
- **Needs:** Budget-conscious, no specific health concerns
- **Budget:** R3,000/month
- **Risk Tolerance:** High

## Questionnaire JSON Schema

```json
{
  "employee": {
    "firstName": "string",
    "lastName": "string",
    "age": number,
    "dependents": [
      {
        "relationship": "spouse|child",
        "age": number
      }
    ],
    "chronicConditions": [
      {
        "condition": "string",
        "type": "CDL|non-CDL",
        "severity": "mild|moderate|severe"
      }
    ],
    "plannedProcedures": [
      {
        "procedure": "string",
        "urgency": "emergency|urgent|routine",
        "timeline": "string"
      }
    ],
    "familyPlanning": {
      "currentlyPregnant": boolean,
      "planningPregnancy": boolean,
      "planningTimeline": "string"
    },
    "budget": {
      "maxMonthly": number,
      "preferredPayment": "string"
    },
    "riskTolerance": "LOW|MEDIUM|HIGH",
    "preferences": {
      "hospitalPreference": "string",
      "networkPreference": "string",
      "digitalServices": boolean
    }
  },
  "questionnaireId": "string",
  "submittedAt": "ISO 8601 timestamp"
}
```

## Output Format

The CLI displays recommendations in a formatted way:

```
╔══════════════════════════════════════════════════════════╗
║           MedAid Advisor - Recommendation CLI            ║
╚══════════════════════════════════════════════════════════╝

📄 Loading questionnaire from: questionnaire-001-young-family.json

✅ Questionnaire loaded successfully!
   Employee: Sarah Johnson
   Age: 32
   Dependents: 2
   Chronic Conditions: 0
   Budget: R5500.0/month

🔄 Generating recommendations...

📊 Recommendations Generated
   Generated at: 2026-02-23T15:30:00

═════════════════════════════════════════════════════════
  🥇 #1 - Comprehensive Plan
  🏥 Scheme: Discovery Health
  📈 Score: 84.50%
  💰 Annual Cost: R66,000.00
  🎯 Confidence: 85.0%

  📊 Component Scores:
     • Cost: 78.0%
     • Coverage: 92.0%
     • Convenience: 75.0%
     • Risk: 85.0%

  ✨ Key Benefits:
     • 100% in-hospital cover
     • CDL chronic disease coverage
     • Comprehensive maternity benefits

  ⚠️  Potential Gaps:
     • Higher monthly contribution

  📝 Explanation:
  Based on your profile, the Comprehensive Plan by Discovery Health is
  recommended because it offers strong coverage for your needs...

═════════════════════════════════════════════════════════
```

## Troubleshooting

### Connection Refused

Make sure the application is running:
```bash
cd ~/Development/zynafin/medaid-advisor
./start.sh
```

### Kotlin Script Not Executable

```bash
chmod +x cli/main.kts
```

### JSON Parse Error

Ensure your JSON file is valid. You can check with:
```bash
cat your-file.json | jq .
```

## Advanced Usage

### Custom Weights

You can modify the CLI to use custom scoring weights:

```kotlin
val request = RecommendationRequest(
    employeeProfile = profile,
    maxRecommendations = 3,
    weights = ScoringWeights(
        cost = 0.5,       // Higher emphasis on cost
        coverage = 0.3,
        convenience = 0.1,
        risk = 0.1
    )
)
```

### Scheme Filtering

Filter to specific schemes:

```kotlin
val request = RecommendationRequest(
    employeeProfile = profile,
    schemeFilter = listOf("Discovery Health", "Bonitas"),
    maxRecommendations = 3
)
```

## Development

The CLI is a Kotlin script that:
1. Parses questionnaire JSON
2. Converts to employee profile format
3. Calls the REST API
4. Formats and displays results

To modify behavior, edit `cli/main.kts` directly.

## License

Proprietary - MedAidAdvisor Project
