package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import cc.zynafin.medaid.domain.extraction.ExtractionStatus
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [cc.zynafin.medaid.TestApplication::class])
@TestPropertySource(properties = [
    "medaid.extraction.top-k=10",
    "medaid.extraction.similarity-threshold=0.65",
    "spring.main.allow-bean-definition-overriding=true",
    "medaid.extraction.local.enabled=true",
    "medaid.extraction.remote.enabled=false"
])
class AgenticPlanExtractionServiceSmokeTest {

    @Autowired
    private lateinit var agenticPlanExtractionService: AgenticPlanExtractionService

    @Autowired
    private lateinit var planRepository: cc.zynafin.medaid.repository.PlanRepository

    @Test
    fun `service bean is properly injected`() {
        assertNotNull(agenticPlanExtractionService)
    }
}
