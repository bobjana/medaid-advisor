package cc.zynafin.medaid.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import technology.tabula.ObjectExtractor
import technology.tabula.extractors.BasicExtractionAlgorithm
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm
import java.io.File

data class ExtractedTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val pageNumber: Int,
    val tableType: TableType,
    val tableTitle: String?,
)

data class PageExtractionResult(
    val pageNumber: Int,
    val tables: List<ExtractedTable>,
    val narrativeText: String,
)

enum class TableType {
    CONTRIBUTION,
    BENEFIT,
    COPAYMENT,
    CHRONIC_LIST,
    COMPARISON,
    UNKNOWN,
}

/**
 * Extracts structured tables from medical aid PDFs using Tabula-java.
 *
 * All Tabula calls are wrapped in try-catch: Tabula 1.0.5 was compiled against
 * PDFBox 2.x whose API changed in 3.x, so runtime failures are expected and
 * handled by returning an empty table list while narrative text extraction
 * continues via PDFBox 3.x PDFTextStripper.
 */
@Service
class PdfTableExtractor {
    private val log = LoggerFactory.getLogger(PdfTableExtractor::class.java)

    // Matches South-African Rand amounts: "R 2 269", "R1,764", "R12.50"
    private val randAmountPattern = Regex("r\\s*\\d+", RegexOption.IGNORE_CASE)

    fun extractTablesAndText(pdfPath: String): List<PageExtractionResult> {
        val file = File(pdfPath)
        val filename = file.name
        val results = mutableListOf<PageExtractionResult>()
        var totalTables = 0

        Loader.loadPDF(file).use { pdfDocument ->
            val pageCount = pdfDocument.numberOfPages

            for (pageNum in 1..pageCount) {
                val tables = extractTablesFromPage(pdfDocument, pageNum)
                val narrativeText = extractNarrativeText(pdfDocument, pageNum)

                totalTables += tables.size
                results.add(
                    PageExtractionResult(
                        pageNumber = pageNum,
                        tables = tables,
                        narrativeText = narrativeText,
                    ),
                )
            }

            log.info("PDF $filename: $totalTables tables found across $pageCount pages")
        }

        return results
    }

    private fun extractTablesFromPage(
        pdfDocument: PDDocument,
        pageNum: Int,
    ): List<ExtractedTable> {
        return try {
            // ObjectExtractor.close() closes the PDDocument it received — do NOT use .use {} here;
            // ownership of pdfDocument belongs to the Loader.loadPDF block in the caller.
            val extractor = ObjectExtractor(pdfDocument)
            val page = extractor.extract(pageNum)

            // Primary: lattice mode — ideal for fully-bordered tables (all 39 PDFs in corpus)
            val latticeTables = SpreadsheetExtractionAlgorithm().extract(page)
            if (latticeTables.isNotEmpty()) {
                log.debug("Page $pageNum: ${latticeTables.size} table(s) via lattice mode")
                return latticeTables.map { table ->
                    buildExtractedTable(
                        allRows = table.rows.map { row -> row.map { cell -> cell.getText().trim() } },
                        pageNum = pageNum,
                    )
                }
            }

            // Fallback: stream mode — for non-bordered / whitespace-delimited tables
            val streamTables = BasicExtractionAlgorithm().extract(page)
            if (streamTables.isNotEmpty()) {
                log.debug("Page $pageNum: ${streamTables.size} table(s) via stream mode (fallback)")
                return streamTables.map { table ->
                    buildExtractedTable(
                        allRows = table.rows.map { row -> row.map { cell -> cell.getText().trim() } },
                        pageNum = pageNum,
                    )
                }
            }

            emptyList()
        } catch (e: Exception) {
            log.warn("Tabula extraction failed for page $pageNum, falling back to text: ${e.message}")
            emptyList()
        }
    }

    private fun buildExtractedTable(
        allRows: List<List<String>>,
        pageNum: Int,
    ): ExtractedTable {
        if (allRows.isEmpty()) {
            return ExtractedTable(
                headers = emptyList(),
                rows = emptyList(),
                pageNumber = pageNum,
                tableType = TableType.UNKNOWN,
                tableTitle = null,
            )
        }

        val headers = allRows.first()
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        return ExtractedTable(
            headers = headers,
            rows = dataRows,
            pageNumber = pageNum,
            tableType = detectTableType(headers, dataRows),
            // Accurate title detection requires PDFTextStripperByArea with the table's
            // bounding box coordinates — not available from Tabula alone.
            tableTitle = null,
        )
    }

    /**
     * Classifies a table by examining header and sample-row keywords.
     * Mirrors domain vocabulary from RagService.detectTableOrigin().
     *
     * Priority order:
     *  COMPARISON   – 5+ columns → multi-plan comparison layout
     *  CONTRIBUTION – premium/member-type keywords + Rand amounts
     *  CHRONIC_LIST – chronic / ICD / PMB keywords
     *  COPAYMENT    – copayment / specialist / fee keywords
     *  BENEFIT      – benefit / hospital / cover / limit keywords
     *  UNKNOWN      – catch-all
     */
    private fun detectTableType(
        headers: List<String>,
        rows: List<List<String>>,
    ): TableType {
        if (headers.size >= 5) return TableType.COMPARISON

        val sampleText = (headers + rows.take(3).flatten()).joinToString(" ").lowercase()

        return when {
            sampleText.containsAny("contribution", "premium", "principal", "adult", "child") &&
                (sampleText.contains("r ") || randAmountPattern.containsMatchIn(sampleText)) -> {
                TableType.CONTRIBUTION
            }

            sampleText.containsAny("chronic", "condition", "icd", "pmb") -> {
                TableType.CHRONIC_LIST
            }

            sampleText.containsAny("copayment", "co-payment", "fee", "charge", "specialist") -> {
                TableType.COPAYMENT
            }

            sampleText.containsAny("benefit", "hospital", "cover", "limit", "day-to-day") -> {
                TableType.BENEFIT
            }

            else -> {
                TableType.UNKNOWN
            }
        }
    }

    private fun extractNarrativeText(
        pdfDocument: PDDocument,
        pageNum: Int,
    ): String =
        try {
            val stripper = PDFTextStripper()
            stripper.startPage = pageNum
            stripper.endPage = pageNum
            stripper.getText(pdfDocument).trim()
        } catch (e: Exception) {
            log.warn("PDFTextStripper failed for page $pageNum: ${e.message}")
            ""
        }

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }
}
