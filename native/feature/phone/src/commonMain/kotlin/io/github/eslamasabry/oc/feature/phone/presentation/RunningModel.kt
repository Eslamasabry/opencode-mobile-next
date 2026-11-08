package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudget
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One process row. [protectedAs] says why it has no Stop (B-LINUX-143); the words point to This
 * phone.
 */
public data class ProcessRowUi(
    val pid: Int,
    val name: String,
    val kind: ProcessKind,
    val cpuPercent: Int?,
    val leftBehind: Boolean,
    val protectedAs: ProcessRole?,
    val command: io.github.eslamasabry.oc.core.common.Redacted? = null,
    val memoryBytes: Long? = null,
    val elapsedSeconds: Long? = null,
    val stopIdentities: Map<Int, Long> = emptyMap(),
    val cpuWords: io.github.eslamasabry.oc.engine.runtime.procs.CpuWords? = null,
    val heavy: Boolean = false,
    val memoryMb: Long? = null,
    val memberCount: Int = 1,
    val identity: String? = null,
    val orphanReason: ProcessOrphanReason? = null,
    val group: RunningGroupKind = RunningGroupKind.Other,
    val session: String? = null,
    val conversationTitle: String? = null,
    val depth: Int = 0,
    val owner: String? = null,
    val heavyForSeconds: Long? = null,
    val parentPid: Int? = null,
) {
    val stoppable: Boolean
        get() = protectedAs == null
}

/**
 * One heading of Running on this phone (Flutter `PhoneProcessGroup`): what started these processes, a Claude Code
 * conversation by its title, and the group's load and memory so heat shows on the heading.
 */
public data class RunningGroupUi(
    val kind: RunningGroupKind,
    /** The conversation's agent session, for [RunningGroupKind.ClaudeCode]. */
    val session: String?,
    /** That conversation's title, when known. */
    val title: String?,
    val rows: ImmutableList<ProcessRowUi>,
) {
    val id: String
        get() = if (session == null) kind.key else "${kind.key}:$session"

    /** Summed load; null when no row was measured. */
    val cpuPercent: Int?
        get() = rows.mapNotNull { it.cpuPercent }.takeIf { it.isNotEmpty() }?.sum()

    val memoryBytes: Long?
        get() = rows.mapNotNull { it.memoryBytes }.takeIf { it.isNotEmpty() }?.sum()

    /** The group's load in words: Very busy is said here, on the heading (Flutter running_on_phone_text.dart). */
    val cpuWords: io.github.eslamasabry.oc.engine.runtime.procs.CpuWords?
        get() = cpuPercent?.let { io.github.eslamasabry.oc.engine.runtime.procs.runningCpuWords(it.toDouble()) }

    /** A heavy process inside: the heading carries the heat mark. */
    val hot: Boolean
        get() = rows.any { it.heavy }
}

/** Flutter `groupPhoneProcesses`'s order and split: by kind in page order, a conversation per session. */
internal object RunningGroups {
    fun of(rows: List<ProcessRowUi>): ImmutableList<RunningGroupUi> {
        val members = LinkedHashMap<String, MutableList<ProcessRowUi>>()
        for (row in rows) {
            val id = if (row.group == RunningGroupKind.ClaudeCode && row.session != null) "${row.group.key}:${row.session}" else row.group.key
            members.getOrPut(id) { mutableListOf() } += row
        }
        return members.values
            .map { list ->
                val first = list.first()
                val session = first.session.takeIf { first.group == RunningGroupKind.ClaudeCode }
                RunningGroupUi(first.group, session, list.firstNotNullOfOrNull { it.conversationTitle }?.takeIf { session != null }, list.toImmutableList())
            }
            .sortedWith(compareBy<RunningGroupUi> { it.kind.ordinal }.thenBy { it.rows.first().pid })
            .toImmutableList()
    }

    /** The group a row without the runtime's word belongs to, from what the reading did say. */
    fun derive(p: PhoneProcess): RunningGroupKind =
        p.group
            ?: when {
                // Busy with no owner stays where it is, marked; only what an ended Linux left is "Left behind".
                p.leftBehind && p.orphanReason != ProcessOrphanReason.CpuWithoutOwner -> RunningGroupKind.LeftBehind
                p.role == ProcessRole.App -> RunningGroupKind.App
                else ->
                    when (p.kind) {
                        ProcessKind.OpenCode -> RunningGroupKind.OpenCode
                        ProcessKind.ClaudeCode -> RunningGroupKind.ClaudeCode
                        ProcessKind.AiTeam -> RunningGroupKind.AiTeam
                        ProcessKind.Terminal -> RunningGroupKind.Terminal
                        ProcessKind.Helper -> RunningGroupKind.Helpers
                        ProcessKind.DevService,
                        ProcessKind.Other -> RunningGroupKind.Other
                    }
            }
}

/** PhoneRoute.Running's state (plan 07 F08, merged Linux processes + Running on this phone). */
public data class RunningUi(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
    val rows: ImmutableList<ProcessRowUi> = persistentListOf(),
    val count: Int? = null,
    val cap: Int = ProcessBudget.CAP,
    val nearLimit: Boolean = false,
    val childLimit: ChildLimitCardKind? = null,
    val confirmStop: ProcessRowUi? = null,
    val stopping: Int? = null,
    val stopFailed: Boolean = false,
    val selected: ProcessRowUi? = null,
    val status: io.github.eslamasabry.oc.engine.runtime.procs.RunningRowStatus? = null,
    val leftover: io.github.eslamasabry.oc.engine.runtime.procs.RunawayNotice? = null,
    /** Non-null while the one question for everything left behind is open; it names each. */
    val confirmStopLeftBehind: ImmutableList<ProcessRowUi>? = null,
    /** What the last "Stop all left behind" did: how many stopped. */
    val stoppedCount: Int? = null,
) {
    /** Every stoppable process left behind: the targets of "Stop all left behind" (Flutter's group). */
    val leftBehind: ImmutableList<ProcessRowUi>
        get() = rows.filter { it.leftBehind && it.stoppable }.toImmutableList()

    /** The rows under what started them, in Flutter's page order. */
    val groups: ImmutableList<RunningGroupUi>
        get() = RunningGroups.of(rows)

    /** All memory the app's processes use, for the summary line; null when none was measured. */
    val memoryBytes: Long?
        get() = rows.mapNotNull { it.memoryBytes }.takeIf { it.isNotEmpty() }?.sum()
}

public interface RunningActions {
    public fun shown(visible: Boolean)

    public fun retry()

    public fun details(pid: Int)

    public fun askStop(pid: Int)

    public fun confirmStop()

    /** One question for every process left behind, naming each (Flutter `_stopLeftBehind`). */
    public fun askStopLeftBehind() {}

    /** Stops only the processes the question named, one after another. */
    public fun confirmStopLeftBehind() {}

    public fun dismiss()

    /** The card's one button: Developer options (or About phone). */
    public fun openChildLimitSettings()

    /** The card's quiet Hide (device-wide, until a newer kill). */
    public fun hideChildLimit()

    public fun back()
}

/**
 * Running on this phone (plan 07 F08): reads every 10 s ([PhoneTimers.runningRefresh]) only while
 * shown, stops one process with a confirm and never offers a protected one, warns from 28 programs,
 * and carries the child-process-limit card (plan 11 §7.2).
 */
public class RunningModel(
    private val source: PhoneProcessSource,
    private val limit: ChildLimitSource,
    private val nav: PhoneNavigator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val pacer: Pacer,
) : RunningActions {
    private val ui = MutableStateFlow(RunningUi())
    public val state: StateFlow<RunningUi> = ui.asStateFlow()

    private val visible = MutableStateFlow(false)
    private var limitStatus: ChildLimitStatus = ChildLimitStatus()
    private var hiddenAt: Instant? = null
    private var lastKill: Instant? = null

    init {
        scope.launch {
            visible.collectLatest { shown ->
                if (!shown) return@collectLatest
                readCard()
                load()
                pacer.ticks(PhoneTimers.runningRefresh).collect { load() }
            }
        }
    }

    override fun shown(visible: Boolean) {
        this.visible.value = visible
    }

    override fun retry() {
        scope.launch { load() }
    }

    private suspend fun load() {
        when (val read = source.read()) {
            is Outcome.Ok -> {
                val reading = read.value
                ui.update {
                    it.copy(
                        loading = false,
                        loadFailed = false,
                        refreshFailed = false,
                        rows = reading.processes.map(::row).toImmutableList(),
                        count = reading.count,
                        cap = ProcessBudget.ceiling(reading.limitLifted),
                        nearLimit = ProcessBudget.nearLimit(reading.count),
                        status = reading.status,
                        leftover = reading.leftover,
                    )
                }
            }
            is Outcome.Failed ->
                ui.update {
                    if (it.rows.isEmpty() && it.count == null) {
                        it.copy(loading = false, loadFailed = true)
                    } else {
                        it.copy(loading = false, refreshFailed = true)
                    }
                }
        }
    }

    private fun row(p: PhoneProcess) =
        ProcessRowUi(
            pid = p.pid,
            name = p.name,
            kind = p.kind,
            cpuPercent = p.cpuPercent?.takeIf { it.isFinite() && it >= 0 }?.roundToInt(),
            leftBehind = p.leftBehind,
            protectedAs = p.role.takeIf { it != ProcessRole.Normal },
            command = p.command,
            memoryBytes = p.memoryBytes,
            elapsedSeconds = p.elapsedSeconds,
            stopIdentities = p.stopIdentities,
            cpuWords =
                p.cpuPercent?.let {
                    io.github.eslamasabry.oc.engine.runtime.procs.runningCpuWords(it)
                },
            heavy = p.heavy,
            memoryMb = p.memoryMb,
            memberCount = p.memberCount,
            identity = p.identity,
            orphanReason = p.orphanReason,
            group = RunningGroups.derive(p),
            session = p.session,
            conversationTitle = p.conversationTitle,
            depth = p.depth.coerceIn(0, 3),
            owner = p.owner,
            heavyForSeconds = p.heavyForSeconds,
            parentPid = p.parentPid,
        )

    override fun details(pid: Int) {
        val row = ui.value.rows.firstOrNull { it.pid == pid } ?: return
        ui.update { it.copy(selected = row) }
    }

    override fun askStop(pid: Int) {
        val row = ui.value.rows.firstOrNull { it.pid == pid } ?: return
        if (!row.stoppable || ui.value.stopping != null) return
        ui.update { it.copy(confirmStop = row, selected = null, stopFailed = false) }
    }

    override fun confirmStop() {
        val row = ui.value.confirmStop ?: return
        ui.update { it.copy(confirmStop = null, stopping = row.pid) }
        scope.launch {
            // A guest-scan group stops by its displayed identity (LINUX-2); a native row by its
            // frozen tree.
            val stopped =
                if (row.stopIdentities.isNotEmpty()) source.stop(row.pid, row.stopIdentities)
                else source.stop(row.pid, row.identity)
            ui.update { it.copy(stopping = null, stopFailed = stopped is Outcome.Failed) }
            load()
        }
    }

    override fun askStopLeftBehind() {
        val targets = ui.value.leftBehind
        if (targets.isEmpty() || ui.value.stopping != null) return
        ui.update {
            it.copy(
                confirmStopLeftBehind = targets,
                confirmStop = null,
                selected = null,
                stopFailed = false,
                stoppedCount = null,
            )
        }
    }

    override fun confirmStopLeftBehind() {
        val targets = ui.value.confirmStopLeftBehind ?: return
        if (ui.value.stopping != null) return
        ui.update { it.copy(confirmStopLeftBehind = null, stopping = targets.first().pid) }
        scope.launch {
            var stopped = 0
            var failed = false
            for (row in targets) {
                ui.update { it.copy(stopping = row.pid) }
                // Each by the identity the question was built from, so a later process at the
                // same pid is never stopped.
                val result =
                    if (row.stopIdentities.isNotEmpty()) source.stop(row.pid, row.stopIdentities)
                    else source.stop(row.pid, row.identity)
                if (result is Outcome.Ok) stopped++ else failed = true
            }
            ui.update { it.copy(stopping = null, stopFailed = failed, stoppedCount = stopped) }
            load()
        }
    }

    override fun dismiss(): Unit = ui.update {
        it.copy(
            confirmStop = null,
            confirmStopLeftBehind = null,
            selected = null,
            stopFailed = false,
            stoppedCount = null,
        )
    }

    override fun openChildLimitSettings() {
        scope.launch {
            if (limit.openDeveloperOptions() == DeveloperOptionsOpened.None) {
                limitStatus = limitStatus.copy(canOpenSettings = false)
                showCard()
            }
        }
    }

    override fun hideChildLimit() {
        scope.launch {
            val now = clock.now()
            limit.hide(now)
            hiddenAt = now
            showCard()
        }
    }

    override fun back(): Unit = nav.back()

    private suspend fun readCard() {
        limitStatus = limit.status()
        hiddenAt = limit.hiddenAt()
        lastKill = limit.lastAndroidKill()
        showCard()
    }

    private fun showCard() {
        val kind =
            ChildLimitCards.kind(
                status = limitStatus,
                recentKill = lastKill != null,
                queuedOften = limit.queuesOften(),
                hiddenAt = hiddenAt,
                lastKillAt = lastKill,
            )
        ui.update { it.copy(childLimit = kind) }
    }
}
