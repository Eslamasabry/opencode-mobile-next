package io.github.eslamasabry.oc.feature.phonesetup.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.linuxsetup.SetupDevice
import io.github.eslamasabry.oc.engine.linuxsetup.SetupEngine
import io.github.eslamasabry.oc.engine.linuxsetup.SetupJourney
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflight
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Android reports about this phone, and the Storage settings page (bound in androidApp PhoneSetupWiring). */
public interface PhoneSetupDevice {
    public suspend fun facts(): SetupDevice

    /** Opens Android's Storage settings; false when no page could open. */
    public fun openStorageSettings(): Boolean
}

/** The native job runner's controls the engine does not own: the app-run step, Stop, and the poll pause. */
public interface PhoneSetupJobs {
    /** Runs the job's "Start OpenCode" step (profile, password, start) and reports it to the runner. */
    public suspend fun finishStep(jobId: String, openCodeChanged: Boolean)

    /** Stops the running job; what finished stays installed. */
    public suspend fun cancel()

    /** The pause between two reads of setup.json while a job runs (features may not import delay, K1). */
    public suspend fun pause()
}

/** Where the page leads. */
public interface PhoneSetupNavigator {
    public fun back()

    /** This phone, once setup is done. */
    public fun thisPhone()
}

/** One component the start page lists. */
public data class SetupItemUi(val id: String, val title: String)

/** The page: the start (preflight, what is installed) until a job exists, then that job's progress. */
public data class PhoneSetupUi(
    val checking: Boolean = true,
    val preflight: SetupPreflightResult? = null,
    val items: ImmutableList<SetupItemUi> = persistentListOf(),
    val downloadBytes: Long = 0,
    /** A job exists (running or ended): the page shows its progress. */
    val progress: SetupProgress? = null,
    val starting: Boolean = false,
    val details: Redacted? = null,
    /** The Stop confirmation is open (steer, never queue: one question at a time). */
    val confirmingStop: Boolean = false,
    val storageUnopened: Boolean = false,
) {
    val canSetUp: Boolean get() = !checking && !starting && preflight?.supported != false && progress == null
}

public interface PhoneSetupActions {
    public fun back()

    public fun setUp()

    public fun continueSetup()

    public fun askStop()

    public fun keepGoing()

    public fun stop()

    public fun openStorage()

    public fun done()
}

/**
 * The Set up page's rules (Flutter phone_setup_start_screen.dart + phone_setup_progress_screen.dart): a job already on
 * the phone is shown, never restarted; otherwise the preflight decides whether "Set up" is offered. While a job runs,
 * setup.json is read again after each [PhoneSetupJobs.pause]; the app-run step is started once per job.
 */
public class PhoneSetupModel(
    private val engine: SetupEngine,
    private val device: PhoneSetupDevice,
    private val jobs: PhoneSetupJobs,
    private val nav: PhoneSetupNavigator,
    private val scope: CoroutineScope,
) : PhoneSetupActions {
    private val mutable = MutableStateFlow(PhoneSetupUi())
    public val state: StateFlow<PhoneSetupUi> = mutable.asStateFlow()
    private val stepsStarted = mutableSetOf<String>()
    private var polling = false

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val job = SetupJourney.defaultJob(engine.registry)
        val items = job.filter { !it.jobStep && !it.app }.map { SetupItemUi(it.id, it.title) }.toImmutableList()
        val download = SetupJourney.downloadBytes(job)
        mutable.update { it.copy(items = items, downloadBytes = download) }
        engine.restore()
        val existing = engine.progress.value
        if (existing.state != SetupState.Idle) {
            show(existing)
            follow()
            return
        }
        val facts = device.facts()
        val result = SetupPreflight.check(facts, download, SetupJourney.installedBytes(job))
        mutable.update { it.copy(checking = false, preflight = result) }
    }

    private fun show(progress: SetupProgress) {
        mutable.update {
            it.copy(
                checking = false,
                starting = false,
                progress = progress.takeIf { p -> p.state != SetupState.Idle },
                details = SetupJourney.details(progress),
            )
        }
    }

    /** Reads the job until it ends; starts the app-run step when the runner waits for it. */
    private suspend fun follow() {
        if (polling) return
        polling = true
        try {
            while (true) {
                val progress = engine.progress.value
                show(progress)
                val jobId = progress.jobId
                if (jobId != null && SetupJourney.stepDue(progress) && stepsStarted.add(jobId)) {
                    scope.launch { jobs.finishStep(jobId, SetupJourney.openCodeChanged(progress)) }
                }
                if (progress.state != SetupState.Running) return
                jobs.pause()
                engine.restore()
            }
        } finally {
            polling = false
        }
    }

    override fun back(): Unit = nav.back()

    override fun setUp() {
        if (!mutable.value.canSetUp) return
        mutable.update { it.copy(starting = true) }
        scope.launch {
            engine.run(SetupJourney.defaultIds(engine.registry))
            show(engine.progress.value)
            follow()
        }
    }

    override fun continueSetup() {
        val progress = mutable.value.progress ?: return
        if (!progress.canContinue || mutable.value.starting) return
        mutable.update { it.copy(starting = true) }
        scope.launch {
            engine.run(SetupJourney.defaultIds(engine.registry))
            show(engine.progress.value)
            follow()
        }
    }

    override fun askStop() {
        if (mutable.value.progress?.state == SetupState.Running) mutable.update { it.copy(confirmingStop = true) }
    }

    override fun keepGoing(): Unit = mutable.update { it.copy(confirmingStop = false) }

    override fun stop() {
        mutable.update { it.copy(confirmingStop = false) }
        scope.launch { jobs.cancel() }
    }

    override fun openStorage() {
        val opened = device.openStorageSettings()
        mutable.update { it.copy(storageUnopened = !opened) }
    }

    override fun done(): Unit = nav.thisPhone()
}
