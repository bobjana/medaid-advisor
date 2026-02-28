package cc.zynafin.medaid.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class ChatClientConfig {

    @Bean
    open fun chatClient(openAiChatModel: OpenAiChatModel): ChatClient {
        return ChatClient.builder(openAiChatModel).build()
    }
}
