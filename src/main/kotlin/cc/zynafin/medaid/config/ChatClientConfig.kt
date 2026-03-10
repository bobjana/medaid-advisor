package cc.zynafin.medaid.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class ChatClientConfig {

    @Bean
    open fun chatClient(ollamaChatModel: OllamaChatModel): ChatClient {
        return ChatClient.builder(ollamaChatModel).build()
    }
}
