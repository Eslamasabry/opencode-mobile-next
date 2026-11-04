package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudget
import io.github.eslamasabry.oc.engine.runtime.storage.HostStorageAvailability
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
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
    private val details: Set<PhoneStateDetails> = emptySet(),
    private val phoneStorage: PhoneStorageReadings? = null,
    private val exits: PhoneExitNotices = PhoneExitNotices.Absent,
    private val processes: PhoneProcessSource? = null,
    private val phoneDetails: PhoneDetailsSource? = null,
) : ThisPhoneActions {
    private data class Local(
        val working: PhoneOp? = null,
        val confirm: ThisPhoneConfirm? = null,
        val notice: ThisPhoneNotice? = null,
        val now: Instant,
        val processReading: ProcessReading? = null,
        val dismissedLeftover: io.github.eslamasabry.oc.engine.runtime.procs.RunawayIdentity? =
            null,
        val lifecycle: io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport? = null,
        val storage: io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts? = null,
        val storageFailed: Boolean = false,
        val phoneStorage: io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSnapshot? =
            null,
        val measuringStorage: Boolean = false,
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
                phoneDetails?.state ?: flowOf(PhoneDetailsUi()),
                ::build,
            )
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                build(
                    source.facts.value,
                    local.value,
                    exits.notice.value,
                    phoneDetails?.state?.value ?: PhoneDetailsUi(),
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
            lifecycle = l.lifecycle,
            storage = l.storage,
            storageFailed = l.storageFailed,
            answer = answer,
            serverRows = serverRows.toImmutableList(),
            version = version(facts),
            running =
                RunningRowUi(
                    count = facts.processCount,
                    cap = ProcessBudget.ceiling(facts.limitLifted),
                    nearLimit = ProcessBudget.nearLimit(facts.processCount),
                    status = l.processReading?.status,
                ),
            working = l.working,
            confirm = l.confirm,
            notice = l.notice,
            storageSupported = phoneStorage?.supported == true,
            phoneStorage = l.phoneStorage,
            measuringStorage = l.measuringStorage,
            exitNotice = exitNotice,
            leftover = l.processReading?.leftover?.takeIf { it.identity != l.dismissedLeftover },
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
            PhoneAct.ExportProjects -> nav.open(PhonePage.ExportProjects)
            PhoneAct.SharedFolders -> nav.open(PhonePage.SharedFolders)
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

    override fun dismissRecovery() {
        details.singleOrNull()?.dismissRecovery()
    }

    override fun refreshStorage() {
        val owner = details.singleOrNull() ?: return
        scope.launch {
            when (val result = owner.measureStorage()) {
                is Outcome.Ok ->
                    local.update { it.copy(storage = result.value, storageFailed = false) }
                is Outcome.Failed -> local.update { it.copy(storageFailed = true) }
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
        local.update {
            it.copy(
                confirm = null,
                notice = null,
                dismissedLeftover = it.processReading?.leftover?.identity,
            )
        }
        exits.dismiss()
    }

    override fun measureStorage() {
        readStorage(includeBuiltinHost = true)
    }

    private fun readStorage(includeBuiltinHost: Boolean) {
        val reader = phoneStorage ?: return
        if (!reader.supported || local.value.measuringStorage) return
        local.update { it.copy(measuringStorage = true) }
        scope.launch {
            try {
                val measured = reader.read(includeBuiltinHost)
                local.update { current ->
                    // A plain free-space refresh keeps sizes the person asked to measure earlier.
                    val earlier = current.phoneStorage
                    val kept =
                        if (
                            measured.builtinAvailability == HostStorageAvailability.NotRequested &&
                                earlier != null &&
                                earlier.builtinAvailability != HostStorageAvailability.NotRequested
                        )
                            measured.copy(
                                builtinHost = earlier.builtinHost,
                                builtinAvailability = earlier.builtinAvailability,
                            )
                        else measured
                    current.copy(phoneStorage = kept)
                }
            } finally {
                local.update { it.copy(measuringStorage = false) }
            }
        }
    }

    override fun openRunning(): Unit = nav.open(PhonePage.Running)

    override fun openKeepRunning(): Unit = nav.open(PhonePage.KeepRunning)

    override fun openLinux(): Unit = nav.open(PhonePage.Linux)

    override fun openTerminal(): Unit = nav.open(PhonePage.Terminal)

    override fun back(): Unit = nav.back()

    init {
        details.singleOrNull()?.let { owner ->
            scope.launch {
                owner.recovery.collect { report -> local.update { it.copy(lifecycle = report) } }
            }
        }
        // While shown: read the status once, then move the countdown each second (no timer while
        // hidden).
        scope.launch {
            visible.collectLatest { shown ->
                phoneDetails?.shown(shown)
                if (!shown) return@collectLatest
                source.refresh()
                details.singleOrNull()?.refresh()
                refreshStorage()
                readStorage(includeBuiltinHost = false)
                launch { phoneDetails?.refresh() }
                local.update { it.copy(now = clock.now()) }
                coroutineScope {
                    if (processes != null)
                        launch {
                            readProcesses()
                            pacer.ticks(PhoneTimers.runningRefresh).collect { readProcesses() }
                        }
                    pacer.ticks(PhoneTimers.answerCountdown).collect {
                        local.update { it.copy(now = clock.now()) }
                    }
                }
            }
        }
        scope.launch {
            combine(visible, local) { shown, value -> shown && value.detailsOpen }
                .distinctUntilChanged()
                .collectLatest { reading ->
                    if (!reading) return@collectLatest
                    phoneDetails?.refreshLog()
                    pacer.ticks(PhoneTimers.detailsLog).collect {
                        if (source.facts.value.status?.serverRunning == true) phoneDetails?.refreshLog()
                    }
                }
        }
    }

    init {
        scope.launch {
            combine(visible, phoneDetails?.state ?: flowOf(PhoneDetailsUi())) { shown, reading ->
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
                            phoneDetails?.refreshHistory() == true
                        }
                        .collect {}
                }
        }
    }

    override fun connect(): Unit =
        run(PhoneOp.Connect) {
            phoneDetails?.connect() ?: Outcome.Failed(Failure.Unavailable(FeatureId("builtinLinux")))
        }

    override fun refreshDetails() {
        scope.launch { phoneDetails?.refresh() }
    }

    override fun showDetails(expanded: Boolean) {
        local.update { it.copy(detailsOpen = expanded) }
    }

    override fun showInstalled(expanded: Boolean) {
        local.update { it.copy(installedOpen = expanded) }
    }

    private suspend fun readProcesses() {
        when (val answer = processes?.read()) {
            is Outcome.Ok -> local.update { it.copy(processReading = answer.value) }
            else -> Unit
        }
    }

    override fun shown(visible: Boolean) {
        if (!visible) details.singleOrNull()?.invalidate()
        this.visible.value = visible
    }

    private fun run(op: PhoneOp, command: suspend () -> Outcome<Unit>) {
        if (local.value.working != null) return
        details.singleOrNull()?.invalidate()
        local.update { it.copy(working = op, notice = null) }
        scope.launch {
            val outcome = command()
            details.singleOrNull()?.refresh()
            local.update {
                it.copy(
                    working = null,
                    notice = if (outcome is Outcome.Failed) ThisPhoneNotice.Failed(op) else null,
                )
            }
            if (visible.value) phoneDetails?.refresh()
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
