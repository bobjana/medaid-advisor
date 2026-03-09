package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.SourceCitation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.test.context.TestPropertySource
import java.util.HashMap

@SpringBootTest(classes = [cc.zynafin.medaid.TestApplication::class])
@TestPropertySource(properties = ["medaid.extraction.top-k=10", "medaid.extraction.similarity-threshold=0.65", "spring.main.allow-bean-definition-overriding=true", "medaid.extraction.local.enabled=true", "medaid.extraction.remote.enabled=false"])
class PlanRetrievalServiceTest {

    @Autowired
    private lateinit var planRetrievalService: PlanRetrievalService

    @Autowired
    private lateinit var vectorStore: VectorStore

    @BeforeEach
    fun setup() {
        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(emptyList())
    }

    @Test
    fun retrieveForMetadataShouldReturnContentWhenChunksFound() {
        val mockDocument = Document(
            "test-id",
            "Discovery Health Comprehensive 2026 plan details",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 1,
                "distance" to 0.2
            )
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(mockDocument))

        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)

        assertNotNull(result.data)
        assertTrue(result.data!!.contains("Comprehensive"))
        assertEquals(0.8, result.confidence)
        assertEquals(1, result.sourceChunks.size)
        assertEquals("Discovery Health", result.sourceChunks[0].content?.let { if (it.contains("Discovery Health")) "Discovery Health" else "" })
        assertEquals(1, result.sourceChunks[0].pageNumber)
        assertEquals(0.2, result.sourceChunks[0].similarityScore)
    }

    @Test
    fun retrieveForMetadataShouldReturnErrorWhenNoChunksFound() {
        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(emptyList())

        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)

        assertEquals("", result.data)
        assertEquals(0.0, result.confidence)
        assertTrue(result.sourceChunks.isEmpty())
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("No relevant chunks found"))
    }

    @Test
    fun retrieveForMetadataShouldFilterByScheme() {
        val matchingDoc = Document(
            "id-1",
            "Matching content",
            mapOf("scheme" to "Discovery Health", "plan_name" to "Comprehensive", "year" to 2026, "distance" to 0.3)
        )
        val otherSchemeDoc = Document(
            "id-2",
            "Other scheme content",
            mapOf("scheme" to "Bonitas", "plan_name" to "Comprehensive", "year" to 2026, "distance" to 0.1)
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(matchingDoc, otherSchemeDoc))

        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)

        assertEquals(1, result.sourceChunks.size)
        assertEquals("id-1", result.sourceChunks[0].chunkId)
    }

    @Test
    fun retrieveForContributionsShouldExtractContributionAmounts() {
        val mockDocument = Document(
            "test-id",
            "Principal: R4500\nAdult Dep: R3000\nChild Dep: R1500",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 5,
                "distance" to 0.25
            )
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(mockDocument))

        val result = planRetrievalService.retrieveForContributions("Discovery Health", "Comprehensive", 2026)

        assertNotNull(result.data)
        assertTrue(result.data!!.contains("R4500"))
        assertTrue(result.data!!.contains("R3000"))
        assertTrue(result.data!!.contains("R1500"))
        assertEquals(0.75, result.confidence)
    }

    @Test
    fun retrieveForBenefitsShouldExtractBenefitDetails() {
        val mockDocument = Document(
            "test-id",
            "Hospital: Full cover at 200%\nChronic: CDL included\nDay-to-day: Unlimited GP visits",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 3,
                "distance" to 0.3
            )
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(mockDocument))

        val result = planRetrievalService.retrieveForBenefits("Discovery Health", "Comprehensive", 2026)

        assertNotNull(result.data)
        assertTrue(result.data!!.contains("Hospital"))
        assertTrue(result.data!!.contains("CDL"))
        assertTrue(result.data!!.contains("GP visits"))
        assertEquals(0.7, result.confidence)
    }

    @Test
    fun retrieveForCopaymentsShouldExtractCopaymentAmounts() {
        val mockDocument = Document(
            "test-id",
            "Hospital copay: R500\nSpecialist: R300\nGP copay: R200",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 7,
                "distance" to 0.2
            )
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(mockDocument))

        val result = planRetrievalService.retrieveForCopayments("Discovery Health", "Comprehensive", 2026)

        assertNotNull(result.data)
        assertTrue(result.data!!.contains("R500"))
        assertTrue(result.data!!.contains("R300"))
        assertTrue(result.data!!.contains("R200"))
        assertEquals(0.8, result.confidence)
    }

    @Test
    fun shouldCombineMultipleChunksWhenFound() {
        val chunk1 = Document(
            "chunk-1",
            "First part of contribution table",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "distance" to 0.2
            )
        )
        val chunk2 = Document(
            "chunk-2",
            "Second part of contribution table",
            mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "distance" to 0.3
            )
        )

        whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(listOf(chunk1, chunk2))

        val result = planRetrievalService.retrieveForContributions("Discovery Health", "Comprehensive", 2026)

        assertEquals(2, result.sourceChunks.size)
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("First part"))
        assertTrue(result.data!!.contains("Second part"))
        val avgConfidence = (0.8 + 0.7) / 2
        assertEquals(avgConfidence, result.confidence, 0.01)
    }
}
