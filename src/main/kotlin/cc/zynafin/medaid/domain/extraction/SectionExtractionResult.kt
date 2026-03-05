package cc.zynafin.medaid.domain.extraction

/**
 * Source citation for tracking which chunks provided extracted data.
 * Provides audit trail for extraction decisions.
 */
data class SourceCitation(
    val chunkId: String,
    val content: String,
    val pageNumber: Int,
    val similarityScore: Double
)

/**
 * Generic container for per-section extraction results.
 * Used for contributions, benefits, copayments, and metadata sections.
 *
 * @param T Type of extracted data (e.g., Contribution, Benefit, etc.)
 */
data class SectionExtractionResult<T>(
    val data: T,
    val confidence: Double,
    val sourceChunks: List<SourceCitation>,
    val retryAttempts: Int = 0,
    val errorMessage: String? = null
)
