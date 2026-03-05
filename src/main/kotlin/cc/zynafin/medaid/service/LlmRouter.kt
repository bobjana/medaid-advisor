package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.ExtractionConfidence
import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
open class LlmRouter(
    private val localPlanExtractor: PlanExtractor,
    @Value("\${medaid.extraction.local-threshold:0.75}")
    private val localConfidenceThreshold: Double,
    @Value("\${medaid.extraction.remote.enabled:false}")
    private val remoteEnabled: Boolean,
    @Value("\${medaid.extraction.remote.url:}")
    private val remoteUrl: String?,
    @Value("\${medaid.extraction.remote.api-key:}")
    private val remoteApiKey: String?
) {
    private val log = LoggerFactory.getLogger(LlmRouter::class.java)
    private val remotePlanExtractor: PlanExtractor? = null

    fun extractMetadataWithRouting(
        context: String,
        scheme: String,
        planName: String,
        year: Int
    ): SectionExtractionResult<JsonNode> {
        val localResult = localPlanExtractor.extractMetadata(context, scheme, planName, year)

        return when {
            shouldUseRemote(localResult) -> {
                log.info("Local confidence low for metadata extraction, trying remote LLM")
                extractWithRetry("metadata") {
                    remotePlanExtractor?.extractMetadata(context, scheme, planName, year) ?: localResult
                }
            }
            else -> {
                log.info("Using local LLM for metadata extraction (confidence: {})", localResult.confidence)
                localResult
            }
        }
    }

    fun extractContributionsWithRouting(
        context: String,
        scheme: String,
        planName: String,
        year: Int
    ): SectionExtractionResult<JsonNode> {
        val localResult = localPlanExtractor.extractContributions(context, scheme, planName, year)

        return when {
            shouldUseRemote(localResult) -> {
                log.info("Local confidence low for contributions extraction, trying remote LLM")
                extractWithRetry("contributions") {
                    remotePlanExtractor?.extractContributions(context, scheme, planName, year) ?: localResult
                }
            }
            else -> {
                log.info("Using local LLM for contributions extraction (confidence: {})", localResult.confidence)
                localResult
            }
        }
    }

    fun extractBenefitsWithRouting(
        context: String,
        scheme: String,
        planName: String,
        year: Int
    ): SectionExtractionResult<JsonNode> {
        val localResult = localPlanExtractor.extractBenefits(context, scheme, planName, year)

        return when {
            shouldUseRemote(localResult) -> {
                log.info("Local confidence low for benefits extraction, trying remote LLM")
                extractWithRetry("benefits") {
                    remotePlanExtractor?.extractBenefits(context, scheme, planName, year) ?: localResult
                }
            }
            else -> {
                log.info("Using local LLM for benefits extraction (confidence: {})", localResult.confidence)
                localResult
            }
        }
    }

    fun extractCopaymentsWithRouting(
        context: String,
        scheme: String,
        planName: String,
        year: Int
    ): SectionExtractionResult<JsonNode> {
        val localResult = localPlanExtractor.extractCopayments(context, scheme, planName, year)

        return when {
            shouldUseRemote(localResult) -> {
                log.info("Local confidence low for copayments extraction, trying remote LLM")
                extractWithRetry("copayments") {
                    remotePlanExtractor?.extractCopayments(context, scheme, planName, year) ?: localResult
                }
            }
            else -> {
                log.info("Using local LLM for copayments extraction (confidence: {})", localResult.confidence)
                localResult
            }
        }
    }

    private fun shouldUseRemote(result: SectionExtractionResult<JsonNode>): Boolean {
        return remoteEnabled &&
               remotePlanExtractor != null &&
               result.data == null &&
               result.confidence < localConfidenceThreshold
    }

    private fun extractWithRetry(section: String, extraction: () -> SectionExtractionResult<JsonNode>): SectionExtractionResult<JsonNode> {
        return try {
            extraction()
        } catch (e: Exception) {
            log.warn("Remote extraction failed for {}, returning local result", section, e)
            throw e
        }
    }

    companion object {
        fun createRemoteExtractor(remoteUrl: String, apiKey: String?): PlanExtractor? {
            throw NotImplementedError("Remote PlanExtractor configuration to be implemented")
        }
    }
}