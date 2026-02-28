package cc.zynafin.medaid.service

import cc.zynafin.medaid.domain.*
import cc.zynafin.medaid.repository.ContributionRepository
import cc.zynafin.medaid.repository.HospitalBenefitRepository
import cc.zynafin.medaid.repository.PlanRepository
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

@Service
open class PlanDataService(
    private val planRepository: PlanRepository,
    private val contributionRepository: ContributionRepository,
    private val hospitalBenefitRepository: HospitalBenefitRepository
) {
    private val log = LoggerFactory.getLogger(PlanDataService::class.java)

    /**
     * Parse contribution tables from PDF and update plan
     */
    @Transactional
    open fun parseAndStoreContributions(pdfPath: String, planId: String): ContributionParseResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val contributions = extractContributionsFromPdf(pdfPath, plan)

        // Clear existing contributions
        contributionRepository.findByPlanId(planId).forEach {
            contributionRepository.delete(it)
        }

        // Save new contributions
        val saved = contributions.map {
            contributionRepository.save(it)
        }

        // Note: Plan entity uses val (immutable), so we cannot modify it directly
        // Contributions are stored separately and can be queried via ContributionRepository

        return ContributionParseResult(
            success = true,
            planId = planId,
            contributionsExtracted = saved.size,
            contributions = saved
        )
    }

    /**
     * Parse hospital benefit limits from PDF and update plan
     */
    @Transactional
    open fun parseAndStoreHospitalBenefits(pdfPath: String, planId: String): HospitalBenefitParseResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val benefits = extractHospitalBenefitsFromPdf(pdfPath, plan)

        // Clear existing benefits
        hospitalBenefitRepository.findByPlanId(planId).forEach {
            hospitalBenefitRepository.delete(it)
        }

        // Save new benefits
        val saved = benefits.map {
            hospitalBenefitRepository.save(it)
        }

        // Note: Plan entity uses val (immutable), so we cannot modify it directly
        // Hospital benefits are stored separately and can be queried via HospitalBenefitRepository

        return HospitalBenefitParseResult(
            success = true,
            planId = planId,
            benefitsExtracted = saved.size,
            benefits = saved
        )
    }

    /**
     * Extract contribution table data from PDF text
     */
    private fun extractContributionsFromPdf(pdfPath: String, plan: Plan): List<Contribution> {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val contributions = mutableListOf<Contribution>()

        // Pattern 1: Table with column headers like "Principal | Spouse | Child"
        val contributionPattern = Pattern.compile(
            """(?i)principal\s*[|:]\s*R\s*(\d+[,\d.]*\d*)\s*[|:]\s*spouse\s*[|:]\s*R\s*(\d+[,\d.]*\d*)""",
            Pattern.MULTILINE
        )

        // Pattern 2: Row-by-row contribution data
        val rowPattern = Pattern.compile(
            """(?i)(principal|spouse|adult|child|dependen)\s*[|:]\s*R\s*(\d+[,\d.]*\d*)""",
            Pattern.MULTILINE
        )

        // Try to extract contributions by searching for common patterns
        val lines = text.lines()

        for (line in lines) {
            // Look for principal contribution
            val principalMatch = extractContributionAmount(line, listOf("principal", "main member", "adult"))
            if (principalMatch != null && contributions.none { it.memberType == MemberType.PRINCIPAL }) {
                contributions.add(Contribution(
                    plan = plan,
                    memberType = MemberType.PRINCIPAL,
                    monthlyAmount = principalMatch,
                    conditions = "Extracted from PDF"
                ))
            }

            // Look for spouse contribution
            val spouseMatch = extractContributionAmount(line, listOf("spouse", "partner", "adult dependent"))
            if (spouseMatch != null && contributions.none { it.memberType == MemberType.SPOUSE }) {
                contributions.add(Contribution(
                    plan = plan,
                    memberType = MemberType.SPOUSE,
                    monthlyAmount = spouseMatch,
                    conditions = "Extracted from PDF"
                ))
            }

            // Look for child contribution
            val childMatch = extractContributionAmount(line, listOf("child", "kid", "dependent"))
            if (childMatch != null) {
                val childNum = contributions.count { it.memberType.name.startsWith("CHILD") }
                val memberType = when (childNum) {
                    0 -> MemberType.CHILD_FIRST
                    1 -> MemberType.CHILD_SECOND
                    2 -> MemberType.CHILD_THIRD
                    3 -> MemberType.CHILD_FOURTH
                    else -> MemberType.CHILD_FIFTH_OR_MORE
                }
                contributions.add(Contribution(
                    plan = plan,
                    memberType = memberType,
                    monthlyAmount = childMatch,
                    conditions = "Extracted from PDF"
                ))
            }
        }

        log.info("Extracted ${contributions.size} contributions from PDF: $pdfPath")
        return contributions
    }

    /**
     * Extract hospital benefit limits from PDF text
     */
    private fun extractHospitalBenefitsFromPdf(pdfPath: String, plan: Plan): List<HospitalBenefit> {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val benefits = mutableListOf<HospitalBenefit>()
        val lines = text.lines()

        // Benefit patterns to look for
        val benefitPatterns = mapOf(
            BenefitCategory.HOSPITAL_COVER to listOf("hospital", "in-hospital", "hospitalization"),
            BenefitCategory.CHRONIC_MEDICINE to listOf("chronic", "chronic medicine", "cdl"),
            BenefitCategory.SPECIALIST_CONSULTATION to listOf("specialist", "consultation", "doctor"),
            BenefitCategory.EMERGENCY_SERVICE to listOf("emergency", "accident", "casualty"),
            BenefitCategory.MATERNITY to listOf("maternity", "pregnancy", "birth"),
            BenefitCategory.DENTAL to listOf("dental", "teeth"),
            BenefitCategory.OPTICAL to listOf("optical", "glasses", "lens", "eye"),
            BenefitCategory.PRESCRIBED_MINIMUM_BENEFITS to listOf("pmb", "prescribed", "minimum benefits")
        )

        for (line in lines) {
            val lowerLine = line.lowercase()

            for ((category, keywords) in benefitPatterns) {
                if (keywords.any { lowerLine.contains(it) }) {
                    // Try to extract limit/coverage amounts
                    val limit = extractLimitAmount(line)

                    if (!benefits.any { it.category == category }) {
                        benefits.add(HospitalBenefit(
                            plan = plan,
                            category = category,
                            benefitName = extractBenefitName(line),
                            limitPerFamily = limit?.familyLimit,
                            limitPerPerson = limit?.personLimit,
                            annualLimit = limit?.annualLimit,
                            covered = true,
                            notes = "Extracted from PDF",
                            conditions = null
                        ))
                    }
                }
            }
        }

        log.info("Extracted ${benefits.size} hospital benefits from PDF: $pdfPath")
        return benefits
    }

    /**
     * Extract contribution amount from text line
     */
    fun extractContributionAmount(line: String, keywords: List<String>): Double? {
        val lowerLine = line.lowercase()
        if (keywords.none { lowerLine.contains(it) }) return null

        // Pattern: R 1234.56 or R 1,234.56
        val amountPattern = Pattern.compile("""R\s*(\d+[,.\d]*)\d*""")
        val matcher = amountPattern.matcher(line)

        if (matcher.find()) {
            return try {
                val amountStr = matcher.group(1).replace(",", "")
                BigDecimal(amountStr).setScale(2, RoundingMode.HALF_UP).toDouble()
            } catch (e: Exception) {
                log.warn("Failed to parse amount: $line", e)
                null
            }
        }

        return null
    }

    /**
     * Extract limit information from benefit line
     */
    private fun extractLimitAmount(line: String): LimitInfo? {
        // Pattern: R 1,000,000 per family or R 200,000 p/a
        val limitPattern = Pattern.compile("""R\s*(\d+[,.\d]*)\d*\s*(per\s+(family|person|p[/a])?)""")
        val matcher = limitPattern.matcher(line)

        if (matcher.find()) {
            return try {
                val amountStr = matcher.group(1).replace(",", "")
                val amount = BigDecimal(amountStr)
                val perType = matcher.group(2)?.lowercase() ?: ""

                LimitInfo(
                    familyLimit = if (perType.contains("family")) amount.toString() else null,
                    personLimit = if (perType.contains("person") || perType == "p/a") amount.toString() else null,
                    annualLimit = if (perType == "p/a") "Annually: ${amount.toString()}" else null
                )
            } catch (e: Exception) {
                log.warn("Failed to parse limit: $line", e)
                null
            }
        }

        return null
    }

    /**
     * Extract benefit name from line
     */
    private fun extractBenefitName(line: String): String {
        // Remove numeric values and limit indicators
        return line
            .replace("""R\s*\d+[,.\d]*\d*""".toRegex(), "")
            .replace("""per\s+(family|person|p[/a])""".toRegex(), "")
            .replace("""\s+""".toRegex(), " ")
            .trim()
            .takeIf { it.isNotEmpty() } ?: "Benefit"
    }

    /**
     * Build summary text for hospital benefits
     */
    private fun buildHospitalBenefitsSummary(benefits: List<HospitalBenefit>): String {
        return benefits.joinToString("\n") { benefit ->
            "${benefit.category.name}: ${benefit.limitPerPerson ?: benefit.limitPerFamily ?: "No limit specified"}"
        }.take(4000) // Limit to VARCHAR(4000)
    }
}

data class ContributionParseResult(
    val success: Boolean,
    val planId: String,
    val contributionsExtracted: Int,
    val contributions: List<Contribution> = emptyList()
)

data class HospitalBenefitParseResult(
    val success: Boolean,
    val planId: String,
    val benefitsExtracted: Int,
    val benefits: List<HospitalBenefit> = emptyList()
)

data class LimitInfo(
    val familyLimit: String?,
    val personLimit: String?,
    val annualLimit: String?
)
