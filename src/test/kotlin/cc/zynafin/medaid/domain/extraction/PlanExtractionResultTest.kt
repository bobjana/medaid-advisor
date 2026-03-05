package cc.zynafin.medaid.domain.extraction

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PlanExtractionResultTest {

    @Test
    fun shouldCreateWithAllFields() {
        val plan = Plan(
            id = UUID.randomUUID(),
            scheme = "Discovery Health",
            planName = "Test Plan",
            planYear = 2026,
            planType = PlanType.COMPREHENSIVE,
            principalContribution = 4500.0
        )

        val metadataResult = SectionExtractionResult(
            data = PlanMetadata(
                scheme = "Discovery Health",
                planName = "Test Plan",
                planYear = 2026,
                planType = "COMPREHENSIVE",
                network = "Delta",
                summary = "Test summary"
            ),
            confidence = 0.95,
            sourceChunks = emptyList(),
            retryAttempts = 0,
            errorMessage = null
        )

        val result = PlanExtractionResult(
            plan = plan,
            overallConfidence = 0.90,
            overallStatus = ExtractionStatus.VALIDATED,
            metadataResult = metadataResult,
            contributionsResult = createDummyContributionsResult(),
            benefitsResult = createDummyBenefitsResult(),
            copaymentsResult = createDummyCopaymentsResult(),
            extractionStartTime = LocalDateTime.now()
        )

        assertEquals(plan, result.plan)
        assertEquals(0.90, result.overallConfidence)
        assertEquals(ExtractionStatus.VALIDATED, result.overallStatus)
        assertNotNull(result.extractionStartTime)
        assertNull(result.extractionEndTime)
    }

    @Test
    fun shouldCalculateIsCompleteWhenAllSectionsSuccessful() {
        val result = PlanExtractionResult(
            plan = createDummyPlan(),
            overallConfidence = 0.90,
            overallStatus = ExtractionStatus.VALIDATED,
            metadataResult = createDummyMetadataResult(),
            contributionsResult = createDummyContributionsResult(),
            benefitsResult = createDummyBenefitsResult(),
            copaymentsResult = createDummyCopaymentsResult(),
            extractionStartTime = LocalDateTime.now()
        )

        assertTrue(result.isComplete)
        assertFalse(result.hasErrors)
    }

    @Test
    fun shouldCalculateIsCompleteFalseWhenAnySectionFailed() {
        val failedResult = SectionExtractionResult<PlanMetadata>(
            data = PlanMetadata("Discovery", "Test", 2026, "COMPREHENSIVE", null, null),
            confidence = 0.0,
            sourceChunks = emptyList(),
            retryAttempts = 3,
            errorMessage = "Extraction failed"
        )

        val result = PlanExtractionResult(
            plan = createDummyPlan(),
            overallConfidence = 0.0,
            overallStatus = ExtractionStatus.FAILED,
            metadataResult = failedResult,
            contributionsResult = createDummyContributionsResult(),
            benefitsResult = createDummyBenefitsResult(),
            copaymentsResult = createDummyCopaymentsResult(),
            extractionStartTime = LocalDateTime.now()
        )

        assertFalse(result.isComplete)
        assertTrue(result.hasErrors)
    }

    @Test
    fun shouldCalculateTotalRetryAttempts() {
        val result = PlanExtractionResult(
            plan = createDummyPlan(),
            overallConfidence = 0.90,
            overallStatus = ExtractionStatus.VALIDATED,
            metadataResult = SectionExtractionResult(
                data = PlanMetadata("Discovery", "Test", 2026, "COMPREHENSIVE", null, null),
                confidence = 0.95,
                sourceChunks = emptyList(),
                retryAttempts = 2,
                errorMessage = null
            ),
            contributionsResult = SectionExtractionResult(
                data = ContributionsData(4500.0, null, null, mapOf()),
                confidence = 0.90,
                sourceChunks = emptyList(),
                retryAttempts = 1,
                errorMessage = null
            ),
            benefitsResult = SectionExtractionResult(
                data = BenefitsData("Full cover", "Full cover", null, emptyList()),
                confidence = 0.95,
                sourceChunks = emptyList(),
                retryAttempts = 3,
                errorMessage = null
            ),
            copaymentsResult = SectionExtractionResult(
                data = CopaymentsData("R500", "R300", "R200", "Fixed"),
                confidence = 0.88,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = null
            ),
            extractionStartTime = LocalDateTime.now()
        )

        assertEquals(6, result.totalRetryAttempts)
    }

    @Test
    fun shouldSetExtractionEndTime() {
        val startTime = LocalDateTime.now()
        val endTime = startTime.plusMinutes(5)

        val result = PlanExtractionResult(
            plan = createDummyPlan(),
            overallConfidence = 0.90,
            overallStatus = ExtractionStatus.VALIDATED,
            metadataResult = createDummyMetadataResult(),
            contributionsResult = createDummyContributionsResult(),
            benefitsResult = createDummyBenefitsResult(),
            copaymentsResult = createDummyCopaymentsResult(),
            extractionStartTime = startTime,
            extractionEndTime = endTime,
            extractedBy = "system"
        )

        assertEquals(startTime, result.extractionStartTime)
        assertEquals(endTime, result.extractionEndTime)
        assertEquals("system", result.extractedBy)
    }

    @Test
    fun planMetadataShouldCreateWithAllFields() {
        val metadata = PlanMetadata(
            scheme = "Discovery Health",
            planName = "Comprehensive",
            planYear = 2026,
            planType = "COMPREHENSIVE",
            network = "Delta",
            summary = "Full cover"
        )

        assertEquals("Discovery Health", metadata.scheme)
        assertEquals("Comprehensive", metadata.planName)
        assertEquals(2026, metadata.planYear)
        assertEquals("COMPREHENSIVE", metadata.planType)
        assertEquals("Delta", metadata.network)
        assertEquals("Full cover", metadata.summary)
    }

    @Test
    fun contributionsDataShouldCreateWithMemberTypeContributions() {
        val contributions = ContributionsData(
            principal = 4500.0,
            adultDependent = 3000.0,
            childDependent = 1500.0,
            memberTypeContributions = mapOf(
                "PRINCIPAL" to 4500.0,
                "ADULT_DEPENDENT" to 3000.0,
                "CHILD_DEPENDENT" to 1500.0
            )
        )

        assertEquals(4500.0, contributions.principal)
        assertEquals(3000.0, contributions.adultDependent)
        assertEquals(1500.0, contributions.childDependent)
        assertEquals(3, contributions.memberTypeContributions.size)
    }

    @Test
    fun benefitsDataShouldCreateWithAllFields() {
        val benefits = BenefitsData(
            hospitalCoverage = "Full cover at 200%",
            chronicCoverage = "CDL and non-CDL",
            dayToDayBenefits = "Unlimited",
            networkProviders = listOf("Delta", "Prime")
        )

        assertEquals("Full cover at 200%", benefits.hospitalCoverage)
        assertEquals("CDL and non-CDL", benefits.chronicCoverage)
        assertEquals("Unlimited", benefits.dayToDayBenefits)
        assertEquals(2, benefits.networkProviders.size)
    }

    private fun createDummyPlan(): Plan {
        return Plan(
            id = UUID.randomUUID(),
            scheme = "Discovery Health",
            planName = "Test Plan",
            planYear = 2026,
            planType = PlanType.COMPREHENSIVE,
            principalContribution = 4500.0
        )
    }

    private fun createDummyMetadataResult(): SectionExtractionResult<PlanMetadata> {
        return SectionExtractionResult(
            data = PlanMetadata("Discovery", "Test", 2026, "COMPREHENSIVE", null, null),
            confidence = 0.95,
            sourceChunks = emptyList(),
            retryAttempts = 0,
            errorMessage = null
        )
    }

    private fun createDummyContributionsResult(): SectionExtractionResult<ContributionsData> {
        return SectionExtractionResult(
            data = ContributionsData(4500.0, null, null, mapOf()),
            confidence = 0.90,
            sourceChunks = emptyList(),
            retryAttempts = 0,
            errorMessage = null
        )
    }

    private fun createDummyBenefitsResult(): SectionExtractionResult<BenefitsData> {
        return SectionExtractionResult(
            data = BenefitsData("Full cover", "Full cover", null, emptyList()),
            confidence = 0.95,
            sourceChunks = emptyList(),
            retryAttempts = 0,
            errorMessage = null
        )
    }

    private fun createDummyCopaymentsResult(): SectionExtractionResult<CopaymentsData> {
        return SectionExtractionResult(
            data = CopaymentsData(null, null, null, null),
            confidence = 0.88,
            sourceChunks = emptyList(),
            retryAttempts = 0,
            errorMessage = null
        )
    }
}
