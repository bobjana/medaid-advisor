package cc.zynafin.medaid.repository

import cc.zynafin.medaid.domain.Contribution
import cc.zynafin.medaid.domain.HospitalBenefit
import cc.zynafin.medaid.domain.MemberType
import cc.zynafin.medaid.domain.BenefitCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ContributionRepository : JpaRepository<Contribution, UUID> {

    fun findByPlanId(planId: UUID): List<Contribution>

    fun findByPlanIdAndMemberType(planId: UUID, memberType: MemberType): Contribution?

    @Query("""
        SELECT c FROM Contribution c
        WHERE c.plan.id = :planId
        ORDER BY c.memberType
    """)
    fun findByPlanIdOrderByMemberType(@Param("planId") planId: UUID): List<Contribution>
}

@Repository
interface HospitalBenefitRepository : JpaRepository<HospitalBenefit, UUID> {

    fun findByPlanId(planId: UUID): List<HospitalBenefit>

    fun findByPlanIdAndCategory(planId: UUID, category: BenefitCategory): List<HospitalBenefit>

    @Query("""
        SELECT hb FROM HospitalBenefit hb
        WHERE hb.plan.id = :planId
        AND hb.covered = true
        ORDER BY hb.category
    """)
    fun findCoveredBenefitsByPlanId(@Param("planId") planId: UUID): List<HospitalBenefit>
}
