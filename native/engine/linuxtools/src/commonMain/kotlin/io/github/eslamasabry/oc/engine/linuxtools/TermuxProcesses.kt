package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Running on this phone (TEAM-305, P5.3; port of lib/termux/processes.dart): every process the app's Termux user owns,
// grouped by ancestry, and polite-then-forced stopping through ~/.oc/tools.sh. The scanner's groups say who owns a
// process; PhoneProcessKind says what runs. Activity is measured between two scans, never read from ps's lifetime
// average; a reading that is missing stays unknown.

/** Who owns a process, as the scanner groups it; declared in display order. */
public enum class TermuxProcessGroup(public val wireName: String) {
    Orphans("orphans"),
    OpencodeServer("opencode_server"),
    AiTeam("ai_team"),
    BuildDaemons("build_daemons"),
    Other("other"),
    ;

    /** The OpenCode server group is managed from the server controls, never stopped wholesale from the list. */
    public val stoppable: Boolean get() = this != OpencodeServer

    public companion object {
        public fun parse(value: String?): TermuxProcessGroup = entries.firstOrNull { it.wireName == value } ?: Other
    }
}

public enum class TermuxOrphanReason { ParentGone, CpuNoOwner }

/** What runs, as the page groups it (P5.3). Declared in the page's order. */
public enum class PhoneProcessKind { OpenCodeServer, AiTeam, ClaudeCode, DevServices, Terminals, Helpers }

/** Whether a process used the processor between the last two scans. */
public enum class PhoneProcessActivity { Busy, Idle, Unknown }

public data class TermuxProcess(
    val pid: Int,
    val ppid: Int = 0,
    val group: TermuxProcessGroup = TermuxProcessGroup.Other,
    val name: String = "",
    val cmd: String = "",
    val cpuPct: Double = 0.0,
    val cpuSeconds: Int = 0,
    val rssKb: Int = 0,
    val elapsedSeconds: Int = 0,
    val cwd: String = "",
    val orphanReason: TermuxOrphanReason? = null,
    val protected: Boolean = false,
) {
    val isOrphan: Boolean get() = group == TermuxProcessGroup.Orphans

    /** The Termux app's own process: the host itself, never stopped from the list. */
    val isHostApp: Boolean get() = name.startsWith("com.termux") || cmd.startsWith("com.termux")

    /** Whether the list may offer to stop it: not protected by the scanner and not the Termux app itself. */
    val stoppable: Boolean get() = !protected && !isHostApp

    /** Memory in whole megabytes, null when the reading is missing. */
    val memoryMb: Int?
        get() {
            if (rssKb <= 0) return null
            val mb = (rssKb + 512) / 1024
            return if (mb < 1) 1 else mb
        }

    /** What runs: the scanner's owner first (the server, the team), then what the process is by name and command. */
    val kind: PhoneProcessKind
        get() {
            when (group) {
                TermuxProcessGroup.OpencodeServer -> return PhoneProcessKind.OpenCodeServer
                TermuxProcessGroup.AiTeam -> return PhoneProcessKind.AiTeam
                else -> Unit
            }
            if (claude.containsMatchIn(name) || claude.containsMatchIn(cmd)) return PhoneProcessKind.ClaudeCode
            if (group == TermuxProcessGroup.BuildDaemons || devTool.containsMatchIn(cmd)) {
                return PhoneProcessKind.DevServices
            }
            if (!isOrphan && !protected && name in shells) return PhoneProcessKind.Terminals
            return PhoneProcessKind.Helpers
        }

    public companion object {
        private val shells = setOf("bash", "zsh", "fish", "sh", "dash", "ash", "login")
        private val claude = Regex("(^|[/ ])claude(-code)?( |$)|@anthropic-ai/claude-code")
        private val devTool =
            Regex(
                "GradleDaemon|GradleWrapperMain|KotlinCompileDaemon|analysis_server|frontend_server|gradle",
            )

        /** One scanner record; null for anything that is not an object with a positive pid. */
        public fun fromJson(raw: JsonElement?): TermuxProcess? {
            val map = raw.asMap() ?: return null
            val pid = map["pid"].asInt()
            if (pid <= 0) return null
            val cpu = map["cpu_pct"]
            return TermuxProcess(
                pid = pid,
                ppid = map["ppid"].asInt(),
                group = TermuxProcessGroup.parse(map["group"].asText()),
                name = map["name"].asText() ?: "",
                cmd = map["cmd"].asText() ?: "",
                cpuPct =
                    when {
                        cpu !is JsonPrimitive -> 0.0
                        cpu.isString -> cpu.content.toDoubleOrNull() ?: 0.0
                        else -> cpu.doubleOrNull ?: 0.0
                    },
                cpuSeconds = map["cpu_seconds"].asInt(),
                rssKb = map["rss_kb"].asInt(),
                elapsedSeconds = map["elapsed_s"].asInt(),
                cwd = map["cwd"].asText() ?: "",
                orphanReason =
                    when (map["orphan_reason"].asText()) {
                        "parent_gone" -> TermuxOrphanReason.ParentGone
                        "cpu_no_owner" -> TermuxOrphanReason.CpuNoOwner
                        else -> null
                    },
                protected = map["protected"].isTrue(),
            )
        }
    }
}

public data class TermuxProcessReport(val processes: List<TermuxProcess>) {
    val count: Int get() = processes.size

    /** The processes that count against [ANDROID_BACKGROUND_LIMIT]: everything Termux started, not the app itself. */
    val backgroundCount: Int get() = processes.count { !it.isHostApp }

    val totalCpuPct: Double get() = processes.sumOf { it.cpuPct }

    val totalRssKb: Long get() = processes.sumOf { it.rssKb.toLong() }

    public fun inGroup(group: TermuxProcessGroup): List<TermuxProcess> = processes.filter { it.group == group }

    /** Groups in display order, only those with members. */
    val groups: List<TermuxProcessGroup>
        get() = TermuxProcessGroup.entries.filter { group -> processes.any { it.group == group } }

    /** Orphans that have burned more than [threshold] of CPU ("Something is still running on this phone"). */
    public fun orphansOver(threshold: Duration): List<TermuxProcess> =
        processes.filter { it.isOrphan && it.cpuSeconds > threshold.inWholeSeconds }

    public companion object {
        /**
         * Android 12 and later stop the oldest child processes of apps when all apps together run more than this many
         * (the phantom process limit's default). Advisory: the owner may have lifted it, other apps count too.
         */
        public const val ANDROID_BACKGROUND_LIMIT: Int = 32

        /**
         * Whether [process] was busy since the same process in [previous]: two or more seconds of processor time and
         * at least a quarter of the time between the scans. Unknown without an earlier reading of the same process
         * (same pid and name, still running longer than before).
         */
        public fun activityOf(process: TermuxProcess, previous: TermuxProcessReport?): PhoneProcessActivity {
            val before =
                previous?.processes?.firstOrNull { it.pid == process.pid && it.name == process.name }
                    ?: return PhoneProcessActivity.Unknown
            val wall = process.elapsedSeconds - before.elapsedSeconds
            val cpu = process.cpuSeconds - before.cpuSeconds
            if (wall <= 0 || cpu < 0) return PhoneProcessActivity.Unknown
            return if (cpu >= 2 && cpu * 4 >= wall) PhoneProcessActivity.Busy else PhoneProcessActivity.Idle
        }

        /** The scanner's JSON list; an empty answer is an empty report; null when it is not a list. */
        public fun parse(json: String): TermuxProcessReport? {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) return TermuxProcessReport(emptyList())
            val decoded = decodeJson(trimmed) as? kotlinx.serialization.json.JsonArray ?: return null
            return TermuxProcessReport(decoded.mapNotNull { TermuxProcess.fromJson(it) })
        }
    }
}

public data class RefusedPid(val pid: Int, val reason: String)

public data class RemainingPid(val pid: Int, val name: String)

public data class TermuxProcessStopResult(
    val stopped: List<Int>,
    val killed: List<Int>,
    val remaining: List<RemainingPid>,
    val refused: List<RefusedPid>,
) {
    val endedCount: Int get() = stopped.size + killed.size

    public companion object {
        /** The stop verb's JSON; null when it is not an object. */
        public fun parse(json: String): TermuxProcessStopResult? {
            val decoded = decodeJson(json.trim()).asMap() ?: return null
            fun pids(raw: JsonElement?): List<Int> = raw.asList().map { it.asInt() }.filter { it > 0 }
            return TermuxProcessStopResult(
                stopped = pids(decoded["stopped"]),
                killed = pids(decoded["killed"]),
                remaining =
                    decoded["remaining"].asList().mapNotNull { raw ->
                        raw.asMap()?.let { RemainingPid(it["pid"].asInt(), it["name"].asText() ?: "") }
                    },
                refused =
                    decoded["refused"].asList().mapNotNull { raw ->
                        raw.asMap()?.let { RefusedPid(it["pid"].asInt(), it["reason"].asText() ?: "") }
                    },
            )
        }
    }
}

/** Runs the process verbs of `~/.oc/tools.sh` (port of `TermuxProcesses`). */
public class TermuxProcesses(private val tools: TermuxToolRunner) {
    public suspend fun scan(): Outcome<TermuxProcessReport> =
        tools.runTool("procs-scan", "", 20.seconds).flatMap { out ->
            TermuxProcessReport.parse(out)?.ok() ?: Failure.UnreadableReply().failed()
        }

    /** TERM, five seconds, then KILL; protected processes are refused. A pid of 1 or below is refused here. */
    public suspend fun stopPid(pid: Int): Outcome<TermuxProcessStopResult> {
        if (pid <= 1) return Failure.Rejected(status = null).failed()
        return stop("$pid")
    }

    /** Stops exactly [pids] (one kind's processes, confirmed together), each checked again by the script. */
    public suspend fun stopPids(pids: List<Int>): Outcome<TermuxProcessStopResult> {
        if (pids.isEmpty() || pids.any { it <= 1 }) return Failure.Rejected(status = null).failed()
        if (pids.size == 1) return stopPid(pids.single())
        return stop(pids.joinToString(","))
    }

    public suspend fun stopGroup(group: TermuxProcessGroup): Outcome<TermuxProcessStopResult> = stop(group.wireName)

    private suspend fun stop(argument: String): Outcome<TermuxProcessStopResult> =
        tools.runTool("procs-stop", argument, 25.seconds).flatMap { out ->
            TermuxProcessStopResult.parse(out)?.ok() ?: Failure.UnreadableReply().failed()
        }
}
