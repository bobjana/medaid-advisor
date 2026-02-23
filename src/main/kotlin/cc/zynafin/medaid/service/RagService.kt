package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.document.DocumentReader
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.PdfDocumentReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

@Service
class RagService(
    private val vectorStore: VectorStore,
    @Value("\${medaid.documents.chunk-size:1000}")
    private val chunkSize: Int,
    @Value("\${medaid.documents.chunk-overlap:200}")
    private val chunkOverlap: Int
) {
    private val log = LoggerFactory.getLogger(RagService::class.java)

    /**
     * Ingest a PDF file into the vector store
     */
    fun ingestPdf(file: MultipartFile, metadata: Map<String, Any> = mapOf()) {
        val tempFile = Files.createTempFile("medaid_", ".pdf").toFile()
        tempFile.writeBytes(file.bytes)

        try {
            ingestDocument(tempFile.absolutePath, metadata)
            log.info("Successfully ingested PDF: ${file.originalFilename}")
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Ingest a PDF from a file path
     */
    fun ingestPdfFromPath(filePath: String, metadata: Map<String, Any> = mapOf()) {
        try {
            ingestDocument(filePath, metadata)
            log.info("Successfully ingested PDF: $filePath")
        } catch (e: Exception) {
            log.error("Failed to ingest PDF: $filePath", e)
            throw e
        }
    }

    /**
     * Ingest all PDFs from a directory
     */
    fun ingestDirectory(directoryPath: String) {
        val dir = Path.of(directoryPath.replaceFirst("^~", System.getProperty("user.home")))

        if (!Files.exists(dir)) {
            log.warn("Directory does not exist: $directoryPath")
            return
        }

        val pdfFiles = Files.list(dir)
            .filter { it.toString().endsWith(".pdf", ignoreCase = true) }
            .toList()

        log.info("Found ${pdfFiles.size} PDF files in $directoryPath")

        pdfFiles.forEach { pdfPath ->
            val filename = pdfPath.fileName.toString()
            val metadata = mapOf(
                "filename" to filename,
                "source" to "medaid_docs"
            )
            ingestPdfFromPath(pdfPath.toString(), metadata)
        }
    }

    private fun ingestDocument(filePath: String, metadata: Map<String, Any>) {
        val reader = PdfDocumentReader(filePath)

        // Read all pages
        val documents: List<Document> = reader.get()

        // Add metadata
        val documentsWithMetadata = documents.map { doc ->
            Document(
                doc.id,
                doc.content,
                doc.metadata + metadata + mapOf(
                    "file_path" to filePath
                )
            )
        }

        // Split into chunks
        val splitter = TokenTextSplitter(chunkSize, chunkOverlap, 5, 5000, true)
        val chunks = splitter.apply(documentsWithMetadata)

        // Store in vector database
        vectorStore.add(chunks)

        log.info("Processed ${documents.size} pages into ${chunks.size} chunks")
    }

    /**
     * Search for relevant documents using semantic search
     */
    fun search(query: String, topK: Int = 5): List<Document> {
        val searchRequest = SearchRequest.query(query)
            .withTopK(topK)
            .withSimilarityThreshold(0.7)

        return vectorStore.similaritySearch(searchRequest)
    }

    /**
     * Get explanation for a query based on ingested documents
     */
    fun explain(query: String, llmService: LlmService): String {
        val relevantDocs = search(query, topK = 3)

        if (relevantDocs.isEmpty()) {
            return "No relevant information found in the medical aid documents."
        }

        val context = relevantDocs.joinToString("\n\n") { doc ->
            """Source: ${doc.metadata["filename"] ?: "Unknown"}
Content: ${doc.content}
"""
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

    /**
     * Get statistics about ingested documents
     */
    fun getStats(): Map<String, Any> {
        // This would require vector store-specific implementation
        // For pgvector, we'd query the database
        return mapOf(
            "status" to "RAG service is running",
            "chunk_size" to chunkSize,
            "chunk_overlap" to chunkOverlap
        )
    }
}
