package cc.zynafin.medaid.domain.extraction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull

class SectionExtractionResultTest {

    @Test
    fun shouldCreateWithDefaultValues() {
        val result = SectionExtractionResult<String>(
                data = "test data",
                confidence = 0.85,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = null
        )

        assertEquals("test data", result.data)
        assertEquals(0.85, result.confidence)
        assertTrue(result.sourceChunks.isEmpty())
        assertEquals(0, result.retryAttempts)
        assertNull(result.errorMessage)
    }

    @Test
    fun shouldCreateWithErrorMessage() {
        val result = SectionExtractionResult<String>(
                data = "",
                confidence = 0.0,
                sourceChunks = emptyList(),
                retryAttempts = 3,
                errorMessage = "Extraction failed"
        )

        assertEquals("", result.data)
        assertEquals(0.0, result.confidence)
        assertEquals(3, result.retryAttempts)
        assertEquals("Extraction failed", result.errorMessage)
    }

    @Test
    fun shouldCreateWithSourceCitations() {
        val citations = listOf(
            SourceCitation("chunk-1", "excerpt 1", 1, 0.95),
            SourceCitation("chunk-2", "excerpt 2", 2, 0.88),
            SourceCitation("chunk-3", "excerpt 3", 3, 0.92)
        )

        val result = SectionExtractionResult<String>(
                data = "test data",
                confidence = 0.90,
                sourceChunks = citations,
                retryAttempts = 1,
                errorMessage = null
        )

        assertEquals(3, result.sourceChunks.size)
        assertEquals("chunk-1", result.sourceChunks[0].chunkId)
        assertEquals("excerpt 1", result.sourceChunks[0].content)
        assertEquals(1, result.sourceChunks[0].pageNumber)
        assertEquals(0.95, result.sourceChunks[0].similarityScore)
    }

    @Test
    fun shouldWorkWithDifferentDataTypes() {
        // Test with custom data class
        data class TestData(val name: String, val value: Int)

        val result = SectionExtractionResult<TestData>(
                data = TestData("test", 42),
                confidence = 0.80,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = null
        )

        assertEquals("test", result.data.name)
        assertEquals(42, result.data.value)
    }

    @Test
    fun shouldCreateWithNonZeroRetryAttempts() {
        val result = SectionExtractionResult<String>(
                data = "test data",
                confidence = 0.70,
                sourceChunks = emptyList(),
                retryAttempts = 5,
                errorMessage = null
        )

        assertEquals(5, result.retryAttempts)
    }

    @Test
    fun sourceCitationShouldCreateWithAllFields() {
        val citation = SourceCitation(
                chunkId = "test-chunk-123",
                content = "This is the content excerpt",
                pageNumber = 42,
                similarityScore = 0.95
        )

        assertEquals("test-chunk-123", citation.chunkId)
        assertEquals("This is the content excerpt", citation.content)
        assertEquals(42, citation.pageNumber)
        assertEquals(0.95, citation.similarityScore)
    }

    @Test
    fun shouldTrackLowConfidence() {
        val result = SectionExtractionResult<String>(
                data = "test data",
                confidence = 0.55,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = null
        )

        assertEquals(0.55, result.confidence)
    }

    @Test
    fun shouldTrackHighConfidence() {
        val result = SectionExtractionResult<String>(
                data = "test data",
                confidence = 0.98,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = null
        )

        assertEquals(0.98, result.confidence)
    }
}
