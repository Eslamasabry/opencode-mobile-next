package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One process row. [protectedAs] says why it has no Stop (B-LINUX-143); the words point to This phone. */
public data class ProcessRowUi(
    val pid: Int,
    val name: String,
    val kind: ProcessKind,
    val cpuPercent: Int?,
    val leftBehind: Boolean,
    val protectedAs: ProcessRole?,
) {
    val stoppable: Boolean get() = protectedAs == null
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
)

public interface RunningActions {
    public fun shown(visible: Boolean)

    public fun retry()

    public fun askStop(pid: Int)

    public fun confirmStop()

    public fun dismiss()

    /** The card's one button: Developer options (or About phone). */
    public fun openChildLimitSettings()

    /** The card's quiet Hide (device-wide, until a newer kill). */
    public fun hideChildLimit()

    public fun back()
}

/**
 * Running on this phone (plan 07 F08): reads every [refreshEvery] only while shown, stops one process with a confirm
 * and never offers a protected one, warns from 28 programs, and carries the child-process-limit card (plan 11 §7.2).
 */
public class RunningModel(
    private val source: PhoneProcessSource,
    private val limit: ChildLimitSource,
    private val nav: PhoneNavigator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val refreshEvery: Duration = 10.seconds,
) : RunningActions {
    private val ui = MutableStateFlow(RunningUi())
    public val state: StateFlow<RunningUi> = ui.asStateFlow()

    private var refresher: Job? = null
    private var limitStatus: ChildLimitStatus = ChildLimitStatus()
    private var hiddenAt: Instant? = null
    private var lastKill: Instant? = null

    override fun shown(visible: Boolean) {
        refresher?.cancel()
        refresher = null
        if (!visible) return
        refresher =
            scope.launch {
                readCard()
                while (true) {
                    load()
                    delay(refreshEvery)
                }
            }
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
        )

    override fun askStop(pid: Int) {
        val row = ui.value.rows.firstOrNull { it.pid == pid } ?: return
        if (!row.stoppable || ui.value.stopping != null) return
        ui.update { it.copy(confirmStop = row, stopFailed = false) }
    }

    override fun confirmStop() {
        val row = ui.value.confirmStop ?: return
        ui.update { it.copy(confirmStop = null, stopping = row.pid) }
        scope.launch {
            val stopped = source.stop(row.pid)
            ui.update { it.copy(stopping = null, stopFailed = stopped is Outcome.Failed) }
            load()
        }
    }

    override fun dismiss(): Unit = ui.update { it.copy(confirmStop = null, stopFailed = false) }

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
