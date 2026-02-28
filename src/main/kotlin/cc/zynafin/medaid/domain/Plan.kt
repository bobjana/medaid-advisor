package cc.zynafin.medaid.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "plans")
class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Plan) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Plan(id=$id, scheme=$scheme, planName=$planName, planYear=$planYear, planType=$planType)"
    }
}

enum class PlanType {
    NETWORK,
    COMPREHENSIVE,
    SAVINGS,
    HOSPITAL,
    CAP
}
