package cz.craftplan.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

internal const val SYNTHETIC_DOCUMENT_ID = "craftplan-synthetic-workbench"

@Entity(tableName = "document_head")
internal data class DocumentHead(
    @PrimaryKey val documentId: String,
    val storageGeneration: String,
    val revisionId: String?,
    val previousRevisionId: String?,
)

@Entity(tableName = "document_revision", indices = [Index(value = ["saveOperationId"], unique = true)])
internal data class DocumentRevision(
    @PrimaryKey val revisionId: String,
    val documentId: String,
    val storageGeneration: String,
    val previousRevisionId: String?,
    val saveOperationId: String,
    val editGeneration: Long,
    val snapshotDigest: String,
    val snapshot: ByteArray,
)

@Dao
internal interface DocumentDao {
    @Query("SELECT * FROM document_head WHERE documentId = :documentId")
    suspend fun head(documentId: String): DocumentHead?

    @Query("SELECT * FROM document_revision WHERE revisionId = :id")
    suspend fun revision(id: String): DocumentRevision?

    @Query("SELECT * FROM document_revision WHERE saveOperationId = :id")
    suspend fun operation(id: String): DocumentRevision?

    @Insert
    suspend fun insertHead(head: DocumentHead)

    @Insert
    suspend fun insertRevision(revision: DocumentRevision)

    @Query(
        """UPDATE document_head SET revisionId = :target, previousRevisionId = :expected
           WHERE documentId = :documentId AND storageGeneration = :generation
             AND revisionId IS :expected""",
    )
    suspend fun publish(documentId: String, generation: String, expected: String?, target: String): Int
}

@Database(entities = [DocumentHead::class, DocumentRevision::class], version = 1)
internal abstract class DocumentDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
}
