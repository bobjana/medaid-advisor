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
        val tableDocuments = mutableListOf<Document>()
        val proseDocuments = mutableListOf<Document>()
        var totalPages = 0
        
        val pdfDoc: PDDocument = Loader.loadPDF(file)
        pdfDoc.use { document ->
            val stripper = PDFTextStripper()
            totalPages = document.numberOfPages

            for (pageNum in 1..totalPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val text = stripper.getText(document)

                val baseMetadata = HashMap<String, Any>()
                baseMetadata["page_number"] = pageNum
                baseMetadata["total_pages"] = totalPages
                baseMetadata.putAll(metadata)
                baseMetadata["file_path"] = filePath

                if (detectTable(text)) {
                    // Table detected - convert to prose and treat as atomic unit
                    val prose = convertTableToProse(text)
                    val tableMetadata = HashMap(baseMetadata)
                    tableMetadata["table_origin"] = detectTableOrigin(text)
                    tableMetadata["chunk_type"] = "table_prose"
                    tableDocuments.add(Document(prose, tableMetadata))
                    log.debug("Page $pageNum: Detected table, converted to prose (${prose.length} chars)")
                } else {
                    // Prose - will be split by TokenTextSplitter
                    baseMetadata["chunk_type"] = "text"
                    proseDocuments.add(Document(text, baseMetadata))
                }
            }
        }

        // Apply TokenTextSplitter only to prose documents (tables remain atomic)
        val splitter = TokenTextSplitter(
            chunkSize,
            chunkOverlap,
            5,
            1500,
            true
        )
        val proseChunks = splitter.apply(proseDocuments)
        
        // Combine table documents (atomic) with prose chunks
        val allChunks = tableDocuments + proseChunks

        vectorStore.add(allChunks)

        val duration = System.currentTimeMillis() - startTime

        log.info("Processed $totalPages pages: ${tableDocuments.size} tables (atomic), ${proseChunks.size} prose chunks in ${duration}ms for: $filename")

        return IngestionResult(
            success = true,
            filename = filename,
            chunksCreated = allChunks.size,
            pagesProcessed = totalPages,
            durationMs = duration,
            metadata = metadata
        )
    }

    /**
     * Detects the origin/type of a table based on content keywords.
     */
    private fun detectTableOrigin(text: String): String {
        return when {
            text.lowercase().contains("contribution") -> "contribution"
            text.lowercase().contains("benefit") -> "benefit"
            text.lowercase().contains("copayment") -> "copayment"
            else -> "unknown"
        }
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

    /**
     * Detects if text content appears to be a table-like structure.
     * Returns true if EITHER:
     * 1. Pipe-separated table: At least 3 lines with consistent pipe count AND Rand amounts
     * 2. Space-separated table: Contains "Contributions" or "copayment" keyword AND has multiple Rand amount lines
     */
    private fun detectTable(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return false

        // Check for pipe separators with reasonable column count
        val pipeLines = lines.filter { line ->
            val pipeCount = line.count { it == '|' }
            pipeCount >= 2 && pipeCount <= 20
        }
        
        // Pipe-separated table detection
        if (pipeLines.size >= 3) {
            // Check column consistency - allow some variance (max 2 different column counts)
            val columnCounts = pipeLines.map { line -> line.count { it == '|' } }
            if (columnCounts.distinct().size <= 2) {
                // Check for numeric amounts (South African Rand format: R 2 269, R1,764, etc.)
                val amountPattern = "R\\s*\\d+(,\\d+)*".toRegex()
                val linesWithAmounts = lines.count { line -> amountPattern.find(line) != null }
                if (linesWithAmounts >= 2) {
                    return true
                }
            }
        }

        // Space-separated table detection (for PDFs where table structure is not pipe-delimited)
        // Look for contribution/benefit tables that have:
        // - Keywords like "Contributions", "Principal", "Adult", "Child"
        // - Multiple lines with Rand amounts
        val hasContributionKeyword = text.contains("Contributions", ignoreCase = true) ||
                                    text.contains("copayment", ignoreCase = true) ||
                                    text.contains("monthly", ignoreCase = true) &&
                                    (text.contains("R ", ignoreCase = true) || text.contains("R\\d", ignoreCase = true))
        
        if (hasContributionKeyword) {
            val amountPattern = "R\\s*\\d+(,\\d+)*".toRegex()
            val linesWithAmounts = lines.count { line -> 
                amountPattern.find(line) != null && amountPattern.findAll(line).count() >= 2
            }
            // If we have at least 2 lines with 2+ Rand amounts each, it's likely a table
            if (linesWithAmounts >= 2) {
                return true
            }
        }

        // Additional check: lines with multiple Rand amounts (even without contribution keyword)
        val amountPattern = "R\\s*\\d+(,\\d+)*".toRegex()
        val linesWithMultipleAmounts = lines.count { line ->
            amountPattern.findAll(line).count() >= 2
        }
        if (linesWithMultipleAmounts >= 3) {
            return true
        }

        return false
    }

    /**
     * Converts detected table content into readable prose.
     * Handles South African Rand format and detects table type from content keywords.
     */
    private fun convertTableToProse(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }

        val tableType = when {
            text.lowercase().contains("contribution") -> "contribution"
            text.lowercase().contains("benefit") -> "benefit"
            text.lowercase().contains("copayment") -> "copayment"
            else -> "data"
        }

        return when (tableType) {
            "contribution" -> convertContributionTable(lines)
            "benefit" -> convertBenefitTable(lines)
            "copayment" -> convertCopaymentTable(lines)
            else -> convertGenericTable(lines)
        }
    }

    /**
     * Formats South African Rand amounts with proper comma separators.
     * Handles formats like: "R2 269", "R 2 269", "R1,764", "R1 764"
     */
    private fun formatAmount(amount: String): String {
        val cleaned = amount
            .replace("R", " R ")
            .replace(",", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val numberMatch = Regex("R?\\s*([\\d\\s]+)").find(cleaned)
        if (numberMatch == null) return amount

        val digits = numberMatch.groupValues[1].replace(" ", "")

        val formatted = digits.reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

        return "R$formatted"
    }

    /**
     * Converts contribution tables to prose format.
     * Input: "Contributions Network Principal|R 2 269 Adult|R1 764 Child|R956"
     * Output: "Contributions: Principal R2,269, Adult R1,764, Child R956 per month"
     */
    private fun convertContributionTable(lines: List<String>): String {
        val contributions = mutableListOf<String>()
        val amountPattern = Regex("R\\s*[\\d\\s,]+")

        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 2) continue

            val hasAmounts = amountPattern.containsMatchIn(line)
            if (!hasAmounts) continue

            val planName = parts[0]
            val amounts = parts.drop(1).mapNotNull { part ->
                val amountMatch = amountPattern.find(part)
                if (amountMatch != null) {
                    val label = part.replace(amountMatch.value, "").trim()
                    val formattedAmount = formatAmount(amountMatch.value)
                    if (label.isNotEmpty()) "$label $formattedAmount" else formattedAmount
                } else null
            }

            if (amounts.isNotEmpty()) {
                contributions.add("$planName: ${amounts.joinToString(", ")} per month")
            }
        }

        return if (contributions.isNotEmpty()) {
            "Contributions: ${contributions.joinToString("; ")}"
        } else {
            convertGenericTable(lines)
        }
    }

    /**
     * Converts benefit tables to prose format.
     * Input: "Benefit Name|Limit|Per|Cover\nHospital|R1,000,000|Family|Yes"
     * Output: "Benefits: Hospital limit R1,000,000 per family covered, ..."
     */
    private fun convertBenefitTable(lines: List<String>): String {
        val benefits = mutableListOf<String>()
        var header: List<String>? = null

        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 2) continue

            if (parts.any { it.lowercase() in listOf("benefit", "limit", "per", "cover", "name") }) {
                header = parts
                continue
            }

            val benefitName = parts[0]
            val limit = parts.getOrNull(1)?.let { formatAmount(it) } ?: "unlimited"
            val per = parts.getOrNull(2)?.lowercase() ?: "person"
            val covered = parts.getOrNull(3)?.lowercase()?.contains("yes") == true

            val coverage = if (covered) "covered" else "not covered"
            benefits.add("$benefitName limit $limit per $per $coverage")
        }

        return if (benefits.isNotEmpty()) {
            "Benefits: ${benefits.joinToString(", ")}"
        } else {
            convertGenericTable(lines)
        }
    }

    /**
     * Converts copayment tables to prose format.
     */
    private fun convertCopaymentTable(lines: List<String>): String {
        val copayments = mutableListOf<String>()

        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 2) continue

            if (parts.any { it.lowercase() in listOf("copayment", "type", "service", "amount") }) {
                continue
            }

            val serviceType = parts[0]
            val amount = parts.getOrNull(1)?.let { formatAmount(it) } ?: "varies"

            copayments.add("$serviceType: $amount")
        }

        return if (copayments.isNotEmpty()) {
            "Copayments: ${copayments.joinToString(", ")}"
        } else {
            convertGenericTable(lines)
        }
    }

    /**
     * Generic table conversion for unknown table types.
     */
    private fun convertGenericTable(lines: List<String>): String {
        return lines.map { line ->
            line.split("|").map { it.trim() }.joinToString(" - ")
        }.joinToString("; ")
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
