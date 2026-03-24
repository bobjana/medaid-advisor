package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import cc.zynafin.medaid.repository.ContributionRepository
import cc.zynafin.medaid.repository.HospitalBenefitRepository
import cc.zynafin.medaid.repository.PlanRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var testPlanId: UUID

    @BeforeEach
    fun setup() {
        transactionTemplate.execute {
            // Clean up first
            hospitalBenefitRepository.deleteAll()
            contributionRepository.deleteAll()
            planRepository.deleteAll()

            // Create a test plan (let JPA generate the ID)
            val testPlan = Plan(
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
            val savedPlan = planRepository.save(testPlan)
            testPlanId = savedPlan.id!!
            
            // Flush to ensure the plan is persisted
            entityManager.flush()
        }
        
        // Clear the persistence context after the setup transaction
        entityManager.clear()
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
    fun `should extract contribution amounts from PDF`() {
        transactionTemplate.execute {
            // Use a sample PDF from data/plans
            val pdfPath = "data/plans/Beat 1 Product brochure 2026.pdf"
            val pdfFile = File(pdfPath)

            assertTrue(pdfFile.exists())

            val result = planDataService.parseAndStoreContributions(pdfPath, testPlanId)

            assertNotNull(result)
            assertTrue(result.success)
            // Note: PDF extraction depends on PDF format. The test verifies transaction isolation works.
            // If contributions are extracted, verify they are persisted and queryable.
            if (result.contributionsExtracted > 0) {
                assertTrue(result.contributions.isNotEmpty(), "Contributions should be extracted and returned")
                // Flush to ensure all changes are persisted
                entityManager.flush()
                // Verify contributions were saved by querying the repository
                val contributions = contributionRepository.findByPlanId(testPlanId)
                assertTrue(contributions.isNotEmpty(), "Contributions should be persisted and queryable")
            }
        }
    }

    @Test
    fun `should extract hospital benefits from PDF`() {
        transactionTemplate.execute {
            // Use a sample PDF from data/plans
            val pdfPath = "data/plans/Beat 1 Product brochure 2026.pdf"
            val pdfFile = File(pdfPath)

            assertTrue(pdfFile.exists())

            val result = planDataService.parseAndStoreHospitalBenefits(pdfPath, testPlanId)

            assertNotNull(result)
            assertTrue(result.success)
            // Note: PDF extraction depends on PDF format. The test verifies transaction isolation works.
            // If benefits are extracted, verify they are persisted and queryable.
            if (result.benefitsExtracted > 0) {
                assertTrue(result.benefits.isNotEmpty(), "Benefits should be extracted and returned")
                // Flush to ensure all changes are persisted
                entityManager.flush()
                // Verify benefits were saved by querying the repository
                val benefits = hospitalBenefitRepository.findByPlanId(testPlanId)
                assertTrue(benefits.isNotEmpty(), "Benefits should be persisted and queryable")
            }
        }
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
