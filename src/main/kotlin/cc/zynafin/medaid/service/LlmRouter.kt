package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.extraction.ExtractionConfidence
import cc.zynafin.medaid.domain.extraction.SectionExtractionResult
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient


@Service
open class LlmRouter(
    private val localPlanExtractor: PlanExtractor,
    @Value("\${medaid.extraction.local.enabled:true}")
    private val localEnabled: Boolean,
    @Value("\${medaid.extraction.local-threshold:0.75}")
    private val localConfidenceThreshold: Double,
    @Value("\${medaid.extraction.remote.enabled:false}")
    private val remoteEnabled: Boolean,
    @Value("\${medaid.extraction.remote.url:}")
    private val remoteUrl: String?,
    @Value("\${medaid.extraction.remote.api-key:}")
    private val remoteApiKey: String?,
    @Value("\${medaid.extraction.remote.model:gemini-1.5-flash}")
    private val remoteModel: String
) {
    private val log = LoggerFactory.getLogger(LlmRouter::class.java)
    private val objectMapper = ObjectMapper()
    
    // Load API key from secret file if not provided via environment variable
    private val effectiveApiKey: String? by lazy {
        remoteApiKey?.takeIf { it.isNotBlank() } 
            ?: loadApiKeyFromFile()
    }
    
    private val remotePlanExtractor: PlanExtractor? = initRemoteExtractor()

    private fun loadApiKeyFromFile(): String? {
        return try {
            val secretFile = FileSystemResource(".secret_gemini_api_key")
            if (secretFile.exists()) {
                secretFile.inputStream.bufferedReader().use { it.readText().trim() }
            } else {
                log.warn("API key file .secret_gemini_api_key not found")
                null
            }
        } catch (e: Exception) {
            log.error("Failed to load API key from file: {}", e.message)
            null
        }
    }

    private fun initRemoteExtractor(): PlanExtractor? {
        val apiKey = effectiveApiKey
        if (!remoteEnabled || remoteUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            log.info("Remote LLM is disabled or not configured - remoteEnabled: {}, url: {}, hasApiKey: {}", 
                remoteEnabled, remoteUrl?.isNotBlank(), apiKey?.isNotBlank())
            return null
        }
        return try {
            createRemoteExtractor(remoteUrl, apiKey, remoteModel)
        } catch (e: Exception) {
            log.error("Failed to create remote extractor: {}", e.message, e)
            null
        }
    }

    init {
        if (!localEnabled && !remoteEnabled) {
            throw IllegalStateException("Both local and remote LLM are disabled. At least one must be enabled.")
        }
        if (!localEnabled && remotePlanExtractor == null) {
            throw IllegalStateException("Local LLM is disabled but remote LLM is not configured properly.")
        }
        if (!localEnabled) {
            log.info("Local LLM is disabled. Using remote LLM exclusively.")
        } else if (remoteEnabled) {
            log.info("Remote LLM is enabled. Will use remote as primary.")
        }
    }

    fun extractMetadataWithRouting(
        context: String,
        scheme: String,
        planName: String,
        year: Int
    ): SectionExtractionResult<JsonNode> {
        return if (!localEnabled || (remoteEnabled && remotePlanExtractor != null)) {
            try {
                remotePlanExtractor?.extractMetadata(context, scheme, planName, year)
                    ?: if (localEnabled) localPlanExtractor.extractMetadata(context, scheme, planName, year)
                    else createFailedResult("Remote LLM not available and local LLM is disabled")
            } catch (e: Exception) {
                log.error("Remote extraction failed for metadata: {}", e.message)
                if (localEnabled) {
                    localPlanExtractor.extractMetadata(context, scheme, planName, year)
                } else {
                    createFailedResult("Remote extraction failed: ${e.message}")
                }
            }
        } else {
            val localResult = localPlanExtractor.extractMetadata(context, scheme, planName, year)
            if (shouldUseRemote(localResult)) {
                log.info("Local confidence low for metadata extraction, trying remote LLM")
                try {
                    remotePlanExtractor?.extractMetadata(context, scheme, planName, year) ?: localResult
                } catch (e: Exception) {
                    localResult
                }
            } else {
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
        return if (!localEnabled || (remoteEnabled && remotePlanExtractor != null)) {
            try {
                remotePlanExtractor?.extractContributions(context, scheme, planName, year)
                    ?: if (localEnabled) localPlanExtractor.extractContributions(context, scheme, planName, year)
                    else createFailedResult("Remote LLM not available and local LLM is disabled")
            } catch (e: Exception) {
                log.error("Remote extraction failed for contributions: {}", e.message)
                if (localEnabled) {
                    localPlanExtractor.extractContributions(context, scheme, planName, year)
                } else {
                    createFailedResult("Remote extraction failed: ${e.message}")
                }
            }
        } else {
            val localResult = localPlanExtractor.extractContributions(context, scheme, planName, year)
            if (shouldUseRemote(localResult)) {
                log.info("Local confidence low for contributions extraction, trying remote LLM")
                try {
                    remotePlanExtractor?.extractContributions(context, scheme, planName, year) ?: localResult
                } catch (e: Exception) {
                    localResult
                }
            } else {
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
        return if (!localEnabled || (remoteEnabled && remotePlanExtractor != null)) {
            try {
                remotePlanExtractor?.extractBenefits(context, scheme, planName, year)
                    ?: if (localEnabled) localPlanExtractor.extractBenefits(context, scheme, planName, year)
                    else createFailedResult("Remote LLM not available and local LLM is disabled")
            } catch (e: Exception) {
                log.error("Remote extraction failed for benefits: {}", e.message)
                if (localEnabled) {
                    localPlanExtractor.extractBenefits(context, scheme, planName, year)
                } else {
                    createFailedResult("Remote extraction failed: ${e.message}")
                }
            }
        } else {
            val localResult = localPlanExtractor.extractBenefits(context, scheme, planName, year)
            if (shouldUseRemote(localResult)) {
                log.info("Local confidence low for benefits extraction, trying remote LLM")
                try {
                    remotePlanExtractor?.extractBenefits(context, scheme, planName, year) ?: localResult
                } catch (e: Exception) {
                    localResult
                }
            } else {
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
        return if (!localEnabled || (remoteEnabled && remotePlanExtractor != null)) {
            try {
                remotePlanExtractor?.extractCopayments(context, scheme, planName, year)
                    ?: if (localEnabled) localPlanExtractor.extractCopayments(context, scheme, planName, year)
                    else createFailedResult("Remote LLM not available and local LLM is disabled")
            } catch (e: Exception) {
                log.error("Remote extraction failed for copayments: {}", e.message)
                if (localEnabled) {
                    localPlanExtractor.extractCopayments(context, scheme, planName, year)
                } else {
                    createFailedResult("Remote extraction failed: ${e.message}")
                }
            }
        } else {
            val localResult = localPlanExtractor.extractCopayments(context, scheme, planName, year)
            if (shouldUseRemote(localResult)) {
                log.info("Local confidence low for copayments extraction, trying remote LLM")
                try {
                    remotePlanExtractor?.extractCopayments(context, scheme, planName, year) ?: localResult
                } catch (e: Exception) {
                    localResult
                }
            } else {
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

    private fun createFailedResult(errorMessage: String): SectionExtractionResult<JsonNode> {
        return SectionExtractionResult(
            data = objectMapper.createObjectNode(),
            confidence = ExtractionConfidence.FAILED.score,
            sourceChunks = emptyList(),
            errorMessage = errorMessage
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LlmRouter::class.java)
        
        fun createRemoteExtractor(remoteUrl: String, apiKey: String, model: String): PlanExtractor {
            log.info("Creating Vertex AI extractor - URL: $remoteUrl, Model: $model, hasApiKey: ${apiKey.isNotBlank()}")
            
            // Vertex AI API uses OpenAI-compatible endpoint at aiplatform.googleapis.com
            // The endpoint format is: https://aiplatform.googleapis.com/v1/publishers/google/models/{model}:streamGenerateContent
            // But Spring AI's OpenAI client expects: baseUrl + /v1/chat/completions
            
            val baseUrl = if (remoteUrl.contains("aiplatform.googleapis.com")) {
                // Use the OpenAI-compatible endpoint
                "${remoteUrl}/v1"
            } else {
                remoteUrl
            }
            
            val restClientBuilder = RestClient.builder()
            
            // Create OpenAI API client pointing to Vertex AI endpoint
            val api = OpenAiApi(
                baseUrl,
                apiKey,
                restClientBuilder,
                org.springframework.web.reactive.function.client.WebClient.builder()
            )
            
            val options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.1)
                .build()
            
            val chatModel = OpenAiChatModel(api, options)
            val chatClient = ChatClient.builder(chatModel).build()
            val resourceLoader = DefaultResourceLoader()
            
            return PlanExtractor(chatClient, resourceLoader, 60, false)
        }
    }
}
