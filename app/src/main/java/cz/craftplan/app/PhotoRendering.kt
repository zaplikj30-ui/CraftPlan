package cz.craftplan.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

/** Maps raw JPEG axes into the canonical top-left photo coordinates. */
internal data class PhotoAxes(val xx: Int, val xy: Int, val yx: Int, val yy: Int) {
    val swapsAxes get() = xx == 0
}

internal fun photoAxes(orientation: Int): PhotoAxes = when (orientation) {
    1 -> PhotoAxes(1, 0, 0, 1)
    2 -> PhotoAxes(-1, 0, 0, 1)
    3 -> PhotoAxes(-1, 0, 0, -1)
    4 -> PhotoAxes(1, 0, 0, -1)
    5 -> PhotoAxes(0, 1, 1, 0)
    6 -> PhotoAxes(0, -1, 1, 0)
    7 -> PhotoAxes(0, -1, -1, 0)
    8 -> PhotoAxes(0, 1, -1, 0)
    else -> throw IllegalArgumentException("Unsupported EXIF orientation")
}

// Android ExifInterface supplies "0" for a missing tag.
internal fun sourceOrientation(tag: String?): Int? =
    tag?.let { requireNotNull(it.toIntOrNull()).takeUnless { value -> value == 0 }?.also(::photoAxes) }

internal fun inspectImportedJpeg(candidate: ImportCandidate, bytes: ByteArray): StoredPhoto {
    val bounds = jpegBounds(bytes)
    val exif = ExifInterface(ByteArrayInputStream(bytes))
    val orientation = sourceOrientation(exif.getAttribute(ExifInterface.TAG_ORIENTATION))
    return StoredPhoto(candidate.photoId, UUID.nameUUIDFromBytes("craftplan-photo:${candidate.photoId.name}".toByteArray(Charsets.US_ASCII)),
        candidate.digest, bytes.size, bounds.outWidth, bounds.outHeight, orientation, 1, orientation ?: 1, "full", "image/jpeg")
}

internal fun decodePhoto(bytes: ByteArray, photo: StoredPhoto): Bitmap {
    val bounds = jpegBounds(bytes)
    require(bounds.outWidth == photo.width && bounds.outHeight == photo.height)
    var sample = 1
    // Limit the reconstructed bitmap only. Original JPEG bytes remain unchanged.
    val longEdge = maxOf(photo.width, photo.height)
    while ((longEdge.toLong() + sample - 1) / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IOException("JPEG cannot be decoded")
    if (photo.storedOrientation == 1) return raw
    val axes = photoAxes(photo.storedOrientation)
    val matrix = Matrix().apply {
        setValues(floatArrayOf(
            axes.xx.toFloat(), axes.xy.toFloat(), 0f,
            axes.yx.toFloat(), axes.yy.toFloat(), 0f,
            0f, 0f, 1f,
        ))
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false).also {
        if (it !== raw) raw.recycle()
    }
}

private fun jpegBounds(bytes: ByteArray): BitmapFactory.Options {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outMimeType == "image/jpeg" && bounds.outWidth > 0 && bounds.outHeight > 0 &&
        bounds.outWidth.toLong() * bounds.outHeight <= MAX_PHOTO_PIXELS)
    return bounds
}
