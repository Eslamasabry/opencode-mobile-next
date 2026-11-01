package io.github.eslamasabry.oc.engine.voice.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.models.AutomaticPack
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.ModelPacks
import io.github.eslamasabry.oc.engine.voice.models.PackBlock
import io.github.eslamasabry.oc.engine.voice.models.PackSupport
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.models.SetupNetworkSource
import io.github.eslamasabry.oc.engine.voice.models.Support
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The first-mic sheet renders only this (plan 10 §7.7). */
public sealed interface VoiceSetupStage {
    /** Created, no mic tap yet: no probe, no network (B-MEDIA-019). */
    public data object Idle : VoiceSetupStage

    public data object Checking : VoiceSetupStage

    /** [attemptId] ties the consent to this sheet; [bytesLeft] counts only what is not downloaded yet. */
    public data class NeedsConsent(val attemptId: Long, val pack: PackId, val network: SetupNetwork, val bytesLeft: Long, val resuming: Boolean) :
        VoiceSetupStage

    public data object AskingNotifications : VoiceSetupStage

    public data class Downloading(val received: Long, val total: Long) : VoiceSetupStage

    public data object Verifying : VoiceSetupStage

    /** The model is on the phone. In the background it waits here: the mic never opens from the background. */
    public data class Ready(val pack: PackId) : VoiceSetupStage

    /** The recording started; [VoiceSetupRun.handOff] gives it to the composer. */
    public data object Listening : VoiceSetupStage

    /** [requiredBytes] for `PackStorageTooSmall` ("free up about …"). */
    public data class Problem(val problem: MediaProblem, val requiredBytes: Long? = null) : VoiceSetupStage
}

/** What the setup needs from the composer and the platform. */
public interface VoiceSetupHost {
    /** Starts listening (`VoiceController.start`). */
    public suspend fun startListening(): Outcome<Unit>

    /** Cancels the recording this setup started (not called after [VoiceSetupRun.handOff]). */
    public suspend fun cancelListening()

    /** Asks POST_NOTIFICATIONS (a denial or an error never blocks setup). */
    public suspend fun askNotifications()

    public val inForeground: Boolean
}

/**
 * First-mic automatic setup (plan 10 §7.7, MEDIA-VOICE-11; Dart `VoiceAutomaticSetup`; B-MEDIA-019…021). Consent is
 * per attempt and never stored. Mobile data or an unknown network needs `allowMetered`; a network change to those
 * during a download that had no such consent pauses it (the `.part` files stay) and asks again. Closing the sheet
 * cancels the download this run started; a download already started elsewhere stays its owner's.
 */
public class VoiceSetupRun internal constructor(
    private val packs: ModelPacks,
    private val network: SetupNetworkSource,
    private val host: VoiceSetupHost,
    private val scope: CoroutineScope,
) {
    private val mutableStage = MutableStateFlow<VoiceSetupStage>(VoiceSetupStage.Idle)
    public val stage: StateFlow<VoiceSetupStage> = mutableStage.asStateFlow()

    private val lane = Mutex()
    private var attempt = 0L
    private var ownDownload: Job? = null
    private var listening = false
    private var handedOff = false
    private var closed = false

    /** The mic tap. Duplicate taps while a step runs are no-ops. */
    public suspend fun start() {
        lane.withLock {
            if (closed) return
            when (mutableStage.value) {
                VoiceSetupStage.Idle, is VoiceSetupStage.Problem, is VoiceSetupStage.Ready -> Unit
                else -> return
            }
            mutableStage.value = VoiceSetupStage.Checking
        }
        check()
    }

    private suspend fun check() {
        val device = packs.device()
        if (device == DeviceProbeResult.Unsupported || (device as? DeviceProbeResult.Known)?.hasMicrophone == false) {
            return set(VoiceSetupStage.Problem(MediaProblem.NoMicrophone))
        }
        val selected = packs.state.value.selected
        val installedIds = packs.speechPacks.filter { packs.installed(it.id) != null }.map { it.id }.toSet()
        val partials = packs.speechPacks.associate { it.id to packs.partialBytes(it.id) }
        val choice =
            AutomaticPack.choose(selected, device, { it in installedIds }, { partials[it] ?: 0L }, packs.speechPacks)
                ?: return set(VoiceSetupStage.Problem(blockedReason(device)))
        if (choice.id != selected) {
            if (packs.select(choice.id) is Outcome.Failed) return set(VoiceSetupStage.Problem(MediaProblem.DownloadBusy))
        }
        if (packs.installed(choice.id) != null) return listen()
        if (packs.downloading != null) return set(VoiceSetupStage.Problem(MediaProblem.DownloadBusy)) // "Show the download"
        val support = PackSupport.supportFor(choice, device, installed = false, partialBytes = packs.partialBytes(choice.id))
        if (support is Support.Blocked && support.block == PackBlock.Storage) {
            return set(VoiceSetupStage.Problem(MediaProblem.PackStorageTooSmall, support.requiredStorageBytes))
        }
        askConsent(choice.id, network.current(), resuming = false)
    }

    private fun blockedReason(device: DeviceProbeResult): MediaProblem {
        val ram = (device as? DeviceProbeResult.Known)?.totalRamMb
        if (ram == null || ram <= 0) return MediaProblem.MemoryUnknown
        val smallest = packs.speechPacks.minBy { it.minTotalRamMb }
        return (PackSupport.supportFor(smallest, device) as? Support.Blocked)?.block?.problem ?: MediaProblem.MemoryUnknown
    }

    private suspend fun askConsent(pack: PackId, now: SetupNetwork, resuming: Boolean) {
        if (now == SetupNetwork.Offline) return set(VoiceSetupStage.Problem(MediaProblem.DownloadOffline))
        val spec = packs.speechPacks.first { it.id == pack }
        val left = packs.partialBytes(pack)
        lane.withLock {
            if (closed) return
            attempt++
            mutableStage.value = VoiceSetupStage.NeedsConsent(attempt, pack, now, spec.totalBytes - left, resuming)
        }
    }

    /**
     * The person agreed for attempt [attemptId]. A consent for an older attempt (a cancelled sheet) authorizes
     * nothing. [allowMetered] is true only when the button said "on mobile data".
     */
    public suspend fun consent(attemptId: Long, allowMetered: Boolean) {
        val asked =
            lane.withLock {
                val current = mutableStage.value as? VoiceSetupStage.NeedsConsent ?: return
                if (closed || current.attemptId != attemptId) return
                mutableStage.value = VoiceSetupStage.AskingNotifications
                current
            }
        if (!networkAllows(network.current(), allowMetered, asked)) return
        try {
            host.askNotifications()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // B-MEDIA-020: a notification denial or error never blocks setup.
        }
        // Re-read after the prompt: the network may have changed while the system dialog was up.
        if (!networkAllows(network.current(), allowMetered, asked)) return
        download(asked.pack, allowMetered)
    }

    private suspend fun networkAllows(now: SetupNetwork, allowMetered: Boolean, asked: VoiceSetupStage.NeedsConsent): Boolean =
        when (now) {
            SetupNetwork.Unmetered -> true
            SetupNetwork.Offline -> false.also { set(VoiceSetupStage.Problem(MediaProblem.DownloadOffline)) }
            SetupNetwork.Metered, SetupNetwork.Unknown ->
                allowMetered || false.also { askConsent(asked.pack, now, asked.resuming) }
        }

    private suspend fun download(pack: PackId, allowMetered: Boolean) {
        set(VoiceSetupStage.Downloading(0, packs.speechPacks.first { it.id == pack }.totalBytes))
        val progress =
            scope.launch {
                packs.state.collect { state ->
                    val job = state.job?.takeIf { it.pack == pack } ?: return@collect
                    when (val phase = job.phase) {
                        is PackJobPhase.Downloading -> set(VoiceSetupStage.Downloading(phase.received, phase.total))
                        PackJobPhase.Verifying -> set(VoiceSetupStage.Verifying)
                    }
                }
            }
        var pausedFor: SetupNetwork? = null
        val watcher =
            scope.launch {
                network.changes.collect { now ->
                    val pause = now == SetupNetwork.Offline || (!allowMetered && (now == SetupNetwork.Metered || now == SetupNetwork.Unknown))
                    if (pause && pausedFor == null && mutableStage.value !is VoiceSetupStage.Verifying) {
                        pausedFor = now
                        packs.cancelDownload() // M3: the .part files stay for the resume
                    }
                }
            }
        val job = scope.launch { packs.download(pack) }
        lane.withLock { ownDownload = job }
        val result =
            try {
                job.join()
                packs.state.value.packs[pack]
            } finally {
                watcher.cancel()
                progress.cancel()
                lane.withLock { ownDownload = null }
            }
        when {
            closed -> return
            pausedFor == SetupNetwork.Offline -> set(VoiceSetupStage.Problem(MediaProblem.DownloadOffline))
            pausedFor != null -> askConsent(pack, pausedFor!!, resuming = true)
            result == PackStatus.Installed -> {
                set(VoiceSetupStage.Ready(pack))
                if (host.inForeground) listen()
            }
            else -> set(VoiceSetupStage.Problem(packs.state.value.lastProblem ?: MediaProblem.DownloadIncomplete))
        }
    }

    /** From Ready ("Start listening") or straight after an installed check. Never from the background. */
    public suspend fun listen() {
        if (closed) return
        if (!host.inForeground) {
            packs.state.value.selected.let { set(VoiceSetupStage.Ready(it)) }
            return
        }
        when (val started = host.startListening()) {
            is Outcome.Ok -> {
                listening = true
                set(VoiceSetupStage.Listening)
            }
            is Outcome.Failed -> {
                val problem = (started.failure as? Failure.Media)?.problem ?: MediaProblem.RecordingInterrupted
                set(VoiceSetupStage.Problem(problem)) // "Try again" re-checks: an installed pack never re-downloads
            }
        }
    }

    /** The recording now belongs to the composer's voice surface: closing the sheet no longer cancels it. */
    public fun handOff() {
        handedOff = true
    }

    /** Closing the sheet: cancels the download this run started (parts stay) and an un-handed-off recording. */
    public suspend fun close() {
        val job =
            lane.withLock {
                if (closed) return
                closed = true
                ownDownload
            }
        if (job != null) packs.cancelDownload()
        if (listening && !handedOff) host.cancelListening()
    }

    private suspend fun set(next: VoiceSetupStage) {
        lane.withLock { if (!closed) mutableStage.value = next }
    }
}

public class VoiceSetup(
    private val packs: ModelPacks,
    private val network: SetupNetworkSource,
    private val scope: CoroutineScope,
) {
    /** Creating a run does no work until [VoiceSetupRun.start] (B-MEDIA-019). */
    public fun begin(host: VoiceSetupHost): VoiceSetupRun = VoiceSetupRun(packs, network, host, scope)
}
