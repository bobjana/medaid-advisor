package cc.zynafin.medaid.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PdfTableExtractor]'s table type classification logic.
 *
 * [PdfTableExtractor.detectTableType] is private — accessed via reflection so that
 * we can unit-test the classification rules without spinning up a full PDF extraction
 * pipeline or a Spring application context.
 */
class PdfTableExtractorTest {
    private val extractor = PdfTableExtractor()

    /** Reflectively invokes the private `detectTableType(headers, rows)` method. */
    private fun detectTableType(
        headers: List<String>,
        rows: List<List<String>>,
    ): TableType {
        val method =
            PdfTableExtractor::class.java.getDeclaredMethod(
                "detectTableType",
                List::class.java,
                List::class.java,
            )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        return method.invoke(extractor, headers, rows) as TableType
    }

    @Test
    fun `detectTableType contributionKeywords returnsContribution`() {
        val headers = listOf("Member Type", "Monthly Premium")
        val rows =
            listOf(
                listOf("Principal", "R2,269"),
                listOf("Adult", "R1,764"),
            )
        assertEquals(TableType.CONTRIBUTION, detectTableType(headers, rows))
    }

    @Test
    fun `detectTableType benefitKeywords returnsBenefit`() {
        val headers = listOf("Benefit", "Limit")
        val rows = listOf(listOf("Hospital Cover", "Unlimited"))
        assertEquals(TableType.BENEFIT, detectTableType(headers, rows))
    }

    @Test
    fun `detectTableType fiveOrMoreColumns returnsComparison`() {
        val headers = listOf("Plan", "Option 1", "Option 2", "Option 3", "Option 4")
        val rows = listOf(listOf("Hospital", "Yes", "Yes", "No", "Yes"))
        assertEquals(TableType.COMPARISON, detectTableType(headers, rows))
    }

    @Test
    fun `detectTableType unknownContent returnsUnknown`() {
        val headers = listOf("Foo", "Bar")
        val rows = listOf(listOf("Baz", "Qux"))
        assertEquals(TableType.UNKNOWN, detectTableType(headers, rows))
    }
}
