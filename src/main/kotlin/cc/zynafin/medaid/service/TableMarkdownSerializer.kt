package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Converts [ExtractedTable] objects into Markdown-formatted strings suitable for LLM consumption.
 *
 * Each serialized table includes a context header (plan, year, scheme, page) followed by a
 * standard pipe-delimited Markdown table. Rand amounts in cells are normalized to a consistent
 * format (e.g., "R 2 269" → "R2,269").
 *
 * @see PdfTableExtractor for the [ExtractedTable] and [TableType] definitions.
 */
@Service
class TableMarkdownSerializer {
    private val log = LoggerFactory.getLogger(TableMarkdownSerializer::class.java)

    /**
     * Serializes an [ExtractedTable] to a single Markdown string.
     *
     * Format:
     * ```
     * ## Contributions — BonClassic (2026)
     * Source: Bonitas | Page: 3
     *
     * | Member Type | Monthly Amount |
     * | --- | --- |
     * | Principal | R8,238 |
     * ```
     *
     * @param sectionName when provided, overrides the default [TableType] label in the heading.
     */
    fun serialize(
        table: ExtractedTable,
        scheme: String,
        planName: String,
        year: Int,
        sectionName: String? = null,
    ): String {
        val label = sectionName ?: tableTypeLabel(table.tableType)
        val contextHeader = "## $label — $planName ($year)\nSource: $scheme | Page: ${table.pageNumber}"

        if (table.headers.isEmpty()) {
            log.debug("Table on page ${table.pageNumber} has no headers — returning context header with no-data notice")
            return "$contextHeader\n\n(no data)"
        }

        val sb = StringBuilder()
        sb.append(contextHeader)
        sb.append("\n\n")
        sb.append(buildMarkdownRow(table.headers))
        sb.append("\n")
        sb.append(buildSeparatorRow(table.headers.size))

        for (row in table.rows) {
            sb.append("\n")
            sb.append(buildMarkdownRow(row))
        }

        return sb.toString()
    }

    /**
     * Splits a large table into multiple Markdown chunks, each ≤ [maxChunkChars] characters.
     *
     * Every chunk repeats the context header and column headers so that it is a self-contained,
     * valid Markdown fragment. If the full serialized table fits within [maxChunkChars], the
     * result contains exactly one element (the output of [serialize]).
     *
     * @param maxChunkChars approximate character limit per chunk (default 1 500).
     * @return list of Markdown strings; never empty.
     */
    fun serializeWithChunking(
        table: ExtractedTable,
        scheme: String,
        planName: String,
        year: Int,
        sectionName: String? = null,
        maxChunkChars: Int = 1500,
    ): List<String> {
        val fullMarkdown = serialize(table, scheme, planName, year, sectionName)

        if (fullMarkdown.length <= maxChunkChars) {
            return listOf(fullMarkdown)
        }

        if (table.headers.isEmpty() || table.rows.isEmpty()) {
            log.warn(
                "Table on page ${table.pageNumber} exceeds maxChunkChars=$maxChunkChars " +
                    "but cannot be split (no data rows) — returning as single chunk",
            )
            return listOf(fullMarkdown)
        }

        val label = sectionName ?: tableTypeLabel(table.tableType)
        val contextHeader = "## $label — $planName ($year)\nSource: $scheme | Page: ${table.pageNumber}"
        val chunkHeader =
            "$contextHeader\n\n${buildMarkdownRow(table.headers)}\n${buildSeparatorRow(table.headers.size)}"

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder(chunkHeader)

        for (row in table.rows) {
            val rowStr = "\n${buildMarkdownRow(row)}"
            val hasDataRows = currentChunk.length > chunkHeader.length

            if (hasDataRows && currentChunk.length + rowStr.length > maxChunkChars) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
                currentChunk.append(chunkHeader)
            }
            currentChunk.append(rowStr)
        }

        // Flush remaining rows (always present since table.rows is non-empty)
        if (currentChunk.length > chunkHeader.length) {
            chunks.add(currentChunk.toString())
        }

        log.info(
            "Table on page ${table.pageNumber} (${table.tableType}) split into ${chunks.size} chunks " +
                "(maxChunkChars=$maxChunkChars, total=${fullMarkdown.length} chars)",
        )
        return chunks
    }

    private fun tableTypeLabel(tableType: TableType): String =
        when (tableType) {
            TableType.CONTRIBUTION -> "Contributions"
            TableType.BENEFIT -> "Benefits"
            TableType.COPAYMENT -> "Copayments"
            TableType.CHRONIC_LIST -> "Chronic Conditions"
            TableType.COMPARISON -> "Plan Comparison"
            TableType.UNKNOWN -> "Table"
        }

    private fun buildMarkdownRow(cells: List<String>): String = "| ${cells.joinToString(" | ") { cleanCell(it) }} |"

    private fun buildSeparatorRow(columnCount: Int): String = "| ${(1..columnCount).joinToString(" | ") { "---" }} |"

    /**
     * Cleans a single table cell for Markdown output:
     * - Trims surrounding whitespace
     * - Returns "-" for blank cells
     * - Replaces in-cell newlines with "; "
     * - Normalizes pure Rand amounts (e.g., "R 2 269" → "R2,269")
     */
    private fun cleanCell(cell: String): String {
        val trimmed = cell.trim()
        if (trimmed.isEmpty()) return "-"
        val noNewlines = trimmed.replace(Regex("\\r?\\n|\\r"), "; ")
        return if (PURE_RAND_AMOUNT.matches(noNewlines)) formatAmount(noNewlines) else noNewlines
    }

    /**
     * Formats South African Rand amounts with comma thousands separators.
     *
     * Replicates the logic from `RagService.formatAmount()` (private method — cannot be called
     * directly). Only invoked when [cleanCell] confirms the value is a pure Rand amount.
     *
     * Examples:
     * - "R 2 269"  → "R2,269"
     * - "R1,764"   → "R1,764"  (idempotent)
     * - "R 12 500" → "R12,500"
     */
    private fun formatAmount(amount: String): String {
        val cleaned =
            amount
                .replace("R", " R ")
                .replace(",", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        val numberMatch = Regex("R?\\s*([\\d\\s]+)").find(cleaned) ?: return amount

        val digits = numberMatch.groupValues[1].replace(" ", "")
        if (digits.isEmpty()) return amount

        val formatted =
            digits
                .reversed()
                .chunked(3)
                .joinToString(",")
                .reversed()

        return "R$formatted"
    }

    companion object {
        /**
         * Matches cells that are purely a Rand amount: starts with R (case-insensitive), followed
         * only by digits, spaces, and commas. Guards [formatAmount] from being applied to text-label
         * cells that happen to contain digits (e.g., "Network Option 1").
         */
        private val PURE_RAND_AMOUNT = Regex("^[Rr]\\s*[\\d\\s,]+$")
    }
}
