package io.github.eslamasabry.oc.android.termux

import java.io.File

/** Which Flutter-era Termux caller a RUN_COMMAND result answered. */
public enum class TermuxResultKind {
    /** A step of the Flutter Termux setup (`oc.setupToken`, TermuxSetupRunner). */
    Setup,

    /** A one-off command the Flutter app ran in Termux (`oc.executionId`). */
    Command,
}

/** One result that reached the app after the process that asked for it was gone. Never holds output. */
public data class TermuxLateResult(val kind: TermuxResultKind, val ok: Boolean?, val atMs: Long)

/**
 * E09-S04: Termux results that arrived with nobody waiting (the Flutter process that asked was replaced by the
 * update). Only the kind, whether it succeeded and when are kept: no stdout, stderr, script or token, so nothing
 * here can hold a credential or a file's contents. At most [LIMIT] entries; the oldest is dropped first. App-wide,
 * not per profile: the Flutter setup runner was app-wide (`files/termux-setup-v2.json`).
 */
public class TermuxLateResults(private val file: File) {
    public fun record(result: TermuxLateResult): Unit =
        synchronized(LOCK) {
            val next = (readUnlocked() + result).takeLast(LIMIT)
            write(next.joinToString("\n") { "${it.kind.name}|${it.ok?.toString() ?: "unknown"}|${it.atMs}" })
        }

    public fun read(): List<TermuxLateResult> = synchronized(LOCK) { readUnlocked() }

    public fun clear(): Unit =
        synchronized(LOCK) {
            file.delete()
            Unit
        }

    private fun readUnlocked(): List<TermuxLateResult> {
        val text =
            try {
                if (file.isFile) file.readText() else ""
            } catch (_: Exception) {
                ""
            }
        return text.lineSequence().mapNotNull(::parse).toList().takeLast(LIMIT)
    }

    private fun parse(line: String): TermuxLateResult? {
        val parts = line.split('|').takeIf { it.size == FIELDS }
        val kind = parts?.let { p -> TermuxResultKind.entries.firstOrNull { it.name == p[0] } }
        val at = parts?.get(2)?.toLongOrNull()?.takeIf { it >= 0 }
        if (kind == null || at == null) return null
        val ok =
            when (parts[1]) {
                "true" -> true
                "false" -> false
                else -> null
            }
        return TermuxLateResult(kind, ok, at)
    }

    private fun write(text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temporary.renameTo(file)) temporary.delete()
    }

    public companion object {
        public const val FILE_NAME: String = "termux-results-after-update.txt"
        public const val LIMIT: Int = 8
        private const val FIELDS = 3
        private val LOCK = Any()

        public fun inApp(filesDir: File): TermuxLateResults = TermuxLateResults(File(filesDir, FILE_NAME))
    }
}

/**
 * The body of `io.github.eslamasabry.opencode_mobile.TermuxResultService` (plan 11 §3 row "TermuxResultService",
 * kept while D7 = migrate-out). Termux delivers a RUN_COMMAND result to the exact class name the Flutter app put in
 * its PendingIntent, so a command started before the update is answered after it. Native code keeps no in-memory
 * callback from the old process, so the answer is recorded (without output) for the Termux notice instead of
 * being dropped; the Termux setup's own truth stays in Termux and is re-read there.
 */
public class TermuxResultBody(private val late: TermuxLateResults, private val nowMs: () -> Long) {
    /**
     * [setupToken] and [executionId] are the Flutter extras (`oc.setupToken`, `oc.executionId`); [err] and
     * [exitCode] come from Termux's `result` bundle (null when absent). Returns what was recorded, or null for an
     * intent that is not a Termux answer.
     */
    public fun accept(setupToken: String?, executionId: Int, err: Int?, exitCode: Int?): TermuxLateResult? {
        val kind =
            when {
                !setupToken.isNullOrEmpty() -> TermuxResultKind.Setup
                executionId >= 0 -> TermuxResultKind.Command
                else -> return null
            }
        val ok = if (err == null) null else err == RESULT_OK && exitCode == 0
        val result = TermuxLateResult(kind, ok, nowMs())
        late.record(result)
        return result
    }

    public companion object {
        /** `android.app.Activity.RESULT_OK`, which Termux puts in `err` when it ran the command. */
        public const val RESULT_OK: Int = -1
    }
}
