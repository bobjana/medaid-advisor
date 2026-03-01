package cc.zynafin.medaid.controller

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.EmployeeProfileRepository
import cc.zynafin.medaid.service.RecommendationEngine
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Recommendations", description = "Generate and compare medical aid recommendations")
@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(
    private val recommendationEngine: RecommendationEngine,
    private val employeeProfileRepository: EmployeeProfileRepository
) {

    @Operation(
        summary = "Generate recommendations",
        description = "Generate personalized medical aid recommendations based on employee profile and preferences"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body")
        ]
    )
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

    @Operation(
        summary = "Explain recommendation",
        description = "Get a detailed explanation for a specific recommendation"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Explanation generated successfully"),
            ApiResponse(responseCode = "404", description = "Recommendation not found")
        ]
    )
    @GetMapping("/{recommendationId}/explain")
    fun explainRecommendation(
        @PathVariable recommendationId: String,
        @RequestParam(defaultValue = "simple") style: String
    ): ResponseEntity<String> {
        // In a full implementation, we'd fetch specific recommendation
        // For POC, we'll return a placeholder
        return ResponseEntity.ok("Explanation for recommendation $recommendationId (style: $style)")
    }

    @Operation(
        summary = "Compare plans",
        description = "Compare multiple medical aid plans side by side"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comparison generated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body")
        ]
    )
    @PostMapping("/compare")
    fun comparePlans(
        @RequestBody @Valid request: ComparisonRequest
    ): ResponseEntity<String> {
        // In a full implementation, we'd use LLM to generate comparison
        // For POC, we'll return a simple comparison
        return ResponseEntity.ok("Comparison between ${request.planIds.size} plans")
    }
}

@Schema(description = "Request object for generating medical aid recommendations")
data class RecommendationRequest(
    @Schema(description = "Optional employee profile ID if updating existing profile", example = "profile-123")
    val employeeProfileId: String? = null,
    @Schema(description = "Employee profile with health and financial information")
    val employeeProfile: EmployeeProfile,
    @Schema(description = "Filter to specific medical aid schemes", example = "[\"Discovery Health\", \"Bonitas\"]")
    val schemeFilter: List<String>? = null,
    @Schema(description = "Maximum number of recommendations to return", example = "3", minimum = "1", maximum = "10")
    val maxRecommendations: Int? = 3,
    @Schema(description = "Custom scoring weights for recommendation algorithm")
    val weights: ScoringWeights? = null
)

@Schema(description = "Response containing generated recommendations")
data class RecommendationResponse(
    @Schema(description = "Employee profile ID", example = "profile-123")
    val employeeProfileId: String?,
    @Schema(description = "List of recommended medical aid plans with scores")
    val recommendations: List<Recommendation>,
    @Schema(description = "Timestamp of when recommendations were generated", example = "2026-02-28T10:30:00")
    val timestamp: java.time.LocalDateTime
)

@Schema(description = "Request object for comparing multiple medical aid plans")
data class ComparisonRequest(
    @Schema(description = "List of plan IDs to compare", example = "[\"plan-1\", \"plan-2\"]")
    val planIds: List<String>,
    @Schema(description = "Employee profile for context")
    val employeeProfile: EmployeeProfile
)
