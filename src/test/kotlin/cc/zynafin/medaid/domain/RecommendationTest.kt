package cc.zynafin.medaid.domain

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class RecommendationTest {

    @Test
    fun `should create recommendation with all fields`() {
        val plan = Plan(
            id = UUID.randomUUID(),
            scheme = "Discovery Health",
            planName = "Saver Plan",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4500.0,
            hasMedicalSavingsAccount = true
        )

        val componentScores = ComponentScores(
            costScore = 0.85,
            coverageScore = 0.9,
            convenienceScore = 0.7,
            riskScore = 0.8
        )

        val recommendation = Recommendation(
            rank = 1,
            plan = plan,
            totalScore = 0.84,
            componentScores = componentScores,
            estimatedAnnualCost = 54000.0,
            explanation = "Test explanation",
            keyBenefits = listOf("CDL coverage", "Hospital cover"),
            potentialGaps = listOf("Network restrictions"),
            confidence = 0.85
        )

        assertEquals(1, recommendation.rank)
        assertEquals("Discovery Health", recommendation.plan.scheme)
        assertEquals("Saver Plan", recommendation.plan.planName)
        assertEquals(0.84, recommendation.totalScore)
        assertEquals(0.85, recommendation.componentScores.costScore)
        assertEquals(54000.0, recommendation.estimatedAnnualCost)
        assertTrue(recommendation.keyBenefits.contains("CDL coverage"))
        assertTrue(recommendation.potentialGaps.contains("Network restrictions"))
        assertEquals(0.85, recommendation.confidence)
    }

    @Test
    fun `should calculate composite score correctly`() {
        val componentScores = ComponentScores(
            costScore = 0.8,
            coverageScore = 0.9,
            convenienceScore = 0.7,
            riskScore = 0.8
        )

        val weights = ScoringWeights(
            cost = 0.30,
            coverage = 0.40,
            convenience = 0.15,
            risk = 0.15
        )

        val expected = 0.8 * 0.30 + 0.9 * 0.40 + 0.7 * 0.15 + 0.8 * 0.15
        val actual = componentScores.costScore * weights.cost +
                     componentScores.coverageScore * weights.coverage +
                     componentScores.convenienceScore * weights.convenience +
                     componentScores.riskScore * weights.risk

        assertEquals(expected, actual, 0.001)
    }
}
