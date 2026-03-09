package cc.zynafin.medaid.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

@Service
open class RagService(
    private val vectorStore: VectorStore,
    @Value("\${medaid.documents.chunk-size:800}")
    private val chunkSize: Int,
    @Value("\${medaid.documents.chunk-overlap:100}")
    private val chunkOverlap: Int
) {
    private val log = LoggerFactory.getLogger(RagService::class.java)

    fun extractMetadataFromFilename(filename: String): Map<String, Any> {
        val normalizedName = filename.replace(".pdf", "", ignoreCase = true)
            .replace("_", " ")
            .replace("-", " ")

        val yearRegex = "(20\\d{2})".toRegex()
        val year = yearRegex.find(normalizedName)?.groupValues?.get(1)?.toIntOrNull() ?: 2026

        val scheme = when {
            normalizedName.contains("discovery", ignoreCase = true) -> "Discovery Health"
            normalizedName.contains("bonitas", ignoreCase = true) ||
                normalizedName.contains("bon", ignoreCase = true) -> "Bonitas"
            normalizedName.contains("bestmed", ignoreCase = true) ||
                normalizedName.contains("beat", ignoreCase = true) ||
                normalizedName.contains("pace", ignoreCase = true) ||
                normalizedName.contains("rhythm", ignoreCase = true) -> "Bestmed"
            normalizedName.contains("momentum", ignoreCase = true) ||
                normalizedName.contains("custom", ignoreCase = true) ||
                normalizedName.contains("evolve", ignoreCase = true) ||
                normalizedName.contains("extender", ignoreCase = true) ||
                normalizedName.contains("incentive", ignoreCase = true) ||
                normalizedName.contains("ingwe", ignoreCase = true) ||
                normalizedName.contains("summit", ignoreCase = true) -> "Momentum"
            else -> "Unknown"
        }

        val planName = extractPlanName(normalizedName)

        val docType = when {
            normalizedName.contains("brochure", ignoreCase = true) -> "brochure"
            normalizedName.contains("guide", ignoreCase = true) -> "guide"
            normalizedName.contains("overview", ignoreCase = true) -> "overview"
            normalizedName.contains("comparative", ignoreCase = true) -> "comparative"
            else -> "document"
        }

        return mapOf(
            "filename" to filename,
            "scheme" to scheme,
            "plan_name" to planName,
            "year" to year,
            "doc_type" to docType,
            "source" to "medaid_docs"
        )
    }

    private fun extractPlanName(normalizedName: String): String {
        return when {
            normalizedName.contains("comprehensive", ignoreCase = true) -> "Comprehensive"
            normalizedName.contains("core", ignoreCase = true) -> "Core"
            normalizedName.contains("saver", ignoreCase = true) -> "Saver"
            normalizedName.contains("keycare", ignoreCase = true) -> "KeyCare"
            normalizedName.contains("priority", ignoreCase = true) -> "Priority"
            normalizedName.contains("smart saver", ignoreCase = true) -> "Smart Saver"
            normalizedName.contains("smart", ignoreCase = true) -> "Smart"
            normalizedName.contains("executive", ignoreCase = true) -> "Executive"
            normalizedName.contains("bonclassic", ignoreCase = true) -> "BonClassic"
            normalizedName.contains("boncomprehensive", ignoreCase = true) -> "BonComprehensive"
            normalizedName.contains("boncomplete", ignoreCase = true) -> "BonComplete"
            normalizedName.contains("boncore", ignoreCase = true) -> "BonCore"
            normalizedName.contains("bonessential", ignoreCase = true) -> "BonEssential"
            normalizedName.contains("bonprime", ignoreCase = true) -> "BonPrime"
            normalizedName.contains("bonfit", ignoreCase = true) -> "BonFit"
            normalizedName.contains("bonsave", ignoreCase = true) -> "BonSave"
            normalizedName.contains("bonstart plus", ignoreCase = true) -> "BonStart Plus"
            normalizedName.contains("bonstart", ignoreCase = true) -> "BonStart"
            normalizedName.contains("boncap", ignoreCase = true) -> "BonCap"
            normalizedName.contains("hospital standard", ignoreCase = true) -> "Hospital Standard"
            normalizedName.contains("hospital", ignoreCase = true) -> "Hospital"
            normalizedName.contains("primary", ignoreCase = true) -> "Primary"
            normalizedName.contains("standard select", ignoreCase = true) -> "Standard Select"
            normalizedName.contains("standard", ignoreCase = true) -> "Standard"
            normalizedName.contains("beat 1", ignoreCase = true) -> "Beat 1"
            normalizedName.contains("beat 2", ignoreCase = true) -> "Beat 2"
            normalizedName.contains("beat 3 plus", ignoreCase = true) -> "Beat 3 Plus"
            normalizedName.contains("beat 3", ignoreCase = true) -> "Beat 3"
            normalizedName.contains("beat 4", ignoreCase = true) -> "Beat 4"
            normalizedName.contains("pace 1", ignoreCase = true) -> "Pace 1"
            normalizedName.contains("pace 2", ignoreCase = true) -> "Pace 2"
            normalizedName.contains("pace 3", ignoreCase = true) -> "Pace 3"
            normalizedName.contains("pace 4", ignoreCase = true) -> "Pace 4"
            normalizedName.contains("rhythm 1", ignoreCase = true) -> "Rhythm 1"
            normalizedName.contains("rhythm 2", ignoreCase = true) -> "Rhythm 2"
            normalizedName.contains("evolve", ignoreCase = true) -> "Evolve"
            normalizedName.contains("extender", ignoreCase = true) -> "Extender"
            normalizedName.contains("custom", ignoreCase = true) -> "Custom"
            normalizedName.contains("incentive", ignoreCase = true) -> "Incentive"
            normalizedName.contains("ingwe", ignoreCase = true) -> "Ingwe"
            normalizedName.contains("summit", ignoreCase = true) -> "Summit"
            normalizedName.contains("brochure", ignoreCase = true) ->
                normalizedName.substringBefore("Product").substringBefore("brochure").trim()
            normalizedName.contains("guide", ignoreCase = true) ->
                normalizedName.substringBefore("plan").substringBefore("guide").trim()
            else -> normalizedName.substringBefore(" ").trim()
        }
    }

    fun ingestPdf(file: MultipartFile, metadata: Map<String, Any> = mapOf()): IngestionResult {
        val tempFile = Files.createTempFile("medaid_", ".pdf").toFile()
        tempFile.writeBytes(file.bytes)

        return try {
            val result = ingestDocument(tempFile.absolutePath, metadata)
            log.info("Successfully ingested PDF: ${file.originalFilename}")
            result
        } finally {
            tempFile.delete()
        }
    }

    fun ingestPdfFromPath(filePath: String, metadata: Map<String, Any> = mapOf()): IngestionResult {
        return try {
            val result = ingestDocument(filePath, metadata)
            log.info("Successfully ingested PDF: $filePath")
            result
        } catch (e: Exception) {
            log.error("Failed to ingest PDF: $filePath", e)
            IngestionResult(
                success = false,
                filename = Path.of(filePath).fileName.toString(),
                chunksCreated = 0,
                error = e.message
            )
        }
    }

    fun ingestDirectory(directoryPath: String): DirectoryIngestionResult {
        val dir = Path.of(directoryPath.replaceFirst("^~", System.getProperty("user.home")))

        if (!Files.exists(dir)) {
            log.warn("Directory does not exist: $directoryPath")
            return DirectoryIngestionResult(
                success = false,
                directory = directoryPath,
                totalFiles = 0,
                successfulIngestions = 0,
                failedIngestions = 0,
                results = emptyList(),
                error = "Directory does not exist"
            )
        }

        val pdfFiles = Files.list(dir)
            .filter { it.toString().endsWith(".pdf", ignoreCase = true) }
            .sorted()
            .toList()

        log.info("Found ${pdfFiles.size} PDF files in $directoryPath")

        val results = mutableListOf<IngestionResult>()
        var successCount = 0
        var failureCount = 0

        pdfFiles.forEachIndexed { index, pdfPath ->
            val filename = pdfPath.fileName.toString()
            log.info("[${index + 1}/${pdfFiles.size}] Processing: $filename")

            val extractedMetadata = extractMetadataFromFilename(filename)

            val result = ingestPdfFromPath(pdfPath.toString(), extractedMetadata)
            results.add(result)

            if (result.success) {
                successCount++
            } else {
                failureCount++
            }
        }

        log.info("Directory ingestion complete. Success: $successCount, Failed: $failureCount")

        return DirectoryIngestionResult(
            success = failureCount == 0,
            directory = directoryPath,
            totalFiles = pdfFiles.size,
            successfulIngestions = successCount,
            failedIngestions = failureCount,
            results = results
        )
    }

    private fun ingestDocument(filePath: String, metadata: Map<String, Any>): IngestionResult {
        val filename = Path.of(filePath).fileName.toString()
        val startTime = System.currentTimeMillis()

        val file = java.io.File(filePath)
        val documents = mutableListOf<Document>()
        
        val pdfDoc: PDDocument = Loader.loadPDF(file)
        pdfDoc.use { document ->
            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages

            for (pageNum in 1..totalPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val text = stripper.getText(document)

                val meta = HashMap<String, Any>()
                meta["page_number"] = pageNum
                meta["total_pages"] = totalPages

                documents.add(Document(
                    "$filePath-page-$pageNum",
                    text,
                    meta
                ))
            }
        }

        val documentsWithMetadata = documents.map { doc ->
            val combinedMetadata = HashMap<String, Any>()
            combinedMetadata.putAll(doc.metadata)
            combinedMetadata.putAll(metadata)
            combinedMetadata["file_path"] = filePath
            Document(doc.id, doc.content, combinedMetadata)
        }

        val splitter = TokenTextSplitter(
            chunkSize,
            chunkOverlap,
            5,
            1500,
            true
        )
        val chunks = splitter.apply(documentsWithMetadata)

        vectorStore.add(chunks)

        val duration = System.currentTimeMillis() - startTime

        log.info("Processed ${documents.size} pages into ${chunks.size} chunks in ${duration}ms for: $filename")

        return IngestionResult(
            success = true,
            filename = filename,
            chunksCreated = chunks.size,
            pagesProcessed = documents.size,
            durationMs = duration,
            metadata = metadata
        )
    }

    fun search(query: String, topK: Int = 5): List<Document> {
        val searchRequest = SearchRequest.query(query)
            .withTopK(topK)
            .withSimilarityThreshold(0.0)

        return vectorStore.similaritySearch(searchRequest)
    }

    fun explain(query: String, llmService: LlmService): String {
        val relevantDocs = search(query, topK = 3)

        if (relevantDocs.isEmpty()) {
            return "No relevant information found in the medical aid documents."
        }

        val context = relevantDocs.joinToString("\n\n") { doc ->
            val source = doc.metadata["filename"] ?: "Unknown"
            "Source: $source\nContent: ${doc.content}"
        }

        val prompt = """
            You are a medical aid advisor. Answer the user's question based on the provided document excerpts.

            CONTEXT:
            $context

            QUESTION: $query

            Provide a clear, accurate answer. If the context doesn't contain enough information, say so.
            Cite the source document for any facts you mention.

            Answer:
        """.trimIndent()

        return llmService.generateResponse(prompt)
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "status" to "RAG service is running",
            "chunk_size" to chunkSize,
            "chunk_overlap" to chunkOverlap
        )
    }
}

data class IngestionResult(
    val success: Boolean,
    val filename: String,
    val chunksCreated: Int = 0,
    val pagesProcessed: Int = 0,
    val durationMs: Long = 0,
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
)

data class DirectoryIngestionResult(
    val success: Boolean,
    val directory: String,
    val totalFiles: Int,
    val successfulIngestions: Int,
    val failedIngestions: Int,
    val results: List<IngestionResult>,
    val error: String? = null
) {
    val totalChunks: Int get() = results.sumOf { it.chunksCreated }
    val totalPages: Int get() = results.sumOf { it.pagesProcessed }
    val totalDurationMs: Long get() = results.sumOf { it.durationMs }
}
