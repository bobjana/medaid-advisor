package cc.zynafin.medaid.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.VectorStore
import org.mockito.kotlin.mock

class RagServiceTest {

    private val vectorStore: VectorStore = mock()
    private val ragService = RagService(vectorStore, 800, 100)

    @Test
    fun `detectTable returns true for pipe-separated table with amounts`() {
        val tableText = """
            Benefit Name|Limit|Per|Cover
            Hospital|R1,000,000|Family|Yes
            Chronic|R50,000|Person|Yes
            Day-to-day|R5,000|Person|No
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableText)
        assertTrue(result, "Should detect table with pipe separators and amounts")
    }

    @Test
    fun `detectTable returns true for contribution table format`() {
        val tableText = """
            Contributions Network Principal|R 2 269 Adult|R1 764 Child|R956
            Network Plus|R2 523 Adult|R1 959 Child|R1 061
            Standard Network|R3 456 Adult|R2 789 Child|R1 234
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableText)
        assertTrue(result, "Should detect contribution table with SA Rand format")
    }

    @Test
    fun `detectTable returns true for table with inconsistent spacing`() {
        val tableText = """
            Plan|Principal|Adult|Child
            Beat 1|R 2 269|R1 764|R956
            Beat 2|R 3 500|R 2 800|R1 200
            Beat 3|R 4 000|R 3 200|R1 500
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableText)
        assertTrue(result, "Should detect table with varying spacing in amounts")
    }

    @Test
    fun `detectTable returns false for prose without pipes`() {
        val proseText = """
            This is a regular paragraph of text that describes
            medical aid benefits. It contains no table structure
            and should not be detected as a table.
        """.trimIndent()

        val result = invokeDetectTable(ragService, proseText)
        assertFalse(result, "Should not detect prose as table")
    }

    @Test
    fun `detectTable returns false for single pipe line`() {
        val singlePipeText = """
            This text has a single | character which
            is likely a formatting artifact and not
            a table structure.
        """.trimIndent()

        val result = invokeDetectTable(ragService, singlePipeText)
        assertFalse(result, "Should not detect single pipe as table")
    }

    @Test
    fun `detectTable returns false for table without amounts`() {
        val tableWithoutAmounts = """
            Column A|Column B|Column C
            Value 1|Value 2|Value 3
            Value 4|Value 5|Value 6
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableWithoutAmounts)
        assertFalse(result, "Should not detect table without Rand amounts")
    }

    @Test
    fun `detectTable returns false for insufficient lines`() {
        val shortText = """
            Plan|Amount
            Test|R500
        """.trimIndent()

        val result = invokeDetectTable(ragService, shortText)
        assertFalse(result, "Should not detect table with fewer than 3 lines")
    }

    @Test
    fun `detectTable returns false for empty content`() {
        val result = invokeDetectTable(ragService, "")
        assertFalse(result, "Should not detect empty content as table")
    }

    @Test
    fun `detectTable returns false for inconsistent column counts`() {
        val inconsistentText = """
            Column A|Column B|Column C
            Value 1|Value 2
            Value 3|Value 4|Value 5|Value 6
            Value 7|Value 8|Value 9
        """.trimIndent()

        val result = invokeDetectTable(ragService, inconsistentText)
        assertFalse(result, "Should not detect table with inconsistent column counts")
    }

    @Test
    fun `detectTable returns true for table with amounts in different formats`() {
        val tableText = """
            Benefit|Limit|Status
            Hospital|R1,000,000|Covered
            Chronic|R50000|Partial
            Day-to-day|R 5 000|Limited
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableText)
        assertTrue(result, "Should detect table with various amount formats")
    }

    @Test
    fun `detectTable handles whitespace-only lines`() {
        val tableText = """
            Plan|Principal|Adult|Child

            Beat 1|R 2 269|R1 764|R956

            Beat 2|R 3 500|R 2 800|R1 200
        """.trimIndent()

        val result = invokeDetectTable(ragService, tableText)
        assertTrue(result, "Should handle whitespace-only lines correctly")
    }

    /**
     * Uses reflection to invoke the private detectTable method.
     */
    private fun invokeDetectTable(service: RagService, text: String): Boolean {
        val method = RagService::class.java.getDeclaredMethod("detectTable", String::class.java)
        method.isAccessible = true
        return method.invoke(service, text) as Boolean
    }

    /**
     * Uses reflection to invoke the private convertTableToProse method.
     */
    private fun invokeConvertTableToProse(service: RagService, text: String): String {
        val method = RagService::class.java.getDeclaredMethod("convertTableToProse", String::class.java)
        method.isAccessible = true
        return method.invoke(service, text) as String
    }

    @Test
    fun `convertTableToProse formats contribution table correctly`() {
        val tableText = """
            Contributions Network Principal|R 2 269 Adult|R1 764 Child|R956
            Network Plus|R2 523 Adult|R1 959 Child|R1 061
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.startsWith("Contributions:"), "Should start with 'Contributions:'")
        assertTrue(result.contains("R2,269"), "Should format Principal amount with commas")
        assertTrue(result.contains("R1,764"), "Should format Adult amount with commas")
        assertTrue(result.contains("R956"), "Should format Child amount correctly")
        assertTrue(result.contains("per month"), "Should include 'per month'")
    }

    @Test
    fun `convertTableToProse formats benefit table correctly`() {
        val tableText = """
            Benefit Name|Limit|Per|Cover
            Hospital|R1,000,000|Family|Yes
            Chronic|R50,000|Person|Yes
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.startsWith("Benefits:"), "Should start with 'Benefits:'")
        assertTrue(result.contains("Hospital"), "Should include benefit name")
        assertTrue(result.contains("R1,000,000"), "Should preserve formatted limit")
        assertTrue(result.contains("per family"), "Should include 'per family'")
        assertTrue(result.contains("covered"), "Should indicate coverage status")
    }

    @Test
    fun `convertTableToProse formats copayment table correctly`() {
        val tableText = """
            Copayment Type|Amount
            Specialist|R500
            Medication|R200
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.startsWith("Copayments:"), "Should start with 'Copayments:'")
        assertTrue(result.contains("Specialist"), "Should include service type")
        assertTrue(result.contains("R500"), "Should include amount")
    }

    @Test
    fun `convertTableToProse handles SA Rand format with spaces`() {
        val tableText = """
            Contributions|Principal|Adult|Child
            Beat 1|R 2 269|R 1 764|R 956
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.contains("R2,269"), "Should convert 'R 2 269' to 'R2,269'")
        assertTrue(result.contains("R1,764"), "Should convert 'R 1 764' to 'R1,764'")
    }

    @Test
    fun `convertTableToProse handles generic table without keywords`() {
        val tableText = """
            Column A|Column B|Column C
            Value 1|Value 2|Value 3
            Value 4|Value 5|Value 6
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.contains("Column A - Column B - Column C"), "Should format as generic table")
        assertTrue(result.contains(";"), "Should separate rows with semicolons")
    }

    @Test
    fun `convertTableToProse handles empty content`() {
        val result = invokeConvertTableToProse(ragService, "")
        assertEquals("", result, "Should return empty string for empty input")
    }

    @Test
    fun `convertTableToProse handles benefit table with no coverage`() {
        val tableText = """
            Benefit|Limit|Per|Cover
            Day-to-day|R5,000|Person|No
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.contains("not covered"), "Should indicate 'not covered' for No status")
    }

    @Test
    fun `convertTableToProse handles multiple contribution plans`() {
        val tableText = """
            Contributions|Principal|Adult|Child
            Beat 1|R 2 269|R1 764|R956
            Beat 2|R 3 500|R 2 800|R1 200
        """.trimIndent()

        val result = invokeConvertTableToProse(ragService, tableText)

        assertTrue(result.contains("Beat 1:"), "Should include first plan name: $result")
        assertTrue(result.contains("Beat 2:"), "Should include second plan name: $result")
        assertTrue(result.contains(";"), "Should separate plans with semicolons: $result")
    }
}