package cc.zynafin.medaid.domain

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to reject an extraction result with a reason")
data class ExtractionRejectRequest(
    @Schema(description = "Reason for rejecting the extraction", example = "Validation failed: contributions not numeric", required = true)
    val reason: String
)
