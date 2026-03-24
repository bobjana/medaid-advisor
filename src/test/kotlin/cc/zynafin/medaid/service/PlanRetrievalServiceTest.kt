package cc.zynafin.medaid.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.web.client.RestTemplate
import java.sql.ResultSet

@ExtendWith(MockitoExtension::class)
class PlanRetrievalServiceTest {

    @Mock
    private lateinit var jdbcTemplate: JdbcTemplate

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var planRetrievalService: PlanRetrievalService

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        planRetrievalService = PlanRetrievalService(
            jdbcTemplate = jdbcTemplate,
            topK = 10,
            similarityThreshold = 0.65,
            ollamaBaseUrl = "http://localhost:11434"
        )
        
        val embedding = (1..768).map { 0.1 }.toList()
        whenever(restTemplate.postForObject(
            any<String>(),
            any<Map<String, Any>>(),
            any<Class<Map<String, Any>>>()
        )).thenReturn(mapOf("embedding" to embedding))
    }
    
    private data class MockDoc(
        val id: String,
        val content: String,
        val metadata: Map<String, Any>,
        val distance: Double
    )
    
    @Suppress("UNCHECKED_CAST")
    private fun mockQueryResults(vararg docs: MockDoc) {
        Mockito.`when`(jdbcTemplate.query(
            any<String>(),
            any<RowMapper<*>>(),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java),
            org.mockito.ArgumentMatchers.any(Object::class.java)
        )).thenAnswer { invocation ->
            val rowMapper = invocation.arguments[1] as RowMapper<Any>
            docs.map { doc ->
                val metadataJson = objectMapper.writeValueAsString(doc.metadata)
                val data = arrayOf(doc.id, doc.content, metadataJson, doc.distance)
                val rs = createMockResultSet(data)
                rowMapper.mapRow(rs, 0)
            }
        }
    }
    
    private fun createMockResultSet(data: Array<out Any>): ResultSet {
        return object : ResultSet by Mockito.mock(ResultSet::class.java) {
            override fun getString(columnIndex: Int): String = data[columnIndex - 1] as String
            override fun getDouble(columnIndex: Int): Double = data[columnIndex - 1] as Double
            override fun getObject(columnIndex: Int): Any = data[columnIndex - 1]
        }
    }

    @Test
    fun retrieveForMetadataShouldReturnContentWhenChunksFound() {
        mockQueryResults(MockDoc(
            id = "test-id",
            content = "Discovery Health Comprehensive 2026 plan details",
            metadata = mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 1
            ),
            distance = 0.2
        ))
        
        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)
        
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("Comprehensive"))
        assertEquals(0.8, result.confidence)
        assertEquals(1, result.sourceChunks.size)
        assertEquals(1, result.sourceChunks[0].pageNumber)
        assertEquals(0.2, result.sourceChunks[0].similarityScore)
    }

    @Test
    fun retrieveForMetadataShouldReturnErrorWhenNoChunksFound() {
        mockQueryResults()
        
        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)
        
        assertEquals("", result.data)
        assertEquals(0.0, result.confidence)
        assertTrue(result.sourceChunks.isEmpty())
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("No relevant chunks found"))
    }

    @Test
    fun retrieveForMetadataShouldFilterByScheme() {
        mockQueryResults(MockDoc(
            id = "id-1",
            content = "Matching content",
            metadata = mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 1
            ),
            distance = 0.3
        ))
        
        val result = planRetrievalService.retrieveForMetadata("Discovery Health", "Comprehensive", 2026)
        
        assertEquals(1, result.sourceChunks.size)
        assertEquals("id-1", result.sourceChunks[0].chunkId)
    }

    @Test
    fun retrieveForContributionsShouldExtractContributionAmounts() {
        mockQueryResults(MockDoc(
            id = "test-id",
            content = "Principal: R4500\nAdult Dep: R3000\nChild Dep: R1500",
            metadata = mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 5,
                "chunk_type" to "table_prose",
                "table_origin" to "contribution"
            ),
            distance = 0.25
        ))
        
        val result = planRetrievalService.retrieveForContributions("Discovery Health", "Comprehensive", 2026)
        
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("R4500"))
        assertTrue(result.data!!.contains("R3000"))
        assertTrue(result.data!!.contains("R1500"))
        assertEquals(0.75, result.confidence)
    }

    @Test
    fun retrieveForBenefitsShouldExtractBenefitDetails() {
        mockQueryResults(MockDoc(
            id = "test-id",
            content = "Hospital: Full cover at 200%\nChronic: CDL included\nDay-to-day: Unlimited GP visits",
            metadata = mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 3,
                "chunk_type" to "table_prose",
                "table_origin" to "benefit"
            ),
            distance = 0.3
        ))
        
        val result = planRetrievalService.retrieveForBenefits("Discovery Health", "Comprehensive", 2026)
        
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("Hospital"))
        assertTrue(result.data!!.contains("CDL"))
        assertTrue(result.data!!.contains("GP visits"))
        assertEquals(0.7, result.confidence)
    }

    @Test
    fun retrieveForCopaymentsShouldExtractCopaymentAmounts() {
        mockQueryResults(MockDoc(
            id = "test-id",
            content = "Hospital copay: R500\nSpecialist: R300\nGP copay: R200",
            metadata = mapOf(
                "scheme" to "Discovery Health",
                "plan_name" to "Comprehensive",
                "year" to 2026,
                "page_number" to 7,
                "chunk_type" to "table_prose",
                "table_origin" to "copayment"
            ),
            distance = 0.2
        ))
        
        val result = planRetrievalService.retrieveForCopayments("Discovery Health", "Comprehensive", 2026)
        
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("R500"))
        assertTrue(result.data!!.contains("R300"))
        assertTrue(result.data!!.contains("R200"))
        assertEquals(0.8, result.confidence)
    }

    @Test
    fun shouldCombineMultipleChunksWhenFound() {
        mockQueryResults(
            MockDoc(
                id = "chunk-1",
                content = "First part of contribution table",
                metadata = mapOf(
                    "scheme" to "Discovery Health",
                    "plan_name" to "Comprehensive",
                    "year" to 2026,
                    "page_number" to 1,
                    "chunk_type" to "table_prose",
                    "table_origin" to "contribution"
                ),
                distance = 0.2
            ),
            MockDoc(
                id = "chunk-2",
                content = "Second part of contribution table",
                metadata = mapOf(
                    "scheme" to "Discovery Health",
                    "plan_name" to "Comprehensive",
                    "year" to 2026,
                    "page_number" to 2,
                    "chunk_type" to "table_prose",
                    "table_origin" to "contribution"
                ),
                distance = 0.3
            )
        )
        
        val result = planRetrievalService.retrieveForContributions("Discovery Health", "Comprehensive", 2026)
        
        assertEquals(2, result.sourceChunks.size)
        assertNotNull(result.data)
        assertTrue(result.data!!.contains("First part"))
        assertTrue(result.data!!.contains("Second part"))
        val avgConfidence = (0.8 + 0.7) / 2
        assertEquals(avgConfidence, result.confidence, 0.01)
    }
}
