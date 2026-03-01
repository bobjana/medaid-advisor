package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.repository.PlanRepository
import cc.zynafin.medaid.service.BatchPlanIngestionService
import cc.zynafin.medaid.service.BatchIngestionResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Medical Aid Plans", description = "Query and manage medical aid plan information")
@RestController
@RequestMapping("/api/v1/plans")
class PlanController(
    private val planRepository: PlanRepository,
    private val batchPlanIngestionService: BatchPlanIngestionService
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
}
