package io.github.eslamasabry.opencode_mobile

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/**
 * Reads this app user's processes from /proc as [ProcEntry]s, and signals one
 * only while it is still the same process (pid and start time) of this user.
 * A process that exits mid-read is simply left out.
 */
internal object NativeProcScan {
    /** Every live process of [uid]; [details] also reads each one's argv. */
    fun scan(uid: Int, details: Boolean = false): List<ProcEntry> =
        File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            dir.name.toIntOrNull()?.let { pid -> read(pid, uid, details) }
        }

    fun read(pid: Int, uid: Int, details: Boolean = false): ProcEntry? = try {
        val dir = "/proc/$pid"
        if (Os.stat(dir).st_uid != uid) null else {
            val stat = File(dir, "stat").readText()
            val parentIsInit = stat.substringAfterLast(')').trim().split(' ').getOrNull(1) == "1"
            // Only an orphan's tracer and executable matter to the reaper.
            val full = details || parentIsInit
            val tracer = if (full) File(dir, "status").useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
            } else null
            val exe = if (full) readLink("$dir/exe") else null
            ProcEntry.stat(stat, uid, tracer, exe, if (details) argv(pid) else emptyList())
        }
    } catch (_: IOException) { null } catch (_: ErrnoException) { null }

    private fun readLink(path: String): String? = try { Os.readlink(path) } catch (_: ErrnoException) { null }

    private fun argv(pid: Int): List<String> = try {
        File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
    } catch (_: IOException) { emptyList() }

    /** Sends [signal] to [entry] if it is still that process; false when it is gone or changed. */
    fun signalIfSame(entry: ProcEntry, signal: Int): Boolean {
        val sent = entry.sameProcess(read(entry.pid, entry.uid))
        if (sent) {
            try { Os.kill(entry.pid, signal) } catch (_: ErrnoException) { /* Exit raced the signal. */ }
        }
        return sent
    }

    /** Whether [entry] still runs (a zombie has already ended). */
    fun alive(entry: ProcEntry): Boolean =
        read(entry.pid, entry.uid)?.let { entry.sameProcess(it) && it.state != 'Z' } == true

    /**
     * SIGTERM to every one of [targets], then SIGKILL to those still there
     * after [graceMs]. Returns the ones that ended.
     */
    fun terminate(targets: List<ProcEntry>, graceMs: Long): List<ProcEntry> {
        targets.forEach { signalIfSame(it, OsConstants.SIGTERM) }
        waitGone(targets, graceMs)
        targets.filter { alive(it) }.forEach { signalIfSame(it, OsConstants.SIGKILL) }
        waitGone(targets, KILL_WAIT_MS)
        return targets.filterNot { alive(it) }
    }

    private fun waitGone(targets: List<ProcEntry>, ms: Long) {
        val deadline = System.nanoTime() + ms * NANOS_PER_MS
        while (targets.any { alive(it) } && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
    }

    private const val KILL_WAIT_MS = 1_000L
    private const val POLL_MS = 50L
    private const val NANOS_PER_MS = 1_000_000L
}
