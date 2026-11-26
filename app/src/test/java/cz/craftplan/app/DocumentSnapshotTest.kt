package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentSnapshotTest {
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
        bytes[3] = 2
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.decode(bytes) }
    }
}
