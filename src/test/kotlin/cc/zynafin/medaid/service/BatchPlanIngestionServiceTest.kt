package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.PlanRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.file.Files
import java.nio.file.Path

class BatchPlanIngestionServiceTest {

    private lateinit var planDataService: PlanDataService
    private lateinit var ragService: RagService
    private lateinit var planRepository: PlanRepository
    private lateinit var service: BatchPlanIngestionService
    
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        planDataService = mock()
        ragService = mock()
        planRepository = mock()
        service = BatchPlanIngestionService(planDataService, ragService, planRepository)
    }

    @Test
    fun `ingestDirectory should return error when directory does not exist`() {
        val result = service.ingestDirectory("/nonexistent/directory")

        assertFalse(result.success)
        assertEquals("Directory does not exist", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestDirectory should return empty result when directory is empty`() {
        Files.createDirectories(tempDir)

        val result = service.ingestDirectory(tempDir.toString())

        assertFalse(result.success)
        assertEquals("No PDF files found", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestSinglePdf should skip when no matching plan found`() {
        val pdfFile = Files.createFile(tempDir.resolve("unknown-scheme-2026.pdf"))
        whenever(ragService.extractMetadataFromFilename(any())).thenReturn(mapOf(
            "scheme" to "Unknown",
            "plan_name" to "Unknown",
            "year" to 2026
        ))
        whenever(planRepository.findBySchemeAndPlanYear(any(), any())).thenReturn(emptyList())

        val result = service.ingestSinglePdf(pdfFile)

        assertNotNull(result)
        assertEquals(BatchItemStatus.SKIPPED, result.status)
    }

    @Test
    fun `ingestDirectory should skip Momentum Option files`() {
        Files.createFile(tempDir.resolve("Momentum_Custom_Option_2026.pdf"))
        Files.createFile(tempDir.resolve("Momentum_Summit_Option_2026.pdf"))

        val result = service.ingestDirectory(tempDir.toString())

        assertFalse(result.success)
        assertEquals("No PDF files found", result.error)
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `ingestDirectory should skip overview file`() {
        Files.createFile(tempDir.resolve("Benefit_Option_Overview_2026.pdf"))

        val result = service.ingestDirectory(tempDir.toString())

        assertFalse(result.success)
        assertEquals("No PDF files found", result.error)
        assertEquals(0, result.totalFiles)
    }
}
