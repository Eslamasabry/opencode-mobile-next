package io.github.eslamasabry.oc.engine.voice.readaloud

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.AudioInterruption
import io.github.eslamasabry.oc.engine.voice.audio.PcmPlayer
import io.github.eslamasabry.oc.engine.voice.dictation.FakeAudioSession
import io.github.eslamasabry.oc.engine.voice.dictation.workScope
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.prefs.InMemoryVoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import io.github.eslamasabry.oc.engine.voice.state.ReadEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class FakeTts : SystemTts {
    val spoken = mutableListOf<Pair<String, String?>>()
    var voiceCalls = 0
    var cancelled = 0
    var answer: (suspend () -> Outcome<Unit>)? = null

    override suspend fun voices(): Outcome<ImmutableList<TtsVoice>> {
        voiceCalls++
        return persistentListOf(TtsVoice("v", "v", "en-US")).ok()
    }

    override suspend fun speak(text: String, voiceId: String?): Outcome<Unit> {
        spoken += text to voiceId
        try {
            return answer?.invoke() ?: awaitCancellation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled++
            throw e
        }
    }
}

private class FakeSynth(var rate: Int = 24_000) : SpeechSynthesizer {
    var opens = 0
    var closes = 0
    val pieces = mutableListOf<String>()
    val speakers = mutableSetOf<Int>()

    override suspend fun open(pack: InstalledPack, threads: Int): Outcome<SynthesisSession> {
        opens++
        return object : SynthesisSession {
            override val sampleRate: Int get() = rate

            override suspend fun synthesize(text: String, speakerId: Int, speed: Float, sink: suspend (FloatArray) -> Unit): Outcome<Unit> {
                pieces += text
                speakers += speakerId
                sink(FloatArray(240))
                return Unit.ok()
            }

            override suspend fun close() {
                closes++
            }
        }.ok()
    }
}

private class FakePlayer : PcmPlayer {
    var frames = 0
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun play(sampleRate: Int, chunks: ReceiveChannel<FloatArray>): Outcome<Unit> {
        for (chunk in chunks) frames += chunk.size
        hold?.await()
        return Unit.ok()
    }
}

class ReadAloudTest {
    private val audio = FakeAudioSession()
    private val tts = FakeTts()
    private val synth = FakeSynth()
    private val player = FakePlayer()
    private val prefs = InMemoryVoicePrefs()
    private var natural: InstalledPack? = null

    private fun TestScope.reader(available: Boolean = true) =
        ReadAloud(audio, tts, synth, player, prefs, workScope(), { natural }, { "en" }, { 8 }, available)

    private val reply = ReadRequest("private-session/voice-reply/m1", "The reply prose.")

    private fun untouched() = tts.spoken.isEmpty() && tts.voiceCalls == 0 && synth.opens == 0 && audio.leases == 0

    @Test
    fun `B-MEDIA-009 construction and disposal without playback never access the engine`() =
        runTest {
            val speech = reader()
            assertEquals(ReadAloudState.Idle, speech.state.value)
            speech.close()
            assertTrue(untouched())
        }

    @Test
    fun `B-MEDIA-009 unsupported platforms cannot query voices or send reply text`() =
        runTest {
            prefs.setReadAloudConsent(true)
            val speech = reader(available = false)
            assertEquals(Failure.Unavailable(FeatureId("readAloud")).failed(), speech.voices())
            assertEquals(Failure.Unavailable(FeatureId("readAloud")).failed(), speech.read(reply))
            assertTrue(untouched())
        }

    @Test
    fun `B-MEDIA-009 S3 nothing reaches the engine before consent, which is asked once per phone`() =
        runTest {
            val speech = reader()
            assertEquals(Failure.Refused(RefusalReason.ConsentMissing).failed(), speech.read(reply))
            assertEquals(Failure.Refused(RefusalReason.ConsentMissing).failed(), speech.voices())
            assertTrue(untouched())
            speech.giveConsent()
            assertTrue(reader().consentGiven())
        }

    @Test
    fun `B-MEDIA-009 backgrounding cancels the active speech and blocks new speech`() =
        runTest {
            prefs.setReadAloudConsent(true)
            val speech = reader()
            val reading = async { speech.read(reply) }
            runCurrent()
            assertIs<ReadAloudState.Speaking>(speech.state.value)
            assertEquals(listOf<Pair<String, String?>>("The reply prose." to null), tts.spoken) // the utterance id never reaches the engine
            speech.onHostChanged(resumed = false)
            advanceUntilIdle()
            assertEquals(ReadAloudState.Idle, speech.state.value)
            assertEquals(1, tts.cancelled)
            assertEquals(Unit.ok(), reading.await())
            assertEquals(Failure.Media(MediaProblem.ReadAloudBusy).failed(), speech.read(reply))
            assertEquals(1, tts.spoken.size)
            assertEquals(1, audio.released)
        }

    @Test
    fun `B-MEDIA-009 disposing pending playback stops it and a late acceptance stays cancelled`() =
        runTest {
            prefs.setReadAloudConsent(true)
            val accepted = CompletableDeferred<Outcome<Unit>>()
            tts.answer = { accepted.await() }
            val speech = reader()
            val reading = async { speech.read(reply) }
            runCurrent()
            speech.close()
            accepted.complete(Unit.ok())
            advanceUntilIdle()
            assertEquals(ReadAloudState.Idle, speech.state.value)
            assertEquals(Unit.ok(), reading.await())
            assertEquals(Failure.Media(MediaProblem.ReadAloudBusy).failed(), speech.read(reply))
        }

    @Test
    fun `B-MEDIA-009 a terminal engine failure during a pending start reaches the caller`() =
        runTest {
            prefs.setReadAloudConsent(true)
            tts.answer = { Failure.Media(MediaProblem.SpeechEngineUnavailable).failed() }
            val speech = reader()
            assertEquals(Failure.Media(MediaProblem.SpeechEngineUnavailable).failed(), speech.read(reply))
            assertEquals(ReadAloudState.Failed(MediaProblem.SpeechEngineUnavailable), speech.state.value)
        }

    @Test
    fun `B-MEDIA-009 a busy player, another recording and too much text are plain refusals`() =
        runTest {
            prefs.setReadAloudConsent(true)
            val speech = reader()
            assertEquals(Failure.Media(MediaProblem.ReadAloudTooLong).failed(), speech.read(reply.copy(text = "  ")))
            assertEquals(Failure.Media(MediaProblem.ReadAloudTooLong).failed(), speech.read(reply.copy(text = "a".repeat(12_001))))
            audio.playbackBusy = true
            assertEquals(Failure.Media(MediaProblem.ReadAloudBusy).failed(), speech.read(reply))
            audio.playbackBusy = false
            val reading = async { speech.read(reply) }
            runCurrent()
            audio.events.emit(AudioInterruption.OtherRecordingStarted)
            assertEquals(Failure.Media(MediaProblem.ReadAloudBusy).failed(), reading.await())
            val unplugged = async { speech.read(reply) }
            runCurrent()
            audio.events.emit(AudioInterruption.OutputDeviceRemoved)
            assertEquals(Unit.ok(), unplugged.await())
            assertEquals(ReadAloudState.Idle, speech.state.value)
        }

    @Test
    fun `plan 10 8_6 one reader app-wide - a new reading replaces the old one`() =
        runTest {
            prefs.setReadAloudConsent(true)
            val speech = reader()
            val first = async { speech.read(reply) }
            runCurrent()
            val second = async { speech.read(reply.copy(utteranceId = "s/2", text = "Second.")) }
            runCurrent()
            assertEquals(Unit.ok(), first.await())
            assertEquals(1, tts.cancelled)
            assertEquals(ReadAloudState.Speaking("s/2", ReadEngine.System), speech.state.value)
            speech.stop()
            assertEquals(Unit.ok(), second.await())
        }

    @Test
    fun `B-MEDIA-005 the natural voice reads English in sentences with speaker 3 at 24 kHz and stops at once`() =
        runTest {
            prefs.setReadAloudConsent(true)
            prefs.setReadAloudNatural(true)
            natural = InstalledPack(ModelCatalog.kokoroEn, "/data/voice_models/kokoro")
            val speech = reader()
            assertEquals(Unit.ok(), speech.read(reply.copy(text = "One. Two! Three?")))
            assertEquals(listOf("One.", "Two!", "Three?"), synth.pieces)
            assertEquals(setOf(3), synth.speakers)
            assertEquals(720, player.frames)
            assertTrue(tts.spoken.isEmpty())

            player.hold = CompletableDeferred()
            val reading = async { speech.read(reply) }
            runCurrent()
            assertEquals(ReadAloudState.Speaking(reply.utteranceId, ReadEngine.Natural), speech.state.value)
            speech.stop()
            assertEquals(Unit.ok(), reading.await())
            assertEquals(ReadAloudState.Idle, speech.state.value)
            assertEquals(1, synth.opens) // the synthesis session stays warm between readings
            advanceTimeBy(21.seconds)
            assertEquals(1, synth.closes)
        }

    @Test
    fun `B-MEDIA-005 a reply the natural voice cannot read falls back to the phone's voice with a note once per language`() =
        runTest {
            prefs.setReadAloudConsent(true)
            prefs.setReadAloudNatural(true)
            natural = InstalledPack(ModelCatalog.kokoroEn, "/data/voice_models/kokoro")
            tts.answer = { Unit.ok() }
            val speech = reader()
            val states = mutableListOf<ReadAloudState>()
            val watch = workScope().async { speech.state.collect { states += it } }
            runCurrent()
            speech.read(reply.copy(text = "مرحبا بك"))
            runCurrent()
            speech.read(reply.copy(text = "مرحبا مرة أخرى"))
            runCurrent()
            val speaking = states.filterIsInstance<ReadAloudState.Speaking>()
            assertEquals(listOf("ar", null), speaking.map { it.fallbackLanguage })
            assertTrue(speaking.all { it.engine == ReadEngine.System })
            assertEquals(0, synth.opens)
            watch.cancel()
        }

    @Test
    fun `B-MEDIA-005 a natural voice model with the wrong sample rate never plays`() =
        runTest {
            prefs.setReadAloudConsent(true)
            prefs.setReadAloudNatural(true)
            natural = InstalledPack(ModelCatalog.kokoroEn, "/data/voice_models/kokoro")
            synth.rate = 22_050
            val speech = reader()
            assertEquals(Failure.Media(MediaProblem.SpeechEngineUnavailable).failed(), speech.read(reply))
            assertEquals(0, player.frames)
        }
}
