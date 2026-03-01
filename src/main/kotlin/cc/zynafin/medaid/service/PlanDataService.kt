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
import java.util.UUID
import java.util.regex.Pattern

@Service
open class PlanDataService(
    private val planRepository: PlanRepository,
    private val contributionRepository: ContributionRepository,
    private val hospitalBenefitRepository: HospitalBenefitRepository
) {
    private val log = LoggerFactory.getLogger(PlanDataService::class.java)

    companion object {
        private const val MIN_VALID_CONTRIBUTION = 100.0
        private const val MAX_VALID_CONTRIBUTION = 50000.0
    }

    @Transactional
    open fun parseAndStoreContributions(pdfPath: String, planId: UUID): ContributionParseResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val contributions = extractContributionsFromPdf(pdfPath, plan)

        contributionRepository.findByPlanId(planId).forEach {
            contributionRepository.delete(it)
        }

        val saved = contributions.map {
            contributionRepository.save(it)
        }

        return ContributionParseResult(
            success = true,
            planId = planId,
            contributionsExtracted = saved.size,
            contributions = saved
        )
    }

    @Transactional
    open fun parseAndStoreHospitalBenefits(pdfPath: String, planId: UUID): HospitalBenefitParseResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val benefits = extractHospitalBenefitsFromPdf(pdfPath, plan)

        hospitalBenefitRepository.findByPlanId(planId).forEach {
            hospitalBenefitRepository.delete(it)
        }

        val saved = benefits.map {
            hospitalBenefitRepository.save(it)
        }

        return HospitalBenefitParseResult(
            success = true,
            planId = planId,
            benefitsExtracted = saved.size,
            benefits = saved
        )
    }

    @Transactional
    open fun parseAndStoreCopayments(pdfPath: String, planId: UUID): CopaymentParseResult {
        val plan = planRepository.findById(planId).orElseThrow {
            IllegalArgumentException("Plan not found: $planId")
        }

        val copayments = extractCopaymentsFromPdf(pdfPath, plan)

        return CopaymentParseResult(
            success = true,
            planId = planId,
            copaymentsExtracted = copayments.size,
            copayments = copayments
        )
    }

    open fun extractMsaInfo(pdfPath: String): MsaInfo {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val lowerText = text.lowercase()

        val hasMsa = lowerText.contains("medical savings account") ||
                lowerText.contains("msa") ||
                lowerText.contains("savings account") ||
                lowerText.contains("personal savings") ||
                lowerText.contains("day-to-day benefit")

        val msaPercentage = if (hasMsa) {
            extractMsaPercentage(lowerText)
        } else null

        return MsaInfo(
            hasMedicalSavingsAccount = hasMsa,
            msaPercentage = msaPercentage
        )
    }

    private fun extractContributionsFromPdf(pdfPath: String, plan: Plan): List<Contribution> {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val contributions = mutableListOf<Contribution>()
        val lines = text.lines()

        for (line in lines) {
            val principalMatch = extractContributionAmount(line, listOf("principal", "main member"))
            if (principalMatch != null && contributions.none { it.memberType == MemberType.PRINCIPAL }) {
                contributions.add(Contribution(
                    plan = plan,
                    memberType = MemberType.PRINCIPAL,
                    monthlyAmount = principalMatch,
                    conditions = "Extracted from PDF"
                ))
            }

            val spouseMatch = extractContributionAmount(line, listOf("spouse", "partner", "adult dependent"))
            if (spouseMatch != null && contributions.none { it.memberType == MemberType.SPOUSE }) {
                contributions.add(Contribution(
                    plan = plan,
                    memberType = MemberType.SPOUSE,
                    monthlyAmount = spouseMatch,
                    conditions = "Extracted from PDF"
                ))
            }

            val childMatch = extractContributionAmount(line, listOf("child", "children"))
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

    private fun extractHospitalBenefitsFromPdf(pdfPath: String, plan: Plan): List<HospitalBenefit> {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val benefits = mutableListOf<HospitalBenefit>()
        val lines = text.lines()

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

    private fun extractCopaymentsFromPdf(pdfPath: String, plan: Plan): Map<String, Double> {
        val file = File(pdfPath)
        val pdfDoc = Loader.loadPDF(file)
        val text = pdfDoc.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }

        val copayments = mutableMapOf<String, Double>()
        val lines = text.lines()

        val copaymentPatterns = mapOf(
            "gp_consultation" to listOf("gp consultation", "general practitioner", "doctor visit"),
            "specialist_consultation" to listOf("specialist consultation", "specialist visit"),
            "acute_medicine" to listOf("acute medicine", "prescribed medicine"),
            "radiology" to listOf("radiology", "x-ray", "scan"),
            "pathology" to listOf("pathology", "blood test", "lab test"),
            "dental" to listOf("dental consultation", "dentist"),
            "optical" to listOf("optical", "eye test", "optometrist")
        )

        for (line in lines) {
            val lowerLine = line.lowercase()

            for ((copaymentType, keywords) in copaymentPatterns) {
                if (keywords.any { lowerLine.contains(it) } && lowerLine.contains("copayment")) {
                    val amount = extractCopaymentAmount(line)
                    if (amount != null && !copayments.containsKey(copaymentType)) {
                        copayments[copaymentType] = amount
                    }
                }
            }
        }

        log.info("Extracted ${copayments.size} copayments from PDF: $pdfPath")
        return copayments
    }

    fun extractContributionAmount(line: String, keywords: List<String>): Double? {
        val lowerLine = line.lowercase()
        if (keywords.none { lowerLine.contains(it) }) return null

        val amountPattern = Pattern.compile("R\\s*(\\d{3,5}(?:[\\s,.]\\d{3})*(?:\\.\\d{2})?)")
        val matcher = amountPattern.matcher(line)

        while (matcher.find()) {
            try {
                val amountStr = matcher.group(1).replace("[\\s,]".toRegex(), "")
                val amount = BigDecimal(amountStr).setScale(2, RoundingMode.HALF_UP).toDouble()

                if (amount in MIN_VALID_CONTRIBUTION..MAX_VALID_CONTRIBUTION) {
                    return amount
                }
            } catch (e: Exception) {
                log.debug("Failed to parse amount from: ${matcher.group(1)}")
            }
        }

        return null
    }

    private fun extractCopaymentAmount(line: String): Double? {
        val lowerLine = line.lowercase()
        if (!lowerLine.contains("copayment") && !lowerLine.contains("co-payment")) return null

        val amountPattern = Pattern.compile("R\\s*(\\d{1,4}(?:[\\s,.]\\d{3})*(?:\\.\\d{2})?)")
        val matcher = amountPattern.matcher(line)

        if (matcher.find()) {
            return try {
                val amountStr = matcher.group(1).replace("[\\s,]".toRegex(), "")
                BigDecimal(amountStr).setScale(2, RoundingMode.HALF_UP).toDouble()
            } catch (e: Exception) {
                log.debug("Failed to parse copayment amount: $line")
                null
            }
        }

        return null
    }

    private fun extractLimitAmount(line: String): LimitInfo? {
        val limitPattern = Pattern.compile("R\\s*(\\d{1,3}(?:[\\s,.]\\d{3})+)\\s*(per\\s+(family|person|p[/a]))?")
        val matcher = limitPattern.matcher(line)

        if (matcher.find()) {
            return try {
                val amountStr = matcher.group(1).replace("[\\s,]".toRegex(), "")
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

    private fun extractMsaPercentage(text: String): Double? {
        val patterns = listOf(
            "(\\d+)%?\\s*(?:of|from)\\s*(?:contribution|premium)".toRegex(),
            "(?:msa|savings)\\s*(?:is)?\\s*(\\d+)%".toRegex(),
            "(\\d+)%\\s*(?:msa|medical savings|savings account)".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return try {
                    match.groupValues[1].toDouble()
                } catch (e: Exception) {
                    null
                }
            }
        }

        return when {
            text.contains("25%") -> 25.0
            text.contains("20%") -> 20.0
            text.contains("15%") -> 15.0
            else -> null
        }
    }

    private fun extractBenefitName(line: String): String {
        return line
            .replace("R\\s*\\d+[\\s,.\\d]*\\d*".toRegex(), "")
            .replace("per\\s+(family|person|p[/a])".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .takeIf { it.isNotEmpty() } ?: "Benefit"
    }

    private fun buildHospitalBenefitsSummary(benefits: List<HospitalBenefit>): String {
        return benefits.joinToString("\n") { benefit ->
            "${benefit.category.name}: ${benefit.limitPerPerson ?: benefit.limitPerFamily ?: "No limit specified"}"
        }.take(4000)
    }
}

data class ContributionParseResult(
    val success: Boolean,
    val planId: UUID,
    val contributionsExtracted: Int,
    val contributions: List<Contribution> = emptyList()
)

data class HospitalBenefitParseResult(
    val success: Boolean,
    val planId: UUID,
    val benefitsExtracted: Int,
    val benefits: List<HospitalBenefit> = emptyList()
)

data class CopaymentParseResult(
    val success: Boolean,
    val planId: UUID,
    val copaymentsExtracted: Int,
    val copayments: Map<String, Double> = emptyMap()
)

data class MsaInfo(
    val hasMedicalSavingsAccount: Boolean,
    val msaPercentage: Double? = null
)

data class LimitInfo(
    val familyLimit: String?,
    val personLimit: String?,
    val annualLimit: String?
)