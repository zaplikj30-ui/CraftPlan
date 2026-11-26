package cz.craftplan.app

import android.content.Context
import android.system.Os
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transactor.SQLiteTransactionType.DEFERRED
import androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

internal object AppDocumentStorage {
    private var storage: DocumentStorage? = null

    @Synchronized
    fun get(context: Context): DocumentStorage =
        storage ?: openDocumentStorage(context.applicationContext).also { storage = it }
}

internal data class DocumentStorage(val synthetic: DocumentRepository, val imports: PhotoImports)

private fun openDocumentStorage(context: Context): DocumentStorage {
    val privateDirectory = context.filesDir
    // Persist the directory entry as well, should Android have just created filesDir.
    syncPrivateDirectory(checkNotNull(privateDirectory.parentFile))
    val database = Room.databaseBuilder<DocumentDatabase>(
        context, File(privateDirectory, "m1-documents.db").absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(PHOTO_CATALOG_MIGRATION)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    val transactions = RoomDocumentTransactions(database)
    val sync = ::syncPrivateDirectory
    val rename: (File, File) -> Unit = { staged, final ->
        // Same private directory. POSIX rename is atomic and is permitted by Android app policy.
        Os.rename(staged.absolutePath, final.absolutePath)
    }
    fun repository(photo: StoredPhoto, source: () -> ByteArray) =
        DocumentRepository(transactions, PrivatePhotoStore(privateDirectory, sync, rename, photo, source)) {
            sync(privateDirectory)
        }
    val synthetic = repository(StoredPhoto.synthetic) {
        context.resources.openRawResource(R.raw.synthetic_workbench).use {
            it.readNBytes(StoredPhoto.synthetic.size + 1)
        }
    }
    val imports = PhotoImports(privateDirectory, sync, ImportStaging(privateDirectory, sync, rename),
        transactions, database.photoImports(), ::repository)
    return DocumentStorage(synthetic, imports)
}

private class RoomDocumentTransactions(private val database: DocumentDatabase) : DocumentTransactions {
    override suspend fun <T> read(block: suspend DocumentDao.() -> T): T =
        database.useReaderConnection { connection ->
            connection.withTransaction(DEFERRED) { block(database.documents()) }
        }

    override suspend fun <T> write(block: suspend DocumentDao.() -> T): T =
        database.useWriterConnection { connection ->
            // Room 2.8.5 configures FULL in rollback journal mode. Verify it on
            // the actual writer rather than assuming a SQLite or Room default.
            storagePhase(StoragePhase.DB_JOURNAL_MODE) {
                check(connection.usePrepared("PRAGMA journal_mode = TRUNCATE") { it.step() && it.getText(0) == "truncate" })
            }
            storagePhase(StoragePhase.DB_SYNC_MODE) {
                check(connection.usePrepared("PRAGMA synchronous") { it.step() && it.getLong(0) == 2L })
            }
            connection.withTransaction(IMMEDIATE) { block(database.documents()) }
        }
}
