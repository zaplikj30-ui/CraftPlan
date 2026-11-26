package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentSnapshotTest {
    @Test
    fun nativeOrientationRecordsRoundTripWithoutRewritingLegacyMaps() {
        val legacy = StoredPhoto.synthetic.copy(
            id = PhotoId(UUID.randomUUID().toString()), sourceExifOrientation = 2, storedOrientation = 2,
        )
        val oldSnapshot = DocumentSnapshot(emptyList(), true, legacy)
        val oldBytes = SnapshotCodec.encode(oldSnapshot)
        assertEquals(2, oldBytes[3].toInt())
        assertEquals(oldSnapshot, SnapshotCodec.decode(oldBytes))

        val newSnapshot = oldSnapshot.copy(photo = legacy.copy(anchorMapVersion = 2))
        val newBytes = SnapshotCodec.encode(newSnapshot)
        assertEquals(3, newBytes[3].toInt())
        assertEquals(newSnapshot, SnapshotCodec.decode(newBytes))
        newBytes[3] = 2
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.decode(newBytes) }
    }

    @Test
    fun legacySnapshotStaysReadableAndPhotoIdentityIsValidated() {
        val legacy = DocumentSnapshot(emptyList(), false)
        val oldBytes = SnapshotCodec.encode(legacy).also { it[3] = 1 }
        assertEquals(legacy, SnapshotCodec.decode(oldBytes))
        val id = PhotoId(UUID.randomUUID().toString())
        val photo = StoredPhoto.synthetic.copy(id = id, mediaId = UUID.randomUUID())
        val imported = DocumentSnapshot(emptyList(), true, photo)
        assertEquals(imported, SnapshotCodec.decode(SnapshotCodec.encode(imported)))
        val mismatched = PhotoDimension(UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f))
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.encode(imported.copy(dimensions = listOf(mismatched)))
        }
    }

    @Test
    fun preservesExactValuesAndEveryEditorField() {
        val base = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(-0.15f, 0.3f), PhotoAnchor(1.25f, -0.05f),
            displayUnit = LengthUnit.CM, labelSuffix = "parapet", note = "Číslo 1\n poznámka",
            showUnit = false, labelT = -0.51f, labelPrefix = "⌀",
        )
        val document = DocumentSnapshot(
            listOf(
                base.copy(textLabel = "Ověřit"),
                base.copy(id = UUID.randomUUID(), measurement = ManualLengthMeasurement.parse("0 mm")),
                base.copy(id = UUID.randomUUID(), measurement = ManualLengthMeasurement.parse(" 1/3 in ")),
                base.copy(id = UUID.randomUUID(), measurement = ManualLengthMeasurement.parse("2' 1/16\"")),
                base.copy(id = UUID.randomUUID(),
                    measurement = ManualLengthMeasurement.calculate("5 / 3 =", LengthUnit.MM, 4),
                    equationPrefix = "5 ÷ 3 ="),
            ),
            showNotes = false,
        )
        assertEquals(document, SnapshotCodec.decode(SnapshotCodec.encode(document)))
    }

    @Test
    fun rejectsDuplicateIdsAndUnknownFormat() {
        val dimension = PhotoDimension(UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f))
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.encode(DocumentSnapshot(listOf(dimension, dimension), true))
        }
        val bytes = SnapshotCodec.encode(DocumentSnapshot(emptyList(), true))
        bytes[3] = 4
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.decode(bytes) }
    }
}
