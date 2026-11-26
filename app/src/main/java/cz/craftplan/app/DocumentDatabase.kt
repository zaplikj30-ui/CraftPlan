package cz.craftplan.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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

// Keep recovery metadata readable without materializing an oversized, corrupt BLOB.
private const val REVISION_COLUMNS = "revisionId, documentId, storageGeneration, previousRevisionId, " +
    "saveOperationId, editGeneration, snapshotDigest, " +
    "CASE WHEN length(snapshot) <= $MAX_SNAPSHOT_BYTES THEN snapshot ELSE X'' END AS snapshot"

@Dao
internal interface DocumentDao {
    @Query("SELECT * FROM document_head WHERE documentId = :documentId")
    suspend fun head(documentId: String): DocumentHead?

    @Query("SELECT $REVISION_COLUMNS FROM document_revision WHERE revisionId = :id")
    suspend fun revision(id: String): DocumentRevision?

    @Query("SELECT $REVISION_COLUMNS FROM document_revision WHERE saveOperationId = :id")
    suspend fun operation(id: String): DocumentRevision?

    @Query("SELECT EXISTS(SELECT 1 FROM document_revision WHERE documentId = :documentId)")
    suspend fun hasRevisions(documentId: String): Boolean

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

@Entity(tableName = "photo_import")
internal data class PhotoImportRow(
    @PrimaryKey val photoId: String,
    val metadata: ByteArray,
)

internal val PHOTO_CATALOG_MIGRATION = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(PHOTO_CATALOG_SCHEMA)
    }
}

internal const val PHOTO_CATALOG_SCHEMA =
    "CREATE TABLE IF NOT EXISTS `photo_import` (`photoId` TEXT NOT NULL, `metadata` BLOB NOT NULL, PRIMARY KEY(`photoId`))"

@Database(entities = [DocumentHead::class, DocumentRevision::class, PhotoImportRow::class], version = 2)
internal abstract class DocumentDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
}
