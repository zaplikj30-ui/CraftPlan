package cz.craftplan.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/** One immutable copy under a content-addressed name inside the app's private directory. */
internal class PrivatePhotoStore(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val rename: (File, File) -> Unit = { staged, final ->
        Files.move(staged.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
    },
    val photo: StoredPhoto = StoredPhoto.synthetic,
    private val bundledBytes: () -> ByteArray,
) {
    private val finalFile = File(directory, photo.mediaFileName())


    /** Only the serialized database writer may publish a photo. It holds the writer
     * transaction across the absence check, rename, media barriers and revision publication. */
    fun prepareForNewRevision(): ByteArray {
        // Require positive proof of absence. Treat unreadable entries and symlinks as existing.
        val absent = storagePhase(StoragePhase.PHOTO_VERIFY) {
            Files.notExists(finalFile.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
        if (absent) {
            val bytes = readBundled()
            val staged = storagePhase(StoragePhase.PHOTO_STAGE_CREATE) {
                Files.createTempFile(directory.toPath(), ".photo-staged-", ".jpg").toFile()
            }
            try {
                storagePhase(StoragePhase.PHOTO_STAGE_WRITE) {
                    FileOutputStream(staged).use {
                        it.write(bytes)
                        storagePhase(StoragePhase.PHOTO_STAGE_SYNC) { it.fd.sync() }
                    }
                }
                read(staged)
                storagePhase(StoragePhase.PHOTO_RENAME) { rename(staged, finalFile) }
                return readAndSync()
            } finally {
                // Only this attempt's temporary copy of the verified bundled fixture.
                try {
                    staged.delete()
                } catch (_: SecurityException) {
                    // A cleanup denial must not hide the original write failure.
                }
            }
        }
        return readAndSync()
    }

    fun readBundled(): ByteArray = storagePhase(StoragePhase.PHOTO_BUNDLE) { verify(bundledBytes()) }

    fun readConfirmed(): ByteArray = read(finalFile)

    fun readAndSync(): ByteArray {
        val bytes = storagePhase(StoragePhase.PHOTO_VERIFY) {
            FileInputStream(finalFile).use {
                val verified = verify(it.readNBytes(photo.size + 1))
                storagePhase(StoragePhase.PHOTO_FILE_SYNC) { it.fd.sync() }
                verified
            }
        }
        storagePhase(StoragePhase.PHOTO_DIRECTORY_SYNC) { syncDirectory(directory) }
        return bytes
    }

    private fun read(file: File): ByteArray = storagePhase(StoragePhase.PHOTO_VERIFY) {
        FileInputStream(file).use { verify(it.readNBytes(photo.size + 1)) }
    }

    private fun verify(bytes: ByteArray): ByteArray {
        require(bytes.size == photo.size && sha256(bytes) == photo.sha256) {
            "Synthetic photo length or SHA-256 differs"
        }
        return bytes
    }
}

internal fun StoredPhoto.mediaFileName(): String = if (id == PhotoId.SYNTHETIC_WORKBENCH) {
    "photo-$sha256.jpg"
} else {
    "photo-${id.name}-$sha256.jpg"
}
