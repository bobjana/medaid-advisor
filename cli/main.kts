#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.retrofit2:retrofit:2.9.0")
@file:DependsOn("com.squareup.retrofit2:converter-jackson:2.9.0")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.15.2")
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Data classes for JSON parsing
data class QuestionnaireResponse(
    val employee: EmployeeData,
    val questionnaireId: String,
    val submittedAt: String
)

data class EmployeeData(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val dependents: List<Dependent>,
    val chronicConditions: List<ChronicCondition>,
    val plannedProcedures: List<PlannedProcedure>,
    val familyPlanning: FamilyPlanning,
    val budget: Budget,
    val riskTolerance: String,
    val preferences: Preferences
)

data class Dependent(
    val relationship: String,
    val age: Int
)

data class ChronicCondition(
    val condition: String,
    val type: String, // "CDL" or "non-CDL"
    val severity: String
)

data class PlannedProcedure(
    val procedure: String,
    val urgency: String,
    val timeline: String
)

data class FamilyPlanning(
    val currentlyPregnant: Boolean,
    val planningPregnancy: Boolean,
    val planningTimeline: String
)

data class Budget(
    val maxMonthly: Double,
    val preferredPayment: String
)

data class Preferences(
    val hospitalPreference: String,
    val networkPreference: String,
    val digitalServices: Boolean
)

// API interfaces
data class RecommendationRequest(
    val employeeProfile: EmployeeProfileDto,
    val schemeFilter: List<String>? = null,
    val maxRecommendations: Int? = 3,
    val weights: ScoringWeights? = null
)

data class EmployeeProfileDto(
    val age: Int,
    val dependents: Int,
    val chronicConditions: List<String>,
    val plannedProcedures: List<String>,
    val planningPregnancy: Boolean,
    val maxMonthlyBudget: Double?,
    val maxAnnualBudget: Double?,
    val riskTolerance: String
)

data class ScoringWeights(
    val cost: Double = 0.30,
    val coverage: Double = 0.40,
    val convenience: Double = 0.15,
    val risk: Double = 0.15
)

data class RecommendationResponse(
    val employeeProfileId: String?,
    val recommendations: List<Recommendation>,
    val timestamp: String
)

data class Recommendation(
    val rank: Int,
    val plan: PlanDto,
    val totalScore: Double,
    val componentScores: ComponentScores,
    val estimatedAnnualCost: Double,
    val explanation: String,
    val keyBenefits: List<String>,
    val potentialGaps: List<String>,
    val confidence: Double
)

data class PlanDto(
    val scheme: String,
    val planName: String,
    val planType: String,
    val principalContribution: Double,
    val hospitalBenefits: String?,
    val chronicBenefits: String?,
    val hasMedicalSavingsAccount: Boolean
)

data class ComponentScores(
    val costScore: Double,
    val coverageScore: Double,
    val convenienceScore: Double,
    val riskScore: Double
)

interface RecommendationApi {
    @POST("/api/v1/recommendations")
    suspend fun generateRecommendations(@Body request: RecommendationRequest): RecommendationResponse
}

fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()

    println("╔══════════════════════════════════════════════════════════╗")
    println("║           MedAid Advisor - Recommendation CLI            ║")
    println("╚══════════════════════════════════════════════════════════╝")
    println()

    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0].lowercase()

    when (command) {
        "test" -> runTest(mapper, args)
        "batch" -> runBatch(mapper, args)
        "help" -> printUsage()
        else -> {
            println("❌ Unknown command: $command")
            println()
            printUsage()
        }
    }
}

fun printUsage() {
    println("Usage: ./cli/main.kts <command> [options]")
    println()
    println("Commands:")
    println("  test <json-file>           Test with a single questionnaire response")
    println("  batch <directory>           Process multiple JSON files from a directory")
    println("  help                       Show this help message")
    println()
    println("Examples:")
    println("  ./cli/main.kts test ./samples/questionnaire-001.json")
    println("  ./cli/main.kts batch ./samples/")
    println()
    println("Make sure the application is running on http://localhost:8080")
}

fun runTest(mapper: ObjectMapper, args: Array<String>) {
    if (args.size < 2) {
        println("❌ Error: Please provide a JSON file path")
        println("   Usage: ./cli/main.kts test <json-file>")
        return
    }

    val jsonFile = File(args[1])
    if (!jsonFile.exists()) {
        println("❌ Error: File not found: ${jsonFile.absolutePath}")
        return
    }

    println("📄 Loading questionnaire from: ${jsonFile.name}")
    println()

    try {
        val questionnaire: QuestionnaireResponse = mapper.readValue(jsonFile)

        println("✅ Questionnaire loaded successfully!")
        println("   Employee: ${questionnaire.employee.firstName} ${questionnaire.employee.lastName}")
        println("   Age: ${questionnaire.employee.age}")
        println("   Dependents: ${questionnaire.employee.dependents.size}")
        println("   Chronic Conditions: ${questionnaire.employee.chronicConditions.size}")
        println("   Budget: R${questionnaire.employee.budget.maxMonthly}/month")
        println()

        // Convert questionnaire to employee profile
        val profile = convertToProfile(questionnaire.employee)

        // Create recommendation request
        val request = RecommendationRequest(
            employeeProfile = profile,
            maxRecommendations = 3
        )

        // Call API
        println("🔄 Generating recommendations...")
        val response = callRecommendationApi(request)

        // Display results
        displayRecommendations(response)

    } catch (e: Exception) {
        println("❌ Error processing questionnaire: ${e.message}")
        e.printStackTrace()
    }
}

fun runBatch(mapper: ObjectMapper, args: Array<String>) {
    if (args.size < 2) {
        println("❌ Error: Please provide a directory path")
        println("   Usage: ./cli/main.kts batch <directory>")
        return
    }

    val directory = File(args[1])
    if (!directory.exists() || !directory.isDirectory) {
        println("❌ Error: Directory not found: ${directory.absolutePath}")
        return
    }

    val jsonFiles = directory.listFiles { file ->
        file.extension == "json"
    } ?: emptyArray()

    if (jsonFiles.isEmpty()) {
        println("❌ No JSON files found in directory: ${directory.absolutePath}")
        return
    }

    println("📁 Found ${jsonFiles.size} questionnaire(s) in ${directory.name}")
    println()

    jsonFiles.forEachIndexed { index, file ->
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("Processing ${index + 1}/${jsonFiles.size}: ${file.name}")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        try {
            val questionnaire: QuestionnaireResponse = mapper.readValue(file)
            val profile = convertToProfile(questionnaire.employee)
            val request = RecommendationRequest(
                employeeProfile = profile,
                maxRecommendations = 3
            )
            val response = callRecommendationApi(request)
            displayRecommendations(response)
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
        }

        println()
    }
}

fun convertToProfile(employee: EmployeeData): EmployeeProfileDto {
    val chronicConditions = employee.chronicConditions.map { it.condition }
    val plannedProcedures = employee.plannedProcedures.map { it.procedure }
    val dependentsCount = employee.dependents.size

    return EmployeeProfileDto(
        age = employee.age,
        dependents = dependentsCount,
        chronicConditions = chronicConditions,
        plannedProcedures = plannedProcedures,
        planningPregnancy = employee.familyPlanning.planningPregnancy ||
                          employee.familyPlanning.currentlyPregnant,
        maxMonthlyBudget = employee.budget.maxMonthly,
        maxAnnualBudget = employee.budget.maxMonthly * 12,
        riskTolerance = employee.riskTolerance
    )
}

suspend fun callRecommendationApi(request: RecommendationRequest): RecommendationResponse {
    val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:8080")
        .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
        .build()

    val api = retrofit.create(RecommendationApi::class.java)
    return api.generateRecommendations(request)
}

fun displayRecommendations(response: RecommendationResponse) {
    println("📊 Recommendations Generated")
    println("   Generated at: ${response.timestamp}")
    println()

    response.recommendations.forEach { rec ->
        println("═════════════════════════════════════════════════════════")
        println("  🥇 #${rec.rank} - ${rec.plan.planName}")
        println("  🏥 Scheme: ${rec.plan.scheme}")
        println("  📈 Score: ${String.format("%.2f", rec.totalScore * 100)}%")
        println("  💰 Annual Cost: R${String.format("%,.2f", rec.estimatedAnnualCost)}")
        println("  🎯 Confidence: ${String.format("%.1f", rec.confidence * 100)}%")
        println()

        println("  📊 Component Scores:")
        println("     • Cost: ${String.format("%.1f", rec.componentScores.costScore * 100)}%")
        println("     • Coverage: ${String.format("%.1f", rec.componentScores.coverageScore * 100)}%")
        println("     • Convenience: ${String.format("%.1f", rec.componentScores.convenienceScore * 100)}%")
        println("     • Risk: ${String.format("%.1f", rec.componentScores.riskScore * 100)}%")
        println()

        println("  ✨ Key Benefits:")
        rec.keyBenefits.forEach { benefit ->
            println("     • $benefit")
        }
        println()

        if (rec.potentialGaps.isNotEmpty()) {
            println("  ⚠️  Potential Gaps:")
            rec.potentialGaps.forEach { gap ->
                println("     • $gap")
            }
            println()
        }

        println("  📝 Explanation:")
        println("  ${rec.explanation.replace(". ", ".\n  ")}")
        println()
    }
}

fun displayPlanDetails(plan: PlanDto) {
    println("  📋 Plan Details:")
    println("     • Type: ${plan.planType}")
    println("     • Monthly Contribution: R${String.format("%,.2f", plan.principalContribution)}")
    println("     • Medical Savings Account: ${if (plan.hasMedicalSavingsAccount) "✅ Yes" else "❌ No"}")
    println()

    if (plan.hospitalBenefits != null) {
        println("  🏥 Hospital Benefits:")
        println("     ${plan.hospitalBenefits}")
        println()
    }

    if (plan.chronicBenefits != null) {
        println("  💊 Chronic Benefits:")
        println("     ${plan.chronicBenefits}")
        println()
    }
}
