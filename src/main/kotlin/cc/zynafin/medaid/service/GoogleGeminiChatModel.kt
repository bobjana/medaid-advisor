package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Google Gemini ChatModel implementation that wraps the GoogleGeminiClient
 */
class GoogleGeminiChatModel(
    private val googleGeminiClient: GoogleGeminiClient,
    private val objectMapper: ObjectMapper,
    @Value("\${medaid.extraction.remote.model:gemini-2.0-flash}")
    private val modelName: String
) : ChatModel {
    
    private val log = LoggerFactory.getLogger(GoogleGeminiChatModel::class.java)
    
    override fun call(prompt: Prompt): ChatResponse {
        try {
            // Extract text content from the prompt - convert entire prompt to string
            val promptText = prompt.toString()
            
            log.debug("Sending prompt to Gemini: {}", promptText.take(500))
            
            val response = googleGeminiClient.generateContent(promptText)
            
            log.debug("Gemini response: {}", response.take(500))
            
            // Create ChatResponse with the text content wrapped in a Generation
            val assistantMessage = AssistantMessage(response)
            val generation = Generation(assistantMessage)
            return ChatResponse(listOf(generation))
        } catch (e: Exception) {
            log.error("Error calling Gemini API: {}", e.message, e)
            throw RuntimeException("Failed to call Gemini API: ${e.message}", e)
        }
    }
    
    override fun getDefaultOptions(): OpenAiChatOptions {
        return OpenAiChatOptions.builder()
            .withModel(modelName)
            .withTemperature(0.1)
            .build()
    }
}
