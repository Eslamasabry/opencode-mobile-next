package io.github.eslamasabry.oc.engine.voice.audio

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** Plan 10 §4.5 / §5. Android: `AndroidAudioSession`, the only class that touches audio focus and the callbacks. */
public enum class AudioInterruption { FocusLost, OutputDeviceRemoved, OtherRecordingStarted, AppLeftForeground }

public interface AudioSession {
    /**
     * Before any microphone start: stops reading aloud, arms the 10 s capture guard and requests capture focus
     * (rules A1, A8). false = playback would not stop (→ MicBusy).
     */
    public suspend fun beforeCapture(): Boolean

    /** After stop, finish, cancel or failure of a recording: abandons the capture focus (A8). */
    public suspend fun endCapture()

    /** Playback focus (GAIN_TRANSIENT). `Failed(ReadAloudBusy)` while the guard or any recording is active (A2). */
    public suspend fun acquirePlayback(): Outcome<PlaybackLease>

    public val interruptions: SharedFlow<AudioInterruption>
}

public fun interface PlaybackLease {
    public suspend fun release()
}

public sealed interface CaptureEvent {
    /** 4096 bytes = 128 ms at 16 kHz mono. */
    public class Frame(public val pcm16le: ByteArray) : CaptureEvent

    /** `AudioRecordingConfiguration.isClientSilenced` (A7). */
    public data object Silenced : CaptureEvent

    /** Capture focus lost (A8) or `read()` < 0. */
    public data object Interrupted : CaptureEvent
}

public interface AudioCapture {
    /** Cold: collecting starts the microphone (16 kHz, mono, PCM16); cancelling stops and releases it. */
    public fun frames(): Flow<CaptureEvent>
}

public interface PcmPlayer {
    /** Plays float mono PCM from [chunks] until it is closed and every frame has played. Cancel = pause + flush. */
    public suspend fun play(sampleRate: Int, chunks: ReceiveChannel<FloatArray>): Outcome<Unit>
}
