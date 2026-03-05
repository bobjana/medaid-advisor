package cc.zynafin.medaid.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/**
 * Configuration properties for agentic plan extraction.
 *
 * These properties control the hybrid LLM routing strategy, confidence thresholds,
 * and remote LLM configuration.
 */
@ConfigurationProperties(prefix = "medaid.extraction", ignoreInvalidFields = false)
data class ExtractionProperties(
    /**
     * Enable/disable agentic extraction feature.
     * Default is false to ensure opt-in rollout.
     */
    val agenticEnabled: Boolean = false,

    /**
     * Confidence threshold for using local LLM (Ollama).
     * Extractions with confidence >= this value use local Ollama (free, fast).
     */
    val confidenceThresholdLocal: Double = 0.75,

    /**
     * Confidence threshold for flagging extractions for human review.
     * Extractions with confidence < this value require manual review.
     */
    val confidenceThresholdReview: Double = 0.6,

    /**
     * Remote LLM configuration for low-confidence extractions.
     * Used when confidence < confidenceThresholdLocal.
     */
    val remoteLlm: RemoteLlmProperties = RemoteLlmProperties()
) {

    companion object {
        /**
         * Default values for validation and documentation.
         */
        const val DEFAULT_CONFIDENCE_THRESHOLD_LOCAL = 0.75
        const val DEFAULT_CONFIDENCE_THRESHOLD_REVIEW = 0.6
    }
}

/**
 * Remote LLM API configuration.
 *
 * Uses environment variables for sensitive values (API key, base URL).
 */
data class RemoteLlmProperties(
    /**
     * Base URL for remote LLM API.
     * Example: https://api.openai.com/v1
     */
    val baseUrl: String? = null,

    /**
     * Model name for remote LLM.
     * Example: gpt-4, claude-3-opus
     */
    val model: String? = null,

    /**
     * API key for remote LLM.
     * Set via REMOTE_LLM_API_KEY environment variable.
     * NOTE: Never log this value.
     */
    val apiKey: String? = null
)
