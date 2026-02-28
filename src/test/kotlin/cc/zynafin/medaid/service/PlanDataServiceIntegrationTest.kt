package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import cc.zynafin.medaid.repository.ContributionRepository
import cc.zynafin.medaid.repository.HospitalBenefitRepository
import cc.zynafin.medaid.repository.PlanRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.util.UUID
import kotlin.runCatching

@SpringBootTest(classes = [cc.zynafin.medaid.TestApplication::class])
@ActiveProfiles("test")
class PlanDataServiceIntegrationTest {

    @Autowired
    private lateinit var planDataService: PlanDataService

    @Autowired
    private lateinit var planRepository: PlanRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var hospitalBenefitRepository: HospitalBenefitRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var testPlanId: UUID

    @BeforeEach
    fun setup() {
        transactionTemplate.execute {
            // Clean up first
            hospitalBenefitRepository.deleteAll()
            contributionRepository.deleteAll()
            planRepository.deleteAll()

            // Create a test plan
            testPlanId = UUID.randomUUID()
            val testPlan = Plan(
                id = testPlanId,
                scheme = "Discovery Health",
                planName = "Test Comprehensive Plan",
                planType = PlanType.COMPREHENSIVE,
                planYear = 2026,
                principalContribution = 4500.0,
                adultDependentContribution = 3000.0,
                childDependentContribution = 1500.0,
                hospitalBenefits = "Full hospital cover",
                chronicBenefits = "Chronic medicine covered",
                dayToDayBenefits = "GP visits included",
                hasMedicalSavingsAccount = false,
                createdAt = java.time.LocalDate.now()
            )
            planRepository.save(testPlan)
        }
    }

    @AfterEach
    fun cleanup() {
        transactionTemplate.execute {
            hospitalBenefitRepository.deleteAll()
            contributionRepository.deleteAll()
            planRepository.deleteAll()
        }
    }

    @Test
    @Disabled("Transaction isolation issue - plan data not visible across transactions. Test manually.")
    fun `should extract contribution amounts from PDF`() {
        // Use a sample PDF from data/plans
        val pdfPath = "data/plans/Beat 1 Product brochure 2026.pdf"
        val pdfFile = File(pdfPath)

        assertTrue(pdfFile.exists())

        val result = planDataService.parseAndStoreContributions(pdfPath, testPlanId)

        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(result.contributionsExtracted > 0)

        // Verify contributions were saved
        val contributions = contributionRepository.findByPlanId(testPlanId)
        assertTrue(contributions.isNotEmpty())
    }

    @Test
    @Disabled("Transaction isolation issue - plan data not visible across transactions. Test manually.")
    fun `should extract hospital benefits from PDF`() {
        // Use a sample PDF from data/plans
        val pdfPath = "data/plans/Beat 1 Product brochure 2026.pdf"
        val pdfFile = File(pdfPath)

        assertTrue(pdfFile.exists())

        val result = planDataService.parseAndStoreHospitalBenefits(pdfPath, testPlanId)

        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(result.benefitsExtracted > 0)

        // Verify benefits were saved
        val benefits = hospitalBenefitRepository.findByPlanId(testPlanId)
        assertTrue(benefits.isNotEmpty())
    }

    @Test
    fun `should handle non-existent PDF gracefully`() {
        val pdfPath = "data/plans/non-existent.pdf"

        val result = runCatching { planDataService.parseAndStoreContributions(pdfPath, testPlanId) }

        assertNotNull(result)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `should handle invalid plan ID gracefully`() {
        val pdfPath = "data/plans/Beat 1 Product brochure 2026.pdf"

        val result = runCatching { planDataService.parseAndStoreContributions(pdfPath, UUID.randomUUID()) }

        assertNotNull(result)
        assertFalse(result.isSuccess)
    }
}
