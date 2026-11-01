package io.github.eslamasabry.oc.android.voice

import io.github.eslamasabry.oc.android.voice.speech.KokoroSink
import io.github.eslamasabry.oc.android.voice.speech.KokoroTtsConfig
import io.github.eslamasabry.oc.android.voice.speech.NativeRecognizer
import io.github.eslamasabry.oc.android.voice.speech.NativeTts
import io.github.eslamasabry.oc.android.voice.speech.SherpaConfigs
import io.github.eslamasabry.oc.android.voice.speech.SherpaRuntime
import io.github.eslamasabry.oc.android.voice.speech.SherpaSpeechRecognizer
import io.github.eslamasabry.oc.android.voice.speech.SherpaSpeechSynthesizer
import io.github.eslamasabry.oc.android.voice.speech.WhisperRecognizerConfig
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.language.recognizerCode
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.models.conservativeThreadCount
import io.github.eslamasabry.oc.engine.voice.readaloud.SynthesisSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SherpaSpeechTest {
    private val base = InstalledPack(ModelCatalog.base, "/data/user/0/app/files/voice_models/base-rev")
    private val kokoro = InstalledPack(ModelCatalog.kokoroEn, "/data/user/0/app/files/voice_models/kokoro-rev")

    @Test
    fun `B-MEDIA-033 the recognizer config maps local paths, language, task and CPU policy`() {
        val config = SherpaConfigs.recognizer(base, VoiceLanguage.Arabic.recognizerCode("en"), conservativeThreadCount(8))
        assertEquals(
            WhisperRecognizerConfig(
                encoder = "/data/user/0/app/files/voice_models/base-rev/base-encoder.int8.onnx",
                decoder = "/data/user/0/app/files/voice_models/base-rev/base-decoder.int8.onnx",
                tokens = "/data/user/0/app/files/voice_models/base-rev/base-tokens.txt",
                language = "ar",
                task = "transcribe",
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "whisper",
            ),
            config,
        )
        assertEquals(1, SherpaConfigs.recognizer(base, "", conservativeThreadCount(2)).numThreads)
    }

    @Test
    fun `B-MEDIA-033 automatic language maps to automatic detection`() {
        assertEquals("", SherpaConfigs.recognizer(base, VoiceLanguage.Detect.recognizerCode("ar"), 1).language)
        assertEquals("", SherpaConfigs.recognizer(base, VoiceLanguage.Auto.recognizerCode("tlh"), 1).language)
    }

    @Test
    fun `B-MEDIA-005 the Kokoro config has the four files, an empty lang and one sentence at a time`() {
        assertEquals(
            KokoroTtsConfig(
                model = "/data/user/0/app/files/voice_models/kokoro-rev/model.int8.onnx",
                voices = "/data/user/0/app/files/voice_models/kokoro-rev/voices.bin",
                tokens = "/data/user/0/app/files/voice_models/kokoro-rev/tokens.txt",
                lexicon = "/data/user/0/app/files/voice_models/kokoro-rev/lexicon-us-en.txt",
                lang = "",
                numThreads = 2,
                provider = "cpu",
                maxNumSentences = 1,
            ),
            SherpaConfigs.kokoro(kokoro, 2),
        )
    }

    private class FakeRuntime : SherpaRuntime {
        var failRecognizer = false
        var decodeGate: CompletableDeferred<Unit>? = null
        var decodes = 0
        var released = 0
        var rate = 24_000
        var callbacks = 0
        var stoppedEarly = false

        override fun newRecognizer(config: WhisperRecognizerConfig): NativeRecognizer {
            if (failRecognizer) throw IllegalArgumentException("encoder missing")
            return object : NativeRecognizer {
                override fun decode(samples: FloatArray, sampleRate: Int): String {
                    decodes++
                    return " words ${samples.size} "
                }

                override fun release() {
                    released++
                }
            }
        }

        override fun newTts(config: KokoroTtsConfig): NativeTts =
            object : NativeTts {
                override val sampleRate: Int get() = rate

                override fun generate(text: String, speakerId: Int, speed: Float, sink: KokoroSink) {
                    repeat(5) {
                        callbacks++
                        if (sink(FloatArray(10)) == 0) {
                            stoppedEarly = true
                            return
                        }
                    }
                }

                override fun release() {
                    released++
                }
            }
    }

    @Test
    fun `plan 10 6_3 a model that cannot load is SpeechModelBroken and decodes run on the speech lane`() =
        runTest {
            val runtime = FakeRuntime()
            val lane = StandardTestDispatcher(testScheduler)
            val recognizer = SherpaSpeechRecognizer(runtime, lane, backgroundScope)
            runtime.failRecognizer = true
            assertEquals(MediaProblem.SpeechModelBroken, (recognizer.open(base, "en", 1).failureOrNull() as Failure.Media).problem)
            runtime.failRecognizer = false
            val session = assertIs<Outcome.Ok<*>>(recognizer.open(base, "en", 1)).value as io.github.eslamasabry.oc.engine.voice.dictation.RecognizerSession
            assertEquals("words 160".ok(), session.transcribe(FloatArray(160)))
            session.close()
            assertEquals(1, runtime.released)
        }

    @Test
    fun `N1 the Kokoro sink returns 0 once the reading is cancelled, so synthesis stops`() {
        val job = Job()
        val channel = Channel<FloatArray>(Channel.UNLIMITED)
        val sink = KokoroSink(job, channel)
        assertEquals(1, sink(FloatArray(4)))
        job.cancel()
        assertEquals(0, sink(FloatArray(4)))
        val open = Job()
        channel.close()
        assertEquals(0, KokoroSink(open, channel)(FloatArray(4)))
    }

    @Test
    fun `N1 Kokoro streams every callback to the player and a wrong sample rate never opens`() =
        runTest {
            val runtime = FakeRuntime()
            val synth = SherpaSpeechSynthesizer(runtime, StandardTestDispatcher(testScheduler))
            val session = assertIs<Outcome.Ok<SynthesisSession>>(synth.open(kokoro, 2)).value
            var heard = 0
            assertEquals(Unit.ok(), session.synthesize("One.", 3, 1f) { heard += it.size })
            assertEquals(50, heard)
            assertTrue(!runtime.stoppedEarly)

            runtime.rate = 22_050
            assertEquals(MediaProblem.SpeechEngineUnavailable, (synth.open(kokoro, 2).failureOrNull() as Failure.Media).problem)
        }

    @Test
    fun `N1 stopping a Kokoro reading stops synthesis at the next callback`() =
        runBlocking {
            val runtime =
                object : SherpaRuntime {
                    @Volatile var callbacks = 0

                    @Volatile var stoppedEarly = false

                    override fun newRecognizer(config: WhisperRecognizerConfig) = error("unused")

                    override fun newTts(config: KokoroTtsConfig): NativeTts =
                        object : NativeTts {
                            override val sampleRate = 24_000

                            override fun generate(text: String, speakerId: Int, speed: Float, sink: KokoroSink) {
                                repeat(200) {
                                    Thread.sleep(5)
                                    callbacks++
                                    if (sink(FloatArray(10)) == 0) {
                                        stoppedEarly = true
                                        return
                                    }
                                }
                            }

                            override fun release() = Unit
                        }
                }
            val lane = Dispatchers.IO.limitedParallelism(1)
            val synth = SherpaSpeechSynthesizer(runtime, lane)
            val session = assertIs<Outcome.Ok<SynthesisSession>>(synth.open(kokoro, 2)).value
            val firstSound = CompletableDeferred<Unit>()
            val reading = async(Dispatchers.Default) { session.synthesize("One.", 3, 1f) { firstSound.complete(Unit) } }
            firstSound.await()
            reading.cancelAndJoin()
            withContext(lane) {} // the lane is free again: the native call returned
            assertTrue(runtime.stoppedEarly)
            assertTrue(runtime.callbacks < 200)
        }
}
