package cc.zynafin.medaid

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ImportAutoConfiguration(exclude = [OpenAiAutoConfiguration::class])
@EnableJpaRepositories(basePackages = ["cc.zynafin.medaid.repository"])
@EntityScan(basePackages = ["cc.zynafin.medaid.domain"])
open class MedAidAdvisorApplication

fun main(args: Array<String>) {
    runApplication<MedAidAdvisorApplication>(*args)
}
