package cz.craftplan.app

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID

internal data class ImportCandidate(val photoId: PhotoId, val digest: String) {
    init {
        require(photoId != PhotoId.SYNTHETIC_WORKBENCH)
        require(digest.length == 64 && digest.all { it in '0'..'9' || it in 'a'..'f' })
    }

    val fileName: String get() = "import-${photoId.name}-$digest.ready"
}

internal class ImportStaging(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val rename: (File, File) -> Unit,
) {
    fun receive(input: InputStream): ImportCandidate {
        val tmp = Files.createTempFile(directory.toPath(), ".import-", ".part").toFile()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(tmp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count > 0) { "Photo stream stopped making progress" }
                    size += count
                    if (size > MAX_PHOTO_BYTES) throw ImportValidationFailure(ImportRejection.PHOTO_SIZE_LIMIT)
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            val candidate = ImportCandidate(
                PhotoId(UUID.randomUUID().toString()), digest.digest().joinToString("") { "%02x".format(it) },
            )
            readVerified(tmp, candidate)
            val ready = File(directory, candidate.fileName)
            check(Files.notExists(ready.toPath(), LinkOption.NOFOLLOW_LINKS))
            rename(tmp, ready)
            syncDirectory(directory)
            return candidate
        } finally {
            tmp.delete()
        }
    }

    fun candidates(): List<ImportCandidate> =
        checkNotNull(directory.list()).mapNotNull(::parseCandidate).sortedBy { it.fileName }

    fun read(candidate: ImportCandidate): ByteArray =
        readVerified(File(directory, candidate.fileName), candidate).also { syncDirectory(directory) }

    fun remove(candidate: ImportCandidate) {
        Files.deleteIfExists(File(directory, candidate.fileName).toPath())
        syncDirectory(directory)
    }

    private fun readVerified(file: File, candidate: ImportCandidate): ByteArray {
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
        val bytes = file.inputStream().use { it.readNBytes(MAX_PHOTO_BYTES + 1) }
        if (bytes.size > MAX_PHOTO_BYTES) throw ImportValidationFailure(ImportRejection.PHOTO_SIZE_LIMIT)
        if (sha256(bytes) != candidate.digest) throw ImportValidationFailure(ImportRejection.STAGED_INTEGRITY)
        if (bytes.isEmpty()) throw ImportValidationFailure(ImportRejection.JPEG_DECODE_FAILED)
        return bytes
    }

    private fun parseCandidate(name: String): ImportCandidate? {
        if (!name.startsWith("import-") || !name.endsWith(".ready") || name.length != 114) return null
        return try {
            ImportCandidate(PhotoId(name.substring(7, 43)), name.substring(44, 108))
                .takeIf { it.fileName == name }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
