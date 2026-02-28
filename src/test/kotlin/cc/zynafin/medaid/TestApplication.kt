package cc.zynafin.medaid

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.mockito.Mockito

@SpringBootApplication
@EnableJpaRepositories(basePackages = ["cc.zynafin.medaid.repository"])
@EntityScan(basePackages = ["cc.zynafin.medaid.domain"])
open class TestApplication {

    @MockBean
    lateinit var vectorStore: VectorStore

    @Bean
    @Primary
    open fun chatClient(): ChatClient {
        return Mockito.mock(ChatClient::class.java)
    }

    @Bean
    @Primary
    open fun openAiChatModel(): OpenAiChatModel {
        return Mockito.mock(OpenAiChatModel::class.java)
    }
}
