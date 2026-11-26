package cz.craftplan.app

internal enum class ImportRejection {
    PHOTO_SIZE_LIMIT, PHOTO_PIXEL_LIMIT, STAGED_INTEGRITY, JPEG_SIGNATURE, JPEG_TRUNCATED,
    JPEG_INVALID_STRUCTURE, JPEG_UNSUPPORTED_MARKER, JPEG_DECODE_MALFORMED, JPEG_DECODE_FAILED,
}

internal class ImportValidationFailure(val reason: ImportRejection) : IllegalArgumentException()

/** Checks marker framing through the primary image's EOI. Pixel decoding remains the preview's job. */
internal fun validateJpegStructure(bytes: ByteArray) {
    if (bytes.size < 2 || bytes.unsigned(0) != 0xff || bytes.unsigned(1) != 0xd8) {
        reject(ImportRejection.JPEG_SIGNATURE)
    }
    var offset = 2
    var inScan = false
    var scanHasData = false
    var completedScan = false
    val frameComponents = BooleanArray(256)
    var frameSeen = false

    while (offset < bytes.size) {
        if (bytes.unsigned(offset++) != 0xff) {
            if (!inScan) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
            scanHasData = true
            continue
        }

        val markerStart = offset
        while (offset < bytes.size && bytes.unsigned(offset) == 0xff) offset++
        if (offset == bytes.size) reject(ImportRejection.JPEG_TRUNCATED)
        val marker = bytes.unsigned(offset++)

        if (inScan) {
            when (marker) {
                0x00 -> {
                    if (offset - markerStart != 1) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                    scanHasData = true
                    continue
                }
                in 0xd0..0xd7, 0x01 -> continue // Restart and TEM markers do not end a scan.
            }
            if (!scanHasData) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
            completedScan = true
            inScan = false
        }

        when (marker) {
            0xd9 -> {
                if (!completedScan) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                return // Bytes after the primary image belong to the original file, not its marker stream.
            }
            0x01 -> continue
            0x00, in 0xd0..0xd8 -> reject(ImportRejection.JPEG_INVALID_STRUCTURE)
        }

        val frame = marker in 0xc0..0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        val segmentSupported = frame || marker in 0xda..0xdf || marker in 0xe0..0xef ||
            marker == 0xc4 || marker == 0xcc || marker == 0xfe
        if (!segmentSupported) reject(ImportRejection.JPEG_UNSUPPORTED_MARKER)
        if (bytes.size - offset < 2) reject(ImportRejection.JPEG_TRUNCATED)
        val length = bytes.unsignedShort(offset)
        if (length < 2) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
        if (length > bytes.size - offset) reject(ImportRejection.JPEG_TRUNCATED)
        val payload = offset + 2

        when {
            frame -> {
                if (length < 8 || bytes.unsigned(payload) == 0 || bytes.unsignedShort(payload + 3) == 0) {
                    reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                }
                val count = bytes.unsigned(payload + 5)
                if (count == 0 || length != 8 + 3 * count) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                frameComponents.fill(false)
                repeat(count) { index ->
                    val id = bytes.unsigned(payload + 6 + 3 * index)
                    if (frameComponents[id]) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                    frameComponents[id] = true
                }
                frameSeen = true
                completedScan = false
            }
            marker == 0xda -> {
                if (!frameSeen || length < 6) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                val count = bytes.unsigned(payload)
                if (count !in 1..4 || length != 6 + 2 * count) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                repeat(count) { index ->
                    val id = bytes.unsigned(payload + 1 + 2 * index)
                    if (!frameComponents[id] || (0 until index).any { bytes.unsigned(payload + 1 + 2 * it) == id }) {
                        reject(ImportRejection.JPEG_INVALID_STRUCTURE)
                    }
                }
                scanHasData = false
                inScan = true
            }
            marker == 0xdc || marker == 0xdd ->
                if (length != 4) reject(ImportRejection.JPEG_INVALID_STRUCTURE)
        }
        offset += length
    }
    reject(ImportRejection.JPEG_TRUNCATED)
}

private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xff
private fun ByteArray.unsignedShort(index: Int): Int = (unsigned(index) shl 8) or unsigned(index + 1)

private fun reject(reason: ImportRejection): Nothing = throw ImportValidationFailure(reason)
