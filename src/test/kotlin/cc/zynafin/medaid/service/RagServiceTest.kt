package cc.zynafin.medaid.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.jdbc.core.JdbcTemplate

class RagServiceTest {
    private val vectorStore: VectorStore = mock()
    private val tableAwareChunker: TableAwareChunker = mock()
    private val jdbcTemplate: JdbcTemplate = mock()
    private val ragService = RagService(vectorStore, tableAwareChunker, jdbcTemplate, 800, 100)

    @Test
    fun `extractMetadataFromFilename extracts Discovery Health scheme`() {
        val metadata = ragService.extractMetadataFromFilename("discovery-comprehensive-2026.pdf")
        assertEquals("Discovery Health", metadata["scheme"])
        assertEquals(2026, metadata["year"])
        assertEquals("discovery-comprehensive-2026.pdf", metadata["filename"])
    }

    @Test
    fun `extractMetadataFromFilename extracts Bonitas scheme`() {
        val metadata = ragService.extractMetadataFromFilename("bonclassic-2026.pdf")
        assertEquals("Bonitas", metadata["scheme"])
    }

    @Test
    fun `extractMetadataFromFilename extracts Bestmed scheme from beat filename`() {
        val metadata = ragService.extractMetadataFromFilename("Beat 1 Product brochure 2026.pdf")
        assertEquals("Bestmed", metadata["scheme"])
        assertEquals(2026, metadata["year"])
    }

    @Test
    fun `extractMetadataFromFilename extracts Momentum scheme`() {
        val metadata = ragService.extractMetadataFromFilename("Custom Option 2026.pdf")
        assertEquals("Momentum", metadata["scheme"])
    }

    @Test
    fun `extractMetadataFromFilename defaults to Unknown scheme for unrecognized name`() {
        val metadata = ragService.extractMetadataFromFilename("unknown-plan-2026.pdf")
        assertEquals("Unknown", metadata["scheme"])
    }

    @Test
    fun `extractMetadataFromFilename extracts year from filename`() {
        val metadata = ragService.extractMetadataFromFilename("bonitas-bonclassic-2025.pdf")
        assertEquals(2025, metadata["year"])
    }

    @Test
    fun `extractMetadataFromFilename defaults year to 2026 when not present`() {
        val metadata = ragService.extractMetadataFromFilename("discovery-classic.pdf")
        assertEquals(2026, metadata["year"])
    }

    @Test
    fun `extractMetadataFromFilename always includes required metadata keys`() {
        val metadata = ragService.extractMetadataFromFilename("discovery-2026.pdf")
        assertNotNull(metadata["filename"])
        assertNotNull(metadata["scheme"])
        assertNotNull(metadata["plan_name"])
        assertNotNull(metadata["year"])
        assertNotNull(metadata["doc_type"])
        assertNotNull(metadata["source"])
        assertEquals("medaid_docs", metadata["source"])
    }
}
