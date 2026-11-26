package cz.craftplan.app

import android.graphics.ImageDecoder.DecodeException
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
import org.junit.Assert.assertTrue
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
        assertThrows(ImportValidationFailure::class.java) {
            staging(dir).receive(byteArrayOf().inputStream())
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
    fun confirmedPhotoListsAndReopensWithoutTrustingTheCatalog() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, staging(dir), db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val bytes = bundledSyntheticPhoto()
        val candidate = imports.receive(bytes.inputStream())
        val photo = testPhoto(candidate, bytes.size)
        val first = imports.confirm(candidate, photo)
        val dimension = PhotoDimension(UUID.randomUUID(), photo.id, PhotoAnchor(0.1f, 0.2f), PhotoAnchor(0.7f, 0.8f),
            measurement = ManualLengthMeasurement.parse("1/3 in"), note = "Synthetic test")
        val updated = first.snapshot.copy(dimensions = listOf(dimension))
        val ack = imports.save(DocumentSave(first.version, 2, updated))
        val originalHead = db.heads[photo.id.documentId]
        val originalRevisions = db.revisions.toMap()
        val pending = imports.receive(bytes.inputStream())
        db.beforeWrite = { error("Opening and listing must not write to the database") }

        val otherPhoto = photo.copy(id = pending.photoId, mediaId = UUID.randomUUID())
        val catalogMetadata = listOf(
            null,
            byteArrayOf(1, 2, 3),
            SnapshotCodec.encode(DocumentSnapshot(emptyList(), true, otherPhoto)),
            SnapshotCodec.encode(DocumentSnapshot(emptyList(), true, photo.copy(sha256 = "a".repeat(64)))),
        )
        for (metadata in catalogMetadata) {
            if (metadata == null) catalog.rows.remove(photo.id.name)
            else catalog.rows[photo.id.name] = PhotoImportRow(photo.id.name, metadata)
            val before = catalog.rows.toMap()
            val listed = imports.list()
            assertEquals(listOf(photo.id), listed.confirmed)
            assertEquals(listOf(pending), listed.pending)
            val opened = imports.open(photo.id)
            assertEquals(ack.version, opened.version)
            assertFalse(opened.recovered)
            assertEquals(updated, opened.snapshot)
            assertArrayEquals(bytes, opened.photoBytes)
            assertEquals(before, catalog.rows)
            assertEquals(originalHead, db.heads[photo.id.documentId])
            assertEquals(originalRevisions, db.revisions)
        }
    }

    @Test
    fun catalogIndependentRecoveryChecksRevisionAndMediaBeforeOpening() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, staging(dir), db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val bytes = bundledSyntheticPhoto()
        val candidate = imports.receive(bytes.inputStream())
        val photo = testPhoto(candidate, bytes.size)
        val first = imports.confirm(candidate, photo)
        val latest = imports.save(DocumentSave(first.version, 2, first.snapshot.copy(showNotes = false)))
        catalog.rows.clear()

        val latestId = checkNotNull(latest.version.revisionId)
        val row = db.revisions.getValue(latestId)
        val foreign = photo.copy(id = PhotoId(UUID.randomUUID().toString()))
        val mismatched = SnapshotCodec.encode(DocumentSnapshot(emptyList(), true, foreign))
        // Include a correct digest for the wrong photo. Hash equality cannot replace identity checks.
        db.revisions[latestId] = row.copy(snapshot = mismatched, snapshotDigest = sha256(mismatched))
        val fallback = imports.open(photo.id)
        assertEquals(first.version, fallback.version)
        assertEquals(first.snapshot, fallback.snapshot)
        assertEquals(true, fallback.recovered)
        assertEquals(latestId, db.heads[photo.id.documentId]?.revisionId)
        assertEquals(emptyMap<String, PhotoImportRow>(), catalog.rows)

        val wrongMedia = photo.copy(sha256 = "a".repeat(64))
        val wrongMediaSnapshot = SnapshotCodec.encode(first.snapshot.copy(photo = wrongMedia, showNotes = false))
        db.revisions[latestId] = row.copy(snapshot = wrongMediaSnapshot, snapshotDigest = sha256(wrongMediaSnapshot))
        assertEquals(first.version, imports.open(photo.id).version)
        val wrongFile = File(dir, wrongMedia.mediaFileName())
        assertTrue(wrongFile.mkdir())
        assertThrows(IOException::class.java) { runBlocking { imports.open(photo.id) } }
        assertTrue(wrongFile.delete())
        wrongFile.writeBytes(bytes)
        assertEquals(fallback.snapshot, imports.open(photo.id).snapshot)

        val firstId = checkNotNull(first.version.revisionId)
        val earlierRow = db.revisions.getValue(firstId)
        db.revisions[firstId] = earlierRow.copy(storageGeneration = UUID.randomUUID().toString())
        assertThrows(IOException::class.java) { runBlocking { imports.open(photo.id) } }
        db.revisions[firstId] = earlierRow
        File(dir, photo.mediaFileName()).writeBytes(bytes.clone().also {
            it[100] = (it[100].toInt() xor 1).toByte()
        })
        assertThrows(IOException::class.java) { runBlocking { imports.open(photo.id) } }
        assertEquals(latestId, db.heads[photo.id.documentId]?.revisionId)
    }

    @Test
    fun stagingPreservesInputForDecoderWithoutInterpretingEndMarkers() {
        val dir = folder.newFolder()
        val files = staging(dir)
        val jpeg = bundledSyntheticPhoto()
        val missingEoi = jpeg.copyOf(jpeg.size - 2)
        val candidate = files.receive(missingEoi.inputStream())
        assertArrayEquals(missingEoi, staging(dir).read(candidate))
        assertEquals(sha256(missingEoi), candidate.digest)
        assertEquals(listOf(candidate), files.candidates())
    }

    @Test
    fun partialPolicyAllowsOnlyDecoderReportedContentDamage() {
        assertEquals(true, canShowPartialJpeg(DecodeException.SOURCE_INCOMPLETE))
        assertEquals(true, canShowPartialJpeg(DecodeException.SOURCE_MALFORMED_DATA))
        assertFalse(canShowPartialJpeg(DecodeException.SOURCE_EXCEPTION))
        assertFalse(canShowPartialJpeg(Int.MAX_VALUE))
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
    fun keepsCanonicalAndStoredDimensionsCompatibleForAllExifOrientations() {
        for (orientation in 1..8) {
            val canonical = photoDimensions(1200, 800, orientation)
            assertEquals(if (orientation <= 4) 1200 to 800 else 800 to 1200, canonical)
            assertEquals(1200 to 800, photoDimensions(canonical.first, canonical.second, orientation))
        }
        assertThrows(IllegalArgumentException::class.java) { photoDimensions(1200, 800, 9) }
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
    val rows = linkedMapOf<String, PhotoImportRow>()

    override suspend fun find(id: String): PhotoImportRow? = rows[id]
    override suspend fun insert(row: PhotoImportRow) {
        check(row.photoId !in rows)
        rows[row.photoId] = row
    }
    override suspend fun delete(id: String) { rows.remove(id) }
    override suspend fun deleteEmptyHead(id: String) {
        if (db.heads[id]?.revisionId == null) db.heads.remove(id)
    }
}
