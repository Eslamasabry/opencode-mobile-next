package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.eslamasabry.oc.core.common.pace.Pacer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * This phone's state holder (plan 07 F01). Plain Kotlin over a scope so its rules test on virtual time;
 * [ThisPhoneViewModel] hands it `viewModelScope`. One command at a time: a tap while one runs does nothing
 * (B-LINUX-038: the starter starts a stopped server once).
 */
public class ThisPhoneModel(
    private val source: PhoneServerSource,
    private val settings: KeepAliveSettings,
    private val nav: PhoneNavigator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val pacer: Pacer,
) : ThisPhoneActions {
    private data class Local(
        val working: PhoneOp? = null,
        val confirm: ThisPhoneConfirm? = null,
        val notice: ThisPhoneNotice? = null,
        val now: Instant,
    )

    private val local = MutableStateFlow(Local(now = clock.now()))
    private val visible = MutableStateFlow(false)

    public val state: StateFlow<ThisPhoneUi> =
        combine(source.facts, local, ::build).stateIn(scope, SharingStarted.Eagerly, build(source.facts.value, local.value))

    private fun build(facts: PhoneServerFacts, l: Local): ThisPhoneUi {
        val shownFacts = if (l.working == PhoneOp.Start || l.working == PhoneOp.Restart) facts.copy(starting = true) else facts
        val server = PhoneServerState.of(shownFacts)
        val answer = ThisPhoneAnswers.of(server, shownFacts, now = l.now, stopping = l.working == PhoneOp.Stop)
        val serverRows =
            when (server) {
                PhoneServerState.Running, PhoneServerState.NotAnswering -> persistentListOf(ServerRow.Stop, ServerRow.Restart)
                PhoneServerState.Starting, PhoneServerState.Checking ->
                    if (facts.status?.serverRunning == true) persistentListOf(ServerRow.Stop) else persistentListOf()
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
        )
    }

    private fun version(facts: PhoneServerFacts): VersionRowUi? {
        if (facts.status?.installed != true) return null
        val installed = facts.installedVersion?.let(::plainVersion)
        val pinned = facts.pinnedVersion?.let(::plainVersion)
        val update =
            when {
                facts.updateRunning -> UpdateRow.Updating
                installed == null || pinned == null -> null // an unknown version offers nothing (B-LINUX-078)
                installed == pinned -> UpdateRow.UpToDate
                facts.updateFailed -> UpdateRow.Failed(pinned)
                else -> UpdateRow.Available(pinned)
            }
        return VersionRowUi(installed, update)
    }

    override fun act(act: PhoneAct) {
        when (act) {
            PhoneAct.SetUp, PhoneAct.SetUpAgain -> nav.open(PhonePage.SetupStart)
            PhoneAct.ShowProgress -> nav.open(PhonePage.SetupProgress)
            PhoneAct.Start, PhoneAct.StartAgain, PhoneAct.RestartNow -> run(PhoneOp.Start) { source.start() }
            PhoneAct.OpenAppSettings ->
                scope.launch {
                    if (!settings.open(KeepAliveSetting.AppDetails)) local.update { it.copy(notice = ThisPhoneNotice.SettingsMissing) }
                }
        }
    }

    override fun stopServer(): Unit = run(PhoneOp.Stop) { source.stop() }

    override fun askRestart() {
        if (local.value.working != null) return
        local.update { it.copy(confirm = ThisPhoneConfirm.Restart(source.facts.value.replyRunning), notice = null) }
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

    override fun dismiss(): Unit = local.update { it.copy(confirm = null, notice = null) }

    override fun openRunning(): Unit = nav.open(PhonePage.Running)

    override fun openKeepRunning(): Unit = nav.open(PhonePage.KeepRunning)

    override fun openLinux(): Unit = nav.open(PhonePage.Linux)

    override fun back(): Unit = nav.back()

    init {
        // While shown: read the status once, then move the countdown each second (no timer while hidden).
        scope.launch {
            visible.collectLatest { shown ->
                if (!shown) return@collectLatest
                source.refresh()
                local.update { it.copy(now = clock.now()) }
                pacer.ticks(PhoneTimers.answerCountdown).collect { local.update { it.copy(now = clock.now()) } }
            }
        }
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
                it.copy(working = null, notice = if (outcome is Outcome.Failed) ThisPhoneNotice.Failed(op) else null)
            }
        }
    }

    private companion object {
        /** Versions read the way the manager trims them: blank is unknown, a leading `v` is ignored (B-LINUX-024/078). */
        fun plainVersion(raw: String): String? = raw.trim().removePrefix("v").removePrefix("V").ifBlank { null }
    }
}
