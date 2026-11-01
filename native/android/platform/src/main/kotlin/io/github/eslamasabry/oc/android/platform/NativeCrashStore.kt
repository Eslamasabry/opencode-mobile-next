// Copied from android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeCrashStore.kt at cdfd713b
// (plan 11 §3 "copy minus channel glue"; it had none). The class body is unchanged except the package line and one
// marked S1-PLAT0 fix in write() (a dangling .tmp symlink was followed; docs/native/units/S1-PLAT0.md D2).
// The typed CrashStore port and NativeCrashRecords adapter below the class are native-authored (S1-PLAT0).
package io.github.eslamasabry.oc.android.platform

import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/** Deliberately retains no throwable.toString(), thread name, arbitrary message,
 * suppressed exception or file path. Exception messages can contain chat text,
 * login codes and short tokens which pattern redaction cannot reliably find.
 */
class NativeCrashStore(private val directory: File) {
    private companion object {
        const val MAX_CONSENT_BYTES = 32L
    }
    private val fileName = "native-last-crash.properties"
    private val allowedClasses = setOf("java.lang.SecurityException", "java.io.IOException",
        "java.lang.NullPointerException", "java.lang.IllegalArgumentException",
        "java.lang.IllegalStateException", "java.lang.InterruptedException", "java.lang.Throwable")
    private val categoryMessages = mapOf(
        "java.lang.SecurityException" to "Permission denied",
        "java.io.IOException" to "Input/output failure",
        "java.lang.NullPointerException" to "Missing value",
        "java.lang.IllegalArgumentException" to "Invalid argument",
        "java.lang.IllegalStateException" to "Invalid state",
        "java.lang.InterruptedException" to "Interrupted operation",
    )

    /** Shared with Dart's crash store. Absent/corrupt/future consent fails closed. */
    fun enabledSince(): Long = try {
        val consent = target("crash-diagnostics-consent")
        if (!consent.isFile || consent.length() !in 1..MAX_CONSENT_BYTES) 0L
        else (consent.readText().toLongOrNull() ?: 0L)
            .takeIf { it > 0L && it <= System.currentTimeMillis() } ?: 0L
    } catch (_: Throwable) { 0L }

    @Synchronized
    fun clear() {
        for (name in listOf(fileName, "$fileName.tmp")) {
            val file = target(name)
            check(!file.exists() || file.delete())
        }
    }

    fun install(): Thread.UncaughtExceptionHandler {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            try { write(error) } catch (_: Throwable) { }
            finally {
                // Preserve Android's fatal handling, but never hand its logger
                // an exception message/cause/stack carrying credential values.
                val safe = RuntimeException("Native application error")
                safe.stackTrace = emptyArray()
                if (previous != null) previous.uncaughtException(thread, safe)
            }
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
        return handler
    }

    private fun target(name: String): File {
        val anchor = directory.canonicalFile
        check(anchor.isDirectory)
        val file = File(anchor, name)
        check(file.canonicalFile == file.absoluteFile)
        return file
    }

    @Synchronized
    fun write(error: Throwable, timestamp: Long = System.currentTimeMillis()) {
        val consent = enabledSince()
        if (consent == 0L || timestamp < consent) {
            clear()
            return
        }
        val summaries = mutableListOf<Map<String, Any?>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && summaries.size < 4 && visited.add(current)) {
            summaries.add(summary(current))
            current = current.cause
        }
        val data = Properties().apply {
            setProperty("timestamp", timestamp.toString())
            for ((index, summary) in summaries.withIndex()) {
                val prefix = if (index == 0) "" else "cause.$index."
                setProperty("${prefix}exceptionClass", summary["exceptionClass"] as String)
                setProperty("${prefix}message", summary["message"] as String)
                setProperty("${prefix}frames", (summary["frames"] as List<*>).joinToString("\n"))
            }
        }
        val destination = target(fileName)
        val temporary = target("$fileName.tmp")
        // S1-PLAT0 fix (B-SEC-043): a dangling symlink passes target()'s canonical check and FileOutputStream would
        // follow it out of the directory. Remove whatever is at the path (the link itself, never its target) first.
        java.nio.file.Files.deleteIfExists(temporary.toPath())
        FileOutputStream(temporary).use {
            temporary.setReadable(false, false)
            temporary.setWritable(false, false)
            check(temporary.setReadable(true, true) && temporary.setWritable(true, true))
            data.store(it, null)
            it.fd.sync()
        }
        // Dart may have disabled capture while this native handler flushed.
        // This is an epoch recheck, not a cross-runtime lock: a fatal crash in
        // the final check/rename window can leave a category-only record which
        // read() discards while disabled and startup erases.
        if (enabledSince() != consent) {
            check(temporary.delete())
            return
        }
        check(temporary.renameTo(destination))
    }

    private val allowedMessages = setOf("Permission denied", "Input/output failure", "Missing value",
        "Invalid argument", "Invalid state", "Interrupted operation", "[redacted]")

    private fun summary(error: Throwable): Map<String, Any?> {
        val kind = when (error) {
            is SecurityException -> "java.lang.SecurityException"
            is java.io.IOException -> "java.io.IOException"
            is NullPointerException -> "java.lang.NullPointerException"
            is IllegalArgumentException -> "java.lang.IllegalArgumentException"
            is IllegalStateException -> "java.lang.IllegalStateException"
            is InterruptedException -> "java.lang.InterruptedException"
            else -> "java.lang.Throwable"
        }
        val message = categoryMessages[kind] ?: "[redacted]"
        // Throwable.stackTrace can itself be caller-supplied. No arbitrary
        // symbols, paths or exception values are allowed onto disk.
        return mapOf("exceptionClass" to kind, "message" to message, "frames" to emptyList<String>())
    }

    @Synchronized
    fun read(): Map<String, Any?>? = try {
        val consent = enabledSince()
        val file = target(fileName)
        if (consent == 0L) {
            clear()
            null
        } else if (!file.isFile || file.length() !in 1..24576) null
        else {
            val data = Properties().apply { file.inputStream().use { load(it) } }
            val timestamp = data.getProperty("timestamp")?.toLongOrNull() ?: 0L
            fun summary(prefix: String): Map<String, Any?>? {
                val kind = data.getProperty("${prefix}exceptionClass", "")
                if (kind !in allowedClasses) return null
                val message = data.getProperty("${prefix}message", "[redacted]")
                return mapOf("exceptionClass" to kind, "message" to if (message in allowedMessages) message else "[redacted]",
                    "frames" to emptyList<String>())
            }
            val outer = summary("")
            if (timestamp < consent || timestamp > System.currentTimeMillis() || outer == null) null
            else outer + mapOf("timestamp" to timestamp,
                "causes" to (1..3).mapNotNull { summary("cause.$it.") })
        }
    } catch (_: Throwable) { null }
}

// ---- Native-authored below this line (S1-PLAT0). ----

// TODO(move-to-core): plan 03 §18 puts `interface CrashStore` in :engine:diagnostics, which does not exist yet. Move
// CrashStore, CrashRecord and CrashKind there (common code, no platform types) when it is created; this file keeps
// only NativeCrashStore and NativeCrashRecords.

/** The latest JVM crash, by category only (plan 16 §1.10, B-SEC-043). Never a message, stack, thread or path. */
public interface CrashStore {
    /** The last crash recorded since consent; null when there is none, consent is off or the record is invalid. Never throws. */
    public fun latest(): CrashRecord?

    /** Deletes the record. Never throws; false when the store could not delete it. */
    public fun clear(): Boolean
}

/** The fixed categories the store can hold; [Other] is anything outside the whitelist. */
public enum class CrashKind { PermissionDenied, InputOutput, MissingValue, InvalidArgument, InvalidState, Interrupted, Other }

/** [causes] has at most three entries, outermost first. */
public data class CrashRecord(val kind: CrashKind, val atEpochMs: Long, val causes: List<CrashKind>)

/** Binds the copied [NativeCrashStore] to [CrashStore]. Everything the copy might throw is caught here. */
public class NativeCrashRecords(private val store: NativeCrashStore) : CrashStore {
    override fun latest(): CrashRecord? = try {
        store.read()?.let { raw ->
            val kind = kindOf(raw["exceptionClass"]) ?: return@let null
            val at = raw["timestamp"] as? Long ?: return@let null
            val causes = (raw["causes"] as? List<*>).orEmpty()
                .mapNotNull { kindOf((it as? Map<*, *>)?.get("exceptionClass")) }
                .take(MAX_CAUSES)
            CrashRecord(kind, at, causes)
        }
    } catch (_: Throwable) {
        null
    }

    override fun clear(): Boolean = try {
        store.clear()
        true
    } catch (_: Throwable) {
        false
    }

    private companion object {
        const val MAX_CAUSES = 3

        fun kindOf(exceptionClass: Any?): CrashKind? = when (exceptionClass) {
            "java.lang.SecurityException" -> CrashKind.PermissionDenied
            "java.io.IOException" -> CrashKind.InputOutput
            "java.lang.NullPointerException" -> CrashKind.MissingValue
            "java.lang.IllegalArgumentException" -> CrashKind.InvalidArgument
            "java.lang.IllegalStateException" -> CrashKind.InvalidState
            "java.lang.InterruptedException" -> CrashKind.Interrupted
            "java.lang.Throwable" -> CrashKind.Other
            else -> null
        }
    }
}
