package cc.zynafin.medaid.domain.extraction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class ExtractionStatusTest {

    @Test
    fun shouldAllowPendingToExtracting() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING, ExtractionStatus.EXTRACTING))
    }

    @Test
    fun shouldAllowExtractingToValidated() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.EXTRACTING, ExtractionStatus.VALIDATED))
    }

    @Test
    fun shouldAllowExtractingToFailed() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.EXTRACTING, ExtractionStatus.FAILED))
    }

    @Test
    fun shouldAllowExtractingToPendingReview() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.EXTRACTING, ExtractionStatus.PENDING_REVIEW))
    }

    @Test
    fun shouldAllowPendingReviewToValidated() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING_REVIEW, ExtractionStatus.VALIDATED))
    }

    @Test
    fun shouldAllowFailedToExtracting() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.FAILED, ExtractionStatus.EXTRACTING))
    }

    @Test
    fun shouldAllowFailedToPending() {
        assertTrue(ExtractionStatus.canTransitionFrom(ExtractionStatus.FAILED, ExtractionStatus.PENDING))
    }

    @Test
    fun shouldNotAllowPendingToValidated() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING, ExtractionStatus.VALIDATED))
    }

    @Test
    fun shouldNotAllowPendingToFailed() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING, ExtractionStatus.FAILED))
    }

    @Test
    fun shouldNotAllowPendingToPendingReview() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING, ExtractionStatus.PENDING_REVIEW))
    }

    @Test
    fun shouldNotAllowExtractingToExtracting() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.EXTRACTING, ExtractionStatus.EXTRACTING))
    }

    @Test
    fun shouldNotAllowValidatedToExtracting() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.VALIDATED, ExtractionStatus.EXTRACTING))
    }

    @Test
    fun shouldNotAllowValidatedToPendingReview() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.VALIDATED, ExtractionStatus.PENDING_REVIEW))
    }

    @Test
    fun shouldNotAllowFailedToValidated() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.FAILED, ExtractionStatus.VALIDATED))
    }

    @Test
    fun shouldNotAllowFailedToPendingReview() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.FAILED, ExtractionStatus.PENDING_REVIEW))
    }

    @Test
    fun shouldHandleSameStateTransition() {
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.PENDING, ExtractionStatus.PENDING))
        assertFalse(ExtractionStatus.canTransitionFrom(ExtractionStatus.EXTRACTING, ExtractionStatus.EXTRACTING))
    }
}
