package cz.craftplan.app

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun snapshotOverTheLimitNeverTouchesStorageAndAQueuedCorrectionCanBePublished() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        val repository = DocumentRepository(storage, PrivatePhotoStore(dir, {}) { bundledSyntheticPhoto() }) {}
        val initial = repository.open()
        val confirmed = repository.save(DocumentSave(initial.version, 1, initial.snapshot))
        val session = DocumentAutosave(confirmed.version)
        val dimension = PhotoDimension(UUID.randomUUID(), initial.snapshot.photo.id,
            PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f))
        session.finishedEdit(initial.snapshot.copy(dimensions = List(20_001) {
            dimension.copy(id = UUID(0, it.toLong()))
        }))
        val rejected = checkNotNull(session.nextRequest())
        val corrected = initial.snapshot.copy(dimensions = listOf(dimension))
        session.finishedEdit(corrected)
        val accesses = storage.transactionAccesses
        val failure = fails<DocumentSnapshotRejected> { repository.save(rejected) }
        assertEquals("SNAPSHOT / INVALID_DATA", storageFailureCode(failure, StoragePhase.ACK))
        assertEquals(accesses, storage.transactionAccesses)
        assertEquals(confirmed.version.revisionId, storage.headRow?.revisionId)
        assertEquals(1, storage.revisions.size)
        assertTrue(session.recordFailure(rejected, failure))

        val replacement = checkNotNull(session.nextRequest())
        assertEquals(confirmed.version, replacement.base)
        assertEquals(corrected, replacement.snapshot)
        val ack = repository.save(replacement)
        assertTrue(session.acknowledge(ack))
        assertEquals(corrected, repository.open().snapshot)
        assertEquals(2, storage.revisions.size)
        assertTrue(rejected.targetId !in storage.revisions)
    }

    @Test
    fun reopenReadsConfirmedContentAndAcceptsTheSameOperationWithoutAnotherRevision() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        val photos = PrivatePhotoStore(dir, {}) { bundledSyntheticPhoto() }
        val repository = DocumentRepository(storage, photos) {}
        val first = repository.open()
        assertNull(first.version.revisionId)
        val request = DocumentSave(first.version, 1, first.snapshot.copy(showNotes = false))
        val ack = repository.save(request)
        assertEquals(ack, repository.save(request))
        assertEquals(1, storage.revisions.size)

        val reopened = DocumentRepository(storage, PrivatePhotoStore(dir, {}) {
            error("Confirmed open must not read the bundled JPEG")
        }) {}.open()
        assertEquals(ack.version, reopened.version)
        assertEquals(request.snapshot, reopened.snapshot)
        assertArrayEquals(bundledSyntheticPhoto(), reopened.photoBytes)
    }
    @Test
    fun failedPublicationRollsBackAndRetryUsesTheSameIdentifiers() = runBlocking {
        val storage = TestDocumentTransactions()
        val repository = newRepository(storage)
        val base = repository.open()
        val first = DocumentSave(base.version, 1, measuredSnapshot())
        val firstAck = repository.save(first)
        val second = DocumentSave(firstAck.version, 2, first.snapshot.copy(showNotes = false))
        storage.failPublish = true
        fails<UncertainDocumentSave> { repository.save(second) }
        assertEquals(setOf(first.targetId), storage.revisions.keys)
        assertEquals(first.snapshot, repository.open().snapshot)
        storage.failPublish = false
        assertEquals(second.operationId, repository.save(second).operationId)
        assertEquals(setOf(first.targetId, second.targetId), storage.revisions.keys)
        assertEquals(second.snapshot, repository.open().snapshot)
    }

    @Test
    fun firstSaveRenamesOnlyInsideWriterAndPermissionFailureCanBeRetried() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        var denyRename = true
        var renameCount = 0
        val photos = PrivatePhotoStore(dir, {
            assertTrue(storage.writerActive)
            assertTrue(storage.revisions.isEmpty())
        }, rename = { staged, final ->
            assertTrue(storage.writerActive)
            assertEquals(staged.parentFile, final.parentFile)
            assertTrue(!final.exists())
            assertArrayEquals(bundledSyntheticPhoto(), staged.readBytes())
            if (denyRename) throw AccessDeniedException("/private/path/never-display")
            Files.move(staged.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
            renameCount++
        }, bundledBytes = ::bundledSyntheticPhoto)
        val repository = DocumentRepository(storage, photos) {}
        val request = DocumentSave(repository.open().version, 1, measuredSnapshot())
        val failure = fails<StorageFailure> { repository.save(request) }
        assertEquals("PHOTO_RENAME / ACCESS_DENIED", storageFailureCode(failure, StoragePhase.ACK))
        assertNull(storage.headRow?.revisionId)
        assertTrue(dir.listFiles()!!.isEmpty())

        denyRename = false
        assertEquals(request.targetId, repository.save(request).version.revisionId)
        assertEquals(1, renameCount)
        assertArrayEquals(bundledSyntheticPhoto(), dir.listFiles()!!.single().readBytes())
    }

    @Test
    fun missingMediaBarrierCannotPublishARevision() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        var mediaFails = true
        val repository = DocumentRepository(storage, PrivatePhotoStore(dir, {
            if (mediaFails) throw IOException("Injected media directory sync error")
        }, bundledBytes = ::bundledSyntheticPhoto)) {}
        val base = repository.open()
        val request = DocumentSave(base.version, 1, measuredSnapshot())
        val error = fails<StorageFailure> { repository.save(request) }
        assertEquals("PHOTO_DIRECTORY_SYNC / IO_ERROR", storageFailureCode(error, StoragePhase.ACK))
        assertNull(storage.headRow?.revisionId)
        assertTrue(storage.revisions.isEmpty())
        mediaFails = false
        assertEquals(request.targetId, repository.save(request).version.revisionId)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun postCommitLostAckIsResolvedAcrossNewRepositoryInstances() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        val photos = PrivatePhotoStore(dir, {}, bundledBytes = ::bundledSyntheticPhoto)
        val repository = DocumentRepository(storage, photos) { throw IOException("Injected lost ACK") }
        val request = DocumentSave(repository.open().version, 1, measuredSnapshot())
        val error = fails<UncertainDocumentSave> { repository.save(request) }
        assertEquals("DB_DIRECTORY_SYNC / IO_ERROR", storageFailureCode(error, StoragePhase.ACK))
        assertEquals(request.targetId, storage.headRow!!.revisionId)

        val restarted = DocumentRepository(storage, photos) {}
        assertEquals(request.snapshot, restarted.open().snapshot)
        val ack = restarted.save(request)
        assertEquals(request.targetId, ack.version.revisionId)
        assertEquals(1, storage.revisions.size)
        fails<DocumentConflict> { restarted.save(request.copy(snapshot = request.snapshot.copy(showNotes = false))) }
        assertEquals(request.snapshot, restarted.open().snapshot)
    }

    @Test
    fun rivalWriterAndOldStorageGenerationCannotReplaceTheHead() = runBlocking {
        val storage = TestDocumentTransactions()
        val repository = newRepository(storage)
        val base = repository.open()
        val loser = DocumentSave(base.version, 1, measuredSnapshot())
        val winner = DocumentSave(base.version, 1, base.snapshot)
        storage.beforeWrite = { repository.save(winner) }
        fails<DocumentConflict> { repository.save(loser) }
        assertEquals(setOf(winner.targetId), storage.revisions.keys)

        val latest = repository.save(DocumentSave(repository.open().version, 2, measuredSnapshot()))
        fails<DocumentConflict> { repository.save(winner) }
        val staleGeneration = latest.version.copy(storageGeneration = UUID.randomUUID().toString())
        fails<DocumentConflict> { repository.save(DocumentSave(staleGeneration, 3, base.snapshot)) }
        assertEquals(latest.version, repository.open().version)
    }

    @Test
    fun recoversOlderVerifiedSnapshotWithoutRepairingOrOverwritingDamagedHead() = runBlocking {
        val storage = TestDocumentTransactions()
        val repository = newRepository(storage)
        val base = repository.open()
        val first = repository.save(DocumentSave(base.version, 1, measuredSnapshot()))
        val later = repository.save(DocumentSave(first.version, 2, base.snapshot))
        val corruptedId = checkNotNull(later.version.revisionId)
        storage.revisions[corruptedId] = storage.revisions.getValue(corruptedId).copy(snapshot = byteArrayOf(1))
        assertOlderVerified(repository, first)
        assertEquals(corruptedId, storage.headRow!!.revisionId)
        assertEquals(2, storage.revisions.size)
        val request = DocumentSave(first.version, 3, base.snapshot)
        fails<DocumentConflict> { repository.save(request) }

        storage.headRow = storage.headRow!!.copy(revisionId = "invalid")
        assertOlderVerified(repository, first)
        assertEquals("invalid", storage.headRow!!.revisionId)

        storage.headRow = storage.headRow!!.copy(revisionId = null, previousRevisionId = null)
        fails<IOException> { repository.open() }
        assertEquals(2, storage.revisions.size)
    }

    @Test
    fun refusesReopenWhenAllConfirmedRevisionsHaveLostTheirSharedPhoto() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        val repository = DocumentRepository(storage, PrivatePhotoStore(dir, {}, bundledBytes = ::bundledSyntheticPhoto)) {}
        val version = repository.open().version
        val ack = repository.save(DocumentSave(version, 1, measuredSnapshot()))
        val media = dir.listFiles()!!.single()
        assertTrue(media.delete())
        fails<IOException> { repository.open() }
        assertEquals(ack.version.revisionId, storage.headRow!!.revisionId)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    private fun newRepository(storage: TestDocumentTransactions) =
        DocumentRepository(storage, PrivatePhotoStore(folder.newFolder(), {}, bundledBytes = ::bundledSyntheticPhoto)) {}

    private fun measuredSnapshot() = DocumentSnapshot(
        listOf(PhotoDimension(
            UUID.fromString("5afcb6df-7aa8-40d1-b3fa-c041763380b4"),
            PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(-0.04f, 0.3f), PhotoAnchor(0.8f, 1.08f),
            measurement = ManualLengthMeasurement.parse("1/3 in"),
            displayUnit = LengthUnit.IN, note = "Poznámka", labelT = 0.34f,
        )),
        showNotes = true,
    )

    private suspend fun assertOlderVerified(repository: DocumentRepository, first: DocumentAck) {
        val recovery = repository.open()
        assertTrue(recovery.recovered)
        assertEquals(first.version, recovery.version)
        assertEquals(measuredSnapshot(), recovery.snapshot)
    }

    private suspend inline fun <reified T : Exception> fails(crossinline block: suspend () -> Unit): T {
        try {
            block()
            throw AssertionError("Expected ${T::class.simpleName}")
        } catch (e: Exception) {
            if (e is T) return e
            throw e
        }
    }

}

/** Simulates transaction isolation/rollback independently of the Android Room runtime. */
internal class TestDocumentTransactions : DocumentTransactions, DocumentDao {
    val heads = linkedMapOf<String, DocumentHead>()
    var headRow: DocumentHead?
        get() = heads[SYNTHETIC_DOCUMENT_ID]
        set(value) {
            if (value == null) heads.remove(SYNTHETIC_DOCUMENT_ID)
            else heads[SYNTHETIC_DOCUMENT_ID] = value
        }
    val revisions = linkedMapOf<String, DocumentRevision>()
    var transactionAccesses = 0
        private set
    var writerActive = false
        private set
    var failPublish = false
    var beforeWrite: (suspend () -> Unit)? = null

    override suspend fun <T> read(block: suspend DocumentDao.() -> T): T {
        transactionAccesses++
        return block(this)
    }

    override suspend fun <T> write(block: suspend DocumentDao.() -> T): T {
        transactionAccesses++
        beforeWrite?.let {
            beforeWrite = null
            it()
        }
        val previousHeads = heads.toMap()
        val previousRevisions = revisions.toMap()
        writerActive = true
        try {
            return block(this)
        } catch (e: Exception) {
            heads.clear()
            heads.putAll(previousHeads)
            revisions.clear()
            revisions.putAll(previousRevisions)
            throw e
        } finally {
            writerActive = false
        }
    }

    override suspend fun confirmedPhotoIds(): List<String> = heads.values
        .filter { it.revisionId != null && it.documentId != SYNTHETIC_DOCUMENT_ID }
        .map(DocumentHead::documentId)

    override suspend fun head(documentId: String): DocumentHead? = heads[documentId]
    override suspend fun revision(id: String): DocumentRevision? = revisions[id]
    override suspend fun operation(id: String): DocumentRevision? = revisions.values.singleOrNull { it.saveOperationId == id }
    override suspend fun hasRevisions(documentId: String): Boolean = revisions.values.any { it.documentId == documentId }
    override suspend fun revisionCount(documentId: String): Long =
        revisions.values.count { it.documentId == documentId }.toLong()
    override suspend fun insertHead(head: DocumentHead) {
        check(head.documentId !in heads)
        heads[head.documentId] = head
    }
    override suspend fun insertRevision(revision: DocumentRevision) {
        check(revision.revisionId !in revisions && operation(revision.saveOperationId) == null)
        revisions[revision.revisionId] = revision
    }
    override suspend fun publish(documentId: String, generation: String, expected: String?, target: String): Int {
        if (failPublish) throw IOException("Injected failure before the pointer update")
        val head = head(documentId) ?: return 0
        if (head.storageGeneration != generation || head.revisionId != expected) return 0
        heads[documentId] = head.copy(revisionId = target, previousRevisionId = expected)
        return 1
    }
}
