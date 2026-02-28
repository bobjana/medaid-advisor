package cc.zynafin.medaid.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
@Entity
@Table(name = "employee_profiles")
data class EmployeeProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    @field:Min(value = 18, message = "Age must be at least 18")
    @field:Max(value = 100, message = "Age must be at most 100")
    val age: Int,

    @field:Min(value = 0, message = "Dependents cannot be negative")
    @field:Max(value = 20, message = "Dependents cannot exceed 20")
    val dependents: Int = 0,

    @ElementCollection
    @CollectionTable(name = "chronic_conditions", joinColumns = [JoinColumn(name = "profile_id")])
    @Column(name = "condition_type")
    val chronicConditions: List<String> = listOf(),

    val plannedProcedures: List<String> = listOf(),

    val planningPregnancy: Boolean = false,

    @field:Positive(message = "Monthly budget must be positive")
    val maxMonthlyBudget: Double? = null,

    @field:Positive(message = "Annual budget must be positive")
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
