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
    fun receive(
        input: InputStream,
        photoId: PhotoId = PhotoId(UUID.randomUUID().toString()),
        beforePublish: (ImportCandidate) -> Unit = {},
    ): ImportCandidate {
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
                photoId, digest.digest().joinToString("") { "%02x".format(it) },
            )
            readVerified(tmp, candidate)
            beforePublish(candidate)
            val ready = File(directory, candidate.fileName)
            check(Files.notExists(ready.toPath(), LinkOption.NOFOLLOW_LINKS))
            rename(tmp, ready)
            syncDirectory(directory)
            return candidate
        } finally {
            tmp.delete()
        }
    }

    private fun rejectionFile(id: PhotoId) = File(directory, ".camera-rejected-${id.name}")

    fun rejectedCapture(id: PhotoId): ImportCandidate? {
        val path = rejectionFile(id).toPath()
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        val digest = Files.newInputStream(path).use { it.readNBytes(65).toString(Charsets.US_ASCII) }
        return ImportCandidate(id, digest)
    }

    fun rejectedCaptureIds(): List<PhotoId> =
        checkNotNull(directory.list()).mapNotNull { name ->
            val prefix = ".camera-rejected-"
            if (!name.startsWith(prefix)) return@mapNotNull null
            val id = runCatching { PhotoId(name.removePrefix(prefix)) }.getOrNull()
            id?.takeUnless { it == PhotoId.SYNTHETIC_WORKBENCH }
        }

    fun recordCaptureRejection(candidate: ImportCandidate) {
        val existing = rejectedCapture(candidate.photoId)
        if (existing == null) {
            val temp = Files.createTempFile(directory.toPath(), ".camera-reject-", ".tmp").toFile()
            try {
                FileOutputStream(temp).use {
                    it.write(candidate.digest.toByteArray(Charsets.US_ASCII))
                    it.fd.sync()
                }
                rename(temp, rejectionFile(candidate.photoId))
            } finally {
                temp.delete()
            }
        } else check(existing == candidate)
        syncDirectory(directory)
    }

    fun clearCaptureRejection(id: PhotoId) {
        Files.deleteIfExists(rejectionFile(id).toPath())
        syncDirectory(directory)
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
