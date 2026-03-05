package cc.zynafin.medaid.domain.extraction

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class JsonPlanExtractionResult(
    val scheme: String,
    val planName: String,
    val year: Int,
    val extractionStatus: ExtractionStatus,
    val metadata: JsonNode? = null,
    val metadataConfidence: ExtractionConfidence,
    val metadataSource: List<String> = emptyList(),
    val contributions: JsonNode? = null,
    val contributionsConfidence: ExtractionConfidence,
    val contributionsSource: List<String> = emptyList(),
    val benefits: JsonNode? = null,
    val benefitsConfidence: ExtractionConfidence,
    val benefitsSource: List<String> = emptyList(),
    val copayments: JsonNode? = null,
    val copaymentsConfidence: ExtractionConfidence,
    val copaymentsSource: List<String> = emptyList(),
    val overallConfidence: ExtractionConfidence,
    val extractionTimestamp: Instant = Instant.now(),
    val errorMessage: String? = null
)
