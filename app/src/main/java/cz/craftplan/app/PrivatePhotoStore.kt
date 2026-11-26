package cz.craftplan.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** One immutable copy under a content-addressed name inside the app's private directory. */
internal class PrivatePhotoStore(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val bundledBytes: () -> ByteArray,
) {
    private val photo = StoredPhoto.synthetic
    private val finalFile = File(directory, "photo-${photo.sha256}.jpg")

    fun prepare(): ByteArray {
        if (!finalFile.exists()) {
            val bytes = readBundled()
            val staged = Files.createTempFile(directory.toPath(), ".photo-staged-", ".jpg").toFile()
            FileOutputStream(staged).use {
                it.write(bytes)
                it.fd.sync()
            }
            read(staged)
            try {
                // Link is atomic and refuses to replace an existing protected file.
                Files.createLink(finalFile.toPath(), staged.toPath())
            } catch (_: FileAlreadyExistsException) {
                // A concurrent installer won. Verify and sync its actual file below.
            }
            return readAndSync().also {
                // The verified final name now protects this content independently.
                staged.delete()
            }
        }
        return readAndSync()
    }

    fun readBundled(): ByteArray = verify(bundledBytes())

    fun readConfirmed(): ByteArray = read(finalFile)

    fun readAndSync(): ByteArray {
        val bytes = FileInputStream(finalFile).use {
            val verified = verify(it.readNBytes(photo.size + 1))
            it.fd.sync()
            verified
        }
        syncDirectory(directory)
        return bytes
    }

    private fun read(file: File): ByteArray =
        FileInputStream(file).use { verify(it.readNBytes(photo.size + 1)) }

    private fun verify(bytes: ByteArray): ByteArray {
        if (bytes.size != photo.size || sha256(bytes) != photo.sha256) {
            throw IOException("Synthetic photo length or SHA-256 differs")
        }
        return bytes
    }
}
