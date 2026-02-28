package cc.zynafin.medaid.service

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.stereotype.Service

@Service
class LlmService(
    private val chatClient: ChatClient
) {
    private val log = LoggerFactory.getLogger(LlmService::class.java)

    fun generateResponse(userPrompt: String, systemPrompt: String? = null): String {
        val messages = if (systemPrompt != null) {
            listOf(SystemMessage(systemPrompt), UserMessage(userPrompt))
        } else {
            listOf(UserMessage(userPrompt))
        }

        val prompt = Prompt(messages)

        return try {
            val response = chatClient.prompt(prompt).call().content()
                ?: "No response generated"
            log.debug("Generated LLM response (length: ${response.length})")
            response
        } catch (e: Exception) {
            log.error("Error generating LLM response", e)
            "Error generating response: ${e.message}"
        }
    }

    fun generateRecommendationExplanation(
        employeeSummary: String,
        planName: String,
        scheme: String,
        componentScores: String,
        matchedBenefits: List<String>,
        gaps: List<String>
    ): String {
        val systemPrompt = """
            You are a knowledgeable medical aid advisor explaining a plan recommendation.

            Your role:
            - Provide clear, professional explanations
            - Be honest about trade-offs
            - Present cost in context of value, not just price
            - Highlight gaps the employee should be aware of
            - Keep responses to 3-4 paragraphs maximum
            - Tone: Professional, helpful, balanced (not salesy)
        """.trimIndent()

        val userPrompt = """
            Generate a clear explanation for this medical aid recommendation:

            CONTEXT:
            - Employee: $employeeSummary
            - Recommended Plan: $planName by $scheme
            - Scoring: $componentScores
            - Key Matches: ${matchedBenefits.joinToString(", ")}
            - Potential Gaps: ${gaps.joinToString(", ")}

            Start with the strongest match between employee needs and plan benefits.
            Address any trade-offs honestly.
            Mention cost in context of value, not just price.
            Highlight any gaps the employee should be aware of.
        """.trimIndent()

        return generateResponse(userPrompt, systemPrompt)
    }

    fun generatePlanComparison(
        planADetails: String,
        planBDetails: String,
        employeeSummary: String
    ): String {
        val systemPrompt = """
            You are comparing two medical aid plans for an employee.

            Create a clear comparison highlighting:
            1. Cost differences and what drives them
            2. Coverage differences relevant to this employee
            3. Which plan is better for which scenarios
            4. Clear recommendation with reasoning

            Format as a markdown table followed by a brief explanation.
        """.trimIndent()

        val userPrompt = """
            Compare these two medical aid plans:

            PLAN A: $planADetails
            PLAN B: $planBDetails

            EMPLOYEE CONTEXT: $employeeSummary

            Create a side-by-side comparison.
        """.trimIndent()

        return generateResponse(userPrompt, systemPrompt)
    }

    fun explainBenefit(term: String, context: String): String {
        val userPrompt = """
            Explain the medical aid term "$term" in simple language.

            Additional context: $context

            Provide a clear explanation that a non-medical professional can understand.
            Include examples if helpful.
        """.trimIndent()

        return generateResponse(userPrompt)
    }
}
