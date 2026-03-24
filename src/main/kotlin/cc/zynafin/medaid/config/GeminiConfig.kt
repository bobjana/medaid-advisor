package cc.zynafin.medaid.config

import cc.zynafin.medaid.service.GoogleGeminiChatModel
import cc.zynafin.medaid.service.GoogleGeminiClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class GeminiConfig {
    
    @Bean
    fun googleGeminiChatModel(
        googleGeminiClient: GoogleGeminiClient,
        objectMapper: ObjectMapper,
        @Value("\${medaid.extraction.remote.model:gemini-2.0-flash}")
        modelName: String
    ): GoogleGeminiChatModel {
        return GoogleGeminiChatModel(googleGeminiClient, objectMapper, modelName)
    }
    
    @Bean
    fun googleGeminiClient(
        restClient: RestClient,
        objectMapper: ObjectMapper,
        @Value("\${medaid.extraction.remote.model:gemini-2.0-flash}")
        modelName: String
    ): GoogleGeminiClient {
        return GoogleGeminiClient(restClient, objectMapper, modelName)
    }
    
    @Bean
    fun restClient(): RestClient {
        return RestClient.builder().build()
    }
}
