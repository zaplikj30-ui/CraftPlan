package cz.craftplan.app

import java.util.Random
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

internal object SyntheticDimensions {
    val counts = listOf(100, 500, 1_000, 5_000, 10_000)
    // The reserved namespace has UUID version 0; manually created objects use random version 4.
    private const val NAMESPACE = 0x4350535452450353L
    private const val SEED = 430024L

    fun owns(dimension: PhotoDimension): Boolean =
        dimension.photoId == PhotoId.SYNTHETIC_WORKBENCH && dimension.id.mostSignificantBits == NAMESPACE

    fun generate(photoId: PhotoId, count: Int): List<PhotoDimension> {
        require(photoId == PhotoId.SYNTHETIC_WORKBENCH && count in counts)
        val random = Random(SEED)
        val measures = listOf(
            null,
            ManualLengthMeasurement.parse("0 mm"),
            ManualLengthMeasurement.parse("850,5 mm"),
            ManualLengthMeasurement.parse("1/3 in"),
            ManualLengthMeasurement.parse("1250 µm"),
        )
        return List(count) { index ->
            val clustered = index % 5 == 0
            val start = if (clustered) PhotoAnchor(0.48f + random.nextFloat() * 0.04f, 0.48f + random.nextFloat() * 0.04f)
            else PhotoAnchor(random.nextFloat(), random.nextFloat())
            val angle = random.nextDouble() * 2 * Math.PI
            val span = if (index % 7 == 0) 0.008f else 0.025f + 0.24f * random.nextFloat()
            val end = PhotoAnchor(start.u + span * cos(angle).toFloat(), start.v + span * sin(angle).toFloat())
            PhotoDimension(
                UUID(NAMESPACE, index.toLong()), photoId, start, end,
                measurement = measures[index % measures.size],
                note = if (index % 11 == 0) "Syntetická poznámka ${index + 1}" else "",
            )
        }
    }
}
