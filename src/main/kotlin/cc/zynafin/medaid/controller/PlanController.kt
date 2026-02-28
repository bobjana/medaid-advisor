package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.repository.PlanRepository
import cc.zynafin.medaid.service.BatchPlanIngestionService
import cc.zynafin.medaid.service.BatchIngestionResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/v1/plans")
class PlanController(
    private val planRepository: PlanRepository,
    private val batchPlanIngestionService: BatchPlanIngestionService
) {

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

    @GetMapping("/{id}")
    fun getPlanById(@PathVariable id: String): ResponseEntity<Plan> {
        val plan = planRepository.findById(id)
        return if (plan.isPresent) {
            ResponseEntity.ok(plan.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/schemes")
    fun getSchemes(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(planRepository.findAllSchemes())
    }

    @GetMapping("/years")
    fun getPlanYears(): ResponseEntity<List<Int>> {
        return ResponseEntity.ok(planRepository.findAllPlanYears())
    }

    @PostMapping("/ingest-directory")
    fun ingestDirectory(@RequestBody request: Map<String, String>): ResponseEntity<BatchIngestionResult> {
        val directoryPath = request["directoryPath"] ?: "data/plans"
        val result = batchPlanIngestionService.ingestDirectory(directoryPath)
        return ResponseEntity.ok(result)
    }

}
