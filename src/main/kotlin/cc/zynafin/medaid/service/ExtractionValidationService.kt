package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.BenefitCategory
import cc.zynafin.medaid.domain.MemberType
import cc.zynafin.medaid.domain.PlanType
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
open class ExtractionValidationService {
    private val log = LoggerFactory.getLogger(ExtractionValidationService::class.java)

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    fun validatePlanType(planType: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val validTypes = PlanType.values().map { it.name }
        if (planType !in validTypes) {
            errors.add("Invalid plan_type '$planType'. Valid types: ${validTypes.joinToString()}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun validateMemberType(memberType: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val validTypes = MemberType.values().map { it.name }
        if (memberType !in validTypes) {
            errors.add("Invalid member_type '$memberType'. Valid types: ${validTypes.joinToString()}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun validateBenefitCategory(category: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val validCategories = BenefitCategory.values().map { it.name }
        if (category !in validCategories) {
            errors.add("Invalid benefit_category '$category'. Valid categories: ${validCategories.joinToString()}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun validateExtractionData(extractionData: JsonNode): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (extractionData.has("plan_type")) {
            val planType = extractionData.get("plan_type").asText()
            val planTypeResult = validatePlanType(planType)
            errors.addAll(planTypeResult.errors)
            warnings.addAll(planTypeResult.warnings)
        }

        if (extractionData.has("contributions")) {
            val contributions = extractionData.get("contributions")
            if (contributions.isArray) {
                contributions.forEach { contrib ->
                    if (contrib.has("member_type")) {
                        val memberType = contrib.get("member_type").asText()
                        val memberTypeResult = validateMemberType(memberType)
                        errors.addAll(memberTypeResult.errors)
                    }
                }
            }
        }

        if (extractionData.has("hospital_benefits") || extractionData.has("day_to_day_benefits")) {
            val benefits = extractionData.get("hospital_benefits") ?: extractionData.get("day_to_day_benefits")
            benefits.fields().forEach { (key, node) ->
                if (node.isObject) {
                    node.fields().forEach { (innerKey, innerNode) ->
                        if (innerKey == "benefit_category" && innerNode.isTextual) {
                            val category = innerNode.asText()
                            val categoryResult = validateBenefitCategory(category)
                            errors.addAll(categoryResult.errors)
                        }
                    }
                }
            }
        }

        val amountWarnings = validateAmounts(extractionData)
        warnings.addAll(amountWarnings)

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateAmounts(extractionData: JsonNode): List<String> {
        val warnings = mutableListOf<String>()

        fun checkAmount(node: JsonNode, path: String) {
            if (node.isNumber) {
                val amount = node.asDouble()
                when {
                    amount < 0 -> warnings.add("Negative amount at $path: $amount")
                    amount > 1000000 -> warnings.add("Suspiciously large amount at $path: $amount")
                }
            }
        }

        if (extractionData.has("contributions")) {
            val contributions = extractionData.get("contributions")
            contributions.forEach { contrib ->
                if (contrib.has("amount")) {
                    checkAmount(contrib.get("amount"), "contributions.amount")
                }
            }
        }

        if (extractionData.has("hospital_copayments")) {
            val copayments = extractionData.get("hospital_copayments")
            copayments.fields().forEach { (key, node) ->
                if (node.has("amount")) {
                    checkAmount(node.get("amount"), "hospital_copayments.$key.amount")
                }
            }
        }

        if (extractionData.has("day_to_day_copayments")) {
            val copayments = extractionData.get("day_to_day_copayments")
            copayments.fields().forEach { (key, node) ->
                if (node.has("amount")) {
                    checkAmount(node.get("amount"), "day_to_day_copayments.$key.amount")
                }
            }
        }

        return warnings
    }
}
