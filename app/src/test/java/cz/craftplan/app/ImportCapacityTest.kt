package cz.craftplan.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCapacityTest {
    @Test
    fun fastPathRequiresKnownValidSizeAndHeadroom() {
        val max = MAX_PHOTO_BYTES.toLong()
        val reserve = IMPORT_CAPACITY_HEADROOM_BYTES
        assertFalse(canSkipDetailedImportCapacity(0, Long.MAX_VALUE))
        assertFalse(canSkipDetailedImportCapacity(-1, Long.MAX_VALUE))
        assertFalse(canSkipDetailedImportCapacity(max + 1, Long.MAX_VALUE))
        assertFalse(canSkipDetailedImportCapacity(1, null))
        assertFalse(canSkipDetailedImportCapacity(1, -1))
        assertFalse(canSkipDetailedImportCapacity(1, 0))
        assertFalse(canSkipDetailedImportCapacity(1, reserve))
        assertTrue(canSkipDetailedImportCapacity(1, reserve + 1))
        assertFalse(canSkipDetailedImportCapacity(max, reserve + max - 1))
        assertTrue(canSkipDetailedImportCapacity(max, reserve + max))
        assertTrue(canSkipDetailedImportCapacity(max, Long.MAX_VALUE))
    }
}
