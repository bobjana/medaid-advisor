package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Direct Google Gemini API client 
 */
@Component
class GoogleGeminiClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(GoogleGeminiClient::class.java)
    
    fun generateContent(prompt: String, model: String = "gemini-2.5-flash"): String {
        try {
            val apiKey = System.getenv("GEMINI_API_KEY") ?: 
                java.io.File(".secret_gemini_api_key").readText().trim()
            
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            
            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to prompt))
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.1,
                    "maxOutputTokens" to 8192
                )
            )
            
            val response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String::class.java)
            
            log.debug("Gemini response: {}", response?.take(500))
            
            return parseGeminiResponse(response)
        } catch (e: Exception) {
            log.error("Error calling Gemini API: {}", e.message, e)
            throw RuntimeException("Failed to call Gemini API: ${e.message}", e)
        }
    }
    
    private fun parseGeminiResponse(response: String?): String {
        if (response.isNullOrBlank()) {
            return ""
        }
        
        val jsonNode = objectMapper.readTree(response)
        
        // Check for errors
        if (jsonNode.has("error")) {
            val error = jsonNode.get("error")
            val message = error?.get("message")?.asText() ?: "Unknown error"
            throw RuntimeException("Gemini API error: $message")
        }
        
        // Extract text from candidates
        val candidates = jsonNode.get("candidates")
        if (candidates == null || candidates.isEmpty) {
            return ""
        }
        
        val firstCandidate = candidates.get(0)
        val content = firstCandidate?.get("content")
        val parts = content?.get("parts")
        
        if (parts == null || parts.isEmpty) {
            return ""
        }
        
        return parts.get(0)?.get("text")?.asText() ?: ""
    }
}
