package io.github.eslamasabry.opencode_mobile

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
