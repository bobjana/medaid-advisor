package cc.zynafin.medaid.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TableAwareChunkerTest {
    private val mockExtractor = mock<PdfTableExtractor>()
    private val mockSerializer = mock<TableMarkdownSerializer>()
    private val chunker = TableAwareChunker(mockExtractor, mockSerializer)

    @Test
    fun `chunk with table produces table_markdown chunk`() {
        val table =
            ExtractedTable(
                headers = listOf("Member Type", "Amount"),
                rows = listOf(listOf("Principal", "R2,269")),
                pageNumber = 1,
                tableType = TableType.CONTRIBUTION,
                tableTitle = null,
            )
        val page = PageExtractionResult(pageNumber = 1, tables = listOf(table), narrativeText = "")
        whenever(mockExtractor.extractTablesAndText(any())).thenReturn(listOf(page))
        whenever(mockSerializer.serializeWithChunking(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(
            listOf("## Contributions\n| Member Type | Amount |\n| --- | --- |\n| Principal | R2,269 |"),
        )

        val result = chunker.chunk("test.pdf", mapOf("scheme" to "Bestmed", "plan_name" to "Beat 1", "year" to 2026))

        assertTrue(result.any { it.metadata["chunk_type"] == "table_markdown" })
        assertTrue(result.any { it.metadata["table_type"] == "CONTRIBUTION" })
        assertTrue(result.any { it.metadata["column_headers"] == "Member Type,Amount" })
    }

    @Test
    fun `chunk with narrative text produces text chunk`() {
        val page = PageExtractionResult(pageNumber = 1, tables = emptyList(), narrativeText = "This is some narrative text about the plan.")
        whenever(mockExtractor.extractTablesAndText(any())).thenReturn(listOf(page))

        val result = chunker.chunk("test.pdf", mapOf("scheme" to "Bestmed", "plan_name" to "Beat 1", "year" to 2026))

        assertTrue(result.any { it.metadata["chunk_type"] == "text" })
    }

    @Test
    fun `chunk with both table and prose produces both chunk types`() {
        val table =
            ExtractedTable(
                headers = listOf("Benefit", "Limit"),
                rows = listOf(listOf("Hospital", "R1,000,000")),
                pageNumber = 1,
                tableType = TableType.BENEFIT,
                tableTitle = null,
            )
        val page = PageExtractionResult(pageNumber = 1, tables = listOf(table), narrativeText = "Some narrative text here.")
        whenever(mockExtractor.extractTablesAndText(any())).thenReturn(listOf(page))
        whenever(mockSerializer.serializeWithChunking(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(
            listOf("## Benefits\n| Benefit | Limit |\n| --- | --- |\n| Hospital | R1,000,000 |"),
        )

        val result = chunker.chunk("test.pdf", mapOf("scheme" to "Bestmed", "plan_name" to "Beat 1", "year" to 2026))

        assertTrue(result.any { it.metadata["chunk_type"] == "table_markdown" })
        assertTrue(result.any { it.metadata["chunk_type"] == "text" })
    }
}
