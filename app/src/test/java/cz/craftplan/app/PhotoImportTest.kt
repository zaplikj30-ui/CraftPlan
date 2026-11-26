package cz.craftplan.app

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoImportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun recoversPrivateBytesAfterSourceRemovalAndDetectsChangedStage() {
        val dir = folder.newFolder()
        val source = folder.newFile()
        val bytes = bundledSyntheticPhoto()
        source.writeBytes(bytes)
        val first = source.inputStream().use { staging(dir).receive(it) }
        val second = source.inputStream().use { staging(dir).receive(it) }
        assertArrayEquals(bytes, source.readBytes())
        source.delete()
        assertNotEquals(first.photoId, second.photoId)
        val restarted = staging(dir)
        assertEquals(setOf(first, second), restarted.candidates().toSet())
        assertArrayEquals(bytes, restarted.read(first))
        val corrupt = bytes.clone().also { it[100] = (it[100].toInt() xor 1).toByte() }
        File(dir, first.fileName).writeBytes(corrupt)
        val failure = assertThrows(ImportValidationFailure::class.java) { restarted.read(first) }
        assertEquals("IMPORT_PREVIEW / STAGED_INTEGRITY", storageFailureCode(failure, StoragePhase.IMPORT_PREVIEW))
        assertArrayEquals(bytes, restarted.read(second))
    }

    @Test
    fun interruptedStreamDoesNotPublishButFailureAfterRenameLeavesRecoveryCandidate() {
        val dir = folder.newFolder()
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Injected source interruption")
        }
        assertThrows(IOException::class.java) { staging(dir).receive(broken) }
        assertEquals(emptyList<ImportCandidate>(), staging(dir).candidates())
        assertThrows(IllegalArgumentException::class.java) {
            staging(dir).receive(bundledSyntheticPhoto().dropLast(2).toByteArray().inputStream())
        }
        assertEquals(emptyList<ImportCandidate>(), staging(dir).candidates())

        val bytes = bundledSyntheticPhoto()
        assertThrows(IOException::class.java) {
            staging(dir) { throw IOException("Injected directory barrier failure") }.receive(bytes.inputStream())
        }
        val reopened = staging(dir)
        assertArrayEquals(bytes, reopened.read(reopened.candidates().single()))
    }

    @Test
    fun independentPhotosKeepBytesAndRevisionSeparateAndDiscardCannotRemoveConfirmedPhoto() = runBlocking {
        val dir = folder.newFolder()
        val files = staging(dir)
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, files, db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val bytes = bundledSyntheticPhoto() + byteArrayOf(0x53, 0x45, 0x46, 0xff.toByte(), 0xd9.toByte(), 0x01)
        val first = imports.receive(bytes.inputStream())
        val second = imports.receive(bytes.inputStream())
        val firstPhoto = testPhoto(first, bytes.size)
        val secondPhoto = testPhoto(second, bytes.size)
        assertEquals(setOf(first, second), imports.list().pending.toSet())

        db.failPublish = true
        try {
            imports.confirm(first, firstPhoto)
            throw AssertionError("Expected failed revision publication")
        } catch (_: UncertainDocumentSave) {
            assertArrayEquals(bytes, files.read(first))
            assertEquals(emptyList<PhotoId>(), imports.list().confirmed)
        }
        db.failPublish = false
        val opened = imports.confirm(first, firstPhoto)
        assertEquals(first.photoId.name, db.revisions.values.single().saveOperationId)
        assertEquals(firstPhoto, opened.snapshot.photo)
        assertEquals(sha256(bytes), opened.snapshot.photo.sha256)
        assertArrayEquals(bytes, imports.open(first.photoId).photoBytes)
        assertArrayEquals(bytes, opened.photoBytes)
        assertEquals(listOf(second), imports.list().pending)
        try {
            imports.discard(first)
            throw AssertionError("Discard accepted a committed photo")
        } catch (_: IllegalStateException) {
            assertArrayEquals(bytes, imports.open(first.photoId).photoBytes)
        }

        val other = imports.confirm(second, secondPhoto)
        assertNotEquals(opened.version, other.version)
        assertNotEquals(firstPhoto.mediaFileName(), secondPhoto.mediaFileName())
        val dimension = PhotoDimension(UUID.randomUUID(), first.photoId, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f))
        imports.save(DocumentSave(opened.version, 2, opened.snapshot.copy(dimensions = listOf(dimension))))
        assertEquals(listOf(dimension), imports.open(first.photoId).snapshot.dimensions)
        assertEquals(emptyList<PhotoDimension>(), imports.open(second.photoId).snapshot.dimensions)

        val discard = imports.receive(bytes.inputStream())
        imports.discard(discard)
        assertFalse(File(dir, discard.fileName).exists())
        assertEquals(setOf(first.photoId, second.photoId), imports.list().confirmed.toSet())
    }

    @Test
    fun structureRejectsPayloadEoiAndIncompleteOrInvalidSegments() {
        val jpeg = bundledSyntheticPhoto()
        val fakeEoiInApp = byteArrayOf(0xff.toByte(), 0xef.toByte(), 0, 5, 0xff.toByte(), 0xd9.toByte(), 0x11)
        val embedded = jpeg.copyOfRange(0, 2) + fakeEoiInApp + jpeg.copyOfRange(2, jpeg.size)
        validateJpegStructure(embedded)

        val cases = listOf(
            byteArrayOf(1, 2, 3) to ImportRejection.JPEG_SIGNATURE,
            embedded.copyOf(embedded.size - 2) to ImportRejection.JPEG_TRUNCATED,
            jpeg.copyOfRange(0, 2) + byteArrayOf(0xff.toByte(), 0xef.toByte(), 0, 1) to
                ImportRejection.JPEG_INVALID_STRUCTURE,
            jpeg.copyOfRange(0, 2) + byteArrayOf(0xff.toByte(), 0xef.toByte(), 0, 9, 0xff.toByte(), 0xd9.toByte()) to
                ImportRejection.JPEG_TRUNCATED,
            jpeg.copyOfRange(0, 2) + byteArrayOf(0xff.toByte(), 0xd9.toByte()) to
                ImportRejection.JPEG_INVALID_STRUCTURE,
        )
        val dir = folder.newFolder()
        val files = staging(dir)
        for ((bytes, reason) in cases) {
            val error = assertThrows(ImportValidationFailure::class.java) { files.receive(bytes.inputStream()) }
            assertEquals("IMPORT_STAGE / ${reason.name}", storageFailureCode(error, StoragePhase.IMPORT_STAGE))
            assertEquals(emptyList<ImportCandidate>(), files.candidates())
        }
    }

    @Test
    fun acceptsSyntheticProgressiveJpegWithTrailingData() {
        // Generated 16×8 RGB gradient, encoded in progressive mode.
        val jpeg = checkNotNull(javaClass.getResourceAsStream("/synthetic-progressive.jpg")).use { it.readBytes() }
        val bytes = jpeg + byteArrayOf(1, 2, 3)
        val dir = folder.newFolder()
        val candidate = staging(dir).receive(bytes.inputStream())
        assertArrayEquals(bytes, staging(dir).read(candidate))
        assertEquals(sha256(bytes), candidate.digest)
    }

    @Test
    fun mapsAllEightExifOrientationsIncludingMirrors() {
        val expected = listOf(
            PhotoAxes(1, 0, 0, 1), PhotoAxes(-1, 0, 0, 1), PhotoAxes(-1, 0, 0, -1), PhotoAxes(1, 0, 0, -1),
            PhotoAxes(0, 1, 1, 0), PhotoAxes(0, -1, 1, 0), PhotoAxes(0, -1, -1, 0), PhotoAxes(0, 1, -1, 0),
        )
        assertEquals(expected, (1..8).map(::photoAxes))
        assertEquals(listOf(false, false, false, false, true, true, true, true), expected.map { it.swapsAxes })
        assertThrows(IllegalArgumentException::class.java) { photoAxes(9) }
        assertNull(sourceOrientation(null))
        assertNull(sourceOrientation("0"))
        assertEquals(6, sourceOrientation("6"))
        assertThrows(IllegalArgumentException::class.java) { sourceOrientation("9") }
    }

    private fun staging(directory: File, sync: (File) -> Unit = {}) = ImportStaging(directory, sync, ::rename)

    private fun rename(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun testPhoto(candidate: ImportCandidate, size: Int) = StoredPhoto.synthetic.copy(
        id = candidate.photoId, mediaId = UUID.randomUUID(), sha256 = candidate.digest, size = size,
    )
}

private class TestCatalog(private val db: TestDocumentTransactions) : PhotoImportDao {
    private val rows = linkedMapOf<String, PhotoImportRow>()

    override suspend fun find(id: String): PhotoImportRow? = rows[id]
    override suspend fun committedIds(): List<String> = rows.keys.filter { db.heads[it]?.revisionId != null }
    override suspend fun insert(row: PhotoImportRow) {
        check(row.photoId !in rows)
        rows[row.photoId] = row
    }
    override suspend fun delete(id: String) { rows.remove(id) }
    override suspend fun deleteEmptyHead(id: String) {
        if (db.heads[id]?.revisionId == null) db.heads.remove(id)
    }
}
