package cz.craftplan.app

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

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
    fun missingMediaBarrierCannotPublishARevision() = runBlocking {
        val storage = TestDocumentTransactions()
        val dir = folder.newFolder()
        var mediaFails = true
        val repository = DocumentRepository(storage, PrivatePhotoStore(dir, {
            if (mediaFails) throw IOException("Injected media directory sync error")
        }, ::bundledSyntheticPhoto)) {}
        val base = repository.open()
        val request = DocumentSave(base.version, 1, measuredSnapshot())
        fails<IOException> { repository.save(request) }
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
        val photos = PrivatePhotoStore(dir, {}, ::bundledSyntheticPhoto)
        val repository = DocumentRepository(storage, photos) { throw IOException("Injected lost ACK") }
        val request = DocumentSave(repository.open().version, 1, measuredSnapshot())
        fails<UncertainDocumentSave> { repository.save(request) }
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
        val repository = DocumentRepository(storage, PrivatePhotoStore(dir, {}, ::bundledSyntheticPhoto)) {}
        val version = repository.open().version
        val ack = repository.save(DocumentSave(version, 1, measuredSnapshot()))
        val media = dir.listFiles()!!.single()
        assertTrue(media.delete())
        fails<IOException> { repository.open() }
        assertEquals(ack.version.revisionId, storage.headRow!!.revisionId)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    private fun newRepository(storage: TestDocumentTransactions) =
        DocumentRepository(storage, PrivatePhotoStore(folder.newFolder(), {}, ::bundledSyntheticPhoto)) {}

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

    private suspend inline fun <reified T : Exception> fails(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Exception) {
            if (e !is T) throw e
        }
    }

}

/** Simulates transaction isolation/rollback independently of the Android Room runtime. */
internal class TestDocumentTransactions : DocumentTransactions, DocumentDao {
    var headRow: DocumentHead? = null
    val revisions = linkedMapOf<String, DocumentRevision>()
    var failPublish = false
    var beforeWrite: (suspend () -> Unit)? = null

    override suspend fun <T> read(block: suspend DocumentDao.() -> T): T = block(this)

    override suspend fun <T> write(block: suspend DocumentDao.() -> T): T {
        beforeWrite?.let {
            beforeWrite = null
            it()
        }
        val previousHead = headRow
        val previousRevisions = revisions.toMap()
        try {
            return block(this)
        } catch (e: Exception) {
            headRow = previousHead
            revisions.clear()
            revisions.putAll(previousRevisions)
            throw e
        }
    }

    override suspend fun head(documentId: String): DocumentHead? = headRow?.takeIf { it.documentId == documentId }
    override suspend fun revision(id: String): DocumentRevision? = revisions[id]
    override suspend fun operation(id: String): DocumentRevision? = revisions.values.singleOrNull { it.saveOperationId == id }
    override suspend fun hasRevisions(documentId: String): Boolean = revisions.values.any { it.documentId == documentId }
    override suspend fun insertHead(head: DocumentHead) {
        check(headRow == null)
        headRow = head
    }
    override suspend fun insertRevision(revision: DocumentRevision) {
        check(revision.revisionId !in revisions && operation(revision.saveOperationId) == null)
        revisions[revision.revisionId] = revision
    }
    override suspend fun publish(documentId: String, generation: String, expected: String?, target: String): Int {
        if (failPublish) throw IOException("Injected failure before the pointer update")
        val head = head(documentId) ?: return 0
        if (head.storageGeneration != generation || head.revisionId != expected) return 0
        headRow = head.copy(revisionId = target, previousRevisionId = expected)
        return 1
    }
}
