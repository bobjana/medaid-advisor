package cc.zynafin.medaid.domain

import jakarta.persistence.*

@Entity
@Table(name = "employee_profiles")
data class EmployeeProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val age: Int,

    val dependents: Int = 0,

    @ElementCollection
    @CollectionTable(name = "chronic_conditions", joinColumns = [JoinColumn(name = "profile_id")])
    @Column(name = "condition_type")
    val chronicConditions: List<String> = listOf(),

    val plannedProcedures: List<String> = listOf(),

    val planningPregnancy: Boolean = false,

    val maxMonthlyBudget: Double? = null,
    val maxAnnualBudget: Double? = null,

    @Enumerated(EnumType.STRING)
    val riskTolerance: RiskTolerance = RiskTolerance.MEDIUM,

    @ElementCollection
    @CollectionTable(name = "preferred_providers", joinColumns = [JoinColumn(name = "profile_id")])
    @MapKeyColumn(name = "provider_type")
    @Column(name = "provider_name")
    val preferredProviders: Map<String, String> = mapOf()
)

enum class RiskTolerance {
    LOW,
    MEDIUM,
    HIGH
}
