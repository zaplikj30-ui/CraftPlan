package cz.craftplan.app

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal fun bundledSyntheticPhoto(): ByteArray =
    File("src/main/res/raw/synthetic_workbench.jpg").readBytes()

class PrivatePhotoStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun syncsFinalNameAndReopensIdenticalBytesWithoutBundledSource() {
        val dir = folder.newFolder()
        val original = bundledSyntheticPhoto()
        var synchronized = 0
        val store = PrivatePhotoStore(dir, {
            val finalName = it.listFiles()!!.single { file -> file.name.startsWith("photo-") }
            assertArrayEquals(original, finalName.readBytes())
            FileChannel.open(it.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) }
            synchronized++
        }) { original }
        assertArrayEquals(original, store.prepare())
        assertEquals(1, synchronized)
        assertEquals(1, dir.listFiles()!!.size)
        val reopened = PrivatePhotoStore(dir, { error("Read must not write") }) { error("No bundled access") }
        assertArrayEquals(original, reopened.readConfirmed())
    }

    @Test
    fun refusesCorruptionAndPreservesExistingFile() {
        val dir = folder.newFolder()
        val store = PrivatePhotoStore(dir, {}) { bundledSyntheticPhoto() }
        store.prepare()
        val file = dir.listFiles()!!.single()
        file.writeBytes(byteArrayOf(0))
        assertThrows(IOException::class.java) { store.readConfirmed() }
        assertThrows(IOException::class.java) { store.prepare() }
        assertArrayEquals(byteArrayOf(0), file.readBytes())
    }

    @Test
    fun failedDirectoryBarrierIsNotSuccessfulAndRetryChecksTheSameBytes() {
        val dir = folder.newFolder()
        var fail = true
        val store = PrivatePhotoStore(dir, { if (fail) throw IOException("Injected directory sync failure") }) {
            bundledSyntheticPhoto()
        }
        assertThrows(IOException::class.java) { store.prepare() }
        assertEquals(1, dir.listFiles()!!.count { it.name.startsWith("photo-") })
        fail = false
        assertArrayEquals(bundledSyntheticPhoto(), store.prepare())
    }
}
