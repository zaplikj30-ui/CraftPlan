package cz.craftplan.app

/**
 * Advisory preflight only. Require space for the known source plus one maximum-sized
 * photo as conservative test headroom; it is not a reservation or guarantee.
 * The bounded stream, actual IO, validation and durability barriers remain authoritative.
 */
internal fun canSkipDetailedImportCapacity(knownSize: Long, available: Long?): Boolean =
    knownSize in 1L..MAX_PHOTO_BYTES.toLong() &&
        available != null && available >= knownSize + MAX_PHOTO_BYTES.toLong()
