package cc.zynafin.medaid.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "plans")
data class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val scheme: String,

    @Column(nullable = false)
    val planName: String,

    @Column(nullable = false)
    val planYear: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val planType: PlanType,

    @Column(nullable = false)
    val principalContribution: Double,

    val adultDependentContribution: Double? = null,
    val childDependentContribution: Double? = null,

    @ElementCollection
    @CollectionTable(name = "plan_benefits", joinColumns = [JoinColumn(name = "plan_id")])
    @MapKeyColumn(name = "benefit_key")
    @Column(name = "benefit_value")
    val benefits: Map<String, String> = mapOf(),

    @ElementCollection
    @CollectionTable(name = "plan_copayments", joinColumns = [JoinColumn(name = "plan_id")])
    @MapKeyColumn(name = "copayment_key")
    @Column(name = "copayment_value")
    val copayments: Map<String, Double> = mapOf(),

    @Column(length = 4000)
    val hospitalBenefits: String? = null,

    @Column(length = 4000)
    val chronicBenefits: String? = null,

    @Column(length = 4000)
    val dayToDayBenefits: String? = null,

    val hasMedicalSavingsAccount: Boolean = false,
    val msaPercentage: Double? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDate = LocalDate.now(),

    @Column(length = 2000)
    val sourceDocument: String? = null
)

enum class PlanType {
    NETWORK,
    COMPREHENSIVE,
    SAVINGS,
    HOSPITAL,
    CAP
}
