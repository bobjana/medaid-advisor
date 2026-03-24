package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.SourceCitation
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
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
    private val ollamaBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(PlanRetrievalService::class.java)
    private val objectMapper = ObjectMapper()
    private val restTemplate = RestTemplate()

    private data class DocResult(
        val id: String,
        val content: String,
        val metadata: Map<String, Any>,
        val distance: Double,
    )

    private val docResultMapper =
        RowMapper { rs: ResultSet, _: Int ->
            val id = rs.getString("id")
            val content = rs.getString("content")
            val metadataJson = rs.getString("metadata")
            val distance = rs.getDouble("distance")
            val metadata = objectMapper.readValue(metadataJson, Map::class.java) as Map<String, Any>
            DocResult(id, content, metadata, distance)
        }

    fun retrieveForMetadata(
        scheme: String,
        planName: String,
        year: Int,
    ): SectionExtractionResult<String> {
        val query = buildMetadataQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "metadata", preferTableMarkdown = false)
    }

    fun retrieveForContributions(
        scheme: String,
        planName: String,
        year: Int,
    ): SectionExtractionResult<String> {
        val query = buildContributionsQuery(scheme, planName, year)
        return retrieveRelevantChunks(
            query,
            scheme,
            planName,
            year,
            "contributions",
            preferTableMarkdown = true,
            tableType = "CONTRIBUTION",
        )
    }

    fun retrieveForBenefits(
        scheme: String,
        planName: String,
        year: Int,
    ): SectionExtractionResult<String> {
        val query = buildBenefitsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "benefits", preferTableMarkdown = true, tableType = "BENEFIT")
    }

    fun retrieveForCopayments(
        scheme: String,
        planName: String,
        year: Int,
    ): SectionExtractionResult<String> {
        val query = buildCopaymentsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "copayments", preferTableMarkdown = true, tableType = "COPAYMENT")
    }

    private fun retrieveRelevantChunks(
        query: String,
        scheme: String,
        planName: String,
        year: Int,
        section: String,
        preferTableMarkdown: Boolean = false,
        tableType: String? = null,
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
                    errorMessage = "Failed to generate embedding",
                )
            }

            // Convert embedding to PostgreSQL vector string format
            val embeddingVector = embedding.joinToString(",", prefix = "[", postfix = "]")

            log.debug(
                "Executing retrieval SQL for scheme=$scheme, planName=$planName, year=$year, section=$section, preferTableMarkdown=$preferTableMarkdown, tableType=$tableType",
            )

            // If we prefer table_markdown chunks, try that first
            if (preferTableMarkdown) {
                val tableMarkdownResults = tryRetrieveWithTableFilter(embeddingVector, scheme, planName, year, tableType)

                if (tableMarkdownResults.isNotEmpty()) {
                    log.info("Found ${tableMarkdownResults.size} table_markdown chunks for section=$section")
                    return buildResult(tableMarkdownResults, section)
                }

                log.debug("No table_markdown chunks found for section=$section, falling back to all chunks")
            }

            // Fallback: retrieve all chunks without chunk_type filter
            val sql =
                """
                SELECT id, content, metadata, embedding <=> (?::vector) as distance
                FROM vector_store
                WHERE metadata->>'scheme' = ?
                  AND metadata->>'plan_name' = ?
                  AND (metadata->>'year')::int = ?
                ORDER BY embedding <=> (?::vector)
                LIMIT ?
                """.trimIndent()

            log.debug("Executing fallback SQL with params: scheme=$scheme, planName=$planName, year=$year, topK=$topK")

            val results =
                try {
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
                    errorMessage = "No relevant chunks found for $section",
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
                errorMessage = "Error retrieving chunks: ${e.message}",
            )
        }
    }

    private fun tryRetrieveWithTableFilter(
        embeddingVector: String,
        scheme: String,
        planName: String,
        year: Int,
        tableType: String?,
    ): List<DocResult> {
        val baseSql =
            """
            SELECT id, content, metadata, embedding <=> (?::vector) as distance
            FROM vector_store
            WHERE metadata->>'scheme' = ?
              AND metadata->>'plan_name' = ?
              AND (metadata->>'year')::int = ?
              AND metadata->>'chunk_type' = 'table_markdown'
            """.trimIndent()

        val sql =
            if (tableType != null) {
                """
                $baseSql
                AND metadata->>'table_type' = ?
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
            if (tableType != null) {
                jdbcTemplate.query(sql, docResultMapper, embeddingVector, scheme, planName, year, tableType, embeddingVector, topK)
            } else {
                jdbcTemplate.query(sql, docResultMapper, embeddingVector, scheme, planName, year, embeddingVector, topK)
            }
        } catch (e: Exception) {
            log.debug("No table_markdown chunks found with table_type=$tableType: ${e.message}")
            emptyList()
        }
    }

    private fun buildResult(
        results: List<DocResult>,
        section: String,
    ): SectionExtractionResult<String> {
        val sourceChunks =
            results.map { doc ->
                SourceCitation(
                    chunkId = doc.id,
                    content = doc.content,
                    pageNumber = doc.metadata["page_number"] as? Int ?: 0,
                    similarityScore = doc.distance,
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
            errorMessage = null,
        )
    }

    private fun generateEmbedding(text: String): List<Double>? =
        try {
            val request =
                mapOf(
                    "model" to "nomic-embed-text",
                    "prompt" to text,
                )

            val response =
                restTemplate.postForObject(
                    "$ollamaBaseUrl/api/embeddings",
                    request,
                    Map::class.java,
                )

            @Suppress("UNCHECKED_CAST")
            response?.get("embedding") as? List<Double>
        } catch (e: Exception) {
            log.error("Error generating embedding", e)
            null
        }

    private fun buildMetadataQuery(
        scheme: String,
        planName: String,
        year: Int,
    ): String =
        """
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

    private fun buildContributionsQuery(
        scheme: String,
        planName: String,
        year: Int,
    ): String =
        """
        Monthly contribution amounts for $scheme $planName plan $year:
        - Principal member monthly contribution amount
        - Adult dependent monthly contribution amount
        - Child dependent monthly contribution amount
        - Network option contribution rates
        - All Rand amounts in contribution table
        """.trimIndent()

    private fun buildBenefitsQuery(
        scheme: String,
        planName: String,
        year: Int,
    ): String =
        """
        Hospital benefit coverage limits for $scheme $planName plan $year:
        - Hospital admission benefit limit
        - Specialist consultation coverage
        - Chronic condition benefit
        - Day-to-day benefit limit
        - Network provider coverage
        - Annual benefit limits and sub-limits
        """.trimIndent()

    private fun buildCopaymentsQuery(
        scheme: String,
        planName: String,
        year: Int,
    ): String =
        """
        Copayment amounts and fees for $scheme $planName plan $year:
        - Hospital admission copayment fee
        - Specialist consultation copayment
        - Chronic medication copayment
        - Day-to-day benefit copayment
        - Network vs non-network copayment difference
        - All copayment Rand amounts and percentages
        """.trimIndent()
}
