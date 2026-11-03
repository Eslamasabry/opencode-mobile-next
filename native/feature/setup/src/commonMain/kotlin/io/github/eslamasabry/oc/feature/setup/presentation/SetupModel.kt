package io.github.eslamasabry.oc.feature.setup.presentation

import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobParams
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgressRules
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupRegistry
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The setup page's state holder (port of phone_setup_start_screen.dart's fresh state and phone_setup_progress_screen.dart).
 * Plain Kotlin over a scope so its rules test on virtual time; [SetupViewModel] hands it `viewModelScope`.
 *
 * - Opening reads the persisted job first ([SetupSource.restore]): until then nothing is said, so an old "done" in
 *   memory never shows as this phone's state (N1-5).
 * - With no job the start page offers the first setup's default selection (required + on by default) with
 *   `SetupJobParams.firstSetup`; with a job it shows that job live. Back leaves; the job keeps going.
 * - Within one stage the measured share never goes backwards, and the time left never goes up within a job.
 */
public class SetupModel(
    private val source: SetupSource,
    private val nav: SetupNavigator,
    private val scope: CoroutineScope,
    private val details: SetupDetails,
) : SetupActions {
    private data class Local(val restored: Boolean = false, val working: Boolean = false, val settingsMissing: Boolean = false)

    private val local = MutableStateFlow(Local())
    private val progress = MutableStateFlow(SetupProgress.Idle)
    private var session: SetupSession? = null
    private val components: List<SetupComponent> get() = session?.components.orEmpty()

    // Per-job memory of what was shown (Flutter `_track` / `_measuredOf`).
    private var job: String? = null
    private var eta: Int? = null
    private var measuredKey = ""
    private var measured = 0f

    public val state: StateFlow<SetupUi> =
        combine(progress, local, ::build).stateIn(scope, SharingStarted.Eagerly, build(progress.value, local.value))

    init {
        scope.launch {
            val opened = source.open()
            session = opened
            source.restore()
            progress.value = opened.progress.value
            local.update { it.copy(restored = true) }
            // Watching keeps the engine reading while this page is open (its idle interval when nothing runs).
            opened.progress.collect { progress.value = it }
        }
    }

    private fun build(progress: SetupProgress, l: Local): SetupUi {
        val phase =
            when {
                !l.restored -> SetupPhase.Loading
                progress.state == SetupState.Idle && progress.components.isEmpty() -> SetupPhase.Start(startPage())
                else -> SetupPhase.Progress(progressOf(progress))
            }
        return SetupUi(phase = phase, working = l.working, settingsMissing = l.settingsMissing)
    }

    private fun registry(): List<SetupComponent> = components

    private fun defaultIds(): Set<String> = PhoneSetupRegistry.defaultSelection(registry())

    private fun startPage(): SetupStartUi {
        val chosen = registry().filter { it.id in defaultIds() }
        return SetupStartUi(PhoneSetupRegistry.downloadBytes(chosen), PhoneSetupRegistry.includedNames(chosen).toImmutableList())
    }

    private fun titleOf(id: String): String = registry().firstOrNull { it.id == id }?.shortTitle ?: id

    /** The job's own rows, or before its first poll the default selection as waiting rows. */
    private fun rowsOf(progress: SetupProgress): List<ComponentProgress> {
        if (progress.components.isNotEmpty()) return progress.components
        val ids = defaultIds()
        return registry().filter { it.id in ids }.map { ComponentProgress(it.id, ComponentState.Pending) }
    }

    private fun progressOf(progress: SetupProgress): SetupProgressUi {
        track(progress)
        val rows = rowsOf(progress)
        fun finished(r: ComponentProgress) = r.state == ComponentState.Done || r.state == ComponentState.Skipped
        val state = progress.state
        val halted = state == SetupState.Failed || state == SetupState.Interrupted || state == SetupState.Cancelled

        // The step being worked on: running, checking or failed; else the engine's current; else the first left.
        var current = rows.indexOfFirst {
            it.state == ComponentState.Running || it.state == ComponentState.Failed || it.state == ComponentState.Checking
        }
        if (current < 0) current = rows.indexOfFirst { it.id == progress.current && !finished(it) }
        if (current < 0 && halted) current = rows.indexOfFirst { !finished(it) }
        val row = rows.getOrNull(current)
        val checking = rows.isNotEmpty() && rows.all { it.state == ComponentState.Checking || finished(it) } &&
            rows.any { it.state == ComponentState.Checking }

        var subject: String? = null
        val headline =
            when {
                state == SetupState.Done -> SetupHeadline.Done
                state == SetupState.Failed -> SetupHeadline.Failed
                state == SetupState.Interrupted -> SetupHeadline.Interrupted
                state == SetupState.Cancelled -> SetupHeadline.Paused
                checking -> SetupHeadline.Checking
                row == null || row.state == ComponentState.Pending -> SetupHeadline.GettingStarted
                else -> {
                    subject = titleOf(row.id)
                    scene(progress, rows)
                }
            }
        val measuredNow = if (!halted && row != null) measuredOf(row) else null
        val bytes =
            if (measuredNow != null && row != null && (row.bytesTotal ?: 0) > 0 && row.bytesDone != null) {
                SetupProgressRules.formatBytePair(row.bytesDone!!, row.bytesTotal!!)
            } else {
                null
            }
        val running = state == SetupState.Running
        val shownEta = if (running) eta else null
        return SetupProgressUi(
            state = state,
            headline = headline,
            subject = subject,
            step = if (row != null && !checking && state != SetupState.Done) current + 1 else null,
            of = rows.size,
            measured = measuredNow,
            bytes = bytes,
            minutesLeft = shownEta?.let { max(1, (it / 60.0).roundToInt()) },
            failure = if (state == SetupState.Failed) failureOf(progress) else null,
            steps = rows.map(::stepOf).toImmutableList(),
            details = progress.logTail.takeIf { it.isNotBlank() }?.let(details::of),
        )
    }

    private fun stepOf(row: ComponentProgress): SetupStepUi {
        val total = row.bytesTotal
        val done = row.bytesDone
        val working = row.state == ComponentState.Running || row.state == ComponentState.Checking
        return SetupStepUi(
            id = row.id,
            title = titleOf(row.id),
            state = row.state,
            stage = if (working) row.stage else null,
            bytes = if (working && total != null && total > 0 && done != null) SetupProgressRules.formatBytePair(done, total) else null,
            percent = if (working && (total == null || total <= 0)) row.percent?.coerceIn(0.0, 100.0)?.toInt() else null,
            version = row.version,
        )
    }

    /** Flutter `SetupProgressView.sceneFor`: which kind of work the current step is doing. */
    private fun scene(progress: SetupProgress, rows: List<ComponentProgress>): SetupHeadline {
        val current = rows.firstOrNull {
            it.state == ComponentState.Running || it.state == ComponentState.Checking || it.state == ComponentState.Failed ||
                it.id == progress.current
        } ?: return if (rows.isNotEmpty() && rows.all { it.state == ComponentState.Done || it.state == ComponentState.Skipped }) {
            SetupHeadline.Starting
        } else {
            SetupHeadline.Downloading
        }
        if (current.id == SetupComponentIds.START) return SetupHeadline.Starting
        val stage = current.stage.orEmpty()
        if (stage == session?.unpackingLinuxStage || unpacking.containsMatchIn(stage)) return SetupHeadline.Unpacking
        val total = current.bytesTotal ?: 0
        if ((total > 0 && (current.bytesDone ?: 0) < total) || stage == session?.downloadingLinuxStage ||
            downloading.containsMatchIn(stage)
        ) {
            return SetupHeadline.Downloading
        }
        return SetupHeadline.Installing
    }

    /** Flutter `failureSentence`: the failed row's (or else the job's) worded reason; never the log's text. */
    private fun failureOf(progress: SetupProgress): SetupFailureLine {
        val ownership = SetupProgressRules.needsOwnershipRecovery(progress)
        if (ownership) return SetupFailureLine.Ownership
        val row = progress.components.firstOrNull { it.state == ComponentState.Failed }
        val error = row?.error ?: progress.error
        if (error != null && error.worded) return SetupFailureLine.Error(error)
        if (row != null) return row.stage?.let { SetupFailureLine.During(it) } ?: SetupFailureLine.Step
        return progress.current?.let { SetupFailureLine.At(titleOf(it)) } ?: SetupFailureLine.Unknown
    }

    /** The errors that have their own sentence; the rest say where it stopped and leave the rest to Details. */
    private val SetupError.worded: Boolean
        get() = this !is SetupError.HostRefused && this !is SetupError.Unexpected && this !is SetupError.HostMismatch

    private fun track(progress: SetupProgress) {
        val id = progress.jobId ?: progress.components.joinToString("") { it.id }
        if (id != job) {
            job = id
            eta = null
            measuredKey = ""
            measured = 0f
        }
        val raw = progress.etaSeconds
        if (progress.state != SetupState.Running || raw == null) return
        val later = progress.components.filter { it.state == ComponentState.Pending }
            .sumOf { row -> registry().firstOrNull { it.id == row.id }?.estimatedSeconds ?: 0 }
        val estimate = max(raw, later)
        eta = eta?.let { min(it, estimate) } ?: estimate
    }

    /** The current step's measured share, never less than this stage already showed; null when it reports none. */
    private fun measuredOf(row: ComponentProgress): Float? {
        if (row.state != ComponentState.Running) return null
        val total = row.bytesTotal
        val fraction =
            if (total != null && total > 0 && row.bytesDone != null) (row.bytesDone!!.toDouble() / total).coerceIn(0.0, 1.0)
            else row.percent?.let { (it / 100).coerceIn(0.0, 1.0) } ?: return null
        val key = "${row.id}|${row.stage}"
        if (key != measuredKey) {
            measuredKey = key
            measured = fraction.toFloat()
        } else {
            measured = max(measured, fraction.toFloat())
        }
        return measured
    }

    // ---- intents

    override fun start() {
        if (local.value.working || session == null) return
        command { source.run(defaultIds(), SetupJobParams.firstSetup) }
    }

    override fun pause() {
        if (local.value.working) return
        command { source.cancel() }
    }

    override fun resume() {
        if (local.value.working || session == null) return
        // The same components as the job that stopped: the check scripts skip what finished.
        val ids = progress.value.components.map { it.id }.toSet()
        command { source.run(ids.ifEmpty { defaultIds() }, emptyMap()) }
    }

    override fun openAppSettings() {
        val opened = nav.openAppSettings()
        local.update { it.copy(settingsMissing = !opened) }
    }

    override fun back(): Unit = nav.back()

    private fun command(block: suspend () -> Unit) {
        local.update { it.copy(working = true, settingsMissing = false) }
        scope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    private companion object {
        val unpacking = Regex("unpack|extract", RegexOption.IGNORE_CASE)
        val downloading = Regex("download|fetch", RegexOption.IGNORE_CASE)
    }
}
