package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.PlanRepository
import org.springframework.stereotype.Service
import kotlin.math.max

@Service
class RecommendationEngine(
    private val planRepository: PlanRepository,
    private val ragService: RagService
) {

    fun recommend(
        profile: EmployeeProfile,
        schemeFilter: List<String>? = null,
        maxRecommendations: Int = 3,
        weights: ScoringWeights = ScoringWeights()
    ): List<Recommendation> {
        // Get candidate plans
        val plans = getEligiblePlans(profile, schemeFilter)

        // Score each plan
        val scoredPlans = plans.map { plan ->
            val componentScores = calculateComponentScores(profile, plan, weights)
            val totalScore = calculateCompositeScore(componentScores, weights)
            val estimatedCost = estimateAnnualCost(profile, plan)

            Recommendation(
                rank = 0, // Will be set after sorting
                plan = plan,
                totalScore = totalScore,
                componentScores = componentScores,
                estimatedAnnualCost = estimatedCost,
                explanation = generateExplanation(profile, plan, componentScores),
                keyBenefits = extractKeyBenefits(profile, plan),
                potentialGaps = identifyGaps(profile, plan),
                confidence = calculateConfidence(profile, plan, componentScores)
            )
        }

        // Sort by score and assign ranks
        return scoredPlans
            .sortedByDescending { it.totalScore }
            .take(maxRecommendations)
            .mapIndexed { index, rec -> rec.copy(rank = index + 1) }
    }

    private fun getEligiblePlans(
        profile: EmployeeProfile,
        schemeFilter: List<String>?
    ): List<Plan> {
        var plans = planRepository.findByPlanYear(2026)

        if (profile.maxAnnualBudget != null) {
            val maxMonthly = profile.maxAnnualBudget!! / 12
            plans = plans.filter { it.principalContribution <= maxMonthly * 1.2 } // Allow 20% over budget for scoring
        }

        if (!schemeFilter.isNullOrEmpty()) {
            plans = plans.filter { it.scheme in schemeFilter }
        }

        return plans
    }

    private fun calculateComponentScores(
        profile: EmployeeProfile,
        plan: Plan,
        weights: ScoringWeights
    ): ComponentScores {
        return ComponentScores(
            costScore = scoreCost(profile, plan),
            coverageScore = scoreCoverage(profile, plan),
            convenienceScore = scoreConvenience(profile, plan),
            riskScore = scoreRisk(profile, plan)
        )
    }

    private fun scoreCost(profile: EmployeeProfile, plan: Plan): Double {
        val totalMonthly = calculateMonthlyTotal(profile, plan)
        val totalAnnual = totalMonthly * 12

        return if (profile.maxAnnualBudget != null) {
            if (totalAnnual <= profile.maxAnnualBudget!!) {
                1.0
            } else {
                max(0.0, 1.0 - (totalAnnual - profile.maxAnnualBudget!!) / profile.maxAnnualBudget!!)
            }
        } else {
            // Normalize against cheapest plan (simplified)
            0.8 // Default reasonable score
        }
    }

    private fun scoreCoverage(profile: EmployeeProfile, plan: Plan): Double {
        var score = 0.0
        var factors = 0

        // Chronic conditions
        if (profile.chronicConditions.isNotEmpty()) {
            factors++
            if (plan.chronicBenefits?.contains("CDL", ignoreCase = true) == true) {
                score += 1.0
            } else if (plan.chronicBenefits?.contains("chronic", ignoreCase = true) == true) {
                score += 0.6 // Partial coverage
            }
        }

        // Pregnancy planning
        if (profile.planningPregnancy) {
            factors++
            if (plan.benefits["maternity"] != null || plan.benefits["antenatal"] != null) {
                score += 1.0
            } else {
                score += 0.3
            }
        }

        // Planned procedures
        if (profile.plannedProcedures.isNotEmpty()) {
            factors++
            score += 0.7 // Simplified - in reality would check specific procedures
        }

        // Default score for no specific needs
        if (factors == 0) {
            return 0.7
        }

        return score / factors
    }

    private fun scoreConvenience(profile: EmployeeProfile, plan: Plan): Double {
        var score = 0.5 // Base score

        // Network availability
        if (plan.planType == PlanType.NETWORK) {
            // Would check proximity in real implementation
            score += 0.2
        }

        // Digital services
        if (plan.benefits["app"] != null || plan.benefits["online"] != null) {
            score += 0.1
        }

        return score.coerceAtMost(1.0)
    }

    private fun scoreRisk(profile: EmployeeProfile, plan: Plan): Double {
        // Simplified - would use scheme stability data in reality
        return when (plan.scheme) {
            "Discovery Health" -> 0.9
            "Bonitas" -> 0.8
            "Bestmed" -> 0.75
            else -> 0.7
        }
    }

    private fun calculateCompositeScore(
        componentScores: ComponentScores,
        weights: ScoringWeights
    ): Double {
        return (
            componentScores.costScore * weights.cost +
            componentScores.coverageScore * weights.coverage +
            componentScores.convenienceScore * weights.convenience +
            componentScores.riskScore * weights.risk
        )
    }

    private fun calculateMonthlyTotal(profile: EmployeeProfile, plan: Plan): Double {
        var total = plan.principalContribution

        // Add dependents (simplified - assuming all adult dependents)
        if (profile.dependents > 0) {
            val adultDep = plan.adultDependentContribution ?: plan.principalContribution * 0.6
            val childDep = plan.childDependentContribution ?: plan.principalContribution * 0.3

            // Max 3 children charged as per South African medical aid norms
            val childrenCharged = minOf(profile.dependents, 3)
            total += childrenCharged * childDep
        }

        return total
    }

    private fun estimateAnnualCost(profile: EmployeeProfile, plan: Plan): Double {
        return calculateMonthlyTotal(profile, plan) * 12
    }

    private fun generateExplanation(
        profile: EmployeeProfile,
        plan: Plan,
        scores: ComponentScores
    ): String {
        // In production, this would use LLM for natural language generation
        val strengths = mutableListOf<String>()

        if (scores.coverageScore > 0.7) {
            strengths.add("strong coverage for your needs")
        }
        if (scores.costScore > 0.8) {
            strengths.add("excellent value for money")
        }
        if (scores.convenienceScore > 0.7) {
            strengths.add("convenient network and digital services")
        }

        val strengthText = if (strengths.isEmpty()) "good overall balance" else strengths.joinToString(", ")

        return """
            Based on your profile, the ${plan.planName} by ${plan.scheme} is recommended because it offers $strengthText.

            The monthly contribution of R${plan.principalContribution.toInt()} provides ${plan.hospitalBenefits?.take(100) ?: "comprehensive in-hospital coverage"}.

            One consideration is that ${if (plan.planType == PlanType.NETWORK) "this is a network plan, so you'll need to use designated hospitals" else "you have hospital choice flexibility"}.

            This plan offers particular value for your situation because ${if (profile.chronicConditions.isNotEmpty()) "it covers chronic conditions through its CDL benefit" else "it provides a good balance of coverage and cost"}.
        """.trimIndent().replace(Regex("\\n+"), " ")
    }

    private fun extractKeyBenefits(profile: EmployeeProfile, plan: Plan): List<String> {
        val benefits = mutableListOf<String>()

        if (plan.hospitalBenefits?.contains("100%", ignoreCase = true) == true) {
            benefits.add("100% in-hospital cover")
        }
        if (plan.chronicBenefits?.contains("CDL", ignoreCase = true) == true) {
            benefits.add("CDL chronic disease coverage")
        }
        if (plan.hasMedicalSavingsAccount) {
            benefits.add("Medical Savings Account included")
        }
        if (plan.benefits["preventative"] != null) {
            benefits.add("Preventative care benefits")
        }

        if (benefits.isEmpty()) {
            benefits.add("Comprehensive medical scheme benefits")
        }

        return benefits
    }

    private fun identifyGaps(profile: EmployeeProfile, plan: Plan): List<String> {
        val gaps = mutableListOf<String>()

        if (profile.chronicConditions.isNotEmpty() &&
            plan.chronicBenefits?.contains("CDL", ignoreCase = true) != true) {
            gaps.add("Limited chronic condition coverage")
        }

        if (profile.planningPregnancy &&
            plan.benefits["maternity"] == null &&
            plan.benefits["antenatal"] == null) {
            gaps.add("Limited maternity benefits")
        }

        if (plan.planType == PlanType.NETWORK) {
            gaps.add("Network restrictions on hospital choice")
        }

        return gaps
    }

    private fun calculateConfidence(
        profile: EmployeeProfile,
        plan: Plan,
        scores: ComponentScores
    ): Double {
        var confidence = 0.7 // Base confidence

        // Higher confidence if scores are consistently good
        val avgScore = (scores.costScore + scores.coverageScore +
                        scores.convenienceScore + scores.riskScore) / 4
        confidence += (avgScore - 0.5) * 0.2

        // Lower confidence if budget is tight
        if (profile.maxAnnualBudget != null) {
            val estimatedCost = estimateAnnualCost(profile, plan)
            if (estimatedCost > profile.maxAnnualBudget!!) {
                confidence -= 0.1
            }
        }

        return confidence.coerceIn(0.5, 0.95)
    }
}
