package io.github.eslamasabry.oc.android.voice.speech

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.VOICE_SAMPLE_RATE
import io.github.eslamasabry.oc.engine.voice.dictation.RecognizerSession
import io.github.eslamasabry.oc.engine.voice.dictation.SpeechRecognizer
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.readaloud.SpeechSynthesizer
import io.github.eslamasabry.oc.engine.voice.readaloud.SynthesisSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Whisper through sherpa-onnx (plan 10 §6.3). [speechLane] is ONE thread app-wide (`io.limitedParallelism(1)`): one
 * decode at a time, and a session closes only after any decode in flight. A cancelled caller stops waiting while the
 * decode already inside JNI finishes on the lane in [scope]; its words are discarded.
 */
public class SherpaSpeechRecognizer(
    private val runtime: SherpaRuntime,
    private val speechLane: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : SpeechRecognizer {
    override suspend fun open(pack: InstalledPack, languageCode: String, threads: Int): Outcome<RecognizerSession> =
        withContext(speechLane) {
            attempt({ Failure.Media(MediaProblem.SpeechModelBroken, Redacted.shape(it)) }) {
                runtime.newRecognizer(SherpaConfigs.recognizer(pack, languageCode, threads))
            }
        }.let { opened ->
            when (opened) {
                is Outcome.Failed -> opened
                is Outcome.Ok -> Session(opened.value).ok()
            }
        }

    private inner class Session(private val native: NativeRecognizer) : RecognizerSession {
        override suspend fun transcribe(samples: FloatArray): Outcome<String> {
            val decode =
                scope.async(speechLane) {
                    attempt({ Failure.Media(MediaProblem.ChunkNotTranscribed, Redacted.shape(it)) }) {
                        native.decode(samples, VOICE_SAMPLE_RATE).trim()
                    }
                }
            return decode.await()
        }

        override suspend fun close() {
            withContext(NonCancellable + speechLane) { native.release() }
        }
    }
}

/** Kokoro through sherpa-onnx (plan 10 §8.5): streamed through [KokoroSink]; a cancel stops at the next callback. */
public class SherpaSpeechSynthesizer(
    private val runtime: SherpaRuntime,
    private val speechLane: CoroutineDispatcher,
) : SpeechSynthesizer {
    override suspend fun open(pack: InstalledPack, threads: Int): Outcome<SynthesisSession> {
        val opened =
            withContext(speechLane) {
                attempt({ Failure.Media(MediaProblem.SpeechEngineUnavailable, Redacted.shape(it)) }) {
                    runtime.newTts(SherpaConfigs.kokoro(pack, threads))
                }
            }
        val native =
            when (opened) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        val expected = pack.spec.natural?.sampleRate
        if (expected != null && native.sampleRate != expected) {
            withContext(NonCancellable + speechLane) { native.release() }
            return Failure.Media(MediaProblem.SpeechEngineUnavailable).failed()
        }
        return Session(native).ok()
    }

    private inner class Session(private val native: NativeTts) : SynthesisSession {
        override val sampleRate: Int get() = native.sampleRate

        override suspend fun synthesize(
            text: String,
            speakerId: Int,
            speed: Float,
            sink: suspend (FloatArray) -> Unit,
        ): Outcome<Unit> =
            coroutineScope {
                val chunks = Channel<FloatArray>(capacity = 16)
                val generating =
                    async(speechLane) {
                        try {
                            attempt({ Failure.Media(MediaProblem.SpeechEngineUnavailable, Redacted.shape(it)) }) {
                                native.generate(text, speakerId, speed, KokoroSink(coroutineContext.job, chunks))
                            }
                        } finally {
                            chunks.close()
                        }
                    }
                for (samples in chunks) sink(samples)
                generating.await()
            }

        override suspend fun close() {
            withContext(NonCancellable + speechLane) { native.release() }
        }
    }
}
