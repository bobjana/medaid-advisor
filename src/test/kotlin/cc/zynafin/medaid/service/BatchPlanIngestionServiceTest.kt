package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.PlanRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class BatchPlanIngestionServiceTest {

    private lateinit var planDataService: PlanDataService
    private lateinit var ragService: RagService
    private lateinit var planRepository: PlanRepository
    private lateinit var service: BatchPlanIngestionService
    
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        planDataService = mock()
        ragService = mock()
        planRepository = mock()
        service = BatchPlanIngestionService(planDataService, ragService, planRepository)
    }

    @Test
    fun `ingestDirectory should return error when directory does not exist`() {
        val result = service.ingestDirectory("/nonexistent/directory")

        assertFalse(result.success)
        assertEquals("Directory does not exist", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestDirectory should return empty result when directory is empty`() {
        Files.createDirectories(tempDir)

        val result = service.ingestDirectory(tempDir.toString())

        assertFalse(result.success)
        assertEquals("No PDF files found", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestSinglePdf should skip when no matching plan found`() {
        val pdfFile = Files.createFile(tempDir.resolve("unknown-scheme-2026.pdf"))
        whenever(ragService.extractMetadataFromFilename(any())).thenReturn(mapOf(
            "scheme" to "Unknown",
            "plan_name" to "Unknown",
            "year" to 2026
        ))

        val result = service.ingestSinglePdf(pdfFile)

        assertNotNull(result)
        assertEquals(BatchItemStatus.SKIPPED, result.status)
    }

    @Test
    fun `ingestDirectory should include Momentum core option plan files`() {
        Files.createFile(tempDir.resolve("Custom Option 2026.pdf"))
        Files.createFile(tempDir.resolve("Evolve Option 2026.pdf"))

        whenever(ragService.extractMetadataFromFilename(any())).thenReturn(
            mapOf(
                "scheme" to "Momentum",
                "plan_name" to "Custom Option",
                "year" to 2026
            )
        )

        whenever(planRepository.findBySchemeAndPlanNameAndPlanYear(any(), any(), any())).thenReturn(null)
        whenever(planRepository.save(any<Plan>())).thenAnswer { invocation ->
            val plan = invocation.getArgument<Plan>(0)
            if (plan.id == null) {
                Plan(
                    id = UUID.randomUUID(),
                    scheme = plan.scheme,
                    planName = plan.planName,
                    planYear = plan.planYear,
                    planType = plan.planType,
                    principalContribution = plan.principalContribution,
                    hasMedicalSavingsAccount = plan.hasMedicalSavingsAccount,
                    createdAt = plan.createdAt,
                    sourceDocument = plan.sourceDocument
                )
            } else plan
        }

        whenever(planDataService.parseAndStoreContributions(any(), any())).thenReturn(
            ContributionParseResult(
                success = true,
                planId = UUID.randomUUID(),
                contributionsExtracted = 0,
                contributions = emptyList()
            )
        )
        whenever(planDataService.parseAndStoreHospitalBenefits(any(), any())).thenReturn(
            HospitalBenefitParseResult(
                success = true,
                planId = UUID.randomUUID(),
                benefitsExtracted = 0,
                benefits = emptyList()
            )
        )
        whenever(planDataService.parseAndStoreCopayments(any(), any())).thenReturn(
            CopaymentParseResult(
                success = true,
                planId = UUID.randomUUID(),
                copaymentsExtracted = 0,
                copayments = emptyMap()
            )
        )
        whenever(planDataService.extractMsaInfo(any())).thenReturn(
            MsaInfo(hasMedicalSavingsAccount = false, msaPercentage = null)
        )

        val result = service.ingestDirectory(tempDir.toString())

        assertTrue(result.success)
        assertNull(result.error)
        assertEquals(2, result.totalFiles)
        assertEquals(2, result.successfulIngestions)
        assertEquals(0, result.failedIngestions)
    }

    @Test
    fun `ingestDirectory should skip overview file`() {
        Files.createFile(tempDir.resolve("Benefit_Option_Overview_2026.pdf"))

        val result = service.ingestDirectory(tempDir.toString())

        assertFalse(result.success)
        assertEquals("No PDF files found", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestSinglePdf should persist extracted summary fields on plan`() {
        val pdfFile = Files.createFile(tempDir.resolve("Beat 1 Product brochure 2026.pdf"))

        whenever(ragService.extractMetadataFromFilename(any())).thenReturn(
            mapOf(
                "scheme" to "Bestmed",
                "plan_name" to "Beat 1",
                "year" to 2026
            )
        )
        whenever(planRepository.findBySchemeAndPlanNameAndPlanYear("Bestmed", "Beat 1", 2026)).thenReturn(null)

        val savedPlans = mutableListOf<Plan>()
        whenever(planRepository.save(any())).thenAnswer { invocation ->
            val candidate = invocation.getArgument<Plan>(0)
            val persisted = Plan(
                id = candidate.id ?: UUID.randomUUID(),
                scheme = candidate.scheme,
                planName = candidate.planName,
                planYear = candidate.planYear,
                planType = candidate.planType,
                principalContribution = candidate.principalContribution,
                adultDependentContribution = candidate.adultDependentContribution,
                childDependentContribution = candidate.childDependentContribution,
                benefits = candidate.benefits,
                copayments = candidate.copayments,
                hospitalBenefits = candidate.hospitalBenefits,
                chronicBenefits = candidate.chronicBenefits,
                dayToDayBenefits = candidate.dayToDayBenefits,
                hasMedicalSavingsAccount = candidate.hasMedicalSavingsAccount,
                msaPercentage = candidate.msaPercentage,
                createdAt = candidate.createdAt,
                sourceDocument = candidate.sourceDocument
            )
            savedPlans.add(persisted)
            persisted
        }

        val parsedPlanId = UUID.randomUUID()
        val parsedPlan = Plan(
            id = parsedPlanId,
            scheme = "Bestmed",
            planName = "Beat 1",
            planYear = 2026,
            planType = PlanType.COMPREHENSIVE,
            principalContribution = 0.0
        )

        val extractedContributions = listOf(
            Contribution(plan = parsedPlan, memberType = MemberType.PRINCIPAL, monthlyAmount = 4599.0),
            Contribution(plan = parsedPlan, memberType = MemberType.SPOUSE, monthlyAmount = 2299.0),
            Contribution(plan = parsedPlan, memberType = MemberType.CHILD_FIRST, monthlyAmount = 899.0)
        )

        val extractedBenefits = listOf(
            HospitalBenefit(
                plan = parsedPlan,
                category = BenefitCategory.HOSPITAL_COVER,
                benefitName = "Private Hospital Cover",
                limitPerFamily = "R1,000,000",
                covered = true
            ),
            HospitalBenefit(
                plan = parsedPlan,
                category = BenefitCategory.CHRONIC_MEDICINE,
                benefitName = "CDL + Additional Chronic",
                limitPerPerson = "R50,000",
                covered = true
            )
        )

        whenever(planDataService.parseAndStoreContributions(any(), any())).thenReturn(
            ContributionParseResult(
                success = true,
                planId = parsedPlanId,
                contributionsExtracted = extractedContributions.size,
                contributions = extractedContributions
            )
        )

        whenever(planDataService.parseAndStoreHospitalBenefits(any(), any())).thenReturn(
            HospitalBenefitParseResult(
                success = true,
                planId = parsedPlanId,
                benefitsExtracted = extractedBenefits.size,
                benefits = extractedBenefits
            )
        )

        whenever(planDataService.parseAndStoreCopayments(any(), any())).thenReturn(
            CopaymentParseResult(
                success = true,
                planId = parsedPlanId,
                copaymentsExtracted = 2,
                copayments = mapOf("gp_consultation" to 150.0, "specialist_consultation" to 300.0)
            )
        )

        whenever(planDataService.extractMsaInfo(any())).thenReturn(
            MsaInfo(hasMedicalSavingsAccount = true, msaPercentage = 25.0)
        )

        val result = service.ingestSinglePdf(pdfFile)

        assertEquals(BatchItemStatus.SUCCESS, result.status)
        assertEquals(2, savedPlans.size)

        val initialPlan = savedPlans.first()
        val enrichedPlan = savedPlans.last()

        verify(planDataService).parseAndStoreContributions(any(), eq(initialPlan.id!!))
        verify(planDataService).parseAndStoreHospitalBenefits(any(), eq(initialPlan.id!!))

        assertEquals(4599.0, enrichedPlan.principalContribution)
        assertEquals(2299.0, enrichedPlan.adultDependentContribution)
        assertEquals(899.0, enrichedPlan.childDependentContribution)
        assertTrue(enrichedPlan.benefits.isNotEmpty())
        assertNotNull(enrichedPlan.hospitalBenefits)
        assertEquals(pdfFile.toAbsolutePath().toString(), enrichedPlan.sourceDocument)
    }
}
