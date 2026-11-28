package cz.craftplan.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCameraRequestTest {
    private val first = ImportCandidate(PhotoId("11111111-1111-4111-8111-111111111111"), "a".repeat(64))
    private val second = ImportCandidate(PhotoId("22222222-2222-4222-8222-222222222222"), "b".repeat(64))

    @Test
    fun newCaptureCannotRetakeOrReplaceAnExistingReview() {
        val request = SystemCameraRequest(null)
        assertTrue(request.matches(null, null, null, false, false))
        assertFalse(request.matches(first.photoId, null, first, true, false))
        assertFalse(request.matches(null, first, first, false, false))
        assertFalse(request.matches(null, null, first, false, false))
        assertFalse(request.matches(null, null, null, false, true))
    }

    @Test
    fun retakePermissionRequiresExactCandidateAndStillSafeReview() {
        val request = SystemCameraRequest(first)
        assertTrue(request.matches(first.photoId, first, first, true, false))
        assertTrue(request.matches(first.photoId, null, first, true, false))
        assertFalse(request.matches(null, null, null, false, false))
        assertFalse(request.matches(second.photoId, second, second, true, false))
        assertFalse(request.matches(first.photoId, second, first, true, false))
        assertFalse(request.matches(first.photoId, first, first.copy(digest = "c".repeat(64)), true, false))
        assertFalse(request.matches(first.photoId, first, first, false, false))
        assertFalse(request.matches(first.photoId, first, first, true, true))
    }
}
