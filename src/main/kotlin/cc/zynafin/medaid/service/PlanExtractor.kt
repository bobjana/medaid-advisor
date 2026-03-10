package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import cc.zynafin.medaid.domain.extraction.ExtractionConfidence
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class PlanExtractor(
    private val chatClient: ChatClient,
    private val resourceLoader: ResourceLoader,
    @Value("\${medaid.extraction.llm.timeout:30}")
    private val llmTimeout: Int,
    @Value("\${medaid.extraction.local.json-mode:true}")
    private val jsonModeEnabled: Boolean
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

            val response = if (jsonModeEnabled) {
                val jsonOptions = OllamaOptions.builder()
                    .withFormat("json")
                    .withTemperature(0.1)
                    .build()
                
                chatClient.prompt()
                    .system("You are a data extraction tool. Output ONLY valid JSON. No explanations, no markdown, no commentary.")
                    .user(prompt)
                    .options(jsonOptions)
                    .call()
                    .content()
            } else {
                chatClient.prompt()
                    .system("You are a data extraction tool. Output ONLY valid JSON. No explanations, no markdown, no commentary.")
                    .user(prompt)
                    .call()
                    .content()
            }
            
            log.info("[PlanExtractor] LLM Response for $section: {}", response?.take(500))
            
            val jsonNode = parseJsonResponse(response)
            
            if (jsonNode != null) {
                return SectionExtractionResult(
                    data = jsonNode,
                    confidence = ExtractionConfidence.HIGH.score,
                    sourceChunks = emptyList()
                )
            } else {
                // Fallback: create partial extraction from narrative
                val fallbackData = createFallbackData(response, section)
                return SectionExtractionResult(
                    data = fallbackData,
                    confidence = ExtractionConfidence.LOW.score,
                    sourceChunks = emptyList(),
                    errorMessage = "LLM returned non-JSON response. Partial data extracted."
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

    private fun parseJsonResponse(response: String?): JsonNode? {
        if (response.isNullOrBlank()) {
            log.warn("Empty response from LLM")
            return null
        }
        
        val trimmed = response.trim()
        
        // Try parsing as-is first
        try {
            return objectMapper.readTree(trimmed)
        } catch (e: Exception) {
            // Continue to fallback methods
        }
        
        // Method 1: Extract from markdown code blocks
        try {
            val jsonCodeBlockPattern = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
            val matches = jsonCodeBlockPattern.findAll(trimmed)
            for (match in matches) {
                val content = match.groupValues[1].trim()
                if (content.startsWith("{") || content.startsWith("[")) {
                    try {
                        return objectMapper.readTree(content)
                    } catch (e: Exception) {
                        // Try next match
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Failed to extract from code blocks", e)
        }
        
        // Method 2: Find first { and matching } by counting braces
        try {
            val jsonStart = trimmed.indexOf('{')
            if (jsonStart >= 0) {
                var braceCount = 0
                var jsonEnd = jsonStart
                for (i in jsonStart until trimmed.length) {
                    when (trimmed[i]) {
                        '{' -> braceCount++
                        '}' -> braceCount--
                    }
                    if (braceCount == 0) {
                        jsonEnd = i
                        break
                    }
                }
                if (braceCount == 0) {
                    val jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)
                    return objectMapper.readTree(jsonStr)
                }
            }
        } catch (e: Exception) {
            log.debug("Failed to extract by brace counting", e)
        }
        
        return null
    }

    private fun createFallbackData(response: String, section: String): JsonNode {
        val node = objectMapper.createObjectNode()
        
        when (section) {
            "metadata" -> {
                node.put("plan_name", extractValue(response, "plan", "name", "Plan"))
                node.put("scheme", extractValue(response, "scheme", "Discovery Health", "Scheme"))
                node.put("year", 2026)
                node.put("plan_type", extractValue(response, "type", "Comprehensive", "Type"))
                node.put("network_type", extractValue(response, "network", "", "Network"))
                node.put("summary", response.take(200))
                node.set<JsonNode>("key_features", objectMapper.createArrayNode())
                node.put("target_market", extractValue(response, "market", "target", "Target"))
                node.put("plan_tier", extractValue(response, "tier", "", "Tier"))
            }
            "contributions" -> {
                val contributions = objectMapper.createArrayNode()
                val principal = objectMapper.createObjectNode()
                principal.put("member_type", "Principal")
                principal.put("amount", extractNumber(response, "principal"))
                principal.put("currency", "ZAR")
                principal.put("frequency", "monthly")
                contributions.add(principal)
                node.set<JsonNode>("contributions", contributions)
                node.put("notes", response.take(200))
            }
            "benefits" -> {
                node.set<JsonNode>("hospital_benefits", objectMapper.createObjectNode())
                node.set<JsonNode>("day_to_day_benefits", objectMapper.createObjectNode())
                node.set<JsonNode>("wellness_benefits", objectMapper.createObjectNode())
                node.put("notes", response.take(200))
            }
            "copayments" -> {
                node.set<JsonNode>("hospital_copayments", objectMapper.createObjectNode())
                node.set<JsonNode>("day_to_day_copayments", objectMapper.createObjectNode())
                node.set<JsonNode>("other_copayments", objectMapper.createObjectNode())
                node.set<JsonNode>("copayment_structures", objectMapper.createObjectNode())
                node.set<JsonNode>("exemptions", objectMapper.createArrayNode())
                node.put("notes", response.take(200))
            }
        }
        
        return node
    }

    private fun extractValue(text: String, vararg keywords: String): String? {
        for (keyword in keywords) {
            val pattern = Regex("""$keyword[:\s]+([^\n,.]+)""", RegexOption.IGNORE_CASE)
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractNumber(text: String, keyword: String): Double? {
        val pattern = Regex("""$keyword[^\d]*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
