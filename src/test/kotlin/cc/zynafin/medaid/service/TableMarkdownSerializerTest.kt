package cc.zynafin.medaid.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TableMarkdownSerializerTest {
    private val serializer = TableMarkdownSerializer()

    @Test
    fun `serialize basicContributionTable producesValidMarkdown`() {
        val table =
            ExtractedTable(
                headers = listOf("Member Type", "Amount"),
                rows = listOf(listOf("Principal", "R2,269"), listOf("Adult", "R1,764")),
                pageNumber = 2,
                tableType = TableType.CONTRIBUTION,
                tableTitle = null,
            )

        val result = serializer.serialize(table, "Bestmed", "Beat 1", 2026)

        assertTrue(result.contains("| Member Type | Amount |"))
        assertTrue(result.contains("| --- | --- |"))
        assertTrue(result.contains("| Principal | R2,269 |"))
        assertTrue(result.contains("## Contributions"))
        assertTrue(result.contains("Source: Bestmed"))
    }

    @Test
    fun `serialize emptyTable returnsContextHeaderWithNoData`() {
        val table =
            ExtractedTable(
                headers = emptyList(),
                rows = emptyList(),
                pageNumber = 1,
                tableType = TableType.UNKNOWN,
                tableTitle = null,
            )

        val result = serializer.serialize(table, "Bestmed", "Beat 1", 2026)

        assertTrue(result.contains("(no data)"))
    }

    @Test
    fun `serialize cellWithNewlines joinsWithSemicolon`() {
        val table =
            ExtractedTable(
                headers = listOf("Column"),
                rows = listOf(listOf("line1\nline2")),
                pageNumber = 1,
                tableType = TableType.UNKNOWN,
                tableTitle = null,
            )

        val result = serializer.serialize(table, "Bestmed", "Beat 1", 2026)

        assertTrue(result.contains("line1; line2"))
    }

    @Test
    fun `serialize emptyCell returnsDash`() {
        val table =
            ExtractedTable(
                headers = listOf("Column"),
                rows = listOf(listOf("")),
                pageNumber = 1,
                tableType = TableType.UNKNOWN,
                tableTitle = null,
            )

        val result = serializer.serialize(table, "Bestmed", "Beat 1", 2026)

        assertTrue(result.contains("| - |"))
    }

    @Test
    fun `serializeWithChunking smallTable returnsSingleChunk`() {
        val table =
            ExtractedTable(
                headers = listOf("Member Type", "Amount"),
                rows = listOf(listOf("Principal", "R2,269"), listOf("Adult", "R1,764")),
                pageNumber = 1,
                tableType = TableType.CONTRIBUTION,
                tableTitle = null,
            )

        val result = serializer.serializeWithChunking(table, "Bestmed", "Beat 1", 2026, maxChunkChars = 1500)

        assertEquals(1, result.size)
    }

    @Test
    fun `serializeWithChunking largeTable splitsWithHeaderRepetition`() {
        val rows = List(50) { listOf("Principal", "R2,269") }
        val table =
            ExtractedTable(
                headers = listOf("Member Type", "Amount"),
                rows = rows,
                pageNumber = 1,
                tableType = TableType.CONTRIBUTION,
                tableTitle = null,
            )

        val result = serializer.serializeWithChunking(table, "Bestmed", "Beat 1", 2026, maxChunkChars = 500)

        assertTrue(result.size > 1)
        assertTrue(result.all { it.contains("| Member Type | Amount |") })
    }
}
