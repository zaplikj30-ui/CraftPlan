package cz.craftplan.app

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

/** Simulates transaction isolation/rollback independently of the Android Room runtime. */
internal class TestDocumentTransactions : DocumentTransactions, DocumentDao {
    var headRow: DocumentHead? = null
    val revisions = linkedMapOf<String, DocumentRevision>()
    var failPublish = false

    override suspend fun <T> read(block: suspend DocumentDao.() -> T): T = block(this)

    override suspend fun <T> write(block: suspend DocumentDao.() -> T): T {
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
