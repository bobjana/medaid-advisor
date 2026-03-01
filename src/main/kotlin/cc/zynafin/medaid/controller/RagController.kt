package cc.zynafin.medaid.controller

import cc.zynafin.medaid.service.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.ai.document.Document
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "RAG (Retrieval-Augmented Generation)", description = "Document ingestion, semantic search, and LLM-powered explanations")
@RestController
@RequestMapping("/api/v1/rag")
class RagController(
    private val ragService: RagService,
    private val llmService: LlmService
) {

    @Operation(
        summary = "Ingest PDF document",
        description = "Upload and process a PDF document for RAG semantic search"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Document ingested successfully"),
            ApiResponse(responseCode = "400", description = "Invalid file format or upload error")
        ]
    )
    @PostMapping("/ingest")
    fun ingestDocument(
        @RequestParam file: MultipartFile,
        @RequestParam(required = false) metadata: String? = null
    ): ResponseEntity<IngestionResult> {
        val metaMap = if (metadata != null) {
            mapOf("custom_metadata" to metadata)
        } else {
            emptyMap()
        }

        val result = ragService.ingestPdf(file, metaMap)
        return ResponseEntity.ok(result)
    }

    @Operation(
        summary = "Ingest directory of PDFs",
        description = "Process all PDF files from a specified directory"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Directory processed successfully"),
            ApiResponse(responseCode = "400", description = "Invalid directory path")
        ]
    )
    @PostMapping("/ingest-directory")
    fun ingestDirectory(@RequestBody request: IngestDirectoryRequest): ResponseEntity<DirectoryIngestionResult> {
        val result = ragService.ingestDirectory(request.directoryPath)
        return ResponseEntity.ok(result)
    }

    @Operation(
        summary = "Semantic search",
        description = "Search for relevant document chunks using vector similarity"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Search results returned successfully")
        ]
    )
    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") topK: Int
    ): ResponseEntity<List<DocumentSearchResult>> {
        val documents = ragService.search(query, topK)

        val results = documents.map { doc ->
            DocumentSearchResult(
                content = doc.content,
                metadata = doc.metadata,
                similarity = doc.metadata["distance"]?.toString()?.toDouble() ?: 0.0
            )
        }

        return ResponseEntity.ok(results)
    }

    @Operation(
        summary = "Get LLM explanation",
        description = "Generate a detailed explanation using RAG (Retrieval-Augmented Generation)"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Explanation generated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid query")
        ]
    )
    @GetMapping("/explain")
    fun explain(@RequestParam query: String): ResponseEntity<String> {
        val explanation = ragService.explain(query, llmService)
        return ResponseEntity.ok(explanation)
    }

    @Operation(
        summary = "Get RAG statistics",
        description = "Get statistics about the RAG vector store and document count"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Statistics returned successfully")
        ]
    )
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(ragService.getStats())
    }
}

@Schema(description = "Result from semantic search")
data class DocumentSearchResult(
    @Schema(description = "Document content snippet")
    val content: String,
    @Schema(description = "Document metadata including source, page, and other attributes")
    val metadata: Map<String, Any>,
    @Schema(description = "Similarity score (lower is more similar)", example = "0.25")
    val similarity: Double
)

@Schema(description = "Request to ingest all PDFs from a directory")
data class IngestDirectoryRequest(
    @Schema(description = "Path to directory containing PDF files", example = "~/Documents/medaids")
    val directoryPath: String
)
