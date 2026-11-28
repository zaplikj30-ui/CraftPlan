package cz.craftplan.app

/** A permission result can authorize only the exact action the user requested. */
internal data class SystemCameraRequest(val expectedRetake: ImportCandidate?) {
    fun matches(
        cameraPreviewId: PhotoId?,
        importCandidate: ImportCandidate?,
        reviewCandidate: ImportCandidate?,
        ordinaryReview: Boolean,
        retryingConfirmation: Boolean,
    ): Boolean {
        if (retryingConfirmation) return false
        val expected = expectedRetake
        return if (expected == null) {
            cameraPreviewId == null && importCandidate == null && reviewCandidate == null
        } else {
            ordinaryReview && cameraPreviewId == expected.photoId && reviewCandidate == expected &&
                (importCandidate == null || importCandidate == expected)
        }
    }
}
