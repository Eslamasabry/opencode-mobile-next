package io.github.eslamasabry.oc.android.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.PcmPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Natural-voice playback (plan 10 §8.5): float mono PCM in `MODE_STREAM`, non-blocking writes with a 15 ms back-off
 * when the track is full (cancellable, never stuck in native code), then the playback head is polled every 100 ms until
 * every written frame has played. Cancel = pause + flush + release at once.
 */
public class AndroidPcmPlayer(private val io: CoroutineDispatcher) : PcmPlayer {
    override suspend fun play(sampleRate: Int, chunks: ReceiveChannel<FloatArray>): Outcome<Unit> =
        withContext(io) {
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track =
                try {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 4))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } catch (_: RuntimeException) {
                    return@withContext Failure.Media(MediaProblem.SpeechEngineUnavailable).failed()
                }
            var finished = false
            try {
                track.play()
                var written = 0L
                for (chunk in chunks) {
                    var offset = 0
                    while (offset < chunk.size) {
                        val n = track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                        if (n < 0) return@withContext Failure.Media(MediaProblem.SpeechEngineUnavailable).failed()
                        if (n == 0) delay(15)
                        offset += n
                        written += n
                    }
                }
                while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < written) delay(100)
                finished = true
                Unit.ok()
            } finally {
                try {
                    if (finished) {
                        track.stop()
                    } else {
                        track.pause()
                        track.flush()
                    }
                } catch (_: IllegalStateException) {
                    // never played
                }
                track.release()
            }
        }
}
