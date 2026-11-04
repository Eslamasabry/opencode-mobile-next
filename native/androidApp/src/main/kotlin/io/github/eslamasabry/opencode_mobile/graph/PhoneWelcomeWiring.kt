package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflight
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeSource
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeSetupLaunch
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeSetupPhase
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupDevice
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneySource
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The screen, customization and welcome all issue commands to NativePhoneSetup's one runtime
 * SetupEngine.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NativeSetupJourney(
    private val setup: NativePhoneSetup,
    private val device: PhoneSetupDevice,
    private val voice: NativeVoiceSetupComponent,
    private val platform: PlatformCapabilities,
    private val redactor: Redactor,
) : SetupJourneySource {
    override val progress: StateFlow<SetupProgress> = setup.progress
    override val toolsChanged: StateFlow<Int> = setup.toolsChanged
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    override val selection: StateFlow<Set<String>> = selected.asStateFlow()
    private var initialized = false

    override fun details(progress: SetupProgress): Redacted? =
        progress.logTail.takeIf { it.isNotBlank() }?.let(redactor::details)

    override suspend fun restore(): Outcome<Unit> = setup.refresh()

    override suspend fun options(adding: Boolean): Outcome<SetupOptions> {
        if (!platform.builtinLinux) return Failure.Unavailable(FeatureId("builtinLinux")).failed()
        val components = setup.components()
        if (components is Outcome.Failed) return components
        val all = (components as Outcome.Ok).value.filter { !it.app || voice.lastOffer != null }
        val installed =
            if (adding) {
                when (val value = setup.optionalIds()) {
                    is Outcome.Failed -> return value
                    is Outcome.Ok -> value.value
                }
            } else if (voice.lastOffer?.installed == true) setOf(SetupComponentIds.VOICE)
            else emptySet()
        val facts = device.facts()
        if (!initialized) {
            selected.value =
                all.filter { !it.required && it.defaultOn && it.id !in installed }
                    .map { it.id }
                    .toSet()
            initialized = true
        }
        return Outcome.Ok(
            SetupOptions(
                all,
                installed,
                SetupDeviceInfo(
                    facts.supportedAbis,
                    facts.totalMemoryMb,
                    facts.availableStorageBytes,
                ),
            )
        )
    }

    override suspend fun choose(ids: Set<String>): Outcome<Unit> {
        selected.value = ids
        initialized = true
        return Outcome.Ok(Unit)
    }

    override suspend fun run(ids: Set<String>, adding: Boolean): Outcome<Unit> {
        val options = options(adding)
        if (options is Outcome.Failed) return options
        val value = (options as Outcome.Ok).value
        if (ids.any { id -> value.components.none { it.id == id } })
            return Failure.Runtime(RuntimeProblem.Generic).failed()
        val install = SetupSelection.install(value, ids, adding)
        if (adding && install.isEmpty()) return Failure.Runtime(RuntimeProblem.Generic).failed()
        val preflight = SetupPreflight.check(value.device, SetupSelection.bytes(install))
        if (!preflight.supported) return Failure.Runtime(RuntimeProblem.Generic).failed()
        return setup.runSelection(ids, adding)
    }

    override suspend fun resume(): Outcome<Unit> = setup.continueSelection()

    override suspend fun cancel(jobId: String): Outcome<Unit> = setup.cancelExpected(jobId)

    override suspend fun removeVoice(): Outcome<Unit> = setup.removeVoice()

    override fun openStorage(): Boolean = device.openStorageSettings()
}

/**
 * One bounded restore on an untouched welcome; a known running job is watched without starting
 * another job.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PhoneWelcomeSource>())
@Inject
class NativePhoneWelcome(
    private val journey: SetupJourneySource,
    private val platform: PlatformCapabilities,
    private val scope: AppCoroutineScope,
) : PhoneWelcomeSource {
    private val mutable = MutableStateFlow(PhoneWelcomeUi(available = platform.builtinLinux))
    override val state: StateFlow<PhoneWelcomeUi> = mutable.asStateFlow()
    private val lane = Mutex()
    private var watcher: Job? = null

    override suspend fun restore() = lane.withLock {
        if (!platform.builtinLinux) return@withLock
        withTimeoutOrNull(3_000) {
            if (journey.restore() is Outcome.Ok) {
                show(journey.progress.value)
                watchKnownJob()
                val options = journey.options(false)
                if (options is Outcome.Ok) {
                    val defaults =
                        SetupSelection.install(options.value, defaultIds(options.value), false)
                    mutable.update {
                        it.copy(
                            seconds = SetupSelection.seconds(defaults),
                            bytes = SetupSelection.bytes(defaults),
                        )
                    }
                }
            }
        }
        Unit
    }

    private fun defaultIds(options: SetupOptions): Set<String> =
        options.components.filter { !it.required && it.defaultOn }.map { it.id }.toSet()

    private fun watchKnownJob() {
        if (journey.progress.value.state != SetupState.Idle && watcher == null)
            watcher = scope.launch { journey.progress.collect(::show) }
    }

    private fun show(progress: SetupProgress) {
        mutable.update {
            it.copy(
                phase =
                    when (progress.state) {
                        SetupState.Idle -> WelcomeSetupPhase.Idle
                        SetupState.Running -> WelcomeSetupPhase.Running
                        SetupState.Done -> WelcomeSetupPhase.Done
                        else -> WelcomeSetupPhase.Stopped
                    },
                percent = kotlin.math.floor(progress.overall * 100).toInt().coerceIn(0, 99),
            )
        }
    }

    override suspend fun start(): Outcome<WelcomeSetupLaunch> = lane.withLock {
        if (mutable.value.working) return@withLock Failure.Runtime(RuntimeProblem.SlotBusy).failed()
        mutable.update { it.copy(working = true, failure = null) }
        val result: Outcome<WelcomeSetupLaunch> =
            try {
                val read = journey.restore()
                if (read is Outcome.Failed) return@withLock failedStart(read.failure)
                val progress = journey.progress.value
                if (progress.state == SetupState.Running) Outcome.Ok(WelcomeSetupLaunch.Progress)
                else if (progress.state != SetupState.Idle) {
                    when (val resumed = journey.resume()) {
                        is Outcome.Failed -> resumed
                        is Outcome.Ok -> Outcome.Ok(WelcomeSetupLaunch.Progress)
                    }
                } else {
                    when (val options = journey.options(false)) {
                        is Outcome.Failed -> options
                        is Outcome.Ok -> {
                            val ids = defaultIds(options.value)
                            journey.choose(ids)
                            val job =
                                SetupSelection.install(
                                    options.value,
                                    ids,
                                    false,
                                )
                            val preflight =
                                SetupPreflight.check(
                                    options.value.device,
                                    SetupSelection.bytes(job),
                                )
                            if (
                                !preflight.supported ||
                                    preflight is
                                        io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult.MayBeSlow
                            )
                                Outcome.Ok(WelcomeSetupLaunch.Setup)
                            else
                                when (val began = journey.run(ids, false)) {
                                    is Outcome.Failed -> began
                                    is Outcome.Ok -> Outcome.Ok(WelcomeSetupLaunch.Progress)
                                }
                        }
                    }
                }
            } finally {
                mutable.update { it.copy(working = false) }
            }
        mutable.update { it.copy(failure = (result as? Outcome.Failed)?.failure) }
        show(journey.progress.value)
        watchKnownJob()
        result
    }

    private fun failedStart(failure: Failure): Outcome<WelcomeSetupLaunch> {
        mutable.update { it.copy(working = false, failure = failure) }
        return failure.failed()
    }
}
