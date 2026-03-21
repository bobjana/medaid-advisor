package cc.zynafin.medaid.config

import cc.zynafin.medaid.service.GoogleGeminiChatModel
import cc.zynafin.medaid.service.GoogleGeminiClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class GeminiConfig {
    
    @Bean
    fun googleGeminiChatModel(
        googleGeminiClient: GoogleGeminiClient,
        objectMapper: ObjectMapper
    ): GoogleGeminiChatModel {
        return GoogleGeminiChatModel(googleGeminiClient, objectMapper)
    }
    
    @Bean
    fun restClient(): RestClient {
        return RestClient.builder().build()
    }
}
