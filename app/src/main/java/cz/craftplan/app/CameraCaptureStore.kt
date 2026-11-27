package cz.craftplan.app

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

/** Only this directory is shared through FileProvider, one UUID-named file per grant. */
internal class CameraCaptureStore(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
) {
    fun begin(): PhotoId {
        if (!directory.isDirectory) check(directory.mkdir() || directory.isDirectory)
        check(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        syncDirectory(checkNotNull(directory.parentFile))
        val id = PhotoId(UUID.randomUUID().toString())
        val output = file(id)
        Files.createFile(output.toPath())
        FileOutputStream(output).use { it.fd.sync() }
        syncDirectory(directory)
        return id
    }

    fun attempts(): List<PhotoId> {
        if (Files.notExists(directory.toPath(), NOFOLLOW_LINKS)) return emptyList()
        check(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        return checkNotNull(directory.list()).mapNotNull { name ->
            if (name.length != 40 || !name.endsWith(".jpg")) return@mapNotNull null
            val id = try {
                PhotoId(name.removeSuffix(".jpg"))
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            id.takeIf { Files.isRegularFile(file(id).toPath(), NOFOLLOW_LINKS) }
        }.sortedBy(PhotoId::name)
    }

    fun <T> readOutput(id: PhotoId, read: (FileInputStream) -> T): T {
        val output = file(id)
        if (!Files.isRegularFile(output.toPath(), NOFOLLOW_LINKS)) throw FileNotFoundException()
        return FileInputStream(output).use {
            // The camera owns its write, but the app must own the durability barrier.
            it.fd.sync()
            syncDirectory(directory)
            read(it)
        }
    }

    fun discardEmpty(id: PhotoId) {
        val output = file(id).toPath()
        if (Files.isRegularFile(output, NOFOLLOW_LINKS) && Files.size(output) == 0L) remove(id)
    }

    fun hasOutput(id: PhotoId): Boolean = Files.exists(file(id).toPath(), NOFOLLOW_LINKS)

    fun matches(id: PhotoId, digest: String): Boolean {
        val output = file(id).toPath()
        val before = Files.readAttributes(output, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        val matches = readOutput(id) { input ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                if (size > MAX_PHOTO_BYTES) return@readOutput false
                hash.update(buffer, 0, count)
            }
            size == before.size() && hash.digest().joinToString("") { "%02x".format(it) } == digest
        }
        if (!matches) return false
        val after = Files.readAttributes(output, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        return after.isRegularFile && before.fileKey() == after.fileKey() &&
            before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime()
    }

    fun removeIfMatching(id: PhotoId, digest: String) {
        if (hasOutput(id) && matches(id, digest)) remove(id)
    }

    fun removeApprovedOutput(id: PhotoId, digest: String) {
        if (hasOutput(id)) {
            if (!matches(id, digest)) throw ImportValidationFailure(ImportRejection.CAMERA_OUTPUT_CHANGED)
            Files.deleteIfExists(file(id).toPath())
        }
        // Retry after a lost unlink acknowledgement even when the name is already absent.
        syncDirectory(directory)
    }

    fun remove(id: PhotoId) {
        if (Files.deleteIfExists(file(id).toPath())) syncDirectory(directory)
    }

    private fun file(id: PhotoId): File = File(directory, outputName(id))

    companion object {
        const val DIRECTORY = "camera-output"
        fun outputFile(filesDir: File, id: PhotoId): File = File(File(filesDir, DIRECTORY), outputName(id))

        private fun outputName(id: PhotoId): String {
            require(id != PhotoId.SYNTHETIC_WORKBENCH)
            return "${id.name}.jpg"
        }
    }
}
