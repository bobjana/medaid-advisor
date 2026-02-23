package cc.zynafin.medaid.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanTest {

    @Test
    fun `should create plan with required fields`() {
        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Saver Plan",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4500.0
        )

        assertEquals("Discovery Health", plan.scheme)
        assertEquals("Saver Plan", plan.planName)
        assertEquals(2026, plan.planYear)
        assertEquals(PlanType.SAVINGS, plan.planType)
        assertEquals(4500.0, plan.principalContribution)
        assertFalse(plan.hasMedicalSavingsAccount)
    }

    @Test
    fun `should create plan with all fields`() {
        val benefits = mapOf(
            "hospital" to "Delta network at 100%",
            "chronic" to "CDL full, non-CDL from MSA"
        )

        val copayments = mapOf(
            "non_network_hospital" to 15025.0,
            "colonoscopy" to 2000.0
        )

        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Saver Plan",
            planYear = 2026,
            planType = PlanType.SAVINGS,
            principalContribution = 4500.0,
            adultDependentContribution = 3200.0,
            childDependentContribution = 1500.0,
            benefits = benefits,
            copayments = copayments,
            hospitalBenefits = "Comprehensive in-hospital cover through Delta network",
            chronicBenefits = "Full CDL cover",
            hasMedicalSavingsAccount = true,
            msaPercentage = 0.25,
            sourceDocument = "discovery_saver_2026.pdf"
        )

        assertEquals("Discovery Health", plan.scheme)
        assertEquals("Saver Plan", plan.planName)
        assertEquals(3200.0, plan.adultDependentContribution)
        assertEquals(1500.0, plan.childDependentContribution)
        assertEquals(2, plan.benefits.size)
        assertEquals(2, plan.copayments.size)
        assertTrue(plan.hasMedicalSavingsAccount)
        assertEquals(0.25, plan.msaPercentage)
        assertEquals("discovery_saver_2026.pdf", plan.sourceDocument)
    }

    @Test
    fun `should handle comprehensive plan`() {
        val plan = Plan(
            scheme = "Discovery Health",
            planName = "Comprehensive Plan",
            planYear = 2026,
            planType = PlanType.COMPREHENSIVE,
            principalContribution = 6800.0,
            hospitalBenefits = "Full in-hospital cover at 200%",
            chronicBenefits = "Full CDL and non-CDL chronic cover"
        )

        assertEquals(PlanType.COMPREHENSIVE, plan.planType)
        assertEquals(6800.0, plan.principalContribution)
        assertFalse(plan.hasMedicalSavingsAccount)
    }

    @Test
    fun `should handle network plan`() {
        val plan = Plan(
            scheme = "Discovery Health",
            planName = "KeyCare Plan",
            planYear = 2026,
            planType = PlanType.NETWORK,
            principalContribution = 2800.0,
            hospitalBenefits = "State hospital network cover"
        )

        assertEquals(PlanType.NETWORK, plan.planType)
        assertEquals(2800.0, plan.principalContribution)
        assertFalse(plan.hasMedicalSavingsAccount)
    }
}
