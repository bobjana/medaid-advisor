package cc.zynafin.medaid.controller

import cc.zynafin.medaid.service.PlanDataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

@RestController
@RequestMapping("/api/v1/plan-data")
class PlanDataController(
    private val planDataService: PlanDataService
) {

    /**
     * Parse and store contribution tables from a PDF
     */
    @PostMapping("/contributions")
    fun parseContributions(
        @RequestParam("planId") planId: String,
        @RequestParam("pdf") file: MultipartFile
    ): ResponseEntity<ParseResponse> {
        val tempFile = Files.createTempFile("plan_contributions_", ".pdf").toFile()
        tempFile.writeBytes(file.bytes)

        return try {
            val result = planDataService.parseAndStoreContributions(tempFile.absolutePath, planId)
            ResponseEntity.ok(
                ParseResponse(
                    success = result.success,
                    message = "Parsed ${result.contributionsExtracted} contributions for plan $planId",
                    itemsExtracted = result.contributionsExtracted,
                    data = result.contributions.map {
                        mapOf(
                            "memberType" to it.memberType.name,
                            "monthlyAmount" to it.monthlyAmount,
                            "ageBracket" to it.ageBracket,
                            "conditions" to it.conditions
                        )
                    }
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                ParseResponse(
                    success = false,
                    message = "Failed to parse contributions: ${e.message}",
                    itemsExtracted = 0,
                    data = emptyList()
                )
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Parse and store hospital benefit limits from a PDF
     */
    @PostMapping("/hospital-benefits")
    fun parseHospitalBenefits(
        @RequestParam("planId") planId: String,
        @RequestParam("pdf") file: MultipartFile
    ): ResponseEntity<ParseResponse> {
        val tempFile = Files.createTempFile("plan_benefits_", ".pdf").toFile()
        tempFile.writeBytes(file.bytes)

        return try {
            val result = planDataService.parseAndStoreHospitalBenefits(tempFile.absolutePath, planId)
            ResponseEntity.ok(
                ParseResponse(
                    success = result.success,
                    message = "Parsed ${result.benefitsExtracted} hospital benefits for plan $planId",
                    itemsExtracted = result.benefitsExtracted,
                    data = result.benefits.map {
                        mapOf(
                            "category" to it.category.name,
                            "benefitName" to it.benefitName,
                            "limitPerFamily" to it.limitPerFamily,
                            "limitPerPerson" to it.limitPerPerson,
                            "annualLimit" to it.annualLimit,
                            "covered" to it.covered,
                            "conditions" to it.conditions
                        )
                    }
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                ParseResponse(
                    success = false,
                    message = "Failed to parse hospital benefits: ${e.message}",
                    itemsExtracted = 0,
                    data = emptyList()
                )
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Parse both contributions and hospital benefits from a PDF
     */
    @PostMapping("/parse-full")
    fun parseFullPlanData(
        @RequestParam("planId") planId: String,
        @RequestParam("pdf") file: MultipartFile
    ): ResponseEntity<FullParseResponse> {
        val tempFile = Files.createTempFile("plan_full_", ".pdf").toFile()
        tempFile.writeBytes(file.bytes)

        return try {
            val contributionsResult = planDataService.parseAndStoreContributions(tempFile.absolutePath, planId)
            val benefitsResult = planDataService.parseAndStoreHospitalBenefits(tempFile.absolutePath, planId)

            ResponseEntity.ok(
                FullParseResponse(
                    success = contributionsResult.success && benefitsResult.success,
                    message = "Parsed ${contributionsResult.contributionsExtracted} contributions and ${benefitsResult.benefitsExtracted} hospital benefits",
                    contributionsParsed = contributionsResult.contributionsExtracted,
                    benefitsParsed = benefitsResult.benefitsExtracted,
                    planId = planId
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                FullParseResponse(
                    success = false,
                    message = "Failed to parse plan data: ${e.message}",
                    contributionsParsed = 0,
                    benefitsParsed = 0,
                    planId = planId
                )
            )
        } finally {
            tempFile.delete()
        }
    }
}

data class ParseResponse(
    val success: Boolean,
    val message: String,
    val itemsExtracted: Int,
    val data: List<Map<String, Any?>> = emptyList()
)

data class FullParseResponse(
    val success: Boolean,
    val message: String,
    val contributionsParsed: Int,
    val benefitsParsed: Int,
    val planId: String
)
