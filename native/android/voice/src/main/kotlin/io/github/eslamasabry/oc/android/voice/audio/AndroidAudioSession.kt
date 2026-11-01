package io.github.eslamasabry.oc.android.voice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.AudioInterruption
import io.github.eslamasabry.oc.engine.voice.audio.AudioSession
import io.github.eslamasabry.oc.engine.voice.audio.PlaybackLease
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The ONLY class that calls `requestAudioFocus`, `registerAudioRecordingCallback` and `registerAudioDeviceCallback`
 * (plan 10 §5, single owner). Capture, the system voice and the natural voice all go through it. Port of
 * `ReadAloudBridge.kt` (`beforeMicrophoneCapture`, `micGuardUntil`, the callbacks) plus `record_android`'s capture focus.
 *
 * [stopPlayback] stops any reading (the app wires `ReadAloud.stop`); false means playback would not stop (→ MicBusy).
 */
public class AndroidAudioSession(
    context: Context,
    private val clock: Clock,
    private val stopPlayback: suspend () -> Boolean = { true },
) : AudioSession {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val events = MutableSharedFlow<AudioInterruption>(extraBufferCapacity = 16)
    override val interruptions: SharedFlow<AudioInterruption> = events

    @Volatile private var guardUntil: Instant = Instant.DISTANT_PAST

    private var captureFocus: AudioFocusRequest? = null
    private var playbackFocus: AudioFocusRequest? = null
    private val captureFocusWatchers = CopyOnWriteArrayList<() -> Unit>()
    private val ownSessions = CopyOnWriteArrayList<Int>()
    private val silencedWatchers = CopyOnWriteArrayList<Pair<Int, () -> Unit>>()
    private var recordingCallbackRegistered = false
    private var deviceCallbackRegistered = false

    private val speechAttributes: AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    // ---- A1, A8

    override suspend fun beforeCapture(): Boolean {
        val stopped = stopPlayback()
        guardUntil = clock.now() + CAPTURE_GUARD
        if (captureFocus == null) {
            val request =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(speechAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener({ change -> onCaptureFocusChange(change) }, handler)
                    .build()
            captureFocus = request
            // A delayed grant does not hold back the mic (Flutter recorded at once too).
            audioManager.requestAudioFocus(request)
        }
        return stopped
    }

    override suspend fun endCapture() {
        captureFocus?.let { audioManager.abandonAudioFocusRequest(it) }
        captureFocus = null
    }

    internal fun onCaptureFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                captureFocusWatchers.forEach { it() }
        }
    }

    /** A8: capture is interrupted when the capture focus is lost. */
    public fun watchCaptureFocus(onLost: () -> Unit): Closeable {
        captureFocusWatchers += onLost
        return Closeable { captureFocusWatchers -= onLost }
    }

    /**
     * A7 and A4: marks [audioSessionId] as our own recording (so it never counts as "another app recording") and
     * reports `isClientSilenced` for it. Register BEFORE `startRecording()`.
     */
    public fun watchOwnRecording(audioSessionId: Int, onSilenced: () -> Unit): Closeable {
        ownSessions += audioSessionId
        val watcher = audioSessionId to onSilenced
        silencedWatchers += watcher
        ensureRecordingCallback()
        return Closeable {
            ownSessions.remove(audioSessionId)
            silencedWatchers -= watcher
        }
    }

    // ---- A2, A3, A4, A5

    override suspend fun acquirePlayback(): Outcome<PlaybackLease> {
        if (clock.now() < guardUntil || recordingActive()) return busy()
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(speechAttributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener({ change -> onPlaybackFocusChange(change) }, handler)
                .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return busy()
        playbackFocus = request
        ensureRecordingCallback()
        if (!deviceCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler)
            deviceCallbackRegistered = true
        }
        return PlaybackLease {
            if (playbackFocus === request) {
                audioManager.abandonAudioFocusRequest(request)
                playbackFocus = null
                if (deviceCallbackRegistered) {
                    audioManager.unregisterAudioDeviceCallback(deviceCallback)
                    deviceCallbackRegistered = false
                }
            }
        }.ok()
    }

    /** A3: anything but a gain stops the reader (it never ducks into mush). */
    internal fun onPlaybackFocusChange(change: Int) {
        if (change != AudioManager.AUDIOFOCUS_GAIN && playbackFocus != null) events.tryEmit(AudioInterruption.FocusLost)
    }

    /** A6: the host activity's ON_PAUSE. Capture uses ON_STOP instead (through VoiceController). */
    public fun onHostPaused() {
        events.tryEmit(AudioInterruption.AppLeftForeground)
    }

    /** A2: any active recording configuration counts; on any exception, assume busy. */
    private fun recordingActive(): Boolean =
        try {
            audioManager.activeRecordingConfigurations.isNotEmpty()
        } catch (_: RuntimeException) {
            true
        }

    private fun busy(): Outcome<Nothing> = Failure.Media(MediaProblem.ReadAloudBusy).failed()

    private fun ensureRecordingCallback() {
        if (recordingCallbackRegistered) return
        audioManager.registerAudioRecordingCallback(recordingCallback, handler)
        recordingCallbackRegistered = true
    }

    internal fun onRecordingConfigs(configs: List<AudioRecordingConfiguration>) {
        val ours = ownSessions.toSet()
        if (playbackFocus != null && configs.any { it.clientAudioSessionId !in ours }) {
            events.tryEmit(AudioInterruption.OtherRecordingStarted) // A4
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (config in configs) {
                if (config.isClientSilenced) silencedWatchers.filter { it.first == config.clientAudioSessionId }.forEach { it.second() }
            }
        }
    }

    internal fun onDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
        if (playbackFocus != null && removed.any { it.isSink }) events.tryEmit(AudioInterruption.OutputDeviceRemoved) // A5
    }

    private val recordingCallback =
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) = onRecordingConfigs(configs)
        }

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesRemoved(removedDevices)
        }

    internal val captureFocusRequest: AudioFocusRequest? get() = captureFocus

    internal val playbackFocusRequest: AudioFocusRequest? get() = playbackFocus

    public companion object {
        /** A1: playback is refused for 10 s after any microphone start. */
        public val CAPTURE_GUARD: kotlin.time.Duration = 10.seconds
    }
}
