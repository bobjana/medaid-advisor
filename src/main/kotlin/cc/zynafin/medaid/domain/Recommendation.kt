package cc.zynafin.medaid.domain

import java.time.LocalDateTime

data class Recommendation(
    val rank: Int,
    val plan: Plan,
    val totalScore: Double,
    val componentScores: ComponentScores,
    val estimatedAnnualCost: Double,
    val explanation: String,
    val keyBenefits: List<String>,
    val potentialGaps: List<String>,
    val confidence: Double,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ComponentScores(
    val costScore: Double,
    val coverageScore: Double,
    val convenienceScore: Double,
    val riskScore: Double
)

data class ScoringWeights(
    val cost: Double = 0.30,
    val coverage: Double = 0.40,
    val convenience: Double = 0.15,
    val risk: Double = 0.15
)
