package io.github.eslamasabry.opencode_mobile

import android.system.Os
import java.io.File
import java.io.FileOutputStream

/** A safe category, never a path or an underlying filesystem message. */
class SetupPersistenceException : Exception(CODE) {
    companion object { const val CODE = "setup_persistence" }
}

/** Called only by SetupRunner's state/persistence owner, while holding its lock. */
internal object SetupPersistence {
    fun replace(file: File, text: String) {
        var temporary: File? = null
        try {
            val parent = file.parentFile ?: throw SetupPersistenceException()
            check(parent.isDirectory || parent.mkdirs())
            temporary = File.createTempFile("setup-", ".tmp", parent)
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // POSIX rename atomically replaces the destination on supported
            // Android versions. Failure keeps the previous complete snapshot;
            // never fall back to truncating the live file.
            Os.rename(temporary.path, file.path)
        } catch (_: Exception) {
            throw SetupPersistenceException()
        } finally {
            temporary?.delete()
        }
    }
}
