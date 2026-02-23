package cc.zynafin.medaid.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmployeeProfileTest {

    @Test
    fun `should create employee profile with default values`() {
        val profile = EmployeeProfile(
            age = 32,
            dependents = 1,
            riskTolerance = RiskTolerance.MEDIUM
        )

        assertEquals(32, profile.age)
        assertEquals(1, profile.dependents)
        assertEquals(RiskTolerance.MEDIUM, profile.riskTolerance)
        assertTrue(profile.chronicConditions.isEmpty())
        assertTrue(profile.plannedProcedures.isEmpty())
        assertFalse(profile.planningPregnancy)
    }

    @Test
    fun `should create employee profile with chronic conditions`() {
        val profile = EmployeeProfile(
            age = 45,
            dependents = 2,
            chronicConditions = listOf("Type 2 Diabetes", "Hypertension"),
            maxMonthlyBudget = 5000.0
        )

        assertEquals(45, profile.age)
        assertEquals(2, profile.dependents)
        assertEquals(2, profile.chronicConditions.size)
        assertTrue(profile.chronicConditions.contains("Type 2 Diabetes"))
        assertTrue(profile.chronicConditions.contains("Hypertension"))
        assertEquals(5000.0, profile.maxMonthlyBudget)
    }

    @Test
    fun `should create employee profile for planning pregnancy`() {
        val profile = EmployeeProfile(
            age = 28,
            dependents = 0,
            planningPregnancy = true,
            maxMonthlyBudget = 6000.0,
            riskTolerance = RiskTolerance.LOW
        )

        assertEquals(28, profile.age)
        assertTrue(profile.planningPregnancy)
        assertEquals(6000.0, profile.maxMonthlyBudget)
        assertEquals(RiskTolerance.LOW, profile.riskTolerance)
    }

    @Test
    fun `should create employee profile with preferred providers`() {
        val profile = EmployeeProfile(
            age = 35,
            dependents = 1,
            preferredProviders = mapOf(
                "hospital" to "Netcare Parklane",
                "gp" to "Dr. Smith's Practice"
            )
        )

        assertEquals(35, profile.age)
        assertEquals(1, profile.dependents)
        assertEquals("Netcare Parklane", profile.preferredProviders["hospital"])
        assertEquals("Dr. Smith's Practice", profile.preferredProviders["gp"])
    }
}
