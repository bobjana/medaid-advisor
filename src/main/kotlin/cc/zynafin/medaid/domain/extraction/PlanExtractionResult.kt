package cc.zynafin.medaid.domain.extraction

import cc.zynafin.medaid.domain.Plan
import java.time.LocalDateTime

data class PlanExtractionResult(
    val plan: Plan,
    val overallConfidence: Double,
    val overallStatus: ExtractionStatus,
    val metadataResult: SectionExtractionResult<PlanMetadata>,
    val contributionsResult: SectionExtractionResult<ContributionsData>,
    val benefitsResult: SectionExtractionResult<BenefitsData>,
    val copaymentsResult: SectionExtractionResult<CopaymentsData>,
    val extractionStartTime: LocalDateTime,
    val extractionEndTime: LocalDateTime? = null,
    val extractedBy: String? = null
) {
    val isComplete: Boolean
        get() = metadataResult.errorMessage == null &&
            contributionsResult.errorMessage == null &&
            benefitsResult.errorMessage == null &&
            copaymentsResult.errorMessage == null

    val hasErrors: Boolean
        get() = metadataResult.errorMessage != null ||
            contributionsResult.errorMessage != null ||
            benefitsResult.errorMessage != null ||
            copaymentsResult.errorMessage != null

    val totalRetryAttempts: Int
        get() = metadataResult.retryAttempts +
            contributionsResult.retryAttempts +
            benefitsResult.retryAttempts +
            copaymentsResult.retryAttempts
}

data class PlanMetadata(
    val scheme: String,
    val planName: String,
    val planYear: Int,
    val planType: String,
    val network: String?,
    val summary: String?
)

data class ContributionsData(
    val principal: Double?,
    val adultDependent: Double?,
    val childDependent: Double?,
    val memberTypeContributions: Map<String, Double>
)

data class BenefitsData(
    val hospitalCoverage: String?,
    val chronicCoverage: String?,
    val dayToDayBenefits: String?,
    val networkProviders: List<String>
)

data class CopaymentsData(
    val hospitalCopayment: String?,
    val specialistCopayment: String?,
    val gpCopayment: String?,
    val copaymentStructure: String?
)
