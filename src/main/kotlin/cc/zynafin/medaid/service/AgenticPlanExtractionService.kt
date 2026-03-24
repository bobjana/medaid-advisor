package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.extraction.JsonPlanExtractionResult
import cc.zynafin.medaid.domain.extraction.ExtractionConfidence
import cc.zynafin.medaid.domain.extraction.ExtractionStatus

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.repository.PlanRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
open class AgenticPlanExtractionService(
    private val planRetrievalService: PlanRetrievalService,
    private val llmRouter: LlmRouter,
    private val validationService: ExtractionValidationService,
    private val planRepository: PlanRepository,
    private val contributionRepository: cc.zynafin.medaid.repository.ContributionRepository,
    private val hospitalBenefitRepository: cc.zynafin.medaid.repository.HospitalBenefitRepository
) {
    private val log = LoggerFactory.getLogger(AgenticPlanExtractionService::class.java)
    private val objectMapper = ObjectMapper()
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    @Transactional
    fun extractPlan(scheme: String, planName: String, year: Int): JsonPlanExtractionResult {
        log.info("Starting agentic extraction for {}/{} {}", scheme, planName, year)

        val plan = planRepository.findBySchemeAndPlanNameAndPlanYear(scheme, planName, year)

        if (plan == null) {
            log.warn("Plan not found: {}/{} {}", scheme, planName, year)
            return JsonPlanExtractionResult(
                scheme = scheme,
                planName = planName,
                year = year,
                extractionStatus = ExtractionStatus.FAILED,
                metadataConfidence = ExtractionConfidence.FAILED,
                contributionsConfidence = ExtractionConfidence.FAILED,
                benefitsConfidence = ExtractionConfidence.FAILED,
                copaymentsConfidence = ExtractionConfidence.FAILED,
                overallConfidence = ExtractionConfidence.FAILED,
                errorMessage = "Plan not found in database"
            )
        }

        try {

            // Run all section extractions in parallel
            val metadataFuture = CompletableFuture.supplyAsync({
                extractSection(plan, "metadata") {
                    llmRouter.extractMetadataWithRouting(it.data, scheme, planName, year)
                }
            }, executor)

            val contributionsFuture = CompletableFuture.supplyAsync({
                extractSection(plan, "contributions") {
                    llmRouter.extractContributionsWithRouting(it.data, scheme, planName, year)
                }
            }, executor)

            val benefitsFuture = CompletableFuture.supplyAsync({
                extractSection(plan, "benefits") {
                    llmRouter.extractBenefitsWithRouting(it.data, scheme, planName, year)
                }
            }, executor)

            val copaymentsFuture = CompletableFuture.supplyAsync({
                extractSection(plan, "copayments") {
                    llmRouter.extractCopaymentsWithRouting(it.data, scheme, planName, year)
                }
            }, executor)

            // Wait for all to complete
            CompletableFuture.allOf(metadataFuture, contributionsFuture, benefitsFuture, copaymentsFuture).join()

            val metadataResult = metadataFuture.join()
            val contributionsResult = contributionsFuture.join()
            val benefitsResult = benefitsFuture.join()
            val copaymentsResult = copaymentsFuture.join()

            val overallConfidence = calculateOverallConfidence(
                *arrayOf(metadataResult, contributionsResult, benefitsResult, copaymentsResult)
            )

            val extractionStatus = determineExtractionStatus(
                overallConfidence,
                *arrayOf(metadataResult, contributionsResult, benefitsResult, copaymentsResult)
            )
            val result = JsonPlanExtractionResult(
                scheme = scheme,
                planName = planName,
                year = year,
                extractionStatus = extractionStatus,
                metadata = metadataResult.data,
                metadataConfidence = ExtractionConfidence.fromScore(metadataResult.confidence),
                metadataSource = metadataResult.sourceChunks.map { it.content },
                contributions = contributionsResult.data,
                contributionsConfidence = ExtractionConfidence.fromScore(contributionsResult.confidence),
                contributionsSource = contributionsResult.sourceChunks.map { it.content },
                benefits = benefitsResult.data,
                benefitsConfidence = ExtractionConfidence.fromScore(benefitsResult.confidence),
                benefitsSource = benefitsResult.sourceChunks.map { it.content },
                copayments = copaymentsResult.data,
                copaymentsConfidence = ExtractionConfidence.fromScore(copaymentsResult.confidence),
                copaymentsSource = copaymentsResult.sourceChunks.map { it.content },
                overallConfidence = ExtractionConfidence.fromScore(overallConfidence.score),
                extractionTimestamp = java.time.Instant.now()
            )

            if (extractionStatus == ExtractionStatus.PENDING_REVIEW) {
                log.warn("Plan extraction requires review due to low confidence: {}/{} {}", scheme, planName, year)
            }

            return result

        } catch (e: Exception) {
            log.error("Failed to extract plan: {}/{} {}", scheme, planName, year, e)
            return JsonPlanExtractionResult(
                scheme = scheme,
                planName = planName,
                year = year,
                extractionStatus = ExtractionStatus.FAILED,
                metadataConfidence = ExtractionConfidence.FAILED,
                contributionsConfidence = ExtractionConfidence.FAILED,
                benefitsConfidence = ExtractionConfidence.FAILED,
                copaymentsConfidence = ExtractionConfidence.FAILED,
                overallConfidence = ExtractionConfidence.FAILED,
                errorMessage = "Extraction failed: ${e.message}"
            )
        }
    }

    @Transactional
    fun extractPlanBatch(scheme: String? = null, year: Int? = null): List<JsonPlanExtractionResult> {
        val plans = if (scheme != null && year != null) {
            planRepository.findBySchemeAndPlanYear(scheme, year)
        } else if (scheme != null) {
            emptyList()
        } else if (year != null) {
            planRepository.findByPlanYear(year)
        } else {
            planRepository.findAll()
        }

        log.info("Starting batch extraction for {} plans", plans.size)

        return plans.map { plan ->
            extractPlan(plan.scheme, plan.planName, plan.planYear)
        }
    }

    private fun extractSection(
        plan: Plan,
        sectionName: String,
        extraction: (SectionExtractionResult<String>) -> SectionExtractionResult<JsonNode>
    ): SectionExtractionResult<JsonNode> {
        val retrievalResult = when (sectionName) {
            "metadata" -> planRetrievalService.retrieveForMetadata(plan.scheme, plan.planName, plan.planYear)
            "contributions" -> planRetrievalService.retrieveForContributions(plan.scheme, plan.planName, plan.planYear)
            "benefits" -> planRetrievalService.retrieveForBenefits(plan.scheme, plan.planName, plan.planYear)
            "copayments" -> planRetrievalService.retrieveForCopayments(plan.scheme, plan.planName, plan.planYear)
            else -> throw IllegalArgumentException("Unknown section: $sectionName")
        }

        if (retrievalResult.data == null) {
            log.warn("No retrieved data for section: {}", sectionName)
            return SectionExtractionResult(
                data = objectMapper.createObjectNode(),
                confidence = 0.0,
                sourceChunks = emptyList(),
                errorMessage = retrievalResult.errorMessage
            )
        }

        val validationResult = if (retrievalResult.data != null) {
            validationService.validateExtractionData(extraction(retrievalResult).data ?: objectMapper.createObjectNode())
        } else {
            null
        }

        val result = extraction(retrievalResult)

        if (validationResult != null && !validationResult.isValid) {
            log.warn("Validation errors for section {}: {}", sectionName, validationResult.errors)
        }

        return result
    }

    private fun calculateOverallConfidence(
        vararg results: SectionExtractionResult<JsonNode>
    ): ExtractionConfidence {
        if (results.any { it.confidence == 0.0 }) {
            return ExtractionConfidence.FAILED
        }

        val scores = results.map { it.confidence }
        val avgScore = scores.average()

        return ExtractionConfidence.fromScore(avgScore)
    }

    private fun determineExtractionStatus(
        overallConfidence: ExtractionConfidence,
        vararg results: SectionExtractionResult<JsonNode>
    ): ExtractionStatus {
        if (results.any { it.errorMessage != null }) {
            return ExtractionStatus.FAILED
        }

        if (results.any { it.confidence == 0.0 }) {
            return ExtractionStatus.PENDING_REVIEW
        }

        return when {
            overallConfidence.score >= 0.8 -> ExtractionStatus.VALIDATED
            overallConfidence.score >= 0.6 -> ExtractionStatus.VALIDATED
            else -> ExtractionStatus.PENDING_REVIEW
        }
    }

    fun getExtractionStats(scheme: String? = null, year: Int? = null): Map<String, Any> {
        val plans = if (scheme != null && year != null) {
            planRepository.findBySchemeAndPlanYear(scheme, year)
        } else if (scheme != null) {
            emptyList()
        } else if (year != null) {
            planRepository.findByPlanYear(year)
        } else {
            planRepository.findAll()
        }

        val stats = mapOf(
            "total_plans" to plans.size,
            "extraction_status_counts" to plans.groupBy { it.extractionStatus }
                .mapValues { it.value.size }
        )
        log.info("Extraction stats: {}", stats)
        return stats
    }

    @Transactional
    fun storeExtractionResult(planId: UUID, extractionResult: JsonPlanExtractionResult): JsonPlanExtractionResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val metadata = extractionResult.metadata
        val hasMedicalSavingsAccount = if (metadata != null && metadata.has("has_medical_savings_account")) {
            metadata.get("has_medical_savings_account").asBoolean()
        } else {
            plan.hasMedicalSavingsAccount
        }
        val msaPercentage = if (metadata != null && metadata.has("msa_percentage")) {
            metadata.get("msa_percentage").asDouble()
        } else {
            plan.msaPercentage
        }

        val updatedPlan = Plan(
            id = plan.id,
            scheme = plan.scheme,
            planName = plan.planName,
            planYear = plan.planYear,
            planType = plan.planType,
            extractionStatus = extractionResult.extractionStatus,
            principalContribution = plan.principalContribution,
            adultDependentContribution = plan.adultDependentContribution,
            childDependentContribution = plan.childDependentContribution,
            benefits = plan.benefits,
            copayments = plan.copayments,
            hospitalBenefits = plan.hospitalBenefits,
            chronicBenefits = plan.chronicBenefits,
            dayToDayBenefits = plan.dayToDayBenefits,
            hasMedicalSavingsAccount = hasMedicalSavingsAccount,
            msaPercentage = msaPercentage,
            createdAt = plan.createdAt,
            sourceDocument = plan.sourceDocument
        )
        planRepository.save(updatedPlan)

        val completenessValidation = validationService.validatePlanCompleteness(planId)
        if (!completenessValidation.isValid) {
            log.warn(
                "Plan completeness validation failed for {}/{} ({}): {}",
                plan.scheme,
                plan.planName,
                plan.planYear,
                completenessValidation.errors.joinToString("; ")
            )
            
            val planWithReviewStatus = Plan(
                id = updatedPlan.id,
                scheme = updatedPlan.scheme,
                planName = updatedPlan.planName,
                planYear = updatedPlan.planYear,
                planType = updatedPlan.planType,
                extractionStatus = ExtractionStatus.PENDING_REVIEW,
                principalContribution = updatedPlan.principalContribution,
                adultDependentContribution = updatedPlan.adultDependentContribution,
                childDependentContribution = updatedPlan.childDependentContribution,
                benefits = updatedPlan.benefits,
                copayments = updatedPlan.copayments,
                hospitalBenefits = updatedPlan.hospitalBenefits,
                chronicBenefits = updatedPlan.chronicBenefits,
                dayToDayBenefits = updatedPlan.dayToDayBenefits,
                hasMedicalSavingsAccount = updatedPlan.hasMedicalSavingsAccount,
                msaPercentage = updatedPlan.msaPercentage,
                createdAt = updatedPlan.createdAt,
                sourceDocument = updatedPlan.sourceDocument
            )
            planRepository.save(planWithReviewStatus)
            
            log.info(
                "Updated extraction status to PENDING_REVIEW for plan {} due to missing data: {}",
                planId,
                completenessValidation.errors.joinToString(", ")
            )
        }

        log.info("Stored extraction result for plan {}", planId)

        return extractionResult
    }
}
