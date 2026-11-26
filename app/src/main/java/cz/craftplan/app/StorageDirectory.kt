package cz.craftplan.app

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

internal fun syncPrivateDirectory(directory: File) {
    try {
        val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw IOException("Expected a private directory")
            Os.fsync(fd)
        } finally {
            Os.close(fd)
        }
    } catch (error: ErrnoException) {
        throw IOException("Private directory sync failed", error)
    }
}
