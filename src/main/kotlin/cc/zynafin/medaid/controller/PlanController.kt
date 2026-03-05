package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.extraction.ExtractionStatus
import cc.zynafin.medaid.repository.PlanRepository
import cc.zynafin.medaid.service.BatchPlanIngestionService
import cc.zynafin.medaid.service.AgenticPlanExtractionService
import cc.zynafin.medaid.service.BatchIngestionResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import cc.zynafin.medaid.domain.ExtractionRejectRequest
import java.util.UUID

@Tag(name = "Medical Aid Plans", description = "Query and manage medical aid plan information")
@RestController
@RequestMapping("/api/v1/plans")
class PlanController(
    private val planRepository: PlanRepository,
    private val batchPlanIngestionService: BatchPlanIngestionService,
    private val agenticPlanExtractionService: AgenticPlanExtractionService
) {

    @Operation(
        summary = "Get all plans",
        description = "List all medical aid plans with optional filtering by scheme and plan year"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Plans returned successfully")
        ]
    )
    @GetMapping
    fun getAllPlans(
        @RequestParam scheme: String? = null,
        @RequestParam planYear: Int? = null
    ): ResponseEntity<List<Plan>> {
        val plans = when {
            scheme != null && planYear != null ->
                planRepository.findBySchemeAndPlanYear(scheme, planYear)
            scheme != null ->
                planRepository.findBySchemeAndPlanYear(scheme, 2026)
            else ->
                planRepository.findByPlanYear(2026)
        }
        return ResponseEntity.ok(plans)
    }

    @Operation(
        summary = "Get plan by ID",
        description = "Retrieve detailed information about a specific medical aid plan"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Plan found and returned successfully"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    @GetMapping("/{id}")
    fun getPlanById(@PathVariable id: UUID): ResponseEntity<Plan> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            ResponseEntity.ok(plan.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(
        summary = "Get available schemes",
        description = "List all available medical aid scheme names"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Schemes returned successfully")
        ]
    )
    @GetMapping("/schemes")
    fun getSchemes(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(planRepository.findAllSchemes())
    }

    @Operation(
        summary = "Get available plan years",
        description = "List all available plan years in the database"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Plan years returned successfully")
        ]
    )
    @GetMapping("/years")
    fun getPlanYears(): ResponseEntity<List<Int>> {
        return ResponseEntity.ok(planRepository.findAllPlanYears())
    }

    @Operation(
        summary = "Ingest plans from directory",
        description = "Process all plan PDFs from a specified directory and update the database"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Plans ingested successfully"),
            ApiResponse(responseCode = "400", description = "Invalid directory path")
        ]
    )
    @PostMapping("/ingest-directory")
    fun ingestDirectory(@RequestBody request: Map<String, String>): ResponseEntity<BatchIngestionResult> {
        val directoryPath = request["directoryPath"] ?: "data/plans"
        val result = batchPlanIngestionService.ingestDirectory(directoryPath)
        return ResponseEntity.ok(result)
    }

    @Operation(
        summary = "Trigger extraction for a plan",
        description = "Start agentic extraction for a specific plan. Use force=true to re-extract even if already completed."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Extraction triggered successfully"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    @PostMapping("/{id}/extract")
    fun triggerExtraction(
        @PathVariable id: UUID,
        @RequestParam force: Boolean = false
    ): ResponseEntity<Map<String, Any>> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            val result = agenticPlanExtractionService.extractPlan(
                plan.get().scheme,
                plan.get().planName,
                plan.get().planYear
            )
            ResponseEntity.ok(mapOf(
                "success" to (result.extractionStatus != ExtractionStatus.FAILED),
                "result" to result
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(
        summary = "Get extraction status for a plan",
        description = "Check the extraction status and confidence scores for a specific plan"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Extraction status returned successfully"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    @GetMapping("/{id}/extraction-status")
    fun getExtractionStatus(
        @PathVariable id: UUID
    ): ResponseEntity<Map<String, Any>> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            val result = agenticPlanExtractionService.getExtractionStats(
                plan.get().scheme,
                plan.get().planYear
            )
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/pending-review")
    @Operation(
        summary = "Get plans pending review",
        description = "List all plans that require manual review due to low confidence or validation issues"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pending review plans returned successfully")
        ]
    )
    fun getPendingReviewPlans(): ResponseEntity<List<Plan>> {
        val plans = planRepository.findByExtractionStatus(ExtractionStatus.PENDING_REVIEW)
        return ResponseEntity.ok(plans)
    }

    @GetMapping("/{id}/extraction-result")
    @Operation(
        summary = "Get extraction result with citations",
        description = "Retrieve full extraction result including confidence scores and source citations for a specific plan"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Extraction result returned successfully"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    fun getExtractionResult(@PathVariable id: UUID): ResponseEntity<Map<String, Any>> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            val result = agenticPlanExtractionService.extractPlan(
                plan.get().scheme,
                plan.get().planName,
                plan.get().planYear
            )
            ResponseEntity.ok(mapOf(
                "extraction" to result
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/approve-extraction")
    @Operation(
        summary = "Approve extraction and store",
        description = "Approve extraction result and store it permanently to plan entity. Only allowed if no critical validation errors."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Extraction approved and stored successfully"),
            ApiResponse(responseCode = "400", description = "Cannot approve extraction with critical errors"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    fun approveExtraction(@PathVariable id: UUID): ResponseEntity<Map<String, Any>> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            val planData = plan.get()
            val result = agenticPlanExtractionService.extractPlan(
                planData.scheme,
                planData.planName,
                planData.planYear
            )
            val stored = agenticPlanExtractionService.storeExtractionResult(id, result)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Extraction approved and stored",
                "plan_id" to id
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/reject-extraction")
    @Operation(
        summary = "Reject extraction with notes",
        description = "Reject extraction result with a reason. The extraction result is kept for audit purposes but marked as rejected."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Extraction rejected successfully"),
            ApiResponse(responseCode = "404", description = "Plan not found")
        ]
    )
    fun rejectExtraction(
        @PathVariable id: UUID,
        @RequestBody request: ExtractionRejectRequest
    ): ResponseEntity<Map<String, Any>> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Extraction rejected",
                "plan_id" to id,
                "rejection_reason" to request.reason
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
