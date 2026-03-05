package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.SourceCitation
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value

@Service
open class PlanRetrievalService(
    private val vectorStore: VectorStore,
    @Value("\${medaid.extraction.top-k:10}")
    private val topK: Int,
    @Value("\${medaid.extraction.similarity-threshold:0.65}")
    private val similarityThreshold: Double
) {
    private val log = LoggerFactory.getLogger(PlanRetrievalService::class.java)

    fun retrieveForMetadata(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildMetadataQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "metadata")
    }

    fun retrieveForContributions(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildContributionsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "contributions")
    }

    fun retrieveForBenefits(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildBenefitsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "benefits")
    }

    fun retrieveForCopayments(scheme: String, planName: String, year: Int): SectionExtractionResult<String> {
        val query = buildCopaymentsQuery(scheme, planName, year)
        return retrieveRelevantChunks(query, scheme, planName, year, "copayments")
    }

    private fun retrieveRelevantChunks(query: String, scheme: String, planName: String, year: Int, section: String): SectionExtractionResult<String> {
        return try {
            val searchRequest = SearchRequest.query(query)
                .withTopK(topK)
                .withSimilarityThreshold(similarityThreshold)

            val results = vectorStore.similaritySearch(searchRequest)

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

            val filteredResults = filterByPlanMetadata(results, scheme, planName, year)

            if (filteredResults.isEmpty()) {
                log.warn("Chunks found but none match plan metadata scheme=$scheme plan=$planName year=$year")
                return SectionExtractionResult(
                    data = "",
                    confidence = 0.0,
                    sourceChunks = emptyList(),
                    retryAttempts = 0,
                    errorMessage = "Chunks found but none match plan metadata"
                )
            }

            val sourceChunks = filteredResults.map { doc ->
                SourceCitation(
                    chunkId = doc.id,
                    content = doc.content,
                    pageNumber = doc.metadata["page_number"] as? Int ?: 0,
                    similarityScore = doc.metadata["distance"] as? Double ?: 0.0
                )
            }

            val combinedContent = filteredResults.joinToString("\n\n") { it.content }
            val avgSimilarity = filteredResults.map { it.metadata["distance"] as? Double ?: 0.0 }.average()

            log.info("Retrieved ${filteredResults.size} chunks for section=$section with avg similarity: ${1 - avgSimilarity}")

            SectionExtractionResult(
                data = combinedContent,
                confidence = 1 - avgSimilarity,
                sourceChunks = sourceChunks,
                retryAttempts = 0,
                errorMessage = null
            )
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

    private fun filterByPlanMetadata(documents: List<Document>, scheme: String, planName: String, year: Int): List<Document> {
        return documents.filter { doc ->
            val docScheme = doc.metadata["scheme"] as? String
            val docPlanName = doc.metadata["plan_name"] as? String
            val docYear = doc.metadata["year"] as? Int

            val schemeMatch = docScheme?.equals(scheme, ignoreCase = true) ?: false
            val planMatch = docPlanName?.equals(planName, ignoreCase = true) ?: false
            val yearMatch = docYear == year

            schemeMatch && planMatch && yearMatch
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
            Extract copayment information from the plan document for:
            - Scheme: $scheme
            - Plan: $planName
            - Year: $year

            Look for:
            - Hospital copayments (amount or percentage)
            - Specialist copayments
            - GP copayments
            - Other co-payment structures
            - Deductibles or excess amounts
            - Any co-payment tiers or schedules

            Extract all copayment amounts, their conditions, and how they apply.
        """.trimIndent()
    }
}
