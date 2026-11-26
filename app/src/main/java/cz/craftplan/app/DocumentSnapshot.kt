package cz.craftplan.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class StoredPhoto(
    val id: PhotoId,
    val mediaId: UUID,
    val sha256: String,
    val size: Int,
    val width: Int,
    val height: Int,
    val sourceExifOrientation: Int?,
    val anchorMapVersion: Int,
    val storedOrientation: Int,
    val quality: String,
    val mimeType: String,
) {
    companion object {
        val synthetic = StoredPhoto(
            PhotoId.SYNTHETIC_WORKBENCH, UUID.fromString("deb69e1d-b85e-494e-bad5-e06ed3899037"),
            "829166f3f2c655b8d59e44e3994b537c0df7418588bfb2ecc07c21cc4dbd052b",
            137298, 1200, 800, null, 1, 1, "full", "image/jpeg",
        )
    }
}

internal data class DocumentSnapshot(
    val dimensions: List<PhotoDimension>,
    val showNotes: Boolean,
    val photo: StoredPhoto = StoredPhoto.synthetic,
)

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024

/** Versioned, length-bounded snapshot. Geometry uses the editor's Float coordinates.
 * Measurements use only their tagged exact representation and validated provenance. */
internal object SnapshotCodec {
    private const val FORMAT_VERSION = 1
    private const val MEASUREMENT_VERSION = 1
    private const val MAX_DIMENSIONS = 20_000

    fun encode(snapshot: DocumentSnapshot): ByteArray {
        validate(snapshot)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(MEASUREMENT_VERSION)
            out.writePhoto(snapshot.photo)
            out.writeBoolean(snapshot.showNotes)
            out.writeInt(snapshot.dimensions.size)
            for (dimension in snapshot.dimensions) {
                out.writeDimension(dimension)
                require(buffer.size() <= MAX_SNAPSHOT_BYTES) { "Snapshot is too large" }
            }
        }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): DocumentSnapshot {
        require(bytes.size <= MAX_SNAPSHOT_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION)
            require(input.readInt() == MEASUREMENT_VERSION)
            val photo = input.readPhoto()
            require(photo == StoredPhoto.synthetic)
            val showNotes = input.readFlag()
            val count = input.readInt()
            require(count in 0..MAX_DIMENSIONS)
            val dimensions = List(count) { input.readDimension() }
            require(input.read() == -1) { "Unexpected trailing snapshot fields" }
            DocumentSnapshot(dimensions, showNotes, photo).also(::validate)
        }
    }

    private fun validate(snapshot: DocumentSnapshot) {
        require(snapshot.photo == StoredPhoto.synthetic)
        require(snapshot.dimensions.size <= MAX_DIMENSIONS)
        val ids = HashSet<UUID>()
        for (dimension in snapshot.dimensions) {
            require(dimension.photoId == snapshot.photo.id && ids.add(dimension.id))
        }
    }

    private fun DataOutputStream.writePhoto(photo: StoredPhoto) {
        writeUTF(photo.id.name)
        writeUuid(photo.mediaId)
        writeUTF(photo.sha256)
        writeInt(photo.size)
        writeInt(photo.width)
        writeInt(photo.height)
        writeInt(photo.sourceExifOrientation ?: 0)
        writeInt(photo.anchorMapVersion)
        writeInt(photo.storedOrientation)
        writeUTF(photo.quality)
        writeUTF(photo.mimeType)
    }

    private fun DataInputStream.readPhoto() = StoredPhoto(
        PhotoId.valueOf(readUTF()), readUuid(), readUTF(), readInt(), readInt(), readInt(),
        readInt().takeUnless { it == 0 }, readInt(), readInt(), readUTF(), readUTF(),
    )

    private fun DataOutputStream.writeDimension(dimension: PhotoDimension) {
        writeUuid(dimension.id)
        writeUTF(dimension.photoId.name)
        writeAnchor(dimension.start)
        writeAnchor(dimension.end)
        writeUTF(dimension.displayUnit.name)
        writeUTF(dimension.labelSuffix)
        writeOptionalText(dimension.textLabel)
        writeUTF(dimension.note)
        writeBoolean(dimension.showUnit)
        writeFloat(dimension.labelT)
        writeUTF(dimension.labelPrefix)
        writeOptionalText(dimension.equationPrefix)
        val measurement = dimension.measurement
        writeBoolean(measurement != null)
        if (measurement != null) writeMeasurement(measurement)
    }

    private fun DataInputStream.readDimension(): PhotoDimension {
        val id = readUuid()
        val photoId = PhotoId.valueOf(readUTF())
        val start = readAnchor()
        val end = readAnchor()
        val unit = LengthUnit.valueOf(readUTF())
        val suffix = readUTF()
        val text = readOptionalText()
        val note = readUTF()
        val showUnit = readFlag()
        val labelT = readFloat()
        val prefix = readUTF()
        val equation = readOptionalText()
        val measurement = if (readFlag()) readMeasurement() else null
        return PhotoDimension(id, photoId, start, end, measurement, unit, suffix, text, note,
            showUnit, labelT, prefix, equation)
    }

    private fun DataOutputStream.writeMeasurement(measurement: ManualLengthMeasurement) {
        val encoding = measurement.length.encoding
        writeUTF(encoding.tag)
        when (encoding) {
            is LengthMmEncoding.Decimal -> writeUTF(encoding.value)
            is LengthMmEncoding.Rational -> {
                writeUTF(encoding.numerator)
                writeUTF(encoding.denominator)
            }
        }
        writeUTF(measurement.originalInput)
        writeInt(measurement.originalUnit.version)
        when (val unit = measurement.originalUnit) {
            is OriginalLengthUnit.Single -> writeUTF(unit.unit.name)
            OriginalLengthUnit.FeetAndInches -> writeUTF("ft+in")
        }
        writeUTF(measurement.source.name)
        writeInt(measurement.calculationPlaces ?: -1)
        writeBoolean(measurement.calculationRounded)
    }

    private fun DataInputStream.readMeasurement(): ManualLengthMeasurement {
        val encoding = when (readUTF()) {
            "decimal-mm" -> LengthMmEncoding.Decimal(readUTF())
            "rational-mm" -> LengthMmEncoding.Rational(readUTF(), readUTF())
            else -> error("Unsupported length encoding")
        }
        val original = readUTF()
        require(readInt() == 1)
        val unitTag = readUTF()
        val unit = if (unitTag == "ft+in") null else LengthUnit.valueOf(unitTag)
        val source = LengthSource.valueOf(readUTF())
        val places = readInt()
        val rounded = readFlag()
        val measurement = requireNotNull(when (source) {
            LengthSource.MANUAL -> ManualLengthMeasurement.parse(original, unit)
            LengthSource.CALCULATED -> ManualLengthMeasurement.calculate(original, requireNotNull(unit), places)
        })
        val originalUnit = unit?.let(OriginalLengthUnit::Single) ?: OriginalLengthUnit.FeetAndInches
        require(measurement.length.encoding == encoding && measurement.originalUnit == originalUnit &&
            measurement.calculationPlaces == places.takeUnless { it == -1 } &&
            measurement.calculationRounded == rounded) { "Inconsistent measurement provenance" }
        return measurement
    }

    private fun DataOutputStream.writeUuid(value: UUID) {
        writeLong(value.mostSignificantBits)
        writeLong(value.leastSignificantBits)
    }

    private fun DataInputStream.readUuid() = UUID(readLong(), readLong())

    private fun DataOutputStream.writeAnchor(anchor: PhotoAnchor) {
        writeFloat(anchor.u)
        writeFloat(anchor.v)
    }

    private fun DataInputStream.readAnchor() = PhotoAnchor(readFloat(), readFloat())

    private fun DataOutputStream.writeOptionalText(text: String?) {
        writeBoolean(text != null)
        if (text != null) writeUTF(text)
    }

    private fun DataInputStream.readOptionalText(): String? = if (readFlag()) readUTF() else null

    private fun DataInputStream.readFlag(): Boolean {
        val flag = readUnsignedByte()
        require(flag == 0 || flag == 1)
        return flag == 1
    }
}
