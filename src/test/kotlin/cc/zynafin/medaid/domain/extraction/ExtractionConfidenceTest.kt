package cc.zynafin.medaid.domain.extraction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull

class ExtractionConfidenceTest {

    @Test
    fun shouldReturnHighForScores090AndAbove() {
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(0.90))
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(0.95))
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(1.0))
    }

    @Test
    fun shouldReturnMediumForScores075To089() {
        assertEquals(ExtractionConfidence.MEDIUM, ExtractionConfidence.fromScore(0.75))
        assertEquals(ExtractionConfidence.MEDIUM, ExtractionConfidence.fromScore(0.80))
        assertEquals(ExtractionConfidence.MEDIUM, ExtractionConfidence.fromScore(0.89))
        assertEquals(ExtractionConfidence.MEDIUM, ExtractionConfidence.fromScore(0.849))
    }

    @Test
    fun shouldReturnLowForScores060To074() {
        assertEquals(ExtractionConfidence.LOW, ExtractionConfidence.fromScore(0.60))
        assertEquals(ExtractionConfidence.LOW, ExtractionConfidence.fromScore(0.65))
        assertEquals(ExtractionConfidence.LOW, ExtractionConfidence.fromScore(0.74))
        assertEquals(ExtractionConfidence.LOW, ExtractionConfidence.fromScore(0.699))
    }

    @Test
    fun shouldReturnManualReviewForScoresBelow060() {
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(0.59))
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(0.50))
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(0.0))
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(-0.1))
    }

    @Test
    fun shouldHandleBoundaryCasesCorrectly() {
        // Exact boundaries
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(0.90))
        assertEquals(ExtractionConfidence.MEDIUM, ExtractionConfidence.fromScore(0.75))
        assertEquals(ExtractionConfidence.LOW, ExtractionConfidence.fromScore(0.60))
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(0.599))
    }

    @Test
    fun shouldHandleEdgeCases() {
        // Negative values
        assertEquals(ExtractionConfidence.MANUAL_REVIEW, ExtractionConfidence.fromScore(-1.0))
        // Values above 1.0
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(2.0))
        assertEquals(ExtractionConfidence.HIGH, ExtractionConfidence.fromScore(10.0))
    }
}
