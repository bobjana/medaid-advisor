package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.extraction.PlanExtractionResult
import cc.zynafin.medaid.domain.extraction.ExtractionStatus
import cc.zynafin.medaid.service.AgenticPlanExtractionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/plans")
class AgenticPlanExtractionController(
    private val extractionService: AgenticPlanExtractionService
) {
    private val log = LoggerFactory.getLogger(AgenticPlanExtractionController::class.java)

    @PostMapping("/{scheme}/{planName}/{year}/extract")
    fun extractPlan(
        @PathVariable scheme: String,
        @PathVariable planName: String,
        @PathVariable year: Int,
        @RequestParam force: Boolean = false
    ): ResponseEntity<Map<String, Any>> {
        log.info("Received extraction request for {}/{} {}", scheme, planName, year)

        val result = extractionService.extractPlan(scheme, planName, year)

        return ResponseEntity.ok(mapOf(
            "success" to (result.extractionStatus != ExtractionStatus.FAILED),
            "result" to result
        ))
    }

    @PostMapping("/batch-extract")
    fun extractPlanBatch(
        @RequestParam scheme: String? = null,
        @RequestParam year: Int? = null,
        @RequestParam force: Boolean = false
    ): ResponseEntity<Map<String, Any>> {
        log.info("Received batch extraction request: scheme={}, year={}", scheme, year)

        val results = extractionService.extractPlanBatch(scheme, year)

        val successCount = results.count { it.extractionStatus == ExtractionStatus.VALIDATED }
        val failedCount = results.count { it.extractionStatus == ExtractionStatus.FAILED }
        val pendingReviewCount = results.count { it.extractionStatus == ExtractionStatus.PENDING_REVIEW }

        return ResponseEntity.ok(mapOf(
            "total" to results.size,
            "success" to successCount,
            "failed" to failedCount,
            "pending_review" to pendingReviewCount,
            "results" to results
        ))
    }

    @GetMapping("/{scheme}/{planName}/{year}/extraction-status")
    fun getExtractionStatus(
        @PathVariable scheme: String,
        @PathVariable planName: String,
        @PathVariable year: Int
    ): ResponseEntity<Map<String, Any>> {
        val stats = extractionService.getExtractionStats(scheme, year)

        val planStats = stats["extraction_status_counts"] as? Map<*, *>

        val status = planStats?.get(ExtractionStatus.VALIDATED)?.let {
            mapOf(
                "status" to "VALIDATED",
                "confidence" to stats["confidence_distribution"]
            )
        } ?: mapOf(
            "status" to "NOT_COMPLETED",
            "details" to planStats
        )

        return ResponseEntity.ok(mapOf(
            "scheme" to scheme,
            "plan_name" to planName,
            "year" to year,
            "extraction" to status
        ))
    }

    @GetMapping("/extraction-stats")
    fun getExtractionStats(
        @RequestParam scheme: String? = null,
        @RequestParam year: Int? = null
    ): ResponseEntity<Map<String, Any>> {
        val stats = extractionService.getExtractionStats(scheme, year)
        return ResponseEntity.ok(stats)
    }

    @PostMapping("/{scheme}/{planName}/{year}/approve")
    fun approveExtraction(
        @PathVariable scheme: String,
        @PathVariable planName: String,
        @PathVariable year: Int
    ): ResponseEntity<Map<String, Any>> {
        log.info("Approving extraction for {}/{} {}", scheme, planName, year)

        return ResponseEntity.ok(mapOf(
            "message" to "Extraction approved",
            "scheme" to scheme,
            "plan_name" to planName,
            "year" to year
        ))
    }

    @PostMapping("/{scheme}/{planName}/{year}/reject")
    fun rejectExtraction(
        @PathVariable scheme: String,
        @PathVariable planName: String,
        @PathVariable year: Int,
        @RequestBody reason: Map<String, String>
    ): ResponseEntity<Map<String, Any>> {
        log.info("Rejecting extraction for {}/{} {}. Reason: {}", scheme, planName, year, reason["reason"])

        return ResponseEntity.ok(mapOf(
            "message" to "Extraction rejected",
            "scheme" to scheme,
            "plan_name" to planName,
            "year" to year,
            "reason" to (reason["reason"] ?: "")
        ))
    }
}
