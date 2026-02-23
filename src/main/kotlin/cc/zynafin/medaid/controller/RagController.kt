package cc.zynafin.medaid.controller

import cc.zynafin.medaid.service.LlmService
import cc.zynafin.medaid.service.RagService
import org.springframework.ai.document.Document
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/rag")
class RagController(
    private val ragService: RagService,
    private val llmService: LlmService
) {

    @PostMapping("/ingest")
    fun ingestDocument(
        @RequestParam file: MultipartFile,
        @RequestParam(required = false) metadata: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val metaMap = if (metadata != null) {
            // Simple parsing for POC - in production, use proper JSON parsing
            mapOf("custom_metadata" to metadata)
        } else {
            emptyMap()
        }

        ragService.ingestPdf(file, metaMap)

        return ResponseEntity.ok(
            mapOf(
                "status" to "success",
                "filename" to file.originalFilename,
                "message" to "Document ingested successfully"
            )
        )
    }

    @PostMapping("/ingest-directory")
    fun ingestDirectory(@RequestBody request: IngestDirectoryRequest): ResponseEntity<Map<String, Any>> {
        ragService.ingestDirectory(request.directoryPath)

        return ResponseEntity.ok(
            mapOf(
                "status" to "success",
                "directory" to request.directoryPath,
                "message" to "Directory ingestion started"
            )
        )
    }

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

    @GetMapping("/explain")
    fun explain(@RequestParam query: String): ResponseEntity<String> {
        val explanation = ragService.explain(query, llmService)
        return ResponseEntity.ok(explanation)
    }

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(ragService.getStats())
    }
}

data class DocumentSearchResult(
    val content: String,
    val metadata: Map<String, Any>,
    val similarity: Double
)

data class IngestDirectoryRequest(
    val directoryPath: String
)
