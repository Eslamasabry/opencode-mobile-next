package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/** Deliberately retains no throwable.toString(), thread name, arbitrary message,
 * suppressed exception or file path. Exception messages can contain chat text,
 * login codes and short tokens which pattern redaction cannot reliably find.
 */
class NativeCrashStore(private val directory: File) {
    private val fileName = "native-last-crash.properties"
    private val symbol = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")
    private val source = Regex("^[A-Za-z0-9_$-]+\\.(?:kt|java)$")
    private val frame = Regex("^[A-Za-z0-9_.$]+\\([A-Za-z0-9_$-]+\\.(?:kt|java):[0-9]{1,7}\\)$")

    fun install(): Thread.UncaughtExceptionHandler {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            try { write(error) } catch (_: Throwable) { }
            finally {
                if (previous != null) previous.uncaughtException(thread, error)
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
        val summaries = mutableListOf<Map<String, Any?>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && summaries.size < 4 && visited.add(current)) {
            summaries.add(summary(current, if (summaries.isEmpty()) 24 else 8))
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
        check(temporary.renameTo(destination))
    }

    private val allowedMessages = setOf("Permission denied", "Input/output failure", "Missing value",
        "Invalid argument", "Invalid state", "Interrupted operation", "[redacted]")

    private fun summary(error: Throwable, frameLimit: Int): Map<String, Any?> {
        val kind = error.javaClass.name.takeIf { it.length <= 180 && symbol.matches(it) } ?: "java.lang.Throwable"
        val message = when (error) {
            is SecurityException -> "Permission denied"
            is java.io.IOException -> "Input/output failure"
            is NullPointerException -> "Missing value"
            is IllegalArgumentException -> "Invalid argument"
            is IllegalStateException -> "Invalid state"
            is InterruptedException -> "Interrupted operation"
            else -> "[redacted]"
        }
        val frames = error.stackTrace.take(frameLimit).mapNotNull { element ->
            val file = element.fileName ?: return@mapNotNull null
            if (element.className.length > 180 || element.methodName.length > 100 ||
                !symbol.matches(element.className) || !symbol.matches(element.methodName) ||
                !source.matches(file) || element.lineNumber !in 0..9999999) return@mapNotNull null
            "${element.className}.${element.methodName}($file:${element.lineNumber})"
        }
        return mapOf("exceptionClass" to kind, "message" to message, "frames" to frames)
    }

    @Synchronized
    fun read(): Map<String, Any?>? = try {
        val file = target(fileName)
        if (!file.isFile || file.length() !in 1..24576) null
        else {
            val data = Properties().apply { file.inputStream().use { load(it) } }
            val timestamp = data.getProperty("timestamp")?.toLongOrNull() ?: 0L
            fun summary(prefix: String): Map<String, Any?>? {
                val kind = data.getProperty("${prefix}exceptionClass", "")
                if (kind.length > 180 || !symbol.matches(kind)) return null
                val message = data.getProperty("${prefix}message", "[redacted]")
                val frames = data.getProperty("${prefix}frames", "").split('\n')
                    .filter { it.length <= 360 && frame.matches(it) }.take(if (prefix.isEmpty()) 24 else 8)
                return mapOf("exceptionClass" to kind, "message" to if (message in allowedMessages) message else "[redacted]",
                    "frames" to frames)
            }
            val outer = summary("")
            if (timestamp <= 0 || outer == null) null
            else outer + mapOf("timestamp" to timestamp,
                "causes" to (1..3).mapNotNull { summary("cause.$it.") })
        }
    } catch (_: Throwable) { null }
}
