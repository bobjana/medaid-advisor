package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.BenefitCategory
import cc.zynafin.medaid.domain.MemberType
import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import cc.zynafin.medaid.repository.PlanRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

@Service
open class BatchPlanIngestionService(
    private val planDataService: PlanDataService,
    private val ragService: RagService,
    private val planRepository: PlanRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(BatchPlanIngestionService::class.java)
        private const val DEFAULT_INGEST_DIR = "data/plans"
    }

    /**
     * Batch ingest plan data from all PDFs in a directory.
     * Creates or updates plans based on PDF metadata. Idempotent operation.
     *
     * @param directoryPath Path to directory containing PDF files
     * @return BatchIngestionResult with summary of processing
     */
    fun ingestDirectory(directoryPath: String): BatchIngestionResult {
        val dir = Path.of(directoryPath.replaceFirst("^~", System.getProperty("user.home")))

        if (!Files.exists(dir)) {
            log.error("Directory does not exist: $directoryPath")
            return BatchIngestionResult(
                success = false,
                directory = directoryPath,
                totalFiles = 0,
                successfulIngestions = 0,
                failedIngestions = 0,
                skippedIngestions = 0,
                results = emptyList(),
                error = "Directory does not exist"
            )
        }

        val pdfFiles = Files.list(dir)
            .filter { it.toString().endsWith(".pdf", ignoreCase = true) }
            .filter { shouldProcessFile(it.fileName.toString()) }
            .sorted()
            .toList()

        if (pdfFiles.isEmpty()) {
            log.warn("No PDF files found in: $directoryPath")
            return BatchIngestionResult(
                success = false,
                directory = directoryPath,
                totalFiles = 0,
                successfulIngestions = 0,
                failedIngestions = 0,
                skippedIngestions = 0,
                results = emptyList(),
                error = "No PDF files found"
            )
        }

        log.info("Starting batch ingestion from: $directoryPath")
        log.info("Found ${pdfFiles.size} PDF files to process")

        val results = mutableListOf<BatchItemResult>()
        var successCount = 0
        var failureCount = 0
        var skippedCount = 0

        pdfFiles.forEachIndexed { index, pdfPath ->
            val filename = pdfPath.fileName.toString()
            log.info("[${index + 1}/${pdfFiles.size}] Processing: $filename")

            val result = ingestSinglePdf(pdfPath)
            results.add(result)

            when {
                result.status == BatchItemStatus.SUCCESS -> {
                    successCount++
                    log.info("  ✓ Successfully processed: $filename")
                }
                result.status == BatchItemStatus.SKIPPED -> {
                    skippedCount++
                    log.warn("  ⊘ Skipped: $filename - ${result.message}")
                }
                else -> {
                    failureCount++
                    log.error("  ✗ Failed: $filename - ${result.error}")
                }
            }
        }

        val overallSuccess = failureCount == 0 && successCount > 0

        log.info("Batch ingestion complete. Success: $successCount, Failed: $failureCount, Skipped: $skippedCount")

        return BatchIngestionResult(
            success = overallSuccess,
            directory = directoryPath,
            totalFiles = pdfFiles.size,
            successfulIngestions = successCount,
            failedIngestions = failureCount,
            skippedIngestions = skippedCount,
            results = results
        )
    }

    /**
     * Ingest a single PDF file and create/update the plan.
     * Idempotent - if plan exists for scheme+name+year, it will be updated.
     */
    @Transactional
    open fun ingestSinglePdf(pdfPath: Path): BatchItemResult {
        val filename = pdfPath.fileName.toString()
        val absolutePath = pdfPath.toAbsolutePath().toString()

        return try {
            val metadata = ragService.extractMetadataFromFilename(filename)
            val scheme = metadata["scheme"] as String
            val planName = metadata["plan_name"] as String
            val year = metadata["year"] as Int

            if (scheme == "Unknown" || planName.isBlank()) {
                return BatchItemResult(
                    filename = filename,
                    status = BatchItemStatus.SKIPPED,
                    message = "Could not extract valid scheme or plan name from filename",
                    planId = null,
                    contributionsExtracted = 0,
                    benefitsExtracted = 0,
                    error = null
                )
            }

            val planType = inferPlanType(planName, filename)

            val existingPlan = planRepository.findBySchemeAndPlanNameAndPlanYear(scheme, planName, year)

            val plan = if (existingPlan != null) {
                log.debug("Found existing plan: ${existingPlan.id} - updating with new data")
                existingPlan
            } else {
                log.debug("Creating new plan for: $scheme - $planName ($year)")
                val newPlan = Plan(
                    scheme = scheme,
                    planName = planName,
                    planYear = year,
                    planType = planType,
                    principalContribution = 0.0,
                    sourceDocument = absolutePath,
                    createdAt = LocalDate.now()
                )
                planRepository.save(newPlan)
            }

            val planId = requireNotNull(plan.id) { "Persisted plan is missing id for file: $filename" }

            log.debug("Processing plan: ${plan.id} - ${plan.scheme} ${plan.planName} (${plan.planYear})")

            val contributionsResult = planDataService.parseAndStoreContributions(
                pdfPath.toString(),
                planId
            )

            val benefitsResult = planDataService.parseAndStoreHospitalBenefits(
                pdfPath.toString(),
                planId
            )

            val copaymentResult = planDataService.parseAndStoreCopayments(
                pdfPath.toString(),
                planId
            )

            val msaInfo = planDataService.extractMsaInfo(pdfPath.toString())

            val enrichedPlan = enrichPlanFromParsedData(
                plan = plan,
                sourceDocument = absolutePath,
                contributionsResult = contributionsResult,
                benefitsResult = benefitsResult,
                copaymentResult = copaymentResult,
                msaInfo = msaInfo
            )
            val savedPlan = planRepository.save(enrichedPlan)

            BatchItemResult(
                filename = filename,
                status = BatchItemStatus.SUCCESS,
                message = if (existingPlan != null) "Updated existing plan" else "Created new plan",
                planId = savedPlan.id,
                planName = "${savedPlan.scheme} ${savedPlan.planName}",
                planYear = savedPlan.planYear,
                contributionsExtracted = contributionsResult.contributionsExtracted,
                benefitsExtracted = benefitsResult.benefitsExtracted,
                error = null
            )

        } catch (e: Exception) {
            log.error("Error processing PDF: $filename", e)
            BatchItemResult(
                filename = filename,
                status = BatchItemStatus.FAILED,
                message = "Failed to process PDF",
                planId = null,
                contributionsExtracted = 0,
                benefitsExtracted = 0,
                error = e.message
            )
        }
    }

    /**
     * Infer plan type from plan name and filename.
     */
    private fun inferPlanType(planName: String, filename: String): PlanType {
        val nameLower = planName.lowercase()
        val fileLower = filename.lowercase()
        
        return when {
            nameLower.contains("comprehensive") || nameLower.contains("complete") -> PlanType.COMPREHENSIVE
            nameLower.contains("saver") || nameLower.contains("savings") -> PlanType.SAVINGS
            nameLower.contains("network") || nameLower.contains("keycare") || 
                nameLower.contains("cap") || nameLower.contains("ingwe") -> PlanType.NETWORK
            nameLower.contains("hospital") || nameLower.contains("standard") -> PlanType.HOSPITAL
            fileLower.contains("hospital") && !nameLower.contains("comprehensive") -> PlanType.HOSPITAL
            fileLower.contains("network") || fileLower.contains("cap") -> PlanType.NETWORK
            else -> PlanType.COMPREHENSIVE
        }
    }

    /**
     * Determine if a file should be processed.
     * Skips Momentum Option files and other non-core plan documents.
     */
    private fun shouldProcessFile(filename: String): Boolean {
        val lowerName = filename.lowercase()

        if (lowerName.contains("comparative") ||
            lowerName.contains("overview")) {
            log.debug("Skipping overview/comparative file: $filename")
            return false
        }

        val isAddOnRider = lowerName.contains("add-on") ||
            lowerName.contains("add on") ||
            lowerName.contains("addon") ||
            lowerName.contains("illness benefit option") ||
            lowerName.contains("cover limit option")

        if (isAddOnRider) {
            log.debug("Skipping add-on rider document: $filename")
            return false
        }

        return true
    }

    private fun enrichPlanFromParsedData(
        plan: Plan,
        sourceDocument: String,
        contributionsResult: ContributionParseResult,
        benefitsResult: HospitalBenefitParseResult,
        copaymentResult: CopaymentParseResult,
        msaInfo: MsaInfo
    ): Plan {
        val contributionByMemberType = contributionsResult.contributions.associateBy { it.memberType }

        val principalContribution = contributionByMemberType[MemberType.PRINCIPAL]?.monthlyAmount
            ?: plan.principalContribution
        val adultDependentContribution = contributionByMemberType[MemberType.SPOUSE]?.monthlyAmount
            ?: plan.adultDependentContribution
        val childDependentContribution = contributionByMemberType[MemberType.CHILD_FIRST]?.monthlyAmount
            ?: plan.childDependentContribution

        val categorySummaries = benefitsResult.benefits
            .groupBy { it.category }
            .mapValues { (_, categoryBenefits) ->
                categoryBenefits.joinToString(" | ") { benefit ->
                    val limit = benefit.limitPerPerson
                        ?: benefit.limitPerFamily
                        ?: benefit.annualLimit
                        ?: "covered"
                    "${benefit.benefitName}: $limit"
                }
            }

        val benefitMap = categorySummaries.mapKeys { (category, _) ->
            category.name.lowercase()
        }

        val hospitalSummary = categorySummaries[BenefitCategory.HOSPITAL_COVER]?.take(4000)
        val chronicSummary = listOfNotNull(
            categorySummaries[BenefitCategory.CHRONIC_MEDICINE],
            categorySummaries[BenefitCategory.PRESCRIBED_MINIMUM_BENEFITS]
        ).joinToString(" | ").ifBlank { null }?.take(4000)
        val dayToDaySummary = listOfNotNull(
            categorySummaries[BenefitCategory.SPECIALIST_CONSULTATION],
            categorySummaries[BenefitCategory.DENTAL],
            categorySummaries[BenefitCategory.OPTICAL]
        ).joinToString(" | ").ifBlank { null }?.take(4000)

        val hasMedicalSavingsAccount = msaInfo.hasMedicalSavingsAccount ||
            plan.hasMedicalSavingsAccount ||
            plan.planType == PlanType.SAVINGS ||
            plan.planName.lowercase().contains("saver") ||
            plan.planName.lowercase().contains("save")

        return Plan(
            id = plan.id,
            scheme = plan.scheme,
            planName = plan.planName,
            planYear = plan.planYear,
            planType = plan.planType,
            principalContribution = principalContribution,
            adultDependentContribution = adultDependentContribution,
            childDependentContribution = childDependentContribution,
            benefits = if (benefitMap.isNotEmpty()) benefitMap else plan.benefits,
            copayments = if (copaymentResult.copayments.isNotEmpty()) copaymentResult.copayments else plan.copayments,
            hospitalBenefits = hospitalSummary ?: plan.hospitalBenefits,
            chronicBenefits = chronicSummary ?: plan.chronicBenefits,
            dayToDayBenefits = dayToDaySummary ?: plan.dayToDayBenefits,
            hasMedicalSavingsAccount = hasMedicalSavingsAccount,
            msaPercentage = msaInfo.msaPercentage ?: plan.msaPercentage,
            createdAt = plan.createdAt,
            sourceDocument = sourceDocument
        )
    }
}

enum class BatchItemStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}

data class BatchIngestionResult(
    val success: Boolean,
    val directory: String,
    val totalFiles: Int,
    val successfulIngestions: Int,
    val failedIngestions: Int,
    val skippedIngestions: Int,
    val results: List<BatchItemResult>,
    val error: String? = null
) {
    val totalContributions: Int get() = results.sumOf { it.contributionsExtracted }
    val totalBenefits: Int get() = results.sumOf { it.benefitsExtracted }
}

data class BatchItemResult(
    val filename: String,
    val status: BatchItemStatus,
    val message: String,
    val planId: UUID?,
    val planName: String? = null,
    val planYear: Int? = null,
    val contributionsExtracted: Int = 0,
    val benefitsExtracted: Int = 0,
    val error: String? = null
)