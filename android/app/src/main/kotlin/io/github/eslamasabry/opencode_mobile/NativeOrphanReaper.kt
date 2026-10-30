package io.github.eslamasabry.opencode_mobile

private const val STAT_STATE = 0
private const val STAT_PARENT = 1
private const val STAT_USER_TICKS = 11
private const val STAT_SYSTEM_TICKS = 12
private const val STAT_START_TICKS = 19
private const val STAT_RSS_PAGES = 21
private const val STAT_FIELDS = 22

/**
 * One `/proc/<pid>` entry as the orphan reaper and the process list read it.
 * Pure data: [NativeProcScan] reads it from the kernel, tests write it.
 *
 * [tracer] is the TracerPid: 0 when nothing traces the process, null when its
 * `status` could not be read. [exe] is the target of `/proc/<pid>/exe`, null
 * when the kernel refused it.
 */
internal data class ProcEntry(
    val pid: Int,
    val uid: Int,
    val parent: Int,
    val tracer: Int?,
    val startTicks: Long,
    val name: String,
    val exe: String?,
    val state: Char = 'S',
    val cpuTicks: Long = 0,
    val rssPages: Long = 0,
    val argv: List<String> = emptyList(),
) {
    /** The same process as [other]: pid reuse gives a new start time. */
    fun sameProcess(other: ProcEntry?): Boolean =
        other != null && other.pid == pid && other.startTicks == startTicks && other.uid == uid

    companion object {
        /** Parses a `/proc/<pid>/stat` line; null when it is not one. */
        fun stat(text: String, uid: Int, tracer: Int?, exe: String?, argv: List<String> = emptyList()): ProcEntry? {
            val open = text.indexOf('(')
            val close = text.lastIndexOf(')')
            val pid = text.substringBefore(' ').trim().toIntOrNull()
            val fields = if (open in 1 until close) text.substring(close + 1).trim().split(' ') else emptyList()
            val parent = fields.getOrNull(STAT_PARENT)?.toIntOrNull()
            val start = fields.getOrNull(STAT_START_TICKS)?.toLongOrNull()?.takeIf { fields.size >= STAT_FIELDS }
            if (pid == null || parent == null || start == null) return null
            val ticks = (fields[STAT_USER_TICKS].toLongOrNull() ?: 0L) +
                (fields[STAT_SYSTEM_TICKS].toLongOrNull() ?: 0L)
            return ProcEntry(pid, uid, parent, tracer, start, text.substring(open + 1, close), exe,
                fields[STAT_STATE].firstOrNull() ?: '?', ticks, fields[STAT_RSS_PAGES].toLongOrNull() ?: 0L, argv)
        }
    }
}

/**
 * Which processes a dead proot left behind (the 2026-10-27 phone incident:
 * Android SIGKILLed the agent helper's proot, so `--kill-on-exit` never ran;
 * five of Claude Code's `bash -c` tool shells and a `flutter_tester` lost
 * their tracer and spun at 100% CPU each for 19 minutes, unreaped).
 *
 * An orphan here is, all at once:
 * - this app's user, and neither the app itself nor pid 0/1;
 * - parented by init (its parent died) and traced by nothing (its proot
 *   died: proot traces everything it runs from the fork on, so a live
 *   proot's program always names it as TracerPid);
 * - a Ubuntu program: its executable is proot's loader, through which proot
 *   runs every program of the guest (the app, Android's own processes and
 *   the native engine run other executables and are never chosen);
 * - not below a live root the app started, by parent or tracer.
 *
 * The untraced children of such an orphan go with it. Anything unknown
 * (status or exe unreadable) is never chosen, and zombies are already dead.
 */
internal object NativeOrphanReaper {
    private const val LOADER_PREFIX = "libproot-loader"
    private const val DELETED = " (deleted)"

    /** Whether [exe] is proot's loader, also when an app update deleted that copy. */
    fun isGuestProgram(exe: String?): Boolean {
        val base = exe?.removeSuffix(DELETED)?.substringAfterLast('/') ?: return false
        return base.startsWith(LOADER_PREFIX) && base.endsWith(".so")
    }

    fun orphans(entries: List<ProcEntry>, appUid: Int, appPid: Int, liveRoots: Set<Int>): List<ProcEntry> {
        val parents = entries.associate { it.pid to it.parent }
        val tracers = entries.mapNotNull { entry -> entry.tracer?.takeIf { it > 1 }?.let { entry.pid to it } }.toMap()
        val alive = HashSet(liveRoots)
        for (root in liveRoots) alive.addAll(NativeProcessTree.descendants(root, parents, tracers))
        fun eligible(entry: ProcEntry): Boolean = entry.uid == appUid && entry.pid > 1 && entry.pid != appPid &&
            entry.tracer == 0 && entry.state != 'Z' && entry.pid !in alive && isGuestProgram(entry.exe)
        val byPid = entries.associateBy { it.pid }
        val chosen = LinkedHashSet<Int>()
        for (root in entries.filter { it.parent == 1 && eligible(it) }) {
            chosen.add(root.pid)
            NativeProcessTree.descendants(root.pid, parents, emptyMap())
                .filter { pid -> byPid[pid]?.let(::eligible) == true }
                .forEach { chosen.add(it) }
        }
        return chosen.mapNotNull { byPid[it] }
    }
}
