package io.github.eslamasabry.opencode_mobile

import kotlin.math.roundToLong

/**
 * The `processes` and `stopProcesses` channel contract ("What's running on
 * this phone"), with no platform in it: [BuiltinLinux] reads /proc twice
 * about a second apart and passes the readings here.
 *
 * Each row: pid, ppid, name (the kernel's short name), command (argv, at most
 * [COMMAND_CHARS] characters, secret-looking values masked), cpuPercent (of
 * one core over the sample, so up to 100 × cores), rssBytes, elapsedSeconds,
 * startTicks (pass back nothing: it only tells a reused pid apart), owner and
 * session (an agent's `--resume`/`--session-id` value, else null).
 *
 * owner is one of [OWNERS]: the app's own processes, the in-app OpenCode
 * server's tree (either version), the agent helper's tree (Paseo, with Claude
 * Code and its tools), an agent sign-in, the AI Team engine, another named
 * service, a one-off task (setup step, check), a terminal, an orphan left by a
 * dead proot ([NativeOrphanReaper]), or other.
 */
internal object NativeProcessList {
    const val COMMAND_CHARS = 200
    val OWNERS = listOf("app", "opencode", "paseo", "agent-sign-in", "engine", "helper", "task", "terminal",
        "orphan", "other")
    private const val PERCENT = 100.0
    private const val TENTHS = 10.0
    private const val MS_PER_SECOND = 1000.0
    private val SECRET_FLAG = Regex("(?i).*(key|token|secret|password|passwd|auth).*")
    private val SECRET_ASSIGNMENT =
        Regex("(?i)^[A-Za-z0-9_.-]*(key|token|secret|password|passwd|auth)[A-Za-z0-9_.-]*=.+")
    private const val MASK = "•••"
    private val SESSION_FLAGS = setOf("--resume", "--session-id", "-r")

    /** The owner label of the named native service [name]. */
    fun ownerOfService(name: String): String = when {
        name == BuiltinLinux.SERVER -> "opencode"
        name.startsWith("agent-host.") -> "paseo"
        name.startsWith("agent-auth.") -> "agent-sign-in"
        name == BuiltinLinux.PHONE_ENGINE -> "engine"
        else -> "helper"
    }

    /**
     * Every pid's owner. [roots] maps a live root pid to its owner label;
     * its whole tree (by parent or tracer) shares it. [appPids] are the
     * app's own processes; [orphans] what the reaper would choose.
     */
    fun owners(entries: List<ProcEntry>, appPids: Set<Int>, roots: Map<Int, String>, orphans: Set<Int>):
        Map<Int, String> {
        val parents = entries.associate { it.pid to it.parent }
        val tracers = entries.mapNotNull { entry -> entry.tracer?.takeIf { it > 1 }?.let { entry.pid to it } }.toMap()
        val owner = HashMap<Int, String>()
        for ((root, label) in roots) {
            owner.putIfAbsent(root, label)
            NativeProcessTree.descendants(root, parents, tracers).forEach { owner.putIfAbsent(it, label) }
        }
        return entries.associate { entry ->
            entry.pid to when (entry.pid) {
                in appPids -> "app"
                in owner -> owner.getValue(entry.pid)
                in orphans -> "orphan"
                else -> "other"
            }
        }
    }

    /** argv joined by spaces, secret-looking values masked, at most [COMMAND_CHARS]. */
    fun command(argv: List<String>, name: String): String {
        if (argv.isEmpty()) return "[$name]"
        val masked = argv.mapIndexed { index, arg ->
            val afterFlag = index > 0 && argv[index - 1].startsWith("-") && SECRET_FLAG.matches(argv[index - 1])
            when {
                afterFlag -> MASK
                SECRET_ASSIGNMENT.matches(arg) -> arg.substringBefore('=') + "=" + MASK
                else -> arg
            }
        }
        return masked.joinToString(" ").take(COMMAND_CHARS)
    }

    /** The agent session [argv] resumes or names, else null. */
    fun session(argv: List<String>): String? {
        val inline = argv.firstNotNullOfOrNull { arg ->
            SESSION_FLAGS.firstOrNull { arg.startsWith("$it=") }?.let { arg.substringAfter('=') }
        }
        val next = argv.zipWithNext().firstOrNull { (flag, value) -> flag in SESSION_FLAGS && !value.startsWith("-") }
        return (inline ?: next?.second)?.takeIf { it.isNotBlank() }
    }

    /** Share of one core over [elapsedMs], one decimal; 0 without a usable sample. */
    fun cpuPercent(beforeTicks: Long?, afterTicks: Long, elapsedMs: Long, ticksPerSecond: Long): Double {
        val used = afterTicks - (beforeTicks ?: afterTicks)
        if (used <= 0 || elapsedMs <= 0 || ticksPerSecond <= 0) return 0.0
        val percent = used * PERCENT * MS_PER_SECOND / (elapsedMs * ticksPerSecond)
        return (percent * TENTHS).roundToLong() / TENTHS
    }

    /** What one process reading says to the UI. [clock] holds the sample's units. */
    class Clock(val elapsedMs: Long, val ticksPerSecond: Long, val pageBytes: Long, val uptimeSeconds: Double)

    fun rows(before: List<ProcEntry>, after: List<ProcEntry>, clock: Clock, owners: Map<Int, String>):
        List<Map<String, Any?>> {
        val earlier = before.associateBy { it.pid }
        return after.filter { it.state != 'Z' }.sortedBy { it.pid }.map { entry ->
            val previous = earlier[entry.pid]?.takeIf { entry.sameProcess(it) }
            val elapsed = if (clock.ticksPerSecond > 0) {
                (clock.uptimeSeconds - entry.startTicks.toDouble() / clock.ticksPerSecond).toLong().coerceAtLeast(0)
            } else 0L
            mapOf(
                "pid" to entry.pid, "ppid" to entry.parent, "name" to entry.name,
                "command" to command(entry.argv, entry.name),
                "cpuPercent" to cpuPercent(previous?.cpuTicks, entry.cpuTicks, clock.elapsedMs, clock.ticksPerSecond),
                "rssBytes" to entry.rssPages * clock.pageBytes, "elapsedSeconds" to elapsed,
                "startTicks" to entry.startTicks, "owner" to (owners[entry.pid] ?: "other"),
                "session" to session(entry.argv),
            )
        }
    }

    /**
     * Why [pid] may not be stopped, or null when it may: only a live process
     * of [appUid] ([entry] is its reading, null when there is none), never
     * the app ([appPids]) nor the root of a tree the app runs ([roots]).
     */
    fun stopRefusal(pid: Int, entry: ProcEntry?, appUid: Int, appPids: Set<Int>, roots: Set<Int>): String? = when {
        pid <= 1 -> "invalid"
        pid in appPids -> "app"
        pid in roots -> "serverRoot"
        entry == null || entry.pid != pid || entry.uid != appUid || entry.state == 'Z' -> "notFound"
        else -> null
    }
}
