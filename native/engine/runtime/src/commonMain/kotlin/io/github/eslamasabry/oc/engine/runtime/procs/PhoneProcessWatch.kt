package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Port of lib/builtin/phone_process_watch.dart and the models of lib/builtin/phone_processes.dart
 * ("Running on this phone": the app's processes grouped under what started them, as a tree, with
 * the heavy and stuck marks that need more than one reading). The native process list itself (pids,
 * owners, cpu over about a second) is the platform's; this is the rules over its readings.
 */
public enum class PhoneProcessOwner(public val wire: String) {
    /** The app's own Android processes. */
    App("app"),

    /** The in-app OpenCode server and everything it runs. */
    OpenCode("opencode"),

    /** The agent helper (Paseo) with Claude Code and its tools. */
    Paseo("paseo"),

    /** An agent's sign-in. */
    AgentSignIn("agent-sign-in"),

    /** The AI Team engine. */
    Engine("engine"),

    /** Another named service of the app. */
    Helper("helper"),

    /** A one-off job: a setup step or a check. */
    Task("task"),

    /** A terminal session. */
    Terminal("terminal"),

    /** Left running by a Linux that has ended; nothing will ever stop it. */
    Orphan("orphan"),

    /** None of the above. */
    Other("other");

    public companion object {
        public fun parse(value: String?): PhoneProcessOwner =
            entries.firstOrNull { it.wire == value } ?: Other
    }
}

/** One process of this app on the phone. */
public data class RunningProcess(
    val pid: Int,
    val ppid: Int,
    /** The kernel's short name (at most 15 characters). */
    val name: String,
    /**
     * Its command line, at most 200 characters, secret-looking values masked. Never shown as the
     * name.
     */
    val command: String,
    /** Share of one CPU core over about a second, so up to 100 × cores. */
    val cpuPercent: Double,
    val rssBytes: Long,
    /** How long it has run. */
    val elapsed: Duration,
    val owner: PhoneProcessOwner,
    /** The agent session it runs (Claude Code's `--resume` id), if any. */
    val session: String? = null,
    val startTicks: Long = 0,
)

/** What runs now, and what the orphan clean-up has stopped since app start. */
public data class RunningSnapshot(
    val processes: List<RunningProcess> = emptyList(),
    val orphansReaped: Int = 0,
    val orphansLastReapedAt: Instant? = null,
)

/**
 * What started a group of processes, in the order the page shows them. [key] is the Flutter enum
 * name.
 */
public enum class RunningGroupKind(public val key: String) {
    /** Left running by a Linux that has ended (native `orphan`). */
    LeftBehind("leftBehind"),
    OpenCode("openCode"),

    /** One Claude Code conversation (a process with an agent session and everything under it). */
    ClaudeCode("claudeCode"),

    /** The agent helper (Paseo) and its sign-ins, outside any conversation. */
    AgentHelper("agentHelper"),
    AiTeam("aiTeam"),
    Terminal("terminal"),

    /** A setup step or a check. */
    Setup("setup"),

    /** Another named service of the app. */
    Helpers("helpers"),

    /** The app's own Android processes. */
    App("app"),
    Other("other"),
}

/** One process in its group's tree. */
public data class RunningNode(
    val process: RunningProcess,
    /** 0 for a process whose parent is not in the same group. */
    val depth: Int,
    /**
     * The root of a tree the app runs (a server, the agent helper, a terminal, a setup step): no
     * Stop offered.
     */
    val root: Boolean,
    /**
     * Over [PhoneProcessWatch.HEAVY_CPU] in every reading for at least
     * [PhoneProcessWatch.HEAVY_FOR].
     */
    val heavy: Boolean,
    /**
     * Left behind, or about 100% for more than [PhoneProcessWatch.STUCK_AFTER] with no conversation
     * above it.
     */
    val stuck: Boolean,
    /** How long it has been heavy, when [heavy]. */
    val heavyFor: Duration? = null,
    /** The agent session of the conversation above it (its own or an ancestor's), if any. */
    val session: String? = null,
) {
    val pid: Int
        get() = process.pid

    val leftBehind: Boolean
        get() = process.owner == PhoneProcessOwner.Orphan

    /** Whether the page offers to stop it: never the app nor a tree's root. */
    val stoppable: Boolean
        get() = !root && process.owner != PhoneProcessOwner.App
}

/**
 * The processes one thing started, as a tree in reading order (parents before children, siblings by
 * pid).
 */
public data class RunningGroup(
    val kind: RunningGroupKind,
    val nodes: List<RunningNode>,
    /** The conversation's agent session, for [RunningGroupKind.ClaudeCode]. */
    val session: String? = null,
) {
    /** A stable id for keys and animations. */
    val id: String
        get() = if (session == null) kind.key else "${kind.key}:$session"

    val cpuPercent: Double
        get() = nodes.sumOf { it.process.cpuPercent }

    val rssBytes: Long
        get() = nodes.sumOf { it.process.rssBytes }
}

/** One reading, grouped and marked. */
public data class RunningReading(
    val snapshot: RunningSnapshot,
    val groups: List<RunningGroup>,
    val at: Instant,
) {
    val nodes: List<RunningNode>
        get() = groups.flatMap { it.nodes }

    /** The processes Android counts toward its limit: all but the app's own. */
    val childCount: Int
        get() = snapshot.processes.count { it.owner != PhoneProcessOwner.App }

    val nearLimit: Boolean
        get() = childCount >= PhoneProcessWatch.NEAR_LIMIT

    val leftBehind: List<RunningNode>
        get() = nodes.filter { it.leftBehind && it.stoppable }

    /** Heavy ones, the busiest first. */
    val heavy: List<RunningNode>
        get() = nodes.filter { it.heavy }.sortedByDescending { it.process.cpuPercent }

    val rssBytes: Long
        get() = snapshot.processes.sumOf { it.rssBytes }

    /** Something on This phone should point at the page. */
    val needsLook: Boolean
        get() = leftBehind.isNotEmpty() || heavy.isNotEmpty()
}

/**
 * Marks the heavy and stuck processes from the readings so far. One per app, so This phone and
 * Running on this phone share the history. Not thread-safe: the caller records from one place.
 */
public class PhoneProcessWatch {
    private class Track(var name: String, var elapsed: Duration) {
        var highSince: Instant? = null
        var lastCpu: Double = 0.0

        fun sameAs(p: RunningProcess): Boolean = p.name == name && p.elapsed + 2.seconds >= elapsed
    }

    private val tracks = HashMap<Int, Track>()
    private var lastAt: Instant? = null

    /** The last reading, if any. */
    public var last: RunningReading? = null
        private set

    /** Reads through [list] and records it at [now] (the app passes its clock). */
    public suspend fun sample(
        list: suspend () -> RunningSnapshot,
        now: () -> Instant,
    ): RunningReading {
        val snapshot = list()
        return record(snapshot, now())
    }

    /** Adds [snapshot], taken at [at], to the history and groups it. */
    public fun record(snapshot: RunningSnapshot, at: Instant): RunningReading {
        val previousAt = lastAt
        val continuous = previousAt != null && at - previousAt <= MAX_GAP
        lastAt = at
        val live = mutableSetOf<Int>()
        val heavy = HashMap<Int, Duration>()
        val aboutFull = mutableSetOf<Int>()
        for (p in snapshot.processes) {
            live.add(p.pid)
            val seen = tracks[p.pid]
            val known = seen != null && continuous && seen.sameAs(p)
            val track = if (known) seen else Track(p.name, p.elapsed).also { tracks[p.pid] = it }
            val high = p.cpuPercent > HEAVY_CPU
            track.highSince = if (high) (if (known) track.highSince ?: at else at) else null
            val since = track.highSince
            if (since != null && at - since >= HEAVY_FOR) heavy[p.pid] = at - since
            if (known && track.lastCpu >= STUCK_CPU && p.cpuPercent >= STUCK_CPU)
                aboutFull.add(p.pid)
            track.lastCpu = p.cpuPercent
            track.elapsed = p.elapsed
            track.name = p.name
        }
        tracks.keys.retainAll(live)
        val reading =
            RunningReading(
                snapshot,
                groupRunningProcesses(snapshot.processes, heavy, aboutFull),
                at,
            )
        last = reading
        return reading
    }

    public companion object {
        /** Over this share of one core counts toward "heavy". */
        public const val HEAVY_CPU: Double = 80.0

        /** For at least this long, across readings. */
        public val HEAVY_FOR: Duration = 2.minutes

        /** "About 100%": at or over this in two readings in a row. */
        public const val STUCK_CPU: Double = 90.0

        /** For a process that has run longer than this. */
        public val STUCK_AFTER: Duration = 10.minutes

        /**
         * Readings further apart than this do not prove a process stayed busy in between: a streak
         * starts again.
         */
        public val MAX_GAP: Duration = 30.seconds

        /** Android stops an app's child processes above this, the oldest first. */
        public const val ANDROID_LIMIT: Int = 32

        /** The page warns from this many. */
        public const val NEAR_LIMIT: Int = 28
    }
}

/**
 * Groups [processes] under what started them, each group a tree. [heavy] maps a pid to how long it
 * has been heavy; [aboutFull] holds the pids at about 100% in the last two readings.
 */
public fun groupRunningProcesses(
    processes: List<RunningProcess>,
    heavy: Map<Int, Duration> = emptyMap(),
    aboutFull: Set<Int> = emptySet(),
): List<RunningGroup> {
    val byPid = processes.associateBy { it.pid }

    // The agent session above a process: its own, else the nearest ancestor's (Claude Code's tools
    // carry none).
    val sessions = HashMap<Int, String?>()
    fun sessionOf(p: RunningProcess): String? {
        if (sessions.containsKey(p.pid)) return sessions[p.pid]
        sessions[p.pid] = null // a loop in a bad reading ends here
        val parent = byPid[p.ppid]
        val found =
            p.session ?: if (parent == null || parent.pid == p.pid) null else sessionOf(parent)
        sessions[p.pid] = found
        return found
    }

    fun kindOf(p: RunningProcess): RunningGroupKind =
        when (p.owner) {
            PhoneProcessOwner.Orphan -> RunningGroupKind.LeftBehind
            PhoneProcessOwner.OpenCode -> RunningGroupKind.OpenCode
            PhoneProcessOwner.Paseo,
            PhoneProcessOwner.AgentSignIn ->
                if (sessionOf(p) == null) RunningGroupKind.AgentHelper
                else RunningGroupKind.ClaudeCode
            PhoneProcessOwner.Engine -> RunningGroupKind.AiTeam
            PhoneProcessOwner.Terminal -> RunningGroupKind.Terminal
            PhoneProcessOwner.Task -> RunningGroupKind.Setup
            PhoneProcessOwner.Helper -> RunningGroupKind.Helpers
            PhoneProcessOwner.App -> RunningGroupKind.App
            PhoneProcessOwner.Other -> RunningGroupKind.Other
        }

    fun groupId(p: RunningProcess): String {
        val kind = kindOf(p)
        return if (kind == RunningGroupKind.ClaudeCode) "${kind.key}:${sessionOf(p)}" else kind.key
    }

    // The root of a tree the app runs: its parent is in no tree of the same owner (native refuses
    // to stop these).
    val treeOwners =
        setOf(
            PhoneProcessOwner.OpenCode,
            PhoneProcessOwner.Paseo,
            PhoneProcessOwner.AgentSignIn,
            PhoneProcessOwner.Engine,
            PhoneProcessOwner.Helper,
            PhoneProcessOwner.Task,
            PhoneProcessOwner.Terminal,
        )
    fun isRoot(p: RunningProcess): Boolean =
        p.owner in treeOwners && byPid[p.ppid]?.owner != p.owner

    val members = LinkedHashMap<String, MutableList<RunningProcess>>()
    for (p in processes) members.getOrPut(groupId(p)) { mutableListOf() }.add(p)

    val groups = mutableListOf<RunningGroup>()
    for (entry in members.values) {
        val inGroup = entry.map { it.pid }.toSet()
        val children = HashMap<Int, MutableList<RunningProcess>>()
        val roots = mutableListOf<RunningProcess>()
        for (p in entry) {
            if (p.ppid != p.pid && p.ppid in inGroup)
                children.getOrPut(p.ppid) { mutableListOf() }.add(p)
            else roots.add(p)
        }
        val nodes = mutableListOf<RunningNode>()
        val placed = mutableSetOf<Int>()
        fun visit(p: RunningProcess, depth: Int) {
            if (!placed.add(p.pid)) return
            val heavyFor = heavy[p.pid]
            val root = isRoot(p)
            val session = sessionOf(p)
            val stuck =
                p.owner == PhoneProcessOwner.Orphan ||
                    (!root &&
                        p.owner != PhoneProcessOwner.App &&
                        session == null &&
                        p.elapsed > PhoneProcessWatch.STUCK_AFTER &&
                        p.pid in aboutFull)
            nodes.add(RunningNode(p, depth, root, heavyFor != null, stuck, heavyFor, session))
            for (kid in children[p.pid].orEmpty().sortedBy { it.pid }) visit(kid, depth + 1)
        }
        for (root in roots.sortedBy { it.pid }) visit(root, 0)
        // A parent loop in a bad reading leaves members unplaced: shown flat.
        for (p in entry) visit(p, 0)
        val first = entry.first()
        val kind = kindOf(first)
        groups.add(
            RunningGroup(
                kind,
                nodes.toList(),
                if (kind == RunningGroupKind.ClaudeCode) sessionOf(first) else null,
            )
        )
    }
    return groups.sortedWith(
        compareBy<RunningGroup> { it.kind.ordinal }.thenBy { it.nodes.first().pid }
    )
}
