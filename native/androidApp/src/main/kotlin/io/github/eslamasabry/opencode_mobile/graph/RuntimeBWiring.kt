package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.procs.*
import io.github.eslamasabry.oc.engine.runtime.procs.PhoneProcessOwner
import io.github.eslamasabry.oc.engine.runtime.procs.PhoneProcessWatch
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import io.github.eslamasabry.oc.engine.runtime.procs.RunningNameKind
import io.github.eslamasabry.oc.engine.runtime.procs.RunningProcess
import io.github.eslamasabry.oc.engine.runtime.procs.RunningProcessName
import io.github.eslamasabry.oc.engine.runtime.procs.RunningReading
import io.github.eslamasabry.oc.engine.runtime.procs.RunningSnapshot
import io.github.eslamasabry.oc.engine.runtime.procs.runningProcessName
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitSource
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitState
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProcess
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProcessSource
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessReading
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__agentHelper
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__app
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__claudeCode
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__command
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__dart
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__flutter
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__flutterTest
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__git
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__gradle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__java
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__linux
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__node
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__npm
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__openCode
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__other
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__python
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__search
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__signIn
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__team
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/*
 * BUILD-RUNTIME-B: Running on this phone, for real. Replaces LinuxWiring's empty process reading with the native
 * process table the Flutter app read (BuiltinLinux.processTable / stopProcesses over its MethodChannel), grouped,
 * named and marked by :engine:runtime's procs port (B-LINUX-144, 146, 180): plain names, never a command line;
 * heavy only after two minutes over 80 %; the app and every tree's root are never offered a Stop.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object RuntimeBWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun phoneProcessSource(
        app: Application,
        dispatchers: AppDispatchers,
        limit: ChildLimitSource,
        clock: Clock,
        host: LinuxHost,
    ): PhoneProcessSource = NativePhoneProcesses(app, dispatchers.io, limit, clock, host)
}

/**
 * Reads and stops the app's own processes (port of lib/builtin/phone_processes.dart over the copied
 * native side).
 */
internal class NativePhoneProcesses(
    private val app: Application,
    private val io: CoroutineDispatcher,
    private val limit: ChildLimitSource,
    private val clock: Clock,
    private val host: LinuxHost,
) : PhoneProcessSource {
    private val linux: BuiltinLinux by lazy { BuiltinLinux.get(app) }

    /**
     * One watch per app, so the heavy history survives between readings; recorded from this lane
     * only.
     */
    private val watch = PhoneProcessWatch()
    private val lane = Mutex()

    private var lastScan: LinuxProcessScan? = null
    private var shownScan: LinuxProcessScan? = null
    private var shownGroups: Map<Int, LinuxProcessGroup> = emptyMap()
    private var shownIdentities: Map<Int, Long> = emptyMap()
    private val runaway = RunawayWatch()

    override suspend fun read(): Outcome<ProcessReading> = lane.withLock {
        val table = native { linux.processTable() }
        if (table is Outcome.Failed) return@withLock table
        val snapshot = ProcessRows.snapshot((table as Outcome.Ok).value)
        val reading = watch.record(snapshot, clock.now())
        val rawRows = ProcessRows.rows(reading) { name(it) }
        val scan =
            if (host.presence() == LinuxPresence.Ready) {
                when (val result = host.run(LINUX_PROCESS_SCAN_SCRIPT, 25)) {
                    is Outcome.Ok ->
                        if (result.value.exitCode == 0) LinuxProcessScan.parse(result.value.output)
                        else null
                    // A failed guest scan keeps the native rows rather than failing the page.
                    is Outcome.Failed -> null
                }
            } else null
        val protectedPids = rawRows.filter { it.role != ProcessRole.Normal }.map { it.pid }.toSet()
        val visibleIdentities = snapshot.processes.associate { it.pid to it.startTicks }
        // The native reader admits only this app's uid. A guest scan cannot add another app's
        // processes.
        val ownScan =
            scan?.copy(
                processes = scan.processes.filter { it.startTicks == visibleIdentities[it.pid] }
            )
        shownIdentities =
            snapshot.processes.mapNotNull { p -> p.startTicks?.let { p.pid to it } }.toMap()
        shownScan = ownScan
        val groups = ownScan?.groups().orEmpty()
        shownGroups = groups.associateBy { it.leader.pid }
        val groupedPids = groups.flatMap { it.members }.map { it.pid }.toSet()
        val rows = rawRows.filter { it.pid !in groupedPids }.toMutableList()
        val previous = lastScan
        for (group in groups) {
            val base = rawRows.firstOrNull { it.pid == group.leader.pid } ?: continue
            val members = rawRows.filter { row -> group.members.any { it.pid == row.pid } }
            val measured = group.members.mapNotNull { ownScan?.cpuPercent(it, previous) }
            rows +=
                base.copy(
                    name =
                        when (group.leader.kind) {
                            LinuxProcessKind.DevServer ->
                                group.leader
                                    .copy(
                                        cmd =
                                            snapshot.processes
                                                .first { it.pid == group.leader.pid }
                                                .command
                                    )
                                    .programName
                            else -> base.name
                        },
                    kind =
                        if (group.leader.kind == LinuxProcessKind.DevServer) ProcessKind.DevService
                        else base.kind,
                    role =
                        if (group.protected || group.members.any { it.pid in protectedPids })
                            ProcessRole.ServerRoot
                        else base.role,
                    cpuPercent =
                        if (measured.size == group.members.size) measured.sum()
                        else members.sumOf { it.cpuPercent ?: 0.0 },
                    leftBehind = members.any { it.leftBehind },
                    heavy = members.any { it.heavy },
                    memoryMb = group.memoryMb,
                    elapsedSeconds = group.elapsedSeconds,
                    memberCount = group.count,
                    identity = group.leader.identity,
                )
        }
        lastScan = ownScan
        val leftover =
            runaway.notice(
                ownScan?.processes.orEmpty().map { p ->
                    LeftoverProcess(
                        p.pid,
                        p.name,
                        p.cwd.orEmpty(),
                        p.cpuSeconds,
                        snapshot.processes.any {
                            it.pid == p.pid && it.owner == PhoneProcessOwner.Orphan
                        },
                        p.startTicks,
                    )
                }
            )
        val lifted = limit.status().state == ChildLimitState.Lifted
        Outcome.Ok(
            ProcessReading(rows, reading.childCount, lifted, runningRowStatus(reading), leftover)
        )
    }

    override suspend fun stop(pid: Int): Outcome<Unit> = stop(pid, null)

    override suspend fun stop(pid: Int, identity: String?): Outcome<Unit> = lane.withLock {
        val currentIdentity =
            shownGroups[pid]?.leader?.identity ?: shownIdentities[pid]?.let { "$pid:$it" }
        if (identity != null && identity != currentIdentity) return@withLock refusedStop()
        val group = shownGroups[pid]
        if (group != null) {
            val scan = shownScan ?: return@withLock refusedStop()
            val nativeRows = watch.last?.let { ProcessRows.rows(it) { name(it) } }.orEmpty()
            val protected =
                nativeRows.filter { it.role != ProcessRole.Normal }.map { it.pid }.toSet()
            if (group.protected || group.members.any { it.pid in protected })
                return@withLock refusedStop()
            val targets = scan.withDescendants(group.roots)
            if (targets.any { it.pid in protected }) return@withLock refusedStop()
            val script =
                when (val built = linuxStopScript(targets.map { it.identity })) {
                    is Outcome.Ok -> built.value
                    is Outcome.Failed -> return@withLock built
                }
            val answer = host.run(script, 15)
            val stopped =
                when (answer) {
                    is Outcome.Failed -> false
                    is Outcome.Ok -> {
                        val result = LinuxStopResult.parse(answer.value.output)
                        answer.value.exitCode == 0 &&
                            result.remaining == 0 &&
                            result.changed == 0 &&
                            targets.all { it.identity in result.outcomes }
                    }
                }
            runaway.stopped(
                RunawayIdentity(pid, group.leader.name, group.leader.startTicks),
                stopped,
            )
            return@withLock if (stopped) Outcome.Ok(Unit) else refusedStop()
        }
        val identity = shownIdentities[pid] ?: return@withLock refusedStop()
        val result = native { linux.stopProcesses(listOf(pid), mapOf(pid to identity)) }
        if (result is Outcome.Failed) return@withLock result
        val stopped = ((result as Outcome.Ok).value["stopped"] as? List<*>).orEmpty()
        if (pid in stopped) Outcome.Ok(Unit) else refusedStop()
    }

    private fun refusedStop(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))

    private suspend fun name(name: RunningProcessName): String =
        when (name) {
            is RunningProcessName.Kernel -> name.name
            is RunningProcessName.Plain -> getString(ProcessRows.word(name.kind))
        }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(classifyRuntime(e))
            }
        }
}

/** The pure half: native map → engine snapshot → page rows. Tested on the JVM. */
internal object ProcessRows {
    fun snapshot(table: Map<String, Any?>): RunningSnapshot =
        RunningSnapshot(
            processes =
                (table["processes"] as? List<*>).orEmpty().mapNotNull { row ->
                    val map = row as? Map<*, *> ?: return@mapNotNull null
                    val pid = (map["pid"] as? Number)?.toInt() ?: return@mapNotNull null
                    RunningProcess(
                        pid = pid,
                        ppid = (map["ppid"] as? Number)?.toInt() ?: 0,
                        name = map["name"] as? String ?: "",
                        command = map["command"] as? String ?: "",
                        cpuPercent = (map["cpuPercent"] as? Number)?.toDouble() ?: 0.0,
                        rssBytes = (map["rssBytes"] as? Number)?.toLong() ?: 0L,
                        elapsed = ((map["elapsedSeconds"] as? Number)?.toLong() ?: 0L).seconds,
                        owner = PhoneProcessOwner.parse(map["owner"] as? String),
                        session = (map["session"] as? String)?.takeIf { it.isNotEmpty() },
                        startTicks = (map["startTicks"] as? Number)?.toLong(),
                    )
                },
            orphansReaped = (table["orphansReaped"] as? Number)?.toInt() ?: 0,
        )

    suspend fun rows(
        reading: RunningReading,
        name: suspend (RunningProcessName) -> String,
    ): List<PhoneProcess> =
        reading.groups.flatMap { group ->
            group.nodes.map { node ->
                val p = node.process
                PhoneProcess(
                    pid = p.pid,
                    parentPid = p.ppid.takeIf { it > 0 },
                    name = name(runningProcessName(p)),
                    kind = kind(group.kind),
                    role =
                        when {
                            p.owner == PhoneProcessOwner.App -> ProcessRole.App
                            node.root -> ProcessRole.ServerRoot
                            else -> ProcessRole.Normal
                        },
                    cpuPercent = p.cpuPercent,
                    leftBehind = node.leftBehind || node.stuck,
                    heavy = node.heavy,
                    memoryMb = (p.rssBytes / 1_048_576).coerceAtLeast(0),
                    elapsedSeconds = p.elapsed.inWholeSeconds,
                    identity = p.startTicks?.let { "${p.pid}:$it" },
                )
            }
        }

    fun kind(group: RunningGroupKind): ProcessKind =
        when (group) {
            RunningGroupKind.OpenCode -> ProcessKind.OpenCode
            RunningGroupKind.ClaudeCode -> ProcessKind.ClaudeCode
            RunningGroupKind.AiTeam -> ProcessKind.AiTeam
            RunningGroupKind.Terminal -> ProcessKind.Terminal
            RunningGroupKind.AgentHelper,
            RunningGroupKind.Helpers,
            RunningGroupKind.Setup -> ProcessKind.Helper
            RunningGroupKind.LeftBehind,
            RunningGroupKind.App,
            RunningGroupKind.Other -> ProcessKind.Other
        }

    /** Flutter `runningPhoneName(kind)`. */
    fun word(kind: RunningNameKind): StringResource =
        when (kind) {
            RunningNameKind.FlutterTest -> Res.string.runningPhoneName__flutterTest
            RunningNameKind.Command -> Res.string.runningPhoneName__command
            RunningNameKind.Linux -> Res.string.runningPhoneName__linux
            RunningNameKind.OpenCode -> Res.string.runningPhoneName__openCode
            RunningNameKind.ClaudeCode -> Res.string.runningPhoneName__claudeCode
            RunningNameKind.AgentHelper -> Res.string.runningPhoneName__agentHelper
            RunningNameKind.SignIn -> Res.string.runningPhoneName__signIn
            RunningNameKind.Team -> Res.string.runningPhoneName__team
            RunningNameKind.Node -> Res.string.runningPhoneName__node
            RunningNameKind.Python -> Res.string.runningPhoneName__python
            RunningNameKind.Git -> Res.string.runningPhoneName__git
            RunningNameKind.Npm -> Res.string.runningPhoneName__npm
            RunningNameKind.Dart -> Res.string.runningPhoneName__dart
            RunningNameKind.Flutter -> Res.string.runningPhoneName__flutter
            RunningNameKind.Gradle -> Res.string.runningPhoneName__gradle
            RunningNameKind.Java -> Res.string.runningPhoneName__java
            RunningNameKind.Search -> Res.string.runningPhoneName__search
            RunningNameKind.App -> Res.string.runningPhoneName__app
            RunningNameKind.Other -> Res.string.runningPhoneName__other
        }
}
