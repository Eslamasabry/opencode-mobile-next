package io.github.eslamasabry.opencode_mobile

import android.os.Process
import java.io.File

/**
 * Easing the phone's agents (perf audit 2026-11-01, S2): moves every
 * program of a Paseo helper, and the proot that traces them, to Android's
 * background scheduling class (`Process.setThreadPriority`, which also puts
 * the thread in the background cpuset, onto the efficient cores) or back.
 *
 * Only this app's own processes are touched: the helpers are found by their
 * command line (`paseo daemon run --home /home/oc/.oc-profiles/`), and
 * everything below their proot, by parent or tracer, follows. Nothing is
 * signalled or stopped; replies keep streaming, tools just get less CPU.
 * Threads started later inherit their parent's class.
 */
internal object AgentPriority {
    const val DAEMON_MARK = "paseo daemon run --home /home/oc/.oc-profiles/"

    /** `normal`, `slow` (background class) or `hold` (lowest). */
    fun priorityOf(level: String): Int? = when (level) {
        "normal" -> Process.THREAD_PRIORITY_DEFAULT
        "slow" -> Process.THREAD_PRIORITY_BACKGROUND
        "hold" -> 19
        else -> null
    }

    /** Pids of the helpers: a program whose argv holds [DAEMON_MARK] and is not a shell running a script about it. */
    fun daemons(entries: List<ProcEntry>, appPid: Int): List<ProcEntry> = entries.filter { entry ->
        entry.pid != appPid && entry.state != 'Z' && entry.name != "sh" && entry.name != "bash" &&
            entry.argv.joinToString(" ").contains(DAEMON_MARK)
    }

    /** The helpers' proot tracers and everything below them, and the helpers themselves. */
    fun targets(entries: List<ProcEntry>, appPid: Int): List<Int> {
        val parents = entries.associate { it.pid to it.parent }
        val tracers = entries.mapNotNull { e -> e.tracer?.takeIf { it > 1 }?.let { e.pid to it } }.toMap()
        val found = LinkedHashSet<Int>()
        for (daemon in daemons(entries, appPid)) {
            found.add(daemon.pid)
            val tracer = daemon.tracer?.takeIf { it > 1 && it != appPid }
            if (tracer != null) {
                found.add(tracer)
                found.addAll(NativeProcessTree.descendants(tracer, parents, tracers))
            }
            found.addAll(NativeProcessTree.descendants(daemon.pid, parents, tracers))
        }
        found.remove(appPid)
        return found.toList()
    }

    data class Result(
        val processes: Int,
        val threads: Int,
        val failed: Int,
        /** The scheduling group of the first helper's main thread afterwards, for the proof. */
        val group: String?,
    ) {
        fun toMap(): Map<String, Any?> =
            mapOf("processes" to processes, "threads" to threads, "failed" to failed, "group" to group)
    }

    /**
     * Sets [level] on every thread of every target. [set] and [tidsOf] are
     * replaceable for tests.
     */
    fun apply(
        level: String,
        uid: Int,
        appPid: Int,
        scan: (Int) -> List<ProcEntry> = { NativeProcScan.scan(it, details = true) },
        tidsOf: (Int) -> List<Int> = { pid ->
            File("/proc/$pid/task").list().orEmpty().mapNotNull { it.toIntOrNull() }
        },
        set: (Int, Int) -> Unit = { tid, prio -> Process.setThreadPriority(tid, prio) },
        groupOf: (Int) -> String? = { tid -> cgroupOf(tid) },
    ): Result? {
        val priority = priorityOf(level) ?: return null
        val entries = scan(uid)
        val pids = targets(entries, appPid)
        var threads = 0
        var failed = 0
        for (pid in pids) {
            for (tid in tidsOf(pid)) {
                try {
                    set(tid, priority)
                    threads++
                } catch (_: Exception) {
                    failed++ // the thread ended, or Android refused
                }
            }
        }
        val first = daemons(entries, appPid).firstOrNull()?.pid
        return Result(pids.size, threads, failed, first?.let(groupOf))
    }

    private fun cgroupOf(tid: Int): String? = try {
        File("/proc/$tid/cgroup").readLines().firstOrNull { it.contains("cpuset") }
            ?: File("/proc/$tid/cgroup").readLines().firstOrNull()
    } catch (_: Exception) { null }
}
