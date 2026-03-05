package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.ExtractionConfidence
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class PlanExtractor(
    private val chatClient: ChatClient,
    private val resourceLoader: ResourceLoader,
    @Value("\${medaid.extraction.llm.timeout:30}")
    private val llmTimeout: Int
) {
    private val log = LoggerFactory.getLogger(PlanExtractor::class.java)
    private val objectMapper = ObjectMapper()

    fun extractMetadata(context: String, scheme: String, planName: String, year: Int): SectionExtractionResult<JsonNode> {
        return extractWithPrompt(
            context = context,
            scheme = scheme,
            planName = planName,
            year = year,
            promptResource = "classpath:prompts/extraction/metadata-prompt.txt",
            section = "metadata"
        )
    }

    fun extractContributions(context: String, scheme: String, planName: String, year: Int): SectionExtractionResult<JsonNode> {
        return extractWithPrompt(
            context = context,
            scheme = scheme,
            planName = planName,
            year = year,
            promptResource = "classpath:prompts/extraction/contributions-prompt.txt",
            section = "contributions"
        )
    }

    fun extractBenefits(context: String, scheme: String, planName: String, year: Int): SectionExtractionResult<JsonNode> {
        return extractWithPrompt(
            context = context,
            scheme = scheme,
            planName = planName,
            year = year,
            promptResource = "classpath:prompts/extraction/benefits-prompt.txt",
            section = "benefits"
        )
    }

    fun extractCopayments(context: String, scheme: String, planName: String, year: Int): SectionExtractionResult<JsonNode> {
        return extractWithPrompt(
            context = context,
            scheme = scheme,
            planName = planName,
            year = year,
            promptResource = "classpath:prompts/extraction/copayments-prompt.txt",
            section = "copayments"
        )
    }

    private fun extractWithPrompt(
        context: String,
        scheme: String,
        planName: String,
        year: Int,
        promptResource: String,
        section: String
    ): SectionExtractionResult<JsonNode> {
        try {
            val promptTemplate = loadPromptTemplate(promptResource)
            val prompt = promptTemplate
                .replace("{{scheme}}", scheme)
                .replace("{{plan_name}}", planName)
                .replace("{{year}}", year.toString())
                .replace("{{context}}", context)

            log.debug("Extracting {} for {}/{} {}", section, scheme, planName, year)

            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()

            val jsonNode = parseJsonResponse(response)
            val confidence = calculateConfidence(response)
            
            if (jsonNode != null) {
                return SectionExtractionResult(
                    data = jsonNode,
                    confidence = confidence.score,
                    sourceChunks = emptyList()
                )
            } else {
                return SectionExtractionResult(
                    data = objectMapper.createObjectNode(),
                    confidence = ExtractionConfidence.LOW.score,
                    sourceChunks = emptyList(),
                    errorMessage = "Could not parse JSON from LLM response"
                )
            }
        } catch (e: Exception) {
            log.error("Error extracting $section for {}/{} {}", scheme, planName, year, e)

            return SectionExtractionResult(
                data = objectMapper.createObjectNode(),
                confidence = ExtractionConfidence.FAILED.score,
                sourceChunks = emptyList(),
                errorMessage = "Extraction failed: ${e.message}"
            )
        }
    }

    private fun loadPromptTemplate(resourcePath: String): String {
        return try {
            val resource: Resource = resourceLoader.getResource(resourcePath)
            resource.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            log.error("Failed to load prompt template: {}", resourcePath, e)
            throw IllegalStateException("Could not load prompt template: $resourcePath", e)
        }
    }

    private fun parseJsonResponse(response: String): JsonNode? {
        return try {
            val jsonMatch = Regex(""""```json\s*(\{.*?\})\s*```""", RegexOption.DOT_MATCHES_ALL).find(response)
            if (jsonMatch != null) {
                objectMapper.readTree(jsonMatch.groupValues[1])
            } else {
                val bracketMatch = Regex(""""(\{.*?\})""", RegexOption.DOT_MATCHES_ALL).find(response)
                if (bracketMatch != null) {
                    objectMapper.readTree(bracketMatch.groupValues[1])
                } else {
                    log.warn("Could not extract JSON from LLM response")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to parse JSON response", e)
            null
        }
    }

    private fun calculateConfidence(response: String): ExtractionConfidence {
        val responseLength = response.length
        val hasStructuredData = response.contains("{") && response.contains("}")

        return when {
            responseLength < 100 || !hasStructuredData -> ExtractionConfidence.LOW
            responseLength < 300 -> ExtractionConfidence.MEDIUM
            responseLength < 500 -> ExtractionConfidence.HIGH
            else -> ExtractionConfidence.HIGH
        }
    }
}
