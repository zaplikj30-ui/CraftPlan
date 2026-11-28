package cz.craftplan.app

/**
 * Advisory preflight only. Require space for the known source plus
 * 1 GiB of conservative headroom; it is not a reservation or guarantee.
 * The bounded stream, actual IO, validation and durability barriers remain authoritative.
 */
internal const val IMPORT_CAPACITY_HEADROOM_BYTES = 1024L * 1024 * 1024

internal fun canSkipDetailedImportCapacity(knownSize: Long, available: Long?): Boolean =
    knownSize in 1L..MAX_PHOTO_BYTES.toLong() &&
        available != null && available >= knownSize + IMPORT_CAPACITY_HEADROOM_BYTES
