package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import cc.zynafin.medaid.repository.PlanRepository
import cc.zynafin.medaid.service.RagService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    classes = [cc.zynafin.medaid.MedAidAsvirorApplication::class]
)
@ImportAutoConfiguration(exclude = [OpenAiAutoConfiguration::class, PgVectorStoreAutoConfiguration::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationControllerTest {

    @MockBean
    private lateinit var ragService: RagService

    @MockBean
    private lateinit var chatClient: ChatClient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var planRepository: PlanRepository

    private val testPlans = mutableListOf<Plan>()

    @BeforeEach
    fun setup() {
        // Create test plans
        val plan1 = Plan(
            scheme = "Test Discovery",
            planName = "Test Comprehensive",
            planYear = 2026,
            planType = PlanType.COMPREHENSIVE,
            principalContribution = 6000.0,
            hospitalBenefits = "Full hospital cover at 200%",
            chronicBenefits = "Full CDL and non-CDL cover",
            hasMedicalSavingsAccount = false
        )

        val plan2 = Plan(
            scheme = "Test Discovery",
            planName = "Test Saver",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4000.0,
            hospitalBenefits = "Delta network at 100%",
            chronicBenefits = "CDL full, non-CDL from MSA",
            hasMedicalSavingsAccount = true,
            msaPercentage = 0.25
        )

        val plan3 = Plan(
            scheme = "Test Bonitas",
            planName = "Test Network",
            planYear = 2026,
            planType = PlanType.NETWORK,
            principalContribution = 3000.0,
            hospitalBenefits = "Designated network at 100%",
            chronicBenefits = "CDL full",
            hasMedicalSavingsAccount = false
        )

        testPlans.addAll(listOf(plan1, plan2, plan3).map { planRepository.save(it) })
    }

    @AfterEach
    fun cleanup() {
        planRepository.deleteAll()
        testPlans.clear()
    }

    @Test
    fun `should generate recommendations successfully`() {
        val requestBody = """
            {
                "employeeProfile": {
                    "age": 32,
                    "dependents": 1,
                    "chronicConditions": ["Hypertension"],
                    "planningPregnancy": false,
                    "maxMonthlyBudget": 5000.0,
                    "riskTolerance": "MEDIUM"
                },
                "maxRecommendations": 3
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendations").isArray)
            .andExpect(jsonPath("$.recommendations[0].rank").value(1))
            .andExpect(jsonPath("$.recommendations[0].totalScore").isNumber)
            .andExpect(jsonPath("$.recommendations[0].estimatedAnnualCost").isNumber)
            .andExpect(jsonPath("$.recommendations[0].explanation").isString)
            .andExpect(jsonPath("$.recommendations[0].keyBenefits").isArray)
            .andExpect(jsonPath("$.recommendations[0].potentialGaps").isArray)
            .andExpect(jsonPath("$.recommendations[0].confidence").isNumber)
    }

    @Test
    fun `should filter by scheme`() {
        val requestBody = """
            {
                "employeeProfile": {
                    "age": 35,
                    "dependents": 0,
                    "maxMonthlyBudget": 6000.0,
                    "riskTolerance": "MEDIUM"
                },
                "schemeFilter": ["Test Discovery"],
                "maxRecommendations": 2
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendations").isArray)
            .andExpect(jsonPath("""$.recommendations[?(@.plan.scheme == 'Test Discovery')]""").exists())
            .andExpect(jsonPath("""$.recommendations[?(@.plan.scheme != 'Test Discovery')]""").doesNotExist())

    }

    @Test
    fun `should handle custom weights`() {
        val requestBody = """
            {
                "employeeProfile": {
                    "age": 30,
                    "dependents": 0,
                    "maxMonthlyBudget": 4500.0,
                    "riskTolerance": "HIGH"
                },
                "weights": {
                    "cost": 0.5,
                    "coverage": 0.3,
                    "convenience": 0.1,
                    "risk": 0.1
                },
                "maxRecommendations": 3
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendations").isArray)
    }

    @Test
    fun `should validate required employee profile fields`() {
        val requestBody = """
            {
                "employeeProfile": {
                    "age": -5,
                    "dependents": -1
                },
                "maxRecommendations": 3
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `should return limited number of recommendations`() {
        val requestBody = """
            {
                "employeeProfile": {
                    "age": 32,
                    "dependents": 0,
                    "maxMonthlyBudget": 7000.0,
                    "riskTolerance": "MEDIUM"
                },
                "maxRecommendations": 2
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendations").isArray)
            .andExpect(jsonPath("$.recommendations.length()").value(2))
    }
}
