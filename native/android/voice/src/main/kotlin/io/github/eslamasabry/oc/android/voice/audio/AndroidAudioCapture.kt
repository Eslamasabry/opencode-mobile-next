package io.github.eslamasabry.oc.android.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.audio.AudioCapture
import io.github.eslamasabry.oc.engine.voice.audio.CaptureEvent
import io.github.eslamasabry.oc.engine.voice.audio.VOICE_SAMPLE_RATE
import io.github.eslamasabry.oc.engine.voice.dictation.CaptureFailed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 16 kHz mono PCM16 from the `MIC` source (plan 10 §6.2; parity with `record_android`: the 0.015 silence level was
 * tuned on MIC levels; no Bluetooth SCO). `RECORD_AUDIO` is granted before this flow is collected (VoiceController asks
 * first). Frames are 4096 bytes (128 ms) and are never dropped. [micLane] is a single-thread lane on the IO pool.
 */
public class AndroidAudioCapture(
    private val session: AndroidAudioSession,
    private val micLane: CoroutineDispatcher,
) : AudioCapture {
    @SuppressLint("MissingPermission") // VoiceController requests RECORD_AUDIO before collecting
    override fun frames(): Flow<CaptureEvent> =
        callbackFlow {
            val minBuffer =
                AudioRecord.getMinBufferSize(VOICE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record =
                try {
                    AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(VOICE_SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build(),
                        )
                        .setBufferSizeInBytes(maxOf(minBuffer, 16_384))
                        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setPrivacySensitive(true) }
                        .build()
                } catch (_: SecurityException) {
                    throw CaptureFailed(MediaProblem.MicDenied)
                } catch (_: UnsupportedOperationException) {
                    throw CaptureFailed(MediaProblem.NoMicrophone)
                } catch (_: IllegalArgumentException) {
                    throw CaptureFailed(MediaProblem.MicBusy)
                }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw CaptureFailed(MediaProblem.MicBusy)
            }
            val silenced = session.watchOwnRecording(record.audioSessionId) { trySend(CaptureEvent.Silenced) } // A7, before start
            val focus = session.watchCaptureFocus { trySend(CaptureEvent.Interrupted) } // A8
            try {
                record.startRecording()
            } catch (_: SecurityException) {
                silenced.close()
                focus.close()
                record.release()
                throw CaptureFailed(MediaProblem.MicDenied)
            } catch (_: IllegalStateException) {
                silenced.close()
                focus.close()
                record.release()
                throw CaptureFailed(MediaProblem.MicBusy)
            }
            val reader =
                launch(micLane) {
                    val buffer = ByteArray(FRAME_BYTES)
                    while (isActive) {
                        val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (n < 0) {
                            trySend(CaptureEvent.Interrupted)
                            break
                        }
                        if (n > 0) send(CaptureEvent.Frame(buffer.copyOf(n))) // send, not trySend: never drop audio
                    }
                }
            awaitClose {
                reader.cancel()
                silenced.close()
                focus.close()
                try {
                    record.stop() // also unblocks a blocking read
                } catch (_: IllegalStateException) {
                    // never started
                }
                record.release()
            }
        }

    public companion object {
        public const val FRAME_BYTES: Int = 4096
    }
}
