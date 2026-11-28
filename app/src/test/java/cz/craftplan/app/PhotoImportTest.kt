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
    fun legacyImportWithUncertainPublicationCannotBeDiscarded() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, files, db, catalog) { _, _ -> error("No publication expected") }
        val bytes = bundledSyntheticPhoto()
        val candidate = imports.receive(bytes.inputStream())
        catalog.rows[candidate.photoId.name] = PhotoImportRow(candidate.photoId.name, ByteArray(4097))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.discard(candidate) } }
        assertArrayEquals(bytes, files.read(candidate))
        assertEquals(listOf(PhotoRecoveryCase.Import(candidate)), imports.list().recoveryCases(null, null))
    }

    @Test
    fun rejectedNewExternalImageIsRemovedButUncertainAndProtectedCopiesSurvive() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, files, db, catalog) { _, _ -> error("No publication expected") }
        val payload = byteArrayOf(1, 2, 3)
        val candidate = imports.receive(payload.inputStream())
        fun failure(reason: ImportRejection, malformed: Boolean = false, incomplete: Boolean = false) =
            StorageFailure(StoragePhase.IMPORT_PREVIEW, ImportValidationFailure(reason, malformed, incomplete))
        val header = failure(ImportRejection.JPEG_HEADER_INVALID)
        val malformed = failure(ImportRejection.JPEG_DECODE_MALFORMED, malformed = true)
        val truncated = failure(ImportRejection.JPEG_TRUNCATED, incomplete = true)
        assertFalse(imports.rejectInvalidPreview(candidate, header))
        for (error in listOf(
            failure(ImportRejection.PHOTO_PIXEL_LIMIT),
            failure(ImportRejection.JPEG_TRUNCATED),
            failure(ImportRejection.JPEG_DECODE_MALFORMED),
            failure(ImportRejection.JPEG_DECODE_FAILED),
            failure(ImportRejection.JPEG_METADATA_INVALID),
            StorageFailure(StoragePhase.IMPORT_PREVIEW, IOException()),
            StorageFailure(StoragePhase.IMPORT_PREVIEW, PhotoSpaceUnavailable()),
            StorageFailure(StoragePhase.OPEN, ImportValidationFailure(ImportRejection.JPEG_DECODE_MALFORMED, true)),
        )) assertFalse(imports.rejectInvalidPreview(candidate, error, freshExternal = true))
        assertArrayEquals(payload, files.read(candidate))
        val protected = imports.receive(payload.inputStream())
        catalog.rows[protected.photoId.name] = PhotoImportRow(protected.photoId.name, ByteArray(4097))
        assertFalse(imports.rejectInvalidPreview(protected, malformed, freshExternal = true))
        assertFalse(imports.rejectInvalidPreview(protected, truncated))
        assertArrayEquals(payload, files.read(protected))
        assertTrue(imports.rejectInvalidPreview(candidate, header, freshExternal = true))
        for (error in listOf(malformed, truncated, failure(ImportRejection.JPEG_SIGNATURE))) {
            val rejected = imports.receive(payload.inputStream())
            assertTrue(imports.rejectInvalidPreview(rejected, error, freshExternal = true))
        }
        val oldRejected = imports.receive(payload.inputStream())
        assertTrue(imports.rejectInvalidPreview(oldRejected, truncated))
        assertEquals(listOf(protected), files.candidates())
        assertEquals("IMPORT_STAGE / NO_SPACE", storageFailureCode(PhotoSpaceUnavailable(), StoragePhase.IMPORT_STAGE))
    }

    @Test
    fun completedCameraRejectsDamagedImageWithExistingRejectionProtocol() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, files, db, catalog) { _, _ -> error("No publication expected") }
        val payload = byteArrayOf(1, 2, 3)
        val id = imports.beginCapture()
        val output = CameraCaptureStore.outputFile(dir, id)
        output.writeBytes(payload)
        val candidate = imports.stageCapture(id)
        val damaged = StorageFailure(StoragePhase.IMPORT_PREVIEW,
            ImportValidationFailure(ImportRejection.JPEG_TRUNCATED, decoderConfirmedIncomplete = true))
        assertFalse(imports.rejectInvalidPreview(candidate, damaged))
        assertArrayEquals(payload, output.readBytes())
        assertArrayEquals(payload, files.read(candidate))
        catalog.rows[id.name] = PhotoImportRow(id.name, ByteArray(4097))
        assertFalse(imports.rejectInvalidPreview(candidate, damaged, completedCamera = true))
        assertArrayEquals(payload, output.readBytes())
        catalog.rows.remove(id.name)
        assertTrue(imports.rejectInvalidPreview(candidate, damaged, completedCamera = true))
        assertFalse(output.exists())
        assertTrue(files.candidates().isEmpty())
        assertNull(files.rejectedCapture(id))
        assertTrue(imports.list().recoveryCases(null, null).isEmpty())
    }

    @Test
    fun unconfirmedUndecodableStageCanBeExplicitlyDiscardedButProtectedSourcesCannot() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, files, db, catalog) { _, _ -> error("No publication expected") }
        val payload = byteArrayOf(1, 2, 3)
        val candidate = imports.receive(payload.inputStream())
        assertTrue(imports.canDiscardUnconfirmedImport(candidate))
        // Publication evidence appearing after the UI check must block the destructive action.
        catalog.rows[candidate.photoId.name] = PhotoImportRow(candidate.photoId.name, ByteArray(4097))
        assertFalse(imports.canDiscardUnconfirmedImport(candidate))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.discard(candidate) } }
        assertArrayEquals(payload, files.read(candidate))
        catalog.rows.remove(candidate.photoId.name)

        val competing = files.receive(byteArrayOf(4, 5, 6).inputStream(), candidate.photoId)
        assertFalse(imports.canDiscardUnconfirmedImport(candidate))
        assertThrows(IllegalStateException::class.java) { runBlocking { imports.discard(candidate) } }
        assertArrayEquals(payload, files.read(candidate))
        assertArrayEquals(byteArrayOf(4, 5, 6), files.read(competing))
        files.remove(competing)

        val cameraId = imports.beginCapture()
        val cameraStage = files.receive(payload.inputStream(), cameraId)
        assertFalse(imports.canDiscardUnconfirmedImport(cameraStage))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.discard(cameraStage) } }
        assertArrayEquals(payload, files.read(cameraStage))

        files.recordCaptureRejection(candidate)
        assertFalse(imports.canDiscardUnconfirmedImport(candidate))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.discard(candidate) } }
        files.clearCaptureRejection(candidate.photoId)

        assertTrue(imports.canDiscardUnconfirmedImport(candidate))
        imports.discard(candidate)
        imports.discard(candidate)
        assertFalse(imports.canDiscardUnconfirmedImport(candidate))
        assertEquals(listOf(cameraStage), files.candidates())
    }

    @Test
    fun changedUnconfirmedStageCannotBeOfferedOrDiscardedWithoutDigestVerification() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val imports = PhotoImports(dir, {}, files, db, TestCatalog(db)) { _, _ -> error("No publication expected") }
        val candidate = imports.receive(byteArrayOf(1, 2, 3).inputStream())
        val changed = byteArrayOf(4, 5, 6)
        File(dir, candidate.fileName).writeBytes(changed)
        assertThrows(ImportValidationFailure::class.java) {
            runBlocking { imports.canDiscardUnconfirmedImport(candidate) }
        }
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.discard(candidate) } }
        assertArrayEquals(changed, File(dir, candidate.fileName).readBytes())
    }

    @Test
    fun directCameraConfirmationRequiresSuccessAndAnUndamagedImage() {
        assertTrue(CameraResultMode.DIRECT_AFTER_SUCCESS.confirmsAutomatically(true, false))
        assertFalse(CameraResultMode.DIRECT_AFTER_SUCCESS.confirmsAutomatically(false, false))
        assertFalse(CameraResultMode.DIRECT_AFTER_SUCCESS.confirmsAutomatically(true, true))
        assertFalse(CameraResultMode.CRAFTPLAN_PREVIEW.confirmsAutomatically(true, false))
        assertFalse(PhotoConfirmation.AUTOMATIC.permits(true))
        assertFalse(PhotoConfirmation.EXPLICIT.permits(true))
        assertTrue(PhotoConfirmation.EXPLICIT.permits(false))
        assertTrue(PhotoConfirmation.AUTOMATIC.permits(false))
    }

    @Test
    fun automaticRecoveryOfferDoesNotReopenDeferredImportInSameSession() {
        val candidate = ImportCandidate(PhotoId(UUID.randomUUID().toString()), "a".repeat(64))
        val empty = PhotoList(emptyList(), emptyList())
        val pending = empty.copy(pending = listOf(candidate))
        val cases = pending.recoveryCases(null, null)
        val session = AutomaticRecoveryOffer()
        assertEquals(-1, session.takeIndex(empty, emptyList(), null))

        // Closing a valid review consumes the offer before the menu reappears.
        session.suppress()
        assertEquals(-1, session.takeIndex(pending, cases, null))
        assertEquals(listOf(PhotoRecoveryCase.Import(candidate)), pending.recoveryCases(null, null))
        // Recomposition, returning from the editor, and refresh do not reset it.
        assertEquals(-1, session.takeIndex(empty, emptyList(), null))
        assertEquals(-1, session.takeIndex(pending, cases, null))

        // A new ViewModel session still offers genuine interrupted work once.
        val restarted = AutomaticRecoveryOffer()
        assertEquals(0, restarted.takeIndex(pending, cases, null))
        assertEquals(-1, restarted.takeIndex(pending, cases, null))
    }

    @Test
    fun recoveryIndexKeepsAllSourcesAndDecodesOnlyTheSelectedCase() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        fun imports() = PhotoImports(dir, {}, staging(dir), db, catalog) { _, _ -> error("No revision expected") }
        val bytes = bundledSyntheticPhoto()
        fun attempt(payload: ByteArray): PhotoId = imports().beginCapture().also {
            CameraCaptureStore.outputFile(dir, it).writeBytes(payload)
        }
        val ready = attempt(bytes)
        val active = attempt(bytes)
        val empty = attempt(byteArrayOf())
        val invalid = attempt(byteArrayOf(1))
        val malformed = attempt(byteArrayOf())
        val malformedOutput = CameraCaptureStore.outputFile(dir, malformed)
        assertTrue(malformedOutput.delete() && malformedOutput.mkdir())
        val conflict = attempt(bytes)
        imports().stageCapture(conflict)
        CameraCaptureStore.outputFile(dir, conflict).writeBytes(bytes + byteArrayOf(1))
        val rejected = attempt(bytes)
        staging(dir).recordCaptureRejection(imports().stageCapture(rejected))
        val uncertain = attempt(bytes)
        imports().stageCapture(uncertain)
        assertFalse(imports().hasConfirmationEvidence(uncertain))
        catalog.rows[uncertain.name] = PhotoImportRow(uncertain.name, ByteArray(4097))
        assertTrue(imports().hasConfirmationEvidence(uncertain))
        val oldImport = imports().receive(bytes.inputStream())
        val releases = mutableListOf<PhotoId>()
        imports().resumeCaptureRejections(active) { releases += it }
        assertEquals(listOf(rejected), releases)
        assertFalse(CameraCaptureStore.outputFile(dir, rejected).exists())
        assertNull(staging(dir).rejectedCapture(rejected))
        val saved = PhotoId(UUID.randomUUID().toString())
        val cases = imports().list().copy(confirmedWithRecoverySources = listOf(saved)).recoveryCases(active, saved)
        assertEquals(setOf(ready, invalid, malformed, conflict, uncertain, oldImport.photoId, saved), cases.map { it.id }.toSet())
        assertEquals(1, cases.count { it is PhotoRecoveryCase.Saved })
        val listing = imports().list()
        assertEquals(setOf(empty), listing.emptyCaptureOnly)
        assertEquals(listOf(empty), listing.emptyTechnicalCaptures(active, saved))
        assertEquals(saved, cases[listing.automaticRecoveryIndex(cases, saved)].id)
        assertEquals(-1, listing.automaticRecoveryIndex(listOf(PhotoRecoveryCase.Camera(empty)), null))
        assertEquals(1, listing.automaticRecoveryIndex(listOf(PhotoRecoveryCase.Camera(empty), PhotoRecoveryCase.Camera(ready)), null))
        assertTrue(CameraCaptureStore.outputFile(dir, empty).exists())
        val decoded = mutableListOf<Int>()
        suspend fun inspect(id: PhotoId) =
            imports().inspectRecovery(if (id == empty) PhotoRecoveryCase.Camera(id) else cases.single { it.id == id }, { releases += it }) {
                decoded += it.bytes.size
                check(it.bytes.size >= bytes.size)
                RecoveryImage(it.bytes.size, sourceDamaged = true)
            }
        assertTrue(decoded.isEmpty())
        val readyPreview = checkNotNull(inspect(ready))
        assertNull(readyPreview.discardCandidate) // Raw-only attempt is offered without destructive action.
        assertTrue(readyPreview.sourceDamaged)
        assertEquals(listOf(bytes.size), decoded)
        assertNull(inspect(conflict)?.discardCandidate)
        assertNull(inspect(uncertain)?.discardCandidate)
        assertNull(inspect(invalid))
        assertNull(inspect(empty))
        assertNull(inspect(malformed))
        assertTrue(malformedOutput.isDirectory)
        assertFalse(active in releases)
        assertTrue(CameraCaptureStore.outputFile(dir, invalid).exists())
        assertTrue(CameraCaptureStore.outputFile(dir, empty).exists())
        val expected = imports().stageCapture(ready)
        val token = checkNotNull(inspect(ready)?.discardCandidate)
        assertEquals(expected, token)
        imports().discardInterruptedCapture(token)
        assertFalse(CameraCaptureStore.outputFile(dir, ready).exists())
        assertTrue(CameraCaptureStore.outputFile(dir, active).exists())
    }

    @Test
    fun emptyCameraOutputIsNotAPhotoButLateBytesAndProtectedSourcesStayRecoverable() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, staging(dir), db, catalog) { _, _ -> error("No publication expected") }
        val empty = imports.beginCapture()
        val output = CameraCaptureStore.outputFile(dir, empty)
        val initial = imports.list()
        assertTrue(initial.recoveryCases(null, null).isEmpty())
        assertEquals(listOf(empty), initial.emptyTechnicalCaptures(null, null))
        assertTrue(initial.emptyTechnicalCaptures(empty, null).isEmpty())
        assertTrue(output.isFile)
        val payload = bundledSyntheticPhoto()
        output.writeBytes(payload)
        val later = imports.list()
        assertEquals(listOf(PhotoRecoveryCase.Camera(empty)), later.recoveryCases(null, null))
        assertTrue(later.emptyTechnicalCaptures(null, null).isEmpty())
        assertArrayEquals(payload, output.readBytes())

        val protected = imports.beginCapture()
        catalog.rows[protected.name] = PhotoImportRow(protected.name, ByteArray(4097))
        val staged = imports.beginCapture()
        val stagedOutput = CameraCaptureStore.outputFile(dir, staged)
        stagedOutput.writeBytes(payload)
        val candidate = imports.stageCapture(staged)
        stagedOutput.writeBytes(byteArrayOf())
        val guarded = imports.list()
        assertEquals(setOf(empty, protected, staged), guarded.recoveryCases(null, null).map { it.id }.toSet())
        assertTrue(guarded.emptyTechnicalCaptures(null, null).isEmpty())
        assertArrayEquals(payload, staging(dir).read(candidate))
    }

    @Test
    fun retakeRecordsRejectionBeforeRemovingOnlyTheApprovedDraft(): Unit = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        var failBarrier = false
        fun imports() = PhotoImports(dir, {}, staging(dir) {
            if (failBarrier && dir.list().orEmpty().any { it.startsWith(".camera-rejected-") }) {
                throw IOException("Injected rejection barrier failure")
            }
        }, db, catalog) { _, _ -> error("No revision expected") }
        val id = imports().beginCapture()
        val other = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val bytes = bundledSyntheticPhoto()
        raw.writeBytes(bytes)
        val candidate = imports().stageCapture(id)
        raw.writeBytes(bytes + byteArrayOf(1))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports().rejectCapture(candidate) } }
        assertNull(staging(dir).rejectedCapture(id))
        raw.writeBytes(bytes)
        failBarrier = true
        assertThrows(IOException::class.java) { runBlocking { imports().rejectCapture(candidate) } }
        assertArrayEquals(bytes, raw.readBytes())
        assertArrayEquals(bytes, staging(dir).read(candidate))

        failBarrier = false
        imports().rejectCapture(candidate)
        assertFalse(raw.exists())
        assertNull(staging(dir).rejectedCapture(id))
        assertTrue(staging(dir).candidates().isEmpty())
        assertEquals(listOf(other), imports().list().captures)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { imports().confirm(candidate, testPhoto(candidate, bytes.size)) }
        }
    }

    @Test
    fun conflictingSourcesAreIndependentlyVerifiedAndRetriedWithoutReplacingEither() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        var failStageBarrier = false
        fun imports() = PhotoImports(dir, {}, staging(dir) {
            if (failStageBarrier) throw IOException("Injected stage barrier failure")
        }, db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val id = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val original = bundledSyntheticPhoto()
        val current = original + byteArrayOf(1, 2, 3)
        raw.writeBytes(original)
        val staged = imports().stageCapture(id)
        raw.writeBytes(current)
        val camera = CaptureSource.Camera(id)
        val stage = CaptureSource.Stage(staged)
        assertEquals(listOf(camera, stage), imports().captureSources(id))
        val stageCopy = imports().readCaptureCopy(stage)
        val rawCopy = imports().readCaptureCopy(camera)
        assertArrayEquals(original, stageCopy.bytes)
        assertArrayEquals(current, rawCopy.bytes)
        assertNotEquals(id, rawCopy.candidate.photoId)
        assertNotEquals(stageCopy.candidate.photoId, rawCopy.candidate.photoId)

        File(dir, staged.fileName).writeBytes(byteArrayOf(8))
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports().readCaptureCopy(stage) } }
        assertArrayEquals(current, imports().readCaptureCopy(camera).bytes)
        failStageBarrier = true
        assertThrows(IOException::class.java) { runBlocking { imports().stageCaptureCopy(camera, rawCopy.candidate) } }
        failStageBarrier = false
        assertEquals(rawCopy.candidate, imports().readCaptureCopy(camera).candidate)
        val later = current + byteArrayOf(4)
        raw.writeBytes(later)
        imports().stageCaptureCopy(camera, rawCopy.candidate)
        val photo = testPhoto(rawCopy.candidate, current.size)
        val stored = imports().confirm(rawCopy.candidate, photo)
        imports().stageCaptureCopy(camera, rawCopy.candidate)
        assertEquals(stored.version, imports().confirm(rawCopy.candidate, photo).version)
        assertEquals(1, db.revisions.size)
        assertArrayEquals(current, imports().open(photo.id).photoBytes)
        assertArrayEquals(later, raw.readBytes())
        assertArrayEquals(byteArrayOf(8), File(dir, staged.fileName).readBytes())
    }

    @Test
    fun changedSourceCannotCommitWhatWasNotPreviewed() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val imports = PhotoImports(dir, {}, staging(dir), db, TestCatalog(db)) { _, _ -> error("No revision expected") }
        val id = imports.beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val bytes = bundledSyntheticPhoto()
        raw.writeBytes(bytes)
        val source = CaptureSource.Camera(id)
        val preview = imports.readCaptureCopy(source)
        raw.writeBytes(bytes + byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { imports.stageCaptureCopy(source, preview.candidate) }
        }
        assertTrue(staging(dir).candidates().isEmpty())
    }

    @Test
    fun confirmedRecoveryUsesExactRevisionAndMediaWithoutChangingEitherAfterRestart() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        var failStageBarrier = false
        fun imports() = PhotoImports(dir, {}, staging(dir) {
            if (failStageBarrier) throw IOException("Injected source sync failure")
        }, db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val bytes = bundledSyntheticPhoto()
        val id = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        raw.writeBytes(bytes)
        val candidate = imports().stageCapture(id)
        val photo = testPhoto(candidate, bytes.size)
        val first = imports().confirm(candidate, photo)
        val measure = PhotoDimension(UUID.randomUUID(), id, PhotoAnchor(-0.2f, 0.3f), PhotoAnchor(0.8f, 1f),
            measurement = ManualLengthMeasurement.parse("1/3 in"), note = "synthetic")
        val snapshot = first.snapshot.copy(dimensions = listOf(measure))
        val latest = imports().save(DocumentSave(first.version, 2, snapshot))
        val stageFile = File(dir, candidate.fileName)
        stageFile.writeBytes(bytes)
        raw.writeBytes(bytes + byteArrayOf(1))
        val final = File(dir, photo.mediaFileName())
        final.writeBytes(byteArrayOf(5))
        catalog.rows[id.name] = PhotoImportRow(id.name, byteArrayOf(8))
        val head = db.heads[id.documentId]
        val revisions = db.revisions.toMap()
        db.beforeWrite = { error("Recovery must not write revisions") }

        assertThrows(IOException::class.java) { runBlocking { imports().open(id) } }
        repeat(2) {
            val recovered = imports().recoverConfirmed(id)
            assertTrue(recovered.recovered && recovered.recoveredFromMedia)
            assertEquals(latest.version, recovered.version)
            assertEquals(snapshot, recovered.snapshot)
            assertArrayEquals(bytes, recovered.photoBytes)
        }
        assertArrayEquals(byteArrayOf(5), final.readBytes())
        assertArrayEquals(bytes, stageFile.readBytes())
        assertArrayEquals(bytes + byteArrayOf(1), raw.readBytes())
        assertEquals(head, db.heads[id.documentId])
        assertEquals(revisions, db.revisions)

        failStageBarrier = true
        val failed = assertThrows(IOException::class.java) { runBlocking { imports().recoverConfirmed(id) } }
        assertEquals("Injected source sync failure", failed.message)
        failStageBarrier = false
        stageFile.writeBytes(byteArrayOf(3))
        assertThrows(IOException::class.java) { runBlocking { imports().recoverConfirmed(id) } }
        raw.writeBytes(bytes)
        assertArrayEquals(bytes, imports().recoverConfirmed(id).photoBytes)
        val revisionId = checkNotNull(latest.version.revisionId)
        db.revisions[revisionId] = db.revisions.getValue(revisionId).copy(snapshotDigest = "bad")
        assertEquals(first.version, imports().recoverConfirmed(id).version)
        db.revisions[checkNotNull(first.version.revisionId)] =
            db.revisions.getValue(checkNotNull(first.version.revisionId)).copy(storageGeneration = UUID.randomUUID().toString())
        assertThrows(IOException::class.java) { runBlocking { imports().recoverConfirmed(id) } }
        assertArrayEquals(bytes, raw.readBytes())
    }

    @Test
    fun confirmedConflictsRemainVisibleAndOnlyTheApprovedUnreferencedSourceCanBeRemoved() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        var failUnlinkBarrier = false
        fun imports() = PhotoImports(dir, { parent ->
            if (failUnlinkBarrier && parent.name == CameraCaptureStore.DIRECTORY && parent.list().orEmpty().isEmpty()) {
                throw IOException("Injected failure after unlink")
            }
        }, staging(dir), db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val id = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val bytes = bundledSyntheticPhoto()
        val other = bytes + byteArrayOf(1)
        raw.writeBytes(bytes)
        val candidate = imports().stageCapture(id)
        val photo = testPhoto(candidate, bytes.size)
        raw.writeBytes(other)
        val confirmed = imports().confirm(candidate, photo)
        assertEquals(listOf(id), imports().list().confirmedWithRecoverySources)
        assertTrue(imports().list().captures.isEmpty())
        val source = CaptureSource.Camera(id)
        assertEquals(listOf(source), imports().captureSources(id))
        val copy = imports().readCaptureCopy(source)
        assertArrayEquals(other, copy.bytes)
        assertNotEquals(id, copy.candidate.photoId)
        val head = db.heads[id.documentId]
        val revisions = db.revisions.toMap()

        val final = File(dir, photo.mediaFileName())
        final.writeBytes(byteArrayOf(1))
        assertThrows(StorageFailure::class.java) {
            runBlocking { imports().discardCaptureSource(source, copy.candidate.digest) }
        }
        assertTrue(raw.isFile)
        final.writeBytes(bytes)

        raw.writeBytes(bytes)
        val protected = assertThrows(ImportValidationFailure::class.java) {
            runBlocking { imports().discardCaptureSource(source, sha256(bytes)) }
        }
        assertEquals(ImportRejection.CAMERA_SOURCE_PROTECTED, protected.reason)
        assertThrows(ImportValidationFailure::class.java) {
            runBlocking { imports().discardCaptureSource(source, copy.candidate.digest) }
        }
        raw.writeBytes(other)

        val unrelated = revisions.values.single().copy(
            revisionId = UUID.randomUUID().toString(), saveOperationId = UUID.randomUUID().toString(),
        )
        db.revisions[unrelated.revisionId] = unrelated
        assertThrows(ImportValidationFailure::class.java) {
            runBlocking { imports().discardCaptureSource(source, copy.candidate.digest) }
        }
        assertArrayEquals(other, raw.readBytes())
        db.revisions.remove(unrelated.revisionId)

        failUnlinkBarrier = true
        assertThrows(IOException::class.java) {
            runBlocking { imports().discardCaptureSource(source, copy.candidate.digest) }
        }
        failUnlinkBarrier = false
        imports().discardCaptureSource(source, copy.candidate.digest)
        assertTrue(imports().list().confirmedWithRecoverySources.isEmpty())
        assertEquals(head, db.heads[id.documentId])
        assertEquals(revisions, db.revisions)
        assertEquals(confirmed.snapshot, imports().open(id).snapshot)
        assertArrayEquals(bytes, final.readBytes())
    }

    @Test
    fun deletingOnePendingConflictLeavesTheCompetingStageAndOtherAttempts() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val files = staging(dir)
        val imports = PhotoImports(dir, {}, files, db, TestCatalog(db)) { _, _ -> error("No revision expected") }
        val bytes = bundledSyntheticPhoto()
        val id = imports.beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        raw.writeBytes(bytes)
        val firstStage = imports.stageCapture(id)
        val secondStage = files.receive((bytes + byteArrayOf(1)).inputStream(), id)
        val other = imports.beginCapture()
        imports.discardCaptureSource(CaptureSource.Camera(id), sha256(bytes))
        assertEquals(setOf(id, other), imports.list().captures.toSet())
        assertEquals(listOf(CaptureSource.Stage(firstStage), CaptureSource.Stage(secondStage)).toSet(),
            imports.captureSources(id).toSet())
        imports.discardCaptureSource(CaptureSource.Stage(secondStage), secondStage.digest)
        assertArrayEquals(bytes, files.read(firstStage))
        assertTrue(CameraCaptureStore.outputFile(dir, other).exists())
    }

    @Test
    fun unreadablePendingPublicationBlocksSelectiveDiscard() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val imports = PhotoImports(dir, {}, staging(dir), db, catalog) { _, _ -> error("No revision expected") }
        val id = imports.beginCapture()
        val bytes = bundledSyntheticPhoto()
        val output = CameraCaptureStore.outputFile(dir, id)
        output.writeBytes(bytes)
        catalog.rows[id.name] = PhotoImportRow(id.name, ByteArray(4097))
        assertNull(catalog.find(id.name))
        val protected = assertThrows(ImportValidationFailure::class.java) {
            runBlocking { imports.discardCaptureSource(CaptureSource.Camera(id), sha256(bytes)) }
        }
        assertEquals(ImportRejection.CAMERA_SOURCE_PROTECTED, protected.reason)
        assertArrayEquals(bytes, output.readBytes())
    }

    @Test
    fun cameraAttemptSurvivesRestartAndFailedConfirmationWithOriginalBytes() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        fun imports() = PhotoImports(dir, {}, staging(dir), db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source)) {}
        }
        val bytes = bundledSyntheticPhoto() + byteArrayOf(0x53, 0x45, 0x46)
        val first = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, first)
        assertTrue(raw.isFile)
        raw.writeBytes(bytes)
        val restarted = imports()
        assertEquals(listOf(first), restarted.list().captures)
        val candidate = restarted.stageCapture(first)
        assertEquals(first, candidate.photoId)
        assertEquals(candidate, imports().stageCapture(first))
        assertEquals(sha256(bytes), candidate.digest)
        assertArrayEquals(bytes, staging(dir).read(candidate))
        assertTrue(restarted.list().pending.isEmpty())
        assertTrue(restarted.list().confirmed.isEmpty())

        val retake = restarted.beginCapture()
        assertNotEquals(first, retake)
        assertArrayEquals(bytes, raw.readBytes())
        val photo = testPhoto(candidate, bytes.size)
        db.failPublish = true
        assertThrows(UncertainDocumentSave::class.java) { runBlocking { restarted.confirm(candidate, photo) } }
        assertTrue(restarted.list().confirmed.isEmpty())
        assertArrayEquals(bytes, raw.readBytes())
        assertArrayEquals(bytes, staging(dir).read(candidate))

        db.failPublish = false
        val verified = imports().confirm(candidate, photo)
        assertEquals(photo.id.name, db.revisions.values.single().saveOperationId)
        assertEquals(photo, verified.snapshot.photo)
        assertArrayEquals(bytes, imports().open(photo.id).photoBytes)
        assertFalse(raw.exists())
        assertTrue(staging(dir).candidates().isEmpty())
        assertEquals(listOf(retake), restarted.list().captures)
        assertThrows(ImportValidationFailure::class.java) { runBlocking { restarted.rejectCapture(candidate) } }
        assertArrayEquals(bytes, restarted.open(first).photoBytes)
    }

    @Test
    fun canceledAndMissingCameraOutputNeverCreatesARevision() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val imports = PhotoImports(dir, {}, staging(dir), db, TestCatalog(db)) { _, _ -> error("No revision expected") }

        val empty = imports.beginCapture()
        val error = assertThrows(StorageFailure::class.java) { runBlocking { imports.stageCapture(empty) } }
        assertEquals("CAMERA_CAPTURE / CAMERA_OUTPUT_EMPTY", storageFailureCode(error, StoragePhase.OPEN))
        imports.finishCanceledCapture(empty)
        assertFalse(CameraCaptureStore.outputFile(dir, empty).exists())
        val missing = assertThrows(StorageFailure::class.java) { runBlocking { imports.stageCapture(empty) } }
        assertEquals("CAMERA_CAPTURE / FILE_UNAVAILABLE", storageFailureCode(missing, StoragePhase.OPEN))

        val written = imports.beginCapture()
        val output = CameraCaptureStore.outputFile(dir, written)
        output.writeBytes(byteArrayOf(1, 2, 3))
        imports.finishCanceledCapture(written)
        assertEquals(listOf(written), imports.list().captures)
        assertTrue(staging(dir).candidates().isEmpty())
        val candidate = imports.stageCapture(written)
        File(dir, candidate.fileName).writeBytes(byteArrayOf(3, 2, 1))
        assertThrows(StorageFailure::class.java) { runBlocking { imports.preview(candidate) } }.also {
            assertEquals("IMPORT_PREVIEW / STAGED_INTEGRITY", storageFailureCode(it, StoragePhase.OPEN))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), output.readBytes())
        assertThrows(ImportValidationFailure::class.java) { runBlocking { imports.rejectCapture(candidate) } }
        assertTrue(output.exists())
        assertEquals(listOf(candidate), staging(dir).candidates())
        assertTrue(imports.list().confirmed.isEmpty())
    }

    @Test
    fun cameraLostRevisionAckKeepsSourcesAndPreventsRestagingAConfirmedId() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        var denyAck = false
        fun verifyDirectory() {
            if (denyAck) throw IOException("Injected database directory sync failure")
        }
        fun imports() = PhotoImports(dir, { verifyDirectory() }, staging(dir), db, catalog) { photo, source ->
            DocumentRepository(db, PrivatePhotoStore(dir, {}, ::rename, photo, source), ::verifyDirectory)
        }
        val id = imports().beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val bytes = bundledSyntheticPhoto()
        raw.writeBytes(bytes)
        val candidate = imports().stageCapture(id)
        val photo = testPhoto(candidate, bytes.size)

        denyAck = true
        assertThrows(UncertainDocumentSave::class.java) { runBlocking { imports().confirm(candidate, photo) } }
        assertEquals(listOf(id), imports().list().confirmed)
        assertTrue(imports().list().captures.isEmpty())
        assertThrows(StorageFailure::class.java) { runBlocking { imports().stageCapture(id) } }
        assertThrows(StorageFailure::class.java) { runBlocking { imports().confirm(candidate, photo) } }
        assertThrows(StorageFailure::class.java) { runBlocking { imports().open(id) } }
        assertArrayEquals(bytes, raw.readBytes())
        assertArrayEquals(bytes, staging(dir).read(candidate))
        assertEquals(1, db.revisions.size)

        denyAck = false
        assertArrayEquals(bytes, imports().open(id).photoBytes)
        assertFalse(raw.exists())
        assertTrue(staging(dir).candidates().isEmpty())
        assertEquals(1, db.revisions.size)
    }

    @Test
    fun lateCameraWriterCannotSubstituteDifferentBytesForTheStagedCandidate() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val imports = PhotoImports(dir, {}, staging(dir), db, TestCatalog(db)) { _, _ -> error("No revision expected") }
        val id = imports.beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val original = bundledSyntheticPhoto()
        raw.writeBytes(original)
        val candidate = imports.stageCapture(id)
        val changed = original + byteArrayOf(1, 2, 3)
        raw.writeBytes(changed)

        val error = assertThrows(StorageFailure::class.java) { runBlocking { imports.stageCapture(id) } }
        assertEquals("CAMERA_CAPTURE / CAMERA_OUTPUT_CHANGED", storageFailureCode(error, StoragePhase.OPEN))
        assertArrayEquals(original, staging(dir).read(candidate))
        assertArrayEquals(changed, raw.readBytes())
        assertTrue(imports.list().confirmed.isEmpty())
    }

    @Test
    fun unstableCameraSourceDoesNotPublishAPartialStageAndCanBeRetried() = runBlocking {
        val dir = folder.newFolder()
        val store = CameraCaptureStore(File(dir, CameraCaptureStore.DIRECTORY)) {}
        val id = store.begin()
        val output = CameraCaptureStore.outputFile(dir, id)
        val original = bundledSyntheticPhoto()
        val completed = original + byteArrayOf(1, 2, 3)
        output.writeBytes(original)
        val stage = staging(dir)
        val error = assertThrows(ImportValidationFailure::class.java) {
            store.readOutput(id) { input ->
                stage.receive(input, id) { candidate ->
                    output.writeBytes(completed)
                    if (!store.matches(id, candidate.digest)) {
                        throw ImportValidationFailure(ImportRejection.CAMERA_OUTPUT_CHANGED)
                    }
                }
            }
        }
        assertEquals(ImportRejection.CAMERA_OUTPUT_CHANGED, error.reason)
        assertTrue(stage.candidates().isEmpty())
        assertArrayEquals(completed, output.readBytes())
        val db = TestDocumentTransactions()
        val imports = PhotoImports(dir, {}, stage, db, TestCatalog(db)) { _, _ -> error("No revision expected") }
        assertArrayEquals(completed, stage.read(imports.stageCapture(id)))
    }

    @Test
    fun cameraStageRenameWithoutDirectoryAckKeepsRawAndReusesTheSameCandidate() = runBlocking {
        val dir = folder.newFolder()
        val db = TestDocumentTransactions()
        val catalog = TestCatalog(db)
        val unstable = PhotoImports(dir, {}, staging(dir) { throw IOException("Injected stage fsync failure") },
            db, catalog) { _, _ -> error("No revision expected") }
        val id = unstable.beginCapture()
        val raw = CameraCaptureStore.outputFile(dir, id)
        val bytes = bundledSyntheticPhoto()
        raw.writeBytes(bytes)
        assertThrows(StorageFailure::class.java) { runBlocking { unstable.stageCapture(id) } }
        val stage = staging(dir)
        val existing = stage.candidates().single()
        val restarted = PhotoImports(dir, {}, stage, db, catalog) { _, _ -> error("No revision expected") }
        assertEquals(id, existing.photoId)
        assertEquals(existing, restarted.stageCapture(id))
        assertArrayEquals(bytes, stage.read(existing))
        assertArrayEquals(bytes, raw.readBytes())
        assertTrue(restarted.list().confirmed.isEmpty())
    }

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
        } catch (_: ImportValidationFailure) {
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
    fun partialPolicyRejectsNewImagesAndPreservesLegacyReadCompatibility() {
        for (error in listOf(DecodeException.SOURCE_INCOMPLETE, DecodeException.SOURCE_MALFORMED_DATA,
            DecodeException.SOURCE_EXCEPTION, Int.MAX_VALUE)) {
            assertFalse(canShowPartialJpeg(error))
        }
        assertTrue(canShowPartialJpeg(DecodeException.SOURCE_INCOMPLETE, allowPartial = true))
        assertTrue(canShowPartialJpeg(DecodeException.SOURCE_MALFORMED_DATA, allowPartial = true))
        assertFalse(canShowPartialJpeg(DecodeException.SOURCE_EXCEPTION, allowPartial = true))
        assertFalse(canShowPartialJpeg(Int.MAX_VALUE, allowPartial = true))
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

    override suspend fun find(id: String): PhotoImportRow? = rows[id]?.takeIf { it.metadata.size <= 4096 }
    override suspend fun exists(id: String): Boolean = id in rows
    override suspend fun insert(row: PhotoImportRow) {
        check(row.photoId !in rows)
        rows[row.photoId] = row
    }
    override suspend fun delete(id: String) { rows.remove(id) }
    override suspend fun deleteEmptyHead(id: String) {
        if (db.heads[id]?.revisionId == null) db.heads.remove(id)
    }
}
