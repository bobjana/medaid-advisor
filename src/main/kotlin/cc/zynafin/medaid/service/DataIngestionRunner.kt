package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Command line runner for batch ingesting medical aid PDF documents.
 *
 * Usage:
 *   java -jar app.jar --ingest-data
 *   java -jar app.jar --ingest-data --ingest-dir=/path/to/pdfs
 */
@Component
@Order(2)  // Run after main application startup
class DataIngestionRunner(
    private val ragService: RagService
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataIngestionRunner::class.java)

    companion object {
        private const val DEFAULT_INGEST_DIR = "data/plans"
    }

    override fun run(args: Array<String>) {
        // Check if ingestion is requested
        val shouldIngest = args.any { it == "--ingest-data" || it.startsWith("--ingest-dir=") }

        if (!shouldIngest) {
            log.debug("Data ingestion not requested. Use --ingest-data to ingest PDFs.")
            return
        }

        // Get ingestion directory
        val ingestDirArg = args.find { it.startsWith("--ingest-dir=") }
        val ingestDir = if (ingestDirArg != null) {
            ingestDirArg.substringAfter("--ingest-dir=")
        } else {
            DEFAULT_INGEST_DIR
        }

        // Resolve path
        val resolvedPath = Path.of(ingestDir.replaceFirst("^~", System.getProperty("user.home")))
            .toAbsolutePath()
            .normalize()

        if (!Files.exists(resolvedPath)) {
            log.error("Ingestion directory does not exist: $resolvedPath")
            return
        }

        val pdfCount = Files.list(resolvedPath)
            .filter { it.toString().endsWith(".pdf", ignoreCase = true) }
            .count()

        if (pdfCount == 0L) {
            log.warn("No PDF files found in: $resolvedPath")
            return
        }

        log.info("=".repeat(60))
        log.info("Starting PDF ingestion from: $resolvedPath")
        log.info("Found $pdfCount PDF files to process")
        log.info("=".repeat(60))

        val startTime = System.currentTimeMillis()
        val result = ragService.ingestDirectory(resolvedPath.toString())
        val totalTime = System.currentTimeMillis() - startTime

        log.info("=".repeat(60))
        log.info("Ingestion Complete!")
        log.info("  Total files: ${result.totalFiles}")
        log.info("  Successful: ${result.successfulIngestions}")
        log.info("  Failed: ${result.failedIngestions}")
        log.info("  Total chunks created: ${result.totalChunks}")
        log.info("  Total pages processed: ${result.totalPages}")
        log.info("  Total duration: ${totalTime}ms (${totalTime / 1000}s)")
        log.info("=".repeat(60))

        // Print any errors
        result.results.filter { !it.success }.forEach {
            log.error("Failed to ingest: ${it.filename} - ${it.error}")
        }
    }
}
