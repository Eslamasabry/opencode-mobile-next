package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.AudioCapture
import io.github.eslamasabry.oc.engine.voice.audio.AudioSession
import io.github.eslamasabry.oc.engine.voice.audio.CaptureEvent
import io.github.eslamasabry.oc.engine.voice.audio.PCM16_BYTES_PER_MS
import io.github.eslamasabry.oc.engine.voice.audio.Pcm16Chunker
import io.github.eslamasabry.oc.engine.voice.audio.SilenceDetector
import io.github.eslamasabry.oc.engine.voice.language.recognizerCode
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbe
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.conservativeThreadCount
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** `VoiceController.start` arguments (plan 10 §4.6). */
public data class VoiceStart(val mode: VoiceMode, val stopOnSilence: Boolean)

/**
 * Voice typing (plan 10 §6.4, MEDIA-VOICE-01…07; ported from Dart `VoiceComposerController`).
 *
 * - The Whisper session is opened once when listening starts and kept warm for [warmKeep] after the recording ends
 *   (M2), instead of reloading the model for every 30 s chunk. On phones under 6 GB total RAM (or unknown), or when
 *   `memoryNow` is low, it is closed after each chunk and reopened for the next (plan 10 §6.3 "Memory check").
 * - Capture starts at once; frames buffer in the chunker while the model loads, so the first words are never lost.
 * - Chunks are transcribed strictly in order; the words are every chunk's text joined with spaces.
 * - A failed chunk stops recording and keeps the words already written (B-MEDIA-010). An interruption drops the
 *   untranscribed audio and keeps the words already written (B-MEDIA-023). A cancelled recording's late results never
 *   become words, because its jobs are cancelled (the Dart generation counter becomes structured concurrency).
 * - The microphone never opens unless the host is in the foreground (plan 10 §17 S13).
 */
public class VoiceController(
    private val audio: AudioSession,
    private val capture: AudioCapture,
    private val recognizer: SpeechRecognizer,
    private val packs: SpeechPackSource,
    private val permission: MicPermission,
    private val probe: DeviceProbe,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val scope: CoroutineScope,
    private val appLanguage: () -> String?,
    /** Stops any reading aloud first (plan 10 §6.4 step 3). */
    private val stopReading: suspend () -> Unit = {},
    /** `PlatformCapabilities.voice`. */
    private val voiceAvailable: Boolean = true,
    private val warmKeep: Duration = 20.seconds,
) {
    private val log = AppLog.tagged("voice")
    private val mutableState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    public val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    private val mutableLevel = MutableStateFlow(0f)

    /** 0..1 RMS of the newest buffer, at most every 33 ms; read by the level meter in the draw phase. */
    public val level: StateFlow<Float> = mutableLevel.asStateFlow()

    private val lane = Mutex()
    private val decodeLock = Mutex()
    private var host = VoiceHost.Foreground
    private var current: Recording? = null
    private var warm: Warm? = null
    private var warmCloser: Job? = null

    /** How many recognizer sessions were opened (diagnostics and the warm-session tests). */
    public var sessionsOpened: Int = 0
        private set

    private class Warm(val pack: InstalledPack, val language: String, val session: RecognizerSession)

    private inner class Recording(val start: VoiceStart, val pack: InstalledPack, val language: String, val since: Instant) {
        val chunker = Pcm16Chunker()
        val silence = SilenceDetector()
        val queue = Channel<FloatArray>(Channel.UNLIMITED)
        val words = mutableListOf<String>()
        var chunksDone = 0
        var bytesSeen = 0L
        var listening = false
        var stopping = false
        var over = false
        var lastLevelAt: Instant? = null
        var silencedTimer: Job? = null
        lateinit var captureJob: Job
        lateinit var transcribeJob: Job

        val text: String get() = words.filter { it.isNotBlank() }.joinToString(" ")
    }

    public suspend fun start(request: VoiceStart): Outcome<Unit> {
        if (!voiceAvailable) return Failure.Unavailable(FeatureId("voice")).failed()
        return lane.withLock {
            when (mutableState.value) {
                VoiceState.Idle, VoiceState.NeedsModel, is VoiceState.Done, is VoiceState.Failed -> Unit
                else -> return@withLock Unit.ok() // duplicate taps are no-ops
            }
            stopReading()
            if (!audio.beforeCapture()) return@withLock failStart(MediaProblem.MicBusy)
            val pack = packs.readySpeechPack()
            if (pack == null) {
                audio.endCapture()
                mutableState.value = VoiceState.NeedsModel
                return@withLock Failure.Media(MediaProblem.SpeechModelMissing).failed()
            }
            when (permission.request()) {
                MicPermissionStatus.Granted -> Unit
                MicPermissionStatus.Denied -> return@withLock failStart(MediaProblem.MicDenied)
                MicPermissionStatus.PermanentlyDenied -> return@withLock failStart(MediaProblem.MicDeniedForever)
            }
            if (!audio.beforeCapture()) return@withLock failStart(MediaProblem.MicBusy) // "first and again on grant"
            if (host != VoiceHost.Foreground) return@withLock failStart(MediaProblem.LeftTheApp)
            warmCloser?.cancel()
            val recording = Recording(request, pack, packs.language.recognizerCode(appLanguage()), clock.now())
            current = recording
            mutableState.value = VoiceState.Starting
            mutableLevel.value = 0f
            recording.transcribeJob = scope.launch { transcribeLoop(recording) }
            recording.captureJob = scope.launch { captureLoop(recording) }
            Unit.ok()
        }
    }

    private suspend fun failStart(problem: MediaProblem): Outcome<Nothing> {
        audio.endCapture()
        mutableState.value = VoiceState.Failed(problem, "")
        return Failure.Media(problem).failed()
    }

    /**
     * Stops the microphone, writes the rest (the chunk in progress included) and returns every word. Used by Done/Send,
     * silence, leaving the app and a covering page. `Done("")` when nothing was heard; `NoAudioCaptured` when no audio
     * arrived at all.
     */
    public suspend fun finish(endedBy: VoiceEnd = VoiceEnd.Person): Outcome<String> {
        val recording =
            lane.withLock {
                val recording = current ?: return finishedResult()
                if (recording.stopping || recording.over) return@withLock null
                recording.stopping = true
                recording.silencedTimer?.cancel()
                recording.captureJob.cancel()
                withTimeoutOrNull(2.seconds) { recording.captureJob.join() }
                if (recording.chunker.length > 0) recording.queue.trySend(recording.chunker.takeSamples())
                recording.queue.close()
                mutableState.value = VoiceState.Writing(recording.text)
                recording
            } ?: return finishedResult()
        recording.transcribeJob.join()
        return lane.withLock {
            if (current !== recording || recording.over) return@withLock finishedResult()
            val result: Outcome<String> =
                if (recording.bytesSeen == 0L) {
                    mutableState.value = VoiceState.Failed(MediaProblem.NoAudioCaptured, "")
                    Failure.Media(MediaProblem.NoAudioCaptured).failed()
                } else {
                    mutableState.value = VoiceState.Done(recording.text, endedBy)
                    recording.text.ok()
                }
            endRecording(recording)
            result
        }
    }

    private fun finishedResult(): Outcome<String> =
        when (val now = mutableState.value) {
            is VoiceState.Done -> now.words.ok()
            is VoiceState.Failed -> Failure.Media(now.problem).failed()
            else -> "".ok()
        }

    /**
     * Leave/Esc/a stop while Starting keep the words written so far ([keepWords]); Cancel, a slip and drag-to-cancel
     * discard them. The chunk in progress is always dropped, so this is never used for a lifecycle event.
     */
    public suspend fun cancel(keepWords: Boolean) {
        val recording =
            lane.withLock {
                val recording = current ?: return
                if (recording.over) return
                recording.over = true
                recording.silencedTimer?.cancel()
                recording.captureJob.cancel()
                recording.transcribeJob.cancel()
                recording.queue.close()
                mutableState.value = VoiceState.Cancelling
                recording
            }
        recording.captureJob.join()
        recording.transcribeJob.join() // a decode already inside the recognizer finishes; its words are discarded
        lane.withLock {
            if (current !== recording) return
            val text = recording.text
            mutableState.value = if (keepWords && text.isNotEmpty()) VoiceState.Done(text, VoiceEnd.Person) else VoiceState.Idle
            endRecording(recording)
        }
    }

    /** From the chat screen (plan 10 §6.5). */
    public fun onHostChanged(next: VoiceHost) {
        host = if (next == VoiceHost.Inactive || next == VoiceHost.ScopeChanged) host else next
        val end =
            when (next) {
                VoiceHost.Background -> VoiceEnd.LeftApp
                VoiceHost.PageCovered -> VoiceEnd.PageCovered
                VoiceHost.ScopeChanged -> VoiceEnd.ScopeChanged
                VoiceHost.Foreground, VoiceHost.Inactive -> return
            }
        if (next == VoiceHost.PageCovered) host = VoiceHost.PageCovered
        scope.launch {
            when (mutableState.value) {
                VoiceState.Starting -> cancel(keepWords = true)
                is VoiceState.Listening -> finish(end)
                else -> Unit
            }
        }
    }

    // ---- recording internals

    private suspend fun captureLoop(recording: Recording) {
        try {
            capture.frames().collect { event ->
                when (event) {
                    is CaptureEvent.Frame -> onFrame(recording, event.pcm16le)
                    CaptureEvent.Silenced ->
                        if (recording.silencedTimer == null) {
                            recording.silencedTimer =
                                scope.launch {
                                    delay(2.seconds)
                                    interrupt(recording, MediaProblem.RecordingInterrupted)
                                }
                        }
                    CaptureEvent.Interrupted -> interrupt(recording, MediaProblem.RecordingInterrupted)
                }
            }
            if (!recording.stopping) interrupt(recording, MediaProblem.RecordingInterrupted) // the stream ended by itself
        } catch (e: CancellationException) {
            throw e
        } catch (e: CaptureFailed) {
            interrupt(recording, e.problem)
        } catch (e: Exception) {
            log.w(e) { "capture failed" }
            interrupt(recording, MediaProblem.RecordingInterrupted)
        }
    }

    private suspend fun onFrame(recording: Recording, bytes: ByteArray) {
        if (recording.stopping || recording.over || bytes.isEmpty()) return
        recording.bytesSeen += bytes.size
        var at = 0
        var level = 0f
        while (at < bytes.size) {
            val took = recording.chunker.add(bytes, at)
            at += took
            level = recording.chunker.level
            if (recording.chunker.isFull) recording.queue.trySend(recording.chunker.takeSamples())
            if (took == 0 && !recording.chunker.isFull) break
        }
        val now = clock.now()
        val last = recording.lastLevelAt
        if (last == null || now - last >= 33.milliseconds) {
            recording.lastLevelAt = now
            mutableLevel.value = level
        }
        if (recording.start.stopOnSilence) {
            val span = (bytes.size / PCM16_BYTES_PER_MS).milliseconds
            if (recording.silence.add(level, span)) scope.launch { finish(VoiceEnd.Silence) }
        }
    }

    private fun interrupt(recording: Recording, problem: MediaProblem) {
        scope.launch {
            lane.withLock {
                if (current !== recording || recording.over) return@launch
                recording.over = true
                recording.silencedTimer?.cancel()
                recording.captureJob.cancel()
                recording.transcribeJob.cancel() // the untranscribed audio is dropped
                recording.queue.close()
                mutableState.value = VoiceState.Failed(problem, recording.text)
            }
            recording.transcribeJob.join()
            lane.withLock { if (current === recording) endRecording(recording) }
        }
    }

    private suspend fun transcribeLoop(recording: Recording) {
        if (openSession(recording) == null) return
        recording.listening = true
        publishProgress(recording)
        for (samples in recording.queue) {
            val session = openSession(recording) ?: return
            val result = decodeLock.withLock { session.transcribe(samples) }
            when (result) {
                is Outcome.Failed -> {
                    chunkFailed(recording)
                    return
                }
                is Outcome.Ok -> {
                    recording.words += result.value.trim()
                    recording.chunksDone++
                    publishProgress(recording)
                }
            }
            if (closeBetweenChunks()) closeWarm()
        }
    }

    private fun publishProgress(recording: Recording) {
        if (current !== recording || recording.over) return
        mutableState.value =
            if (recording.stopping) {
                VoiceState.Writing(recording.text)
            } else {
                VoiceState.Listening(recording.since, recording.start.mode, recording.chunksDone, recording.text)
            }
    }

    private suspend fun chunkFailed(recording: Recording) {
        lane.withLock {
            if (current !== recording || recording.over) return
            recording.over = true
            recording.captureJob.cancel()
            recording.queue.close()
            mutableState.value = VoiceState.Failed(MediaProblem.ChunkNotTranscribed, recording.text)
            endRecording(recording)
        }
    }

    /** The warm session for this recording's pack and language, opened when missing. null = failed (state set). */
    private suspend fun openSession(recording: Recording): RecognizerSession? {
        warm?.let { if (it.pack == recording.pack && it.language == recording.language) return it.session }
        closeWarm()
        val threads = conservativeThreadCount((packs.device() as? DeviceProbeResult.Known)?.cpuCount ?: 1)
        return when (val opened = recognizer.open(recording.pack, recording.language, threads)) {
            is Outcome.Ok -> {
                sessionsOpened++
                warm = Warm(recording.pack, recording.language, opened.value)
                opened.value
            }
            is Outcome.Failed -> {
                // A load failure re-checks the whole pack; a mismatch makes it Missing (plan 10 §6.3 "Load failure").
                withContext(NonCancellable) { packs.verifyFully(recording.pack.spec.id) }
                lane.withLock {
                    if (current === recording && !recording.over) {
                        recording.over = true
                        recording.captureJob.cancel()
                        recording.queue.close()
                        mutableState.value = VoiceState.Failed(MediaProblem.SpeechModelBroken, recording.text)
                        endRecording(recording)
                    }
                }
                null
            }
        }
    }

    /** Phones under 6 GB total RAM (or unknown) and low memory close the session between chunks (plan 10 §6.3). */
    private suspend fun closeBetweenChunks(): Boolean {
        val total = (packs.device() as? DeviceProbeResult.Known)?.totalRamMb
        if (total == null || total < WARM_MIN_TOTAL_RAM_MB) return true
        val now = probe.memoryNow() ?: return false
        return now.low || now.availMb < now.thresholdMb + 256
    }

    private suspend fun closeWarm() {
        val session = warm?.session ?: return
        warm = null
        decodeLock.withLock { withContext(NonCancellable) { session.close() } }
    }

    /** Under [lane]. */
    private suspend fun endRecording(recording: Recording) {
        recording.over = true
        current = null
        mutableLevel.value = 0f
        withContext(NonCancellable) { audio.endCapture() }
        warmCloser?.cancel()
        warmCloser =
            scope.launch {
                delay(warmKeep)
                closeWarm()
            }
    }

    /** Pack change or removal: the warm session goes now. */
    public suspend fun releaseModel() {
        warmCloser?.cancel()
        closeWarm()
    }

    public companion object {
        /** Below this total RAM the session is closed between chunks until P-M2 reports (plan 10 §6.3). */
        public const val WARM_MIN_TOTAL_RAM_MB: Long = 6 * 1024
    }
}
