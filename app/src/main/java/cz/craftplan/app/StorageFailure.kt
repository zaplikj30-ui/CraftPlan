package cz.craftplan.app

import android.system.ErrnoException
import androidx.sqlite.SQLiteException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.SyncFailedException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.NoSuchFileException
import kotlinx.coroutines.CancellationException

internal enum class StoragePhase(val photoPreparation: Boolean = false) {
    CAMERA_CAPTURE, IMPORT_STAGE, IMPORT_PREVIEW, OPEN, SNAPSHOT, REVISION_CHECK, REVISION_WRITE, REVISION_CONFIRM, ACK,
    DB_JOURNAL_MODE, DB_SYNC_MODE, DB_DIRECTORY_SYNC, CONFLICT,
    PHOTO_BUNDLE(true), PHOTO_STAGE_CREATE(true), PHOTO_STAGE_WRITE(true), PHOTO_STAGE_SYNC(true),
    PHOTO_VERIFY(true), PHOTO_RENAME(true), PHOTO_FILE_SYNC(true), PHOTO_DIRECTORY_SYNC(true),
}

internal class StorageFailure(val phase: StoragePhase, cause: Exception) : IOException(cause)

internal inline fun <T> storagePhase(phase: StoragePhase, block: () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException || error is StorageFailure || error is DocumentConflict) throw error
    throw StorageFailure(phase, error)
}

/** Only phase, fixed reason codes and numeric OS errno. No exception text or document contents. */
internal fun storageFailureCode(error: Exception, fallback: StoragePhase): String {
    val initial = when (error) {
        is UncertainDocumentSave, is DocumentSnapshotRejected -> error.cause
        else -> error
    }
    val phase = (initial as? StorageFailure)?.phase ?: fallback
    var cause: Throwable? = (initial as? StorageFailure)?.cause ?: initial
    var reason = "UNEXPECTED"
    repeat(6) {
        val current = cause ?: return "$phase / $reason"
        when (current) {
            is ImportValidationFailure -> return "$phase / ${current.reason.name}"
            is ErrnoException -> return "$phase / ERRNO_${current.errno}"
            is DocumentConflict -> return "${StoragePhase.CONFLICT} / STALE_REVISION"
            is AtomicMoveNotSupportedException -> return "$phase / ATOMIC_MOVE_UNSUPPORTED"
            is AccessDeniedException, is SecurityException -> reason = "ACCESS_DENIED"
            is SQLiteException -> reason = "SQLITE_ERROR"
            is SyncFailedException -> reason = "SYNC_FAILED"
            is NoSuchFileException, is FileNotFoundException -> reason = "FILE_UNAVAILABLE"
            is IllegalArgumentException -> reason = "INVALID_DATA"
            is IllegalStateException -> reason = "VALIDATION_FAILED"
            is IOException -> if (reason == "UNEXPECTED") reason = "IO_ERROR"
        }
        cause = current.cause
    }
    return "$phase / $reason"
}
