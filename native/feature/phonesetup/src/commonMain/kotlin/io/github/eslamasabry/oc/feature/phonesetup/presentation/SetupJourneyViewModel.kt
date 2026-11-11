package io.github.eslamasabry.oc.feature.phonesetup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflight
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class SetupJourneyUi(
    val customizing: Boolean = false,
    val adding: Boolean = false,
    val options: SetupOptions? = null,
    val chosen: Set<String> = emptySet(),
    val checking: Boolean = true,
    val working: Boolean = false,
    val progress: SetupProgress = SetupProgress.Idle,
    val details: Redacted? = null,
    val failure: Failure? = null,
    val confirmStop: String? = null,
    val confirmRemoveVoice: Boolean = false,
    val storageUnopened: Boolean = false,
    /** The Ready step can make the first project (Flutter phone_setup_ready_screen.dart). */
    val canMakeProject: Boolean = false,
    /** The Ready step's name field; it starts as Flutter's suggestion. */
    val projectName: String = FirstProjectName.SUGGESTED,
    val nameProblem: FirstProjectNameProblem? = null,
    val creatingProject: Boolean = false,
    /** The last Create and open did not finish: which step, and why. */
    val projectFailure: Pair<FirstProjectStep, Failure>? = null,
    /** The network the setup downloads over, for the ring's cost line. */
    val transport: SetupTransport = SetupTransport.Unknown,
    /** Notifications are allowed: "Safe to leave. We'll let you know when it's ready." */
    val canNotify: Boolean = false,
    /** The phone closed the app while the setup ran (#138); the page says so instead of "not answering". */
    val systemKill: SystemKill? = null,
    /** The battery steps for this phone's maker, while the phone is likely to close the app. */
    val batteryCard: BatteryCard? = null,
    /** The battery button found nothing to open: the steps on the card are the way on. */
    val batteryOpenFailed: Boolean = false,
) {
    val install: List<io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent>
        get() = options?.let { SetupSelection.install(it, chosen, adding) }.orEmpty()

    val bytes: Long
        get() = SetupSelection.bytes(install)

    val seconds: Long
        get() = SetupSelection.seconds(install)

    /** The extracted size of the install, when every part of it reports one (the start check counts it too). */
    val installedBytes: Long?
        get() = install.map { it.installedBytes }.takeIf { sizes -> sizes.isNotEmpty() && sizes.all { it != null } }
            ?.sumOf { it ?: 0L }

    val preflight: SetupPreflightResult?
        get() = options?.let { SetupPreflight.check(it.device, bytes, installedBytes) }

    /**
     * What the install needs free and what this phone has, while the phone is within 1 GB of the need (so a nearly
     * full phone is told before the download, not only when it is already too full). Null when free space is unknown.
     */
    val spaceTight: Pair<Long, Long>?
        get() {
            val free = options?.device?.availableStorageBytes ?: return null
            if (bytes <= 0) return null
            val need = SetupPreflight.requiredFreeBytes(bytes, installedBytes)
            return if (free < need + SPACE_MARGIN_BYTES) need to free else null
        }

    val canConfirm: Boolean
        get() =
            !checking &&
                !working &&
                options != null &&
                failure == null &&
                preflight?.supported != false &&
                (!adding || install.isNotEmpty())
}

public interface SetupJourneyActions {
    public fun back()

    public fun retry()

    public fun toggle(id: String, checked: Boolean)

    public fun accept()

    public fun start()

    public fun customize()

    public fun continueSetup()

    public fun askStop()

    public fun cancelConfirm()

    public fun confirmStop()

    public fun askRemoveVoice()

    public fun confirmRemoveVoice()

    public fun openStorage()

    public fun done()

    /** Node.js cannot run on this phone (#117): use OpenCode through a computer instead. */
    public fun connectComputer() {}

    /** Node.js cannot run on this phone (#117): send the failure as a problem report. */
    public fun reportFailure() {}

    /** The battery card's button: the app's own page, or the maker's screen when it resolves. */
    public fun openBatterySettings(open: BatteryOpen) {}

    /** The Ready step's name field changed. */
    public fun projectName(name: String) {}

    /** "Create and open": the folder, then its first conversation. */
    public fun createProject() {}

    /** "Open a folder instead": the project picker, for a folder that exists. */
    public fun openFolderInstead() {}
}

/**
 * Pure presentation over the shared durable owner; this class never starts a second runner or polls
 * setup.json.
 */
@AssistedInject
public class SetupJourneyViewModel(
    @Assisted private val route: PhoneSetupRoute,
    private val source: SetupJourneySource,
    private val nav: AppNavigator,
    private val firstProject: FirstProject = NoFirstProject(),
    private val ringFacts: SetupRingFacts = NoSetupRingFacts(),
    private val keepAlive: SetupKeepAlive = NoSetupKeepAlive(),
) : ViewModel(), SetupJourneyActions {
    private val customizing = route is PhoneSetupRoute.Customize
    private val adding = (route as? PhoneSetupRoute.Customize)?.adding == true
    private val mutable =
        MutableStateFlow(
            SetupJourneyUi(customizing = customizing, adding = adding, canMakeProject = !customizing && firstProject.available())
        )
    public val state: StateFlow<SetupJourneyUi> = mutable.asStateFlow()
    private var reads = 0
    private var loadedOnce = false
    private var judged: Pair<SetupState, String?>? = null

    init {
        viewModelScope.launch {
            source.progress.collect { progress ->
                mutable.update { it.copy(progress = progress, details = source.details(progress), canNotify = ringFacts.canNotify()) }
                judgeKeepAlive(progress)
                // Flutter `_readNetwork`: what Android answers now, read again as the setup moves on.
                val transport = ringFacts.transport()
                mutable.update { it.copy(transport = transport) }
            }
        }
        viewModelScope.launch {
            source.selection.collect { selected ->
                if (!adding && !customizing) mutable.update { it.copy(chosen = selected) }
            }
        }
        viewModelScope.launch { source.toolsChanged.drop(1).collect { retry() } }
        retry()
    }

    /**
     * Once per state of a job: a stopped job asks whether the phone killed the app, and the battery card is decided
     * for a setup not yet started or stopped. A running or finished setup shows neither.
     */
    private suspend fun judgeKeepAlive(progress: SetupProgress) {
        val key = progress.state to progress.jobId
        if (key == judged) return
        judged = key
        val stopped = progress.state == SetupState.Failed || progress.state == SetupState.Interrupted
        val waiting = stopped || progress.state == SetupState.Idle
        // Only a setup the system stopped ("interrupted") can be a kill. A step that failed while the app lived says
        // its own reason (no network, storage, the step), never "ran short of memory".
        val kill =
            if (progress.state == SetupState.Interrupted) keepAlive.interruption(progress.startedAtMs) else null
        val card = if (waiting) keepAlive.facts()?.let { BatteryCard.decide(it, afterKill = kill != null) } else null
        mutable.update { it.copy(systemKill = kill, batteryCard = card, batteryOpenFailed = false) }
    }

    override fun openBatterySettings(open: BatteryOpen) {
        val maker = state.value.batteryCard?.maker ?: return
        val opened = SettingsOpener.open(BatteryGuide.targets(maker, open), keepAlive::tryOpen)
        mutable.update { it.copy(batteryOpenFailed = !opened) }
    }

    override fun retry() {
        val revision = ++reads
        mutable.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            val restore = source.restore()
            val options = source.options(adding)
            if (revision != reads) return@launch
            when (options) {
                is Outcome.Failed ->
                    mutable.update { it.copy(checking = false, failure = options.failure) }
                is Outcome.Ok -> {
                    val previous =
                        if (loadedOnce) state.value.chosen
                        else if (adding) emptySet() else source.selection.value
                    val known = options.value.components.map { it.id }.toSet()
                    val installed = options.value.installed
                    val chosen =
                        previous.filterTo(mutableSetOf()) { it in known && it !in installed }
                    loadedOnce = true
                    mutable.update {
                        it.copy(
                            checking = false,
                            options = options.value,
                            chosen = chosen,
                            failure = (restore as? Outcome.Failed)?.failure,
                        )
                    }
                }
            }
        }
    }

    override fun toggle(id: String, checked: Boolean) {
        val options = state.value.options ?: return
        if (state.value.checking || state.value.working) return
        val component = options.components.firstOrNull { it.id == id } ?: return
        if (component.required || id in options.installed) return
        mutable.update {
            it.copy(
                chosen =
                    SetupSelection.toggle(
                        options.components,
                        it.chosen,
                        options.installed,
                        id,
                        checked,
                    )
            )
        }
    }

    override fun accept() {
        if (!state.value.canConfirm) return
        val selected = state.value.chosen.toSet()
        if (adding) begin(openProgress = true) { source.run(selected, true) }
        else {
            viewModelScope.launch {
                source.choose(selected)
                nav.back()
            }
        }
    }

    override fun start() {
        if (!state.value.canConfirm) return
        val selected = state.value.chosen.toSet()
        begin { source.run(selected, false) }
    }

    private fun begin(openProgress: Boolean = false, command: suspend () -> Outcome<Unit>) {
        if (state.value.working) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = command()
            mutable.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
            if (result is Outcome.Ok && openProgress) {
                nav.back()
                nav.open(PhoneSetupRoute.Setup)
            }
        }
    }

    override fun customize() {
        nav.open(PhoneSetupRoute.Customize())
    }

    override fun continueSetup() {
        begin { source.resume() }
    }

    override fun askStop() {
        if (state.value.progress.state == SetupState.Running)
            mutable.update {
                it.copy(confirmStop = it.progress.jobId, confirmRemoveVoice = false, failure = null)
            }
    }

    override fun cancelConfirm() {
        if (!state.value.working)
            mutable.update {
                it.copy(confirmStop = null, confirmRemoveVoice = false, failure = null)
            }
    }

    override fun confirmStop() {
        val jobId = state.value.confirmStop ?: return
        begin {
            source.cancel(jobId).also {
                if (it is Outcome.Ok) mutable.update { s -> s.copy(confirmStop = null) }
            }
        }
    }

    override fun askRemoveVoice() {
        if (!state.value.working)
            mutable.update {
                it.copy(confirmRemoveVoice = true, confirmStop = null, failure = null)
            }
    }

    override fun confirmRemoveVoice() {
        begin {
            source.removeVoice().also {
                if (it is Outcome.Ok) {
                    mutable.update { s -> s.copy(confirmRemoveVoice = false) }
                    retry()
                }
            }
        }
    }

    override fun openStorage() {
        mutable.update { it.copy(storageUnopened = !source.openStorage()) }
        retry()
    }

    override fun back() {
        nav.back()
    }

    override fun done() {
        nav.back()
        nav.open(PhoneRoute.ThisPhone)
    }

    override fun connectComputer() {
        nav.back()
        nav.open(ServersRoute.Edit())
    }

    override fun reportFailure() {
        nav.open(SettingsRoute.ReportProblem)
    }

    override fun projectName(name: String) {
        if (!state.value.creatingProject) mutable.update { it.copy(projectName = name, nameProblem = null, projectFailure = null) }
    }

    override fun createProject() {
        val s = state.value
        if (!s.canMakeProject || s.creatingProject || s.progress.state != SetupState.Done) return
        val problem = FirstProjectName.problem(s.projectName)
        if (problem != null) return mutable.update { it.copy(nameProblem = problem) }
        mutable.update { it.copy(creatingProject = true, nameProblem = null, projectFailure = null) }
        viewModelScope.launch {
            val made = firstProject.create(s.projectName.trim())
            if (made is Outcome.Failed)
                return@launch mutable.update { it.copy(creatingProject = false, projectFailure = FirstProjectStep.Create to made.failure) }
            val opened = firstProject.open((made as Outcome.Ok).value)
            mutable.update {
                it.copy(creatingProject = false, projectFailure = (opened as? Outcome.Failed)?.let { f -> FirstProjectStep.Open to f.failure })
            }
            if (opened is Outcome.Ok) {
                // Straight into the new project's first conversation, the setup closed beneath it.
                nav.back()
                nav.open(opened.value)
            }
        }
    }

    override fun openFolderInstead() {
        if (state.value.creatingProject) return
        nav.back()
        nav.open(ProjectsRoute.Picker())
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PhoneSetupRoute): SetupJourneyViewModel
    }
}

private const val SPACE_MARGIN_BYTES: Long = 1_000_000_000
