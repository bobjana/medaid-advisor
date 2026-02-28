package cc.zynafin.medaid.repository

import cc.zynafin.medaid.domain.Plan
import cc.zynafin.medaid.domain.PlanType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlanRepository : JpaRepository<Plan, String> {

    fun findBySchemeAndPlanYear(scheme: String, planYear: Int): List<Plan>

    fun findByPlanYear(planYear: Int): List<Plan>

    fun findByPlanType(planType: PlanType): List<Plan>

    @Query("""
        SELECT p FROM Plan p
        WHERE p.principalContribution <= :maxPrincipal
        ORDER BY p.principalContribution ASC
    """)
    fun findByMaxPrincipalContribution(@Param("maxPrincipal") maxPrincipal: Double): List<Plan>

    @Query("""
        SELECT DISTINCT p.scheme FROM Plan p
        ORDER BY p.scheme
    """)
    fun findAllSchemes(): List<String>

    @Query("""
        SELECT DISTINCT p.planYear FROM Plan p
        ORDER BY p.planYear DESC
    """)
    fun findAllPlanYears(): List<Int>

    fun findBySchemeAndPlanNameAndPlanYear(scheme: String, planName: String, planYear: Int): Plan?
}
