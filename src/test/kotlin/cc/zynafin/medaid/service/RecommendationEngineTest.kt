package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.PlanRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationEngineTest {

    private lateinit var recommendationEngine: RecommendationEngine
    private val planRepository: PlanRepository = mockk()
    private val ragService: RagService = mockk()

    @BeforeEach
    fun setup() {
        recommendationEngine = RecommendationEngine(planRepository, ragService)
    }

    @Test
    fun `should recommend plans sorted by score`() {
        val profile = EmployeeProfile(
            age = 32,
            dependents = 1,
            chronicConditions = listOf("Hypertension"),
            maxMonthlyBudget = 5000.0,
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plans = listOf(
            Plan(
                scheme = "Discovery Health",
                planName = "Saver Plan",
                planYear = 2026,
                planType = PlanType.SAVINGS,
                principalContribution = 4500.0,
                chronicBenefits = "Full CDL cover",
                hospitalBenefits = "Delta network at 100%"
            ),
            Plan(
                scheme = "Discovery Health",
                planName = "Comprehensive Plan",
                planYear = 2026,
                planType = PlanType.COMPREHENSIVE,
                principalContribution = 6800.0,
                chronicBenefits = "Full CDL and non-CDL chronic cover",
                hospitalBenefits = "Full in-hospital cover at 200%"
            ),
            Plan(
                scheme = "Bonitas",
                planName = "Comprehensive",
                planYear = 2026,
                planType = PlanType.COMPREHENSIVE,
                principalContribution = 5200.0,
                chronicBenefits = "Full CDL and non-CDL chronic cover",
                hospitalBenefits = "Full in-hospital cover at 200%"
            )
        )

        every { planRepository.findByPlanYear(2026) } returns plans

        val recommendations = recommendationEngine.recommend(profile, maxRecommendations = 3)

        assertTrue(recommendations.isNotEmpty())
        assertEquals(1, recommendations[0].rank)
        assertEquals(2, recommendations[1].rank)
        assertEquals(3, recommendations[2].rank)

        // Scores should be in descending order
        assertTrue(recommendations[0].totalScore >= recommendations[1].totalScore)
        assertTrue(recommendations[1].totalScore >= recommendations[2].totalScore)
    }

    @Test
    fun `should filter by budget`() {
        val profile = EmployeeProfile(
            age = 35,
            dependents = 0,
            maxAnnualBudget = 50000.0, // R50,000 per year
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plans = listOf(
            Plan(
                scheme = "Discovery Health",
                planName = "Saver Plan",
                planYear = 2026,
                planType = PlanType.SAVINGS,
                principalContribution = 4000.0, // R48,000 per year
                hospitalBenefits = "Delta network at 100%"
            ),
            Plan(
                scheme = "Discovery Health",
                planName = "Comprehensive Plan",
                planYear = 2026,
                planType = PlanType.COMPREHENSIVE,
                principalContribution = 6000.0, // R72,000 per year - over budget
                hospitalBenefits = "Full in-hospital cover at 200%"
            )
        )

        every { planRepository.findByPlanYear(2026) } returns plans

        val recommendations = recommendationEngine.recommend(profile)

        assertTrue(recommendations.isNotEmpty())
        // Cheaper plan should rank higher due to better budget fit
        assertTrue(recommendations[0].plan.principalContribution < recommendations[1].plan.principalContribution)
    }

    @Test
    fun `should prioritize coverage for chronic conditions`() {
        val profile = EmployeeProfile(
            age = 45,
            dependents = 1,
            chronicConditions = listOf("Type 2 Diabetes", "Hypertension"),
            riskTolerance = RiskTolerance.LOW
        )

        val plans = listOf(
            Plan(
                scheme = "Scheme A",
                planName = "Plan without CDL",
                planYear = 2026,
                planType = PlanType.SAVINGS,
                principalContribution = 3000.0,
                chronicBenefits = "Limited chronic coverage",
                hospitalBenefits = "Basic hospital cover"
            ),
            Plan(
                scheme = "Scheme B",
                planName = "Plan with CDL",
                planYear = 2026,
                planType = PlanType.COMPREHENSIVE,
                principalContribution = 4500.0,
                chronicBenefits = "Full CDL cover",
                hospitalBenefits = "Full hospital cover"
            )
        )

        every { planRepository.findByPlanYear(2026) } returns plans

        val recommendations = recommendationEngine.recommend(profile)

        assertTrue(recommendations.isNotEmpty())
        // Plan with CDL should rank higher for chronic condition profile
        assertEquals("Plan with CDL", recommendations[0].plan.planName)
    }

    @Test
    fun `should include key benefits in recommendations`() {
        val profile = EmployeeProfile(
            age = 32,
            dependents = 1,
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Saver Plan",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4500.0,
            hospitalBenefits = "Comprehensive in-hospital cover through Delta network at 100%",
            chronicBenefits = "Full CDL cover",
            hasMedicalSavingsAccount = true
        )

        every { planRepository.findByPlanYear(2026) } returns listOf(plan)

        val recommendations = recommendationEngine.recommend(profile)

        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations[0].keyBenefits.isNotEmpty())
    }

    @Test
    fun `should identify potential gaps`() {
        val profile = EmployeeProfile(
            age = 30,
            dependents = 0,
            planningPregnancy = true,
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Basic Plan",
            planYear = 2026,
            planType = PlanType.NETWORK,
            principalContribution = 2800.0,
            hospitalBenefits = "State hospital network",
            chronicBenefits = "Basic chronic coverage"
        )

        every { planRepository.findByPlanYear(2026) } returns listOf(plan)

        val recommendations = recommendationEngine.recommend(profile)

        assertTrue(recommendations.isNotEmpty())
        // Should identify gaps for pregnancy and network restrictions
        assertTrue(recommendations[0].potentialGaps.isNotEmpty())
    }

    @Test
    fun `should estimate annual cost correctly`() {
        val profile = EmployeeProfile(
            age = 35,
            dependents = 2, // 2 children
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Test Plan",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4000.0,
            childDependentContribution = 1500.0
        )

        every { planRepository.findByPlanYear(2026) } returns listOf(plan)

        val recommendations = recommendationEngine.recommend(profile)

        assertTrue(recommendations.isNotEmpty())
        // Expected: (4000 + 2*1500) * 12 = 84000
        val expectedCost = 84000.0
        assertEquals(expectedCost, recommendations[0].estimatedAnnualCost, 0.01)
    }

    @Test
    fun `should respect scheme filter`() {
        val profile = EmployeeProfile(
            age = 32,
            dependents = 0,
            riskTolerance = RiskTolerance.MEDIUM
        )

        val plans = listOf(
            Plan(
                scheme = "Discovery Health",
                planName = "Discovery Plan",
                planYear = 2026,
                planType = PlanType.SAVINGS,
                principalContribution = 4500.0
            ),
            Plan(
                scheme = "Bonitas",
                planName = "Bonitas Plan",
                planYear = 2026,
                planType = PlanType.COMPREHENSIVE,
                principalContribution = 5000.0
            ),
            Plan(
                scheme = "Bestmed",
                planName = "Bestmed Plan",
                planYear = 2026,
                planType = PlanType.NETWORK,
                principalContribution = 4000.0
            )
        )

        every { planRepository.findByPlanYear(2026) } returns plans

        val recommendations = recommendationEngine.recommend(
            profile,
            schemeFilter = listOf("Discovery Health", "Bonitas")
        )

        assertTrue(recommendations.isNotEmpty())
        // Should not include Bestmed
        assertTrue(recommendations.all { it.plan.scheme != "Bestmed" })
    }
}
