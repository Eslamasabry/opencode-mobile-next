package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudget
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * This phone's state holder (plan 07 F01). Plain Kotlin over a scope so its rules test on virtual
 * time; [ThisPhoneViewModel] hands it `viewModelScope`. One command at a time: a tap while one runs
 * does nothing (B-LINUX-038: the starter starts a stopped server once).
 */
public class ThisPhoneModel(
    private val source: PhoneServerSource,
    private val settings: KeepAliveSettings,
    private val nav: PhoneNavigator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val pacer: Pacer,
    private val exits: PhoneExitNotices = PhoneExitNotices.Absent,
    private val details: PhoneDetailsSource? = null,
) : ThisPhoneActions {
    private data class Local(
        val working: PhoneOp? = null,
        val confirm: ThisPhoneConfirm? = null,
        val notice: ThisPhoneNotice? = null,
        val now: Instant,
        val detailsOpen: Boolean = false,
        val installedOpen: Boolean = false,
    )

    private val local = MutableStateFlow(Local(now = clock.now()))
    private val visible = MutableStateFlow(false)

    public val state: StateFlow<ThisPhoneUi> =
        combine(
                source.facts,
                local,
                exits.notice,
                details?.state ?: flowOf(PhoneDetailsUi()),
                ::build,
            )
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                build(
                    source.facts.value,
                    local.value,
                    exits.notice.value,
                    details?.state?.value ?: PhoneDetailsUi(),
                ),
            )

    private fun build(
        facts: PhoneServerFacts,
        l: Local,
        exitNotice: PhoneExitNotice?,
        reading: PhoneDetailsUi,
    ): ThisPhoneUi {
        val shownFacts =
            if (l.working == PhoneOp.Start || l.working == PhoneOp.Restart)
                facts.copy(starting = true)
            else facts
        val server = PhoneServerState.of(shownFacts)
        val baseAnswer =
            ThisPhoneAnswers.of(
                server,
                shownFacts,
                now = l.now,
                stopping = l.working == PhoneOp.Stop,
            )
        val answer =
            if (server == PhoneServerState.Running && l.working != PhoneOp.Stop) {
                baseAnswer.copy(
                    line =
                        reading.history?.let { AnswerLine.History(it) }
                            ?: reading.memoryBytes?.let { AnswerLine.Memory(it) }
                            ?: baseAnswer.line
                )
            } else baseAnswer
        val serverRows =
            when (server) {
                PhoneServerState.Running,
                PhoneServerState.NotAnswering -> persistentListOf(ServerRow.Stop, ServerRow.Restart)
                PhoneServerState.Starting,
                PhoneServerState.Checking ->
                    if (facts.status?.serverRunning == true) persistentListOf(ServerRow.Stop)
                    else persistentListOf()
                else -> persistentListOf()
            }
        return ThisPhoneUi(
            answer = answer,
            serverRows = serverRows.toImmutableList(),
            version = version(facts),
            running =
                RunningRowUi(
                    count = facts.processCount,
                    cap = ProcessBudget.ceiling(facts.limitLifted),
                    nearLimit = ProcessBudget.nearLimit(facts.processCount),
                ),
            working = l.working,
            confirm = l.confirm,
            notice = l.notice,
            exitNotice = exitNotice,
            details =
                reading.copy(canConnect = reading.canConnect && server == PhoneServerState.Running),
            detailsOpen = l.detailsOpen,
            installedOpen = l.installedOpen,
        )
    }

    private fun version(facts: PhoneServerFacts): VersionRowUi? {
        if (facts.status?.installed != true) return null
        val installed = facts.installedVersion?.let(::plainVersion)
        val pinned = facts.pinnedVersion?.let(::plainVersion)
        val update =
            when {
                facts.updateRunning -> UpdateRow.Updating
                installed == null || pinned == null ->
                    null // an unknown version offers nothing (B-LINUX-078)
                installed == pinned -> UpdateRow.UpToDate
                facts.updateFailed -> UpdateRow.Failed(pinned)
                else -> UpdateRow.Available(pinned)
            }
        return VersionRowUi(installed, update)
    }

    override fun act(act: PhoneAct) {
        when (act) {
            PhoneAct.SetUp,
            PhoneAct.SetUpAgain -> nav.open(PhonePage.SetupStart)
            PhoneAct.ShowProgress -> nav.open(PhonePage.SetupProgress)
            PhoneAct.Start,
            PhoneAct.StartAgain,
            PhoneAct.RestartNow -> run(PhoneOp.Start) { source.start() }
            PhoneAct.OpenAppSettings ->
                scope.launch {
                    if (!settings.open(KeepAliveSetting.AppDetails))
                        local.update { it.copy(notice = ThisPhoneNotice.SettingsMissing) }
                }
        }
    }

    override fun stopServer(): Unit = run(PhoneOp.Stop) { source.stop() }

    override fun askRestart() {
        if (local.value.working != null) return
        local.update {
            it.copy(
                confirm = ThisPhoneConfirm.Restart(source.facts.value.replyRunning),
                notice = null,
            )
        }
    }

    override fun askUpdate() {
        if (local.value.working != null) return
        val facts = source.facts.value
        if (facts.replyRunning) {
            local.update { it.copy(notice = ThisPhoneNotice.UpdateBusy) }
            return
        }
        val version = facts.pinnedVersion?.let(::plainVersion) ?: return
        local.update { it.copy(confirm = ThisPhoneConfirm.Update(version), notice = null) }
    }

    override fun confirm() {
        val asked = local.value.confirm ?: return
        local.update { it.copy(confirm = null) }
        when (asked) {
            is ThisPhoneConfirm.Restart -> run(PhoneOp.Restart) { source.restart() }
            is ThisPhoneConfirm.Update -> run(PhoneOp.Update) { source.updateOpenCode() }
        }
    }

    override fun dismiss() {
        local.update { it.copy(confirm = null, notice = null) }
        exits.dismiss()
    }

    override fun openRunning(): Unit = nav.open(PhonePage.Running)

    override fun openKeepRunning(): Unit = nav.open(PhonePage.KeepRunning)

    override fun openLinux(): Unit = nav.open(PhonePage.Linux)

    override fun back(): Unit = nav.back()

    init {
        // While shown: read the status once, then move the countdown each second (no timer while
        // hidden).
        scope.launch {
            visible.collectLatest { shown ->
                details?.shown(shown)
                if (!shown) return@collectLatest
                source.refresh()
                launch { details?.refresh() }
                local.update { it.copy(now = clock.now()) }
                pacer.ticks(PhoneTimers.answerCountdown).collect {
                    local.update { it.copy(now = clock.now()) }
                }
            }
        }
        scope.launch {
            combine(visible, local) { shown, value -> shown && value.detailsOpen }
                .distinctUntilChanged()
                .collectLatest { reading ->
                    if (!reading) return@collectLatest
                    details?.refreshLog()
                    pacer.ticks(PhoneTimers.detailsLog).collect {
                        if (source.facts.value.status?.serverRunning == true) details?.refreshLog()
                    }
                }
        }
    }

    init {
        scope.launch {
            combine(visible, details?.state ?: flowOf(PhoneDetailsUi())) { shown, reading ->
                    shown && reading.history?.phase == PhoneHistoryPhase.Moving
                }
                .distinctUntilChanged()
                .collectLatest { pending ->
                    if (!pending) return@collectLatest
                    // Flutter's bounded import loop: one initial read plus at most 149 follow-ups.
                    pacer
                        .ticks(PhoneTimers.historyImport)
                        .take(149)
                        .takeWhile {
                            details?.refreshHistory() == true
                        }
                        .collect {}
                }
        }
    }

    override fun connect(): Unit =
        run(PhoneOp.Connect) {
            details?.connect() ?: Outcome.Failed(Failure.Unavailable(FeatureId("builtinLinux")))
        }

    override fun refreshDetails() {
        scope.launch { details?.refresh() }
    }

    override fun showDetails(expanded: Boolean) {
        local.update { it.copy(detailsOpen = expanded) }
    }

    override fun showInstalled(expanded: Boolean) {
        local.update { it.copy(installedOpen = expanded) }
    }

    override fun shown(visible: Boolean) {
        this.visible.value = visible
    }

    private fun run(op: PhoneOp, command: suspend () -> Outcome<Unit>) {
        if (local.value.working != null) return
        local.update { it.copy(working = op, notice = null) }
        scope.launch {
            val outcome = command()
            local.update {
                it.copy(
                    working = null,
                    notice = if (outcome is Outcome.Failed) ThisPhoneNotice.Failed(op) else null,
                )
            }
            if (visible.value) details?.refresh()
        }
    }

    private companion object {
        /**
         * Versions read the way the manager trims them: blank is unknown, a leading `v` is ignored
         * (B-LINUX-024/078).
         */
        fun plainVersion(raw: String): String? =
            raw.trim().removePrefix("v").removePrefix("V").ifBlank { null }
    }
}
