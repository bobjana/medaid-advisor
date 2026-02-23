package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.EmployeeProfileRepository
import cc.zynafin.medaid.service.RecommendationEngine
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(
    private val recommendationEngine: RecommendationEngine,
    private val employeeProfileRepository: EmployeeProfileRepository
) {

    @PostMapping
    fun generateRecommendations(
        @RequestBody @Valid request: RecommendationRequest
    ): ResponseEntity<RecommendationResponse> {
        // Generate recommendations
        val recommendations = recommendationEngine.recommend(
            profile = request.employeeProfile,
            schemeFilter = request.schemeFilter,
            maxRecommendations = request.maxRecommendations ?: 3,
            weights = request.weights ?: ScoringWeights()
        )

        return ResponseEntity.ok(
            RecommendationResponse(
                employeeProfileId = request.employeeProfileId,
                recommendations = recommendations,
                timestamp = java.time.LocalDateTime.now()
            )
        )
    }

    @GetMapping("/{recommendationId}/explain")
    fun explainRecommendation(
        @PathVariable recommendationId: String,
        @RequestParam(defaultValue = "simple") style: String
    ): ResponseEntity<String> {
        // In a full implementation, we'd fetch the specific recommendation
        // For POC, we'll return a placeholder
        return ResponseEntity.ok("Explanation for recommendation $recommendationId (style: $style)")
    }

    @PostMapping("/compare")
    fun comparePlans(
        @RequestBody @Valid request: ComparisonRequest
    ): ResponseEntity<String> {
        // In a full implementation, we'd use LLM to generate comparison
        // For POC, we'll return a simple comparison
        return ResponseEntity.ok("Comparison between ${request.planIds.size} plans")
    }
}

data class RecommendationRequest(
    val employeeProfileId: String? = null,
    val employeeProfile: EmployeeProfile,
    val schemeFilter: List<String>? = null,
    val maxRecommendations: Int? = 3,
    val weights: ScoringWeights? = null
)

data class RecommendationResponse(
    val employeeProfileId: String?,
    val recommendations: List<Recommendation>,
    val timestamp: java.time.LocalDateTime
)

data class ComparisonRequest(
    val planIds: List<String>,
    val employeeProfile: EmployeeProfile
)
