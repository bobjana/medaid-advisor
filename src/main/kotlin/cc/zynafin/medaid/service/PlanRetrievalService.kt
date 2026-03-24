package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.SourceCitation
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.sql.ResultSet

@Service
open class PlanRetrievalService(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${medaid.extraction.top-k:10}")
    private val topK: Int,
    @Value("\${medaid.extraction.similarity-threshold:0.65}")
    private val similarityThreshold: Double,
    @Value("\${OLLAMA_BASE_URL:http://localhost:11434}")
    private val ollamaBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(PlanRetrievalService::class.java)
    private val objectMapper = ObjectMapper()
    private val restTemplate = RestTemplate()

    private data class DocResult(
        val id: String,
        val content: String,
        val metadata: Map<String, Any>,
        val distance: Double
    )

    private val docResultMapper = RowMapper { rs: ResultSet, _: Int ->
        val id = rs.getString("id")
        val content = rs.getString("content")
        val metadataJson = rs.getString("metadata")
        val distance = rs.getDouble("distance")
        val metadata = objectMapper.readValue(metadataJson, Map::class.java) as Map<String, Any>
        DocResult(id, content, metadata, distance)
    }

    fun retrieveForMetadata(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildMetadataQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "metadata", preferTableProse = false)
    }

    fun retrieveForContributions(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildContributionsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "contributions", preferTableProse = true, tableOrigin = "contribution")
    }

    fun retrieveForBenefits(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildBenefitsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "benefits", preferTableProse = true, tableOrigin = "benefit")
    }

    fun retrieveForCopayments(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildCopaymentsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "copayments", preferTableProse = true, tableOrigin = "copayment")
    }

    private fun retrieveRelevantChunks(
        query: String,
        scheme: String,
        planName: String,
        year: Int,
        section: String,
        preferTableProse: Boolean = false,
        tableOrigin: String? = null
    ): SectionExtractionResult<String> {
        return try {
            val embedding = generateEmbedding(query)
            
            if (embedding == null) {
                log.error("Failed to generate embedding for query: $query")
                return SectionExtractionResult(
                    data = "",
                    confidence = 0.0,
                    sourceChunks = emptyList(),
                    retryAttempts = 0,
                    errorMessage = "Failed to generate embedding"
                )
            }
            
            // Convert embedding to PostgreSQL vector string format
            val embeddingVector = embedding.joinToString(",", prefix = "[", postfix = "]")
            
            log.debug("Executing retrieval SQL for scheme=$scheme, planName=$planName, year=$year, section=$section, preferTableProse=$preferTableProse, tableOrigin=$tableOrigin")
            
            // If we prefer table_prose chunks, try that first
            if (preferTableProse) {
                val tableProseResults = tryRetrieveWithTableFilter(embeddingVector, scheme, planName, year, tableOrigin)
                
                if (tableProseResults.isNotEmpty()) {
                    log.info("Found ${tableProseResults.size} table_prose chunks for section=$section")
                    return buildResult(tableProseResults, section)
                }
                
                log.debug("No table_prose chunks found for section=$section, falling back to all chunks")
            }
            
            // Fallback: retrieve all chunks without chunk_type filter
            val sql = """
                SELECT id, content, metadata, embedding <=> (?::vector) as distance
                FROM vector_store
                WHERE metadata->>'scheme' = ?
                  AND metadata->>'plan_name' = ?
                  AND (metadata->>'year')::int = ?
                ORDER BY embedding <=> (?::vector)
                LIMIT ?
            """.trimIndent()
            
            log.debug("Executing fallback SQL with params: scheme=$scheme, planName=$planName, year=$year, topK=$topK")
            
            val results = try {
                jdbcTemplate.query(sql, docResultMapper, embeddingVector, scheme, planName, year, embeddingVector, topK)
            } catch (e: Exception) {
                log.error("SQL execution error: ${e.message}", e)
                emptyList()
            }
            
            if (results.isEmpty()) {
                log.warn("No relevant chunks found for section=$section query=$query")
                return SectionExtractionResult(
                    data = "",
                    confidence = 0.0,
                    sourceChunks = emptyList(),
                    retryAttempts = 0,
                    errorMessage = "No relevant chunks found for $section"
                )
            }
            
            buildResult(results, section)
        } catch (e: Exception) {
            log.error("Failed to retrieve chunks for section=$section", e)
            SectionExtractionResult(
                data = "",
                confidence = 0.0,
                sourceChunks = emptyList(),
                retryAttempts = 0,
                errorMessage = "Error retrieving chunks: ${e.message}"
            )
        }
    }
    
    /**
     * Try to retrieve chunks with table_prose filter.
     * Optionally filter by table_origin (contribution, benefit, copayment).
     */
    private fun tryRetrieveWithTableFilter(
        embeddingVector: String,
        scheme: String,
        planName: String,
        year: Int,
        tableOrigin: String?
    ): List<DocResult> {
        // Build SQL with chunk_type filter
        val baseSql = """
            SELECT id, content, metadata, embedding <=> (?::vector) as distance
            FROM vector_store
            WHERE metadata->>'scheme' = ?
              AND metadata->>'plan_name' = ?
              AND (metadata->>'year')::int = ?
              AND metadata->>'chunk_type' = 'table_prose'
        """.trimIndent()
        
        // Add table_origin filter if specified
        val sql = if (tableOrigin != null) {
            """
                $baseSql
                AND metadata->>'table_origin' = ?
                ORDER BY embedding <=> (?::vector)
                LIMIT ?
            """.trimIndent()
        } else {
            """
                $baseSql
                ORDER BY embedding <=> (?::vector)
                LIMIT ?
            """.trimIndent()
        }
        
        return try {
            if (tableOrigin != null) {
                jdbcTemplate.query(sql, docResultMapper, embeddingVector, scheme, planName, year, tableOrigin, embeddingVector, topK)
            } else {
                jdbcTemplate.query(sql, docResultMapper, embeddingVector, scheme, planName, year, embeddingVector, topK)
            }
        } catch (e: Exception) {
            log.debug("No table_prose chunks found with table_origin=$tableOrigin: ${e.message}")
            emptyList()
        }
    }
    
    private fun buildResult(results: List<DocResult>, section: String): SectionExtractionResult<String> {
        val sourceChunks = results.map { doc ->
            SourceCitation(
                chunkId = doc.id,
                content = doc.content,
                pageNumber = doc.metadata["page_number"] as? Int ?: 0,
                similarityScore = doc.distance
            )
        }

        val combinedContent = results.joinToString("\n\n") { it.content }
        val avgDistance = results.map { it.distance }.average()

        log.info("Retrieved ${results.size} chunks for section=$section with avg distance: $avgDistance")

        return SectionExtractionResult(
            data = combinedContent,
            confidence = 1.0 - avgDistance,
            sourceChunks = sourceChunks,
            retryAttempts = 0,
            errorMessage = null
        )
    }

    private fun generateEmbedding(text: String): List<Double>? {
        return try {
            val request = mapOf(
                "model" to "nomic-embed-text",
                "prompt" to text
            )
            
            val response = restTemplate.postForObject(
                "$ollamaBaseUrl/api/embeddings",
                request,
                Map::class.java
            )
            
            @Suppress("UNCHECKED_CAST")
            response?.get("embedding") as? List<Double>
        } catch (e: Exception) {
            log.error("Error generating embedding", e)
            null
        }
    }

    private fun buildMetadataQuery(scheme: String, planName: String, year: Int): String {
        return """
            Extract plan metadata including:
            - Plan name: $planName
            - Scheme: $scheme
            - Plan year: $year
            - Plan type (Network, Comprehensive, Savings, Hospital, Cap)
            - Network type
            - Summary or overview of the plan
            - Any key features or highlights

            Return the exact plan name, scheme, year, and type as stated in the document.
        """.trimIndent()
    }

    private fun buildContributionsQuery(scheme: String, planName: String, year: Int): String {
        return """
            Extract contribution amounts from the plan document for:
            - Scheme: $scheme
            - Plan: $planName
            - Year: $year

            Look for contribution tables showing monthly amounts for:
            - Principal member
            - Adult dependent
            - Child dependent
            - Any other member categories

            Extract all contribution amounts mentioned, including specific values for each member type.
            If a table is present, extract all rows with their amounts.
        """.trimIndent()
    }

    private fun buildBenefitsQuery(scheme: String, planName: String, year: Int): String {
        return """
            Extract benefit information from the plan document for:
            - Scheme: $scheme
            - Plan: $planName
            - Year: $year

            Focus on:
            - Hospital benefits (what's covered, co-insurance, network)
            - Chronic benefits (CDL, chronic medication, chronic conditions)
            - Day-to-day benefits (GP visits, dentistry, optometry)
            - Network providers (which doctors/hospitals are covered)
            - Any other key benefits mentioned

            Extract specific coverage details, limits, and conditions.
        """.trimIndent()
    }

    private fun buildCopaymentsQuery(scheme: String, planName: String, year: Int): String {
        return """
            Copayment amounts for Discovery Health $planName plan $year:
            - Hospital admission copayments, theatre fees
            - Specialist and GP consultation copayments  
            - Chronic medication copayments
            - Day-to-day benefit copayments
            - Any deductibles, excess charges, or levies
            - Network vs non-network copayment differences
            - Maternity copayments (normal delivery vs cesarean)
            - Scope procedures copayments
            - Cancer treatment copayments
            - All rand amounts and percentage copayments
        """.trimIndent()
    }
}
