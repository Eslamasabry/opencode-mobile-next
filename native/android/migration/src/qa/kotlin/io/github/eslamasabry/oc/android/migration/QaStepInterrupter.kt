package io.github.eslamasabry.oc.android.migration

import java.io.File

/**
 * `files/native/qa-kill-after` holds one point name (`S0`, `M1`, `M2:1`, `M2:all`, `M3`, `M4`, `M5`, `M6`, or a fine S0
 * point such as `S0:renamed`). When the runner reaches it, the file is deleted FIRST (so the relaunch runs through)
 * and then [kill] ends the process. `qa-delay-ms` and `qa-free-bytes` are decimal numbers. Compiled only into the
 * `qa` build type; release has no such code path.
 */
internal class QaStepInterrupter(
    private val paths: MigrationPaths,
    private val kill: () -> Unit,
) : StepInterrupter {
    override fun reached(point: String) {
        val file = paths.qaKillAfter
        val wanted = read(file) ?: return
        if (wanted != point) return
        file.delete()
        kill()
    }

    override fun delayMs(): Long = read(paths.qaDelayMs)?.toLongOrNull()?.coerceIn(0L, MAX_DELAY_MS) ?: 0L

    override fun freeBytesOverride(): Long? = read(paths.qaFreeBytes)?.toLongOrNull()

    private fun read(file: File): String? =
        if (file.isFile && file.length() in 1..MAX_FILE_BYTES) file.readText(Charsets.UTF_8).trim() else null

    private companion object {
        const val MAX_FILE_BYTES = 64L
        const val MAX_DELAY_MS = 120_000L
    }
}
