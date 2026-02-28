package cc.zynafin.medaid.domain

import jakarta.persistence.*
import java.util.UUID

/**
 * Member type for contribution tables
 */
enum class MemberType {
    PRINCIPAL,
    SPOUSE,
    CHILD_FIRST,
    CHILD_SECOND,
    CHILD_THIRD,
    CHILD_FOURTH,
    CHILD_FIFTH_OR_MORE
}

/**
 * Benefit category for hospital benefit limits
 */
enum class BenefitCategory {
    HOSPITAL_COVER,
    CHRONIC_MEDICINE,
    SPECIALIST_CONSULTATION,
    EMERGENCY_SERVICE,
    MATERNITY,
    DENTAL,
    OPTICAL,
    PRESCRIBED_MINIMUM_BENEFITS
}

/**
 * Detailed contribution data by member type
 */
@Entity
@Table(name = "contributions")
data class Contribution(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    val plan: Plan,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val memberType: MemberType,

    @Column(nullable = false)
    val monthlyAmount: Double,

    @Column(length = 50)
    val ageBracket: String? = null,

    @Column(length = 2000)
    val conditions: String? = null
)

/**
 * Hospital benefit limits and coverage details
 */
@Entity
@Table(name = "hospital_benefits")
data class HospitalBenefit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    val plan: Plan,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: BenefitCategory,

    @Column(nullable = false, length = 500)
    val benefitName: String,

    @Column(length = 500)
    val limitPerFamily: String? = null,

    @Column(length = 500)
    val limitPerPerson: String? = null,

    @Column(length = 500)
    val annualLimit: String? = null,

    @Column(nullable = false)
    val covered: Boolean,

    @Column(length = 2000)
    val notes: String? = null,

    @Column(length = 2000)
    val conditions: String? = null
)
