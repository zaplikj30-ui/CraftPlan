package cz.craftplan.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID

/** Maps raw JPEG axes into the canonical top-left photo coordinates. */
private data class PhotoAxes(val xx: Int, val xy: Int, val yx: Int, val yy: Int)

private fun photoAxes(orientation: Int): PhotoAxes = when (orientation) {
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
    tag?.let {
        val value = requireNotNull(it.toIntOrNull())
        require(value in 0..8)
        value.takeUnless { it == 0 }
    }

internal enum class ImportRejection {
    CAMERA_OUTPUT_EMPTY, CAMERA_OUTPUT_CHANGED, CAMERA_SOURCE_PROTECTED, PHOTO_SIZE_LIMIT, PHOTO_PIXEL_LIMIT, STAGED_INTEGRITY, JPEG_SIGNATURE, JPEG_TRUNCATED,
    JPEG_DECODE_MALFORMED, JPEG_DECODE_FAILED, JPEG_METADATA_INVALID,
}

internal class ImportValidationFailure(val reason: ImportRejection) : IllegalArgumentException()

private data class DecodedJpeg(val bitmap: Bitmap, val partial: Boolean)

internal fun previewJpeg(candidate: ImportCandidate, bytes: ByteArray, existing: StoredPhoto?): ImportPreview {
    val photo = existing ?: inspectJpeg(candidate, bytes)
    require(photo.id == candidate.photoId && photo.sha256 == candidate.digest && photo.size == bytes.size)
    val decoded = if (photo.anchorMapVersion == 1) {
        // A resumed legacy import can still need approval for partial content.
        // Keep its catalogued orientation and use the native decode only as a probe.
        val probe = decodeJpeg(bytes, photo, minimumSample = 8)
        probe.bitmap.recycle()
        DecodedJpeg(decodeLegacyPhoto(bytes, photo), probe.partial)
    } else decodeJpeg(bytes, photo)
    return ImportPreview(candidate, photo, decoded.bitmap, decoded.partial)
}

internal fun decodeRecoveryPreview(copy: CaptureCopy): RecoveryImage<Bitmap> {
    val decoded = decodeJpeg(copy.bytes, inspectJpeg(copy.candidate, copy.bytes), maximumEdge = 768)
    return RecoveryImage(decoded.bitmap, decoded.partial)
}

private fun inspectJpeg(candidate: ImportCandidate, bytes: ByteArray): StoredPhoto {
    // Header-only probe supplies raw dimensions. ImageDecoder returns oriented dimensions.
    val bounds = jpegBounds(bytes)
    val orientation = try {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        sourceOrientation(exif.getAttribute(ExifInterface.TAG_ORIENTATION))
    } catch (_: IOException) {
        throw ImportValidationFailure(ImportRejection.JPEG_METADATA_INVALID)
    } catch (_: IllegalArgumentException) {
        throw ImportValidationFailure(ImportRejection.JPEG_METADATA_INVALID)
    }
    val mediaId = UUID.nameUUIDFromBytes("craftplan-photo:${candidate.photoId.name}".toByteArray(Charsets.US_ASCII))
    return StoredPhoto(candidate.photoId, mediaId, candidate.digest, bytes.size, bounds.outWidth, bounds.outHeight,
        orientation, 2, orientation ?: 1, "full", "image/jpeg")
}

internal fun decodePhoto(bytes: ByteArray, photo: StoredPhoto): Bitmap =
    if (photo.anchorMapVersion == 1) decodeLegacyPhoto(bytes, photo) else decodeJpeg(bytes, photo).bitmap

/** Maps extents between the raw JPEG axes and the canonical editor axes in either direction. */
internal fun photoDimensions(width: Int, height: Int, orientation: Int): Pair<Int, Int> {
    require(orientation in 1..8)
    return if (orientation >= 5) height to width else width to height
}

/** Source errors are not consent to keep a partially decoded image. */
internal fun canShowPartialJpeg(error: Int): Boolean =
    error == ImageDecoder.DecodeException.SOURCE_INCOMPLETE ||
        error == ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA

private fun decodeJpeg(
    bytes: ByteArray, photo: StoredPhoto, minimumSample: Int = 1, maximumEdge: Int? = null,
): DecodedJpeg {
    val expectedSize = photoDimensions(photo.width, photo.height, photo.storedOrientation)
    var partial = false
    val bitmap = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            if (info.mimeType != "image/jpeg") throw ImportValidationFailure(ImportRejection.JPEG_SIGNATURE)
            val width = info.size.width
            val height = info.size.height
            if (photo.anchorMapVersion == 2 && expectedSize != (width to height)) {
                throw ImportValidationFailure(ImportRejection.JPEG_METADATA_INVALID)
            }
            if (width.toLong() * height > MAX_PHOTO_PIXELS) {
                throw ImportValidationFailure(ImportRejection.PHOTO_PIXEL_LIMIT)
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            val edge = maxOf(width, height)
            if (maximumEdge != null && edge > maximumEdge) {
                fun scaled(size: Int) = (size.toLong() * maximumEdge / edge).toInt().coerceAtLeast(1)
                decoder.setTargetSize(scaled(width), scaled(height))
            } else {
                decoder.setTargetSampleSize(maxOf(minimumSample, jpegSampleSize(width, height)))
            }
            decoder.setOnPartialImageListener { error ->
                canShowPartialJpeg(error.error).also { partial = it }
            }
        }
    } catch (error: ImageDecoder.DecodeException) {
        val reason = when (error.error) {
            ImageDecoder.DecodeException.SOURCE_INCOMPLETE -> ImportRejection.JPEG_TRUNCATED
            ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA -> ImportRejection.JPEG_DECODE_MALFORMED
            else -> ImportRejection.JPEG_DECODE_FAILED
        }
        throw ImportValidationFailure(reason)
    }
    return DecodedJpeg(bitmap, partial)
}

private fun decodeLegacyPhoto(bytes: ByteArray, photo: StoredPhoto): Bitmap {
    val bounds = jpegBounds(bytes)
    require(bounds.outWidth == photo.width && bounds.outHeight == photo.height)
    val options = BitmapFactory.Options().apply { inSampleSize = jpegSampleSize(photo.width, photo.height) }
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

private fun jpegSampleSize(width: Int, height: Int): Int {
    var sample = 1
    val longEdge = maxOf(width, height).toLong()
    while ((longEdge + sample - 1) / sample > 2048) sample *= 2
    return sample
}

private fun jpegBounds(bytes: ByteArray): BitmapFactory.Options {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outMimeType != "image/jpeg" || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw ImportValidationFailure(ImportRejection.JPEG_DECODE_MALFORMED)
    }
    if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PHOTO_PIXELS) {
        throw ImportValidationFailure(ImportRejection.PHOTO_PIXEL_LIMIT)
    }
    return bounds
}
