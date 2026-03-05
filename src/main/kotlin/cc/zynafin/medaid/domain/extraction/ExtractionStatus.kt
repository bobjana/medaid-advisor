package cc.zynafin.medaid.domain.extraction

/**
 * Extraction status tracking for plan extraction lifecycle.
 * Tracks the state of extraction from PENDING through completion.
 */
enum class ExtractionStatus {
    /**
     * Initial state: Extraction requested but not started
     */
    PENDING,

    /**
     * Active state: Extraction in progress, LLM processing
     */
    EXTRACTING,

    /**
     * Final success state: Extraction completed and validated
     */
    VALIDATED,

    /**
     * Failure state: Extraction failed with errors
     */
    FAILED,

    /**
     * Review required state: Extraction completed with low confidence or validation issues
     * Requires human approval before use
     */
    PENDING_REVIEW;

    companion object {
        private val validTransitions = mapOf(
                // Initial flow
                PENDING to setOf(EXTRACTING),
                // Success flow
                EXTRACTING to setOf(VALIDATED, FAILED, PENDING_REVIEW),
                // Review approval flow
                PENDING_REVIEW to setOf(VALIDATED),
                // Retry flow
                FAILED to setOf(EXTRACTING, PENDING)
        )

        /**
         * Checks if a transition from current status to target status is valid.
         *
         * @param target The target status to transition to
         * @return true if transition is valid, false otherwise
         */
        fun canTransitionFrom(current: ExtractionStatus, target: ExtractionStatus): Boolean {
            return validTransitions[current]?.contains(target) ?: false
        }
    }
}
