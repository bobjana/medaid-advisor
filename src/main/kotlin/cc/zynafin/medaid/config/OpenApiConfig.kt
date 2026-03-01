package cc.zynafin.medaid.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class OpenApiConfig {

    @Bean
    open fun medAidAdvisorOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("MedAid Advisor API")
                    .description("RAG + Recommendation Engine API for Medical Aid Advisory")
                    .version("0.1.0")
                    .contact(
                        Contact()
                            .name("MedAid Advisor Team")
                            .email("support@zynafin.co.za")
                    )
                    .license(
                        License()
                            .name("Proprietary")
                            .url("https://zynafin.co.za")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("Local Development Server"),
                    Server()
                        .url("/api")
                        .description("Production Server")
                )
            )
    }
}
