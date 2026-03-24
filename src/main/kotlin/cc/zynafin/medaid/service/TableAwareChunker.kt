package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.stereotype.Service

@Service
class TableAwareChunker(
    private val pdfTableExtractor: PdfTableExtractor,
    private val tableMarkdownSerializer: TableMarkdownSerializer,
) {
    private val log = LoggerFactory.getLogger(TableAwareChunker::class.java)

    fun chunk(
        pdfPath: String,
        baseMetadata: Map<String, Any>,
        chunkSize: Int = 800,
        chunkOverlap: Int = 100,
    ): List<Document> {
        val scheme = baseMetadata["scheme"]?.toString() ?: "Unknown"
        val planName = baseMetadata["plan_name"]?.toString() ?: "Unknown"
        val year = (baseMetadata["year"] as? Int) ?: 2026

        val pages = pdfTableExtractor.extractTablesAndText(pdfPath)

        val tableChunks = mutableListOf<Document>()
        val proseDocuments = mutableListOf<Document>()

        for (page in pages) {
            for (table in page.tables) {
                val serializedChunks =
                    tableMarkdownSerializer.serializeWithChunking(table, scheme, planName, year)
                for (chunkStr in serializedChunks) {
                    val metadata = HashMap<String, Any>(baseMetadata)
                    metadata["page_number"] = page.pageNumber
                    metadata["chunk_type"] = "table_markdown"
                    metadata["table_type"] = table.tableType.name
                    metadata["column_headers"] = table.headers.joinToString(",")
                    tableChunks.add(Document(chunkStr, metadata))
                }
            }

            if (page.narrativeText.isNotBlank()) {
                val proseMetadata = HashMap<String, Any>(baseMetadata)
                proseMetadata["page_number"] = page.pageNumber
                proseMetadata["chunk_type"] = "text"
                proseDocuments.add(Document(page.narrativeText, proseMetadata))
            }
        }

        val proseChunks =
            if (proseDocuments.isNotEmpty()) {
                TokenTextSplitter(chunkSize, chunkOverlap, 5, 1500, true).apply(proseDocuments)
            } else {
                emptyList()
            }

        log.info(
            "Chunked $pdfPath: ${tableChunks.size} table chunk(s), ${proseChunks.size} prose chunk(s) " +
                "across ${pages.size} page(s)",
        )

        return tableChunks + proseChunks
    }
}
