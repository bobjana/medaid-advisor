package cc.zynafin.medaid.domain.extraction

/**
 * Confidence level for extraction results.
 * Maps confidence scores to qualitative levels for routing decisions.
 */
enum class ExtractionConfidence(val score: Double) {
    /**
     * High confidence: >= 0.90
     * Extracted using local LLM (Ollama) only, very reliable
     */
    HIGH(0.90),

    /**
     * Medium confidence: 0.75 - 0.89
     * Extracted using local LLM (Ollama), reliable
     */
    MEDIUM(0.75),

    /**
     * Low confidence: 0.60 - 0.74
     * May need remote LLM (Claude/GPT-4) for improved accuracy
     */
    LOW(0.60),

    /**
     * Manual review required: < 0.60
     * Confidence too low, requires human-in-the-loop verification
     */
    MANUAL_REVIEW(0.0),

    /**
     * Extraction failed completely
     * Error occurred during extraction process
     */
    FAILED(0.0);

    companion object {
        /**
         * Maps a numeric confidence score to ExtractionConfidence level.
         *
         * @param score Confidence score from 0.0 to 1.0
         * @return ExtractionConfidence level
         */
        fun fromScore(score: Double): ExtractionConfidence {
            return when {
                score >= 0.90 -> HIGH
                score >= 0.75 -> MEDIUM
                score >= 0.60 -> LOW
                else -> MANUAL_REVIEW
            }
        }
    }
}
