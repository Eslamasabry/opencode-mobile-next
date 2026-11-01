package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.voice.audio.CaptureEvent
import io.github.eslamasabry.oc.engine.voice.audio.VOICE_CHUNK_SAMPLES
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.MemoryNow
import io.github.eslamasabry.oc.engine.voice.models.SchedulerClock
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Not backgroundScope: advanceUntilIdle() skips background work, and the controller's own launches must run. */
internal fun TestScope.workScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

class VoiceControllerTest {
    private val audio = FakeAudioSession()
    private val capture = FakeCapture()
    private val recognizer = FakeRecognizer()
    private val source = FakePackSource()
    private val memory = FakeMemoryProbe()
    private var permission = MicPermissionStatus.Granted
    private var stops = 0

    private fun TestScope.controller(available: Boolean = true, app: String? = "en") =
        VoiceController(
            audio, capture, recognizer, source, { permission }, memory, SchedulerClock(testScheduler),
            TestDispatchers(StandardTestDispatcher(testScheduler)), workScope(), { app }, { stops++ }, available,
        )

    private val tap = VoiceStart(VoiceMode.Tap, stopOnSilence = false)

    @Test
    fun `B-MEDIA-010 a 3-minute dictation is transcribed in 30 s chunks in order`() =
        runTest {
            val voice = controller()
            assertEquals(Unit.ok(), voice.start(tap))
            runCurrent()
            capture.speak(180)
            runCurrent()
            val listening = assertIs<VoiceState.Listening>(voice.state.value)
            assertEquals(6, listening.chunksDone)
            assertEquals("w1 w2 w3 w4 w5 w6", listening.words)
            assertEquals("w1 w2 w3 w4 w5 w6".ok(), voice.finish())
            assertEquals(VoiceState.Done("w1 w2 w3 w4 w5 w6", VoiceEnd.Person), voice.state.value)
            assertEquals(List(6) { VOICE_CHUNK_SAMPLES }, recognizer.sampleCounts)
            assertEquals(1, recognizer.opens) // M2: the model stays loaded while talking
            assertEquals(1, capture.stops)
            assertEquals(1, audio.endCalls)
        }

    @Test
    fun `B-MEDIA-010 a chunk that cannot be transcribed stops recording and keeps what was written`() =
        runTest {
            recognizer.result = { i -> if (i == 3) Failure.Media(MediaProblem.ChunkNotTranscribed).failed() else "w${i + 1}".ok() }
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(180)
            runCurrent()
            assertEquals(VoiceState.Failed(MediaProblem.ChunkNotTranscribed, "w1 w2 w3"), voice.state.value)
            assertEquals(1, capture.stops)
            assertEquals(1, audio.endCalls)
            assertEquals(4, recognizer.calls)
        }

    @Test
    fun `B-MEDIA-010 leaving the app 20 s into chunk 2 keeps those 20 s of words`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(50)
            runCurrent()
            voice.onHostChanged(VoiceHost.Background)
            advanceUntilIdle()
            assertEquals(VoiceState.Done("w1 w2", VoiceEnd.LeftApp), voice.state.value)
            assertEquals(listOf(VOICE_CHUNK_SAMPLES, 20 * 16_000), recognizer.sampleCounts)
        }

    @Test
    fun `B-MEDIA-010 Done with no words leaves the box as it was`() =
        runTest {
            recognizer.result = { "  ".ok() }
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(3)
            runCurrent()
            assertEquals("".ok(), voice.finish())
            assertEquals(VoiceState.Done("", VoiceEnd.Person), voice.state.value)
        }

    @Test
    fun `B-MEDIA-010 no audio at all is NoAudioCaptured`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            assertEquals(Failure.Media(MediaProblem.NoAudioCaptured).failed(), voice.finish())
        }

    @Test
    fun `B-MEDIA-023 cancel kills recognition and a stale result never becomes words`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            recognizer.result = { gate.await(); "late".ok() }
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(30)
            runCurrent()
            val cancelling = launch { voice.cancel(keepWords = false) }
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            cancelling.join()
            assertEquals(VoiceState.Idle, voice.state.value)
            assertEquals(1, capture.stops)
            assertEquals(1, audio.endCalls)
        }

    @Test
    fun `B-MEDIA-023 an interruption discards the untranscribed audio and keeps the words written`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(40)
            runCurrent()
            capture.send(CaptureEvent.Interrupted)
            advanceUntilIdle()
            assertEquals(VoiceState.Failed(MediaProblem.RecordingInterrupted, "w1"), voice.state.value)
            assertEquals(1, recognizer.calls) // the last 10 s were never transcribed
            assertEquals(1, audio.endCalls)
        }

    @Test
    fun `B-MEDIA-023 two seconds of being silenced by another app is an interruption`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.send(CaptureEvent.Silenced)
            runCurrent()
            advanceTimeBy(1.9.seconds)
            assertIs<VoiceState.Listening>(voice.state.value)
            advanceTimeBy(0.2.seconds)
            runCurrent()
            assertEquals(VoiceState.Failed(MediaProblem.RecordingInterrupted, ""), voice.state.value)
        }

    @Test
    fun `B-MEDIA-023 startup cancellation returns to idle`() =
        runTest {
            recognizer.openGate = CompletableDeferred()
            val voice = controller()
            voice.start(tap)
            runCurrent()
            assertEquals(VoiceState.Starting, voice.state.value)
            voice.onHostChanged(VoiceHost.Background) // ON_STOP while Starting = cancel keeping words (none)
            advanceUntilIdle()
            assertEquals(VoiceState.Idle, voice.state.value)
            assertEquals(1, audio.endCalls)
        }

    @Test
    fun `B-MEDIA-023 an unexpected stream end or error shows a recovery error`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.end()
            advanceUntilIdle()
            assertEquals(VoiceState.Failed(MediaProblem.RecordingInterrupted, ""), voice.state.value)

            voice.start(tap)
            runCurrent()
            capture.fail(CaptureFailed(MediaProblem.MicBusy))
            advanceUntilIdle()
            assertEquals(VoiceState.Failed(MediaProblem.MicBusy, ""), voice.state.value)
        }

    @Test
    fun `B-MEDIA-023 simultaneous starts are guarded`() =
        runTest {
            val voice = controller()
            val one = async { voice.start(tap) }
            val two = async { voice.start(tap) }
            assertEquals(Unit.ok(), one.await())
            assertEquals(Unit.ok(), two.await())
            runCurrent()
            assertEquals(1, capture.starts)
            assertEquals(Unit.ok(), voice.start(tap)) // a tap while listening is a no-op
            runCurrent()
            assertEquals(1, capture.starts)
        }

    @Test
    fun `B-MEDIA-020 no installed pack asks for the model and never opens the mic`() =
        runTest {
            source.ready = null
            val voice = controller()
            assertEquals(Failure.Media(MediaProblem.SpeechModelMissing).failed(), voice.start(tap))
            assertEquals(VoiceState.NeedsModel, voice.state.value)
            runCurrent()
            assertEquals(0, capture.starts)
        }

    @Test
    fun `B-MEDIA-020 microphone denial, a busy player and the background give plain failures`() =
        runTest {
            val voice = controller()
            permission = MicPermissionStatus.Denied
            assertEquals(Failure.Media(MediaProblem.MicDenied).failed(), voice.start(tap))
            permission = MicPermissionStatus.PermanentlyDenied
            assertEquals(Failure.Media(MediaProblem.MicDeniedForever).failed(), voice.start(tap))
            permission = MicPermissionStatus.Granted
            audio.allowCapture = false
            assertEquals(Failure.Media(MediaProblem.MicBusy).failed(), voice.start(tap))
            audio.allowCapture = true
            voice.onHostChanged(VoiceHost.Background)
            assertEquals(Failure.Media(MediaProblem.LeftTheApp).failed(), voice.start(tap))
            runCurrent()
            assertEquals(0, capture.starts) // S13: the mic never opens in the background
            voice.onHostChanged(VoiceHost.Foreground)
            assertEquals(Unit.ok(), voice.start(tap))
            assertTrue(stops > 0) // reading aloud is stopped before every start
        }

    @Test
    fun `B-MEDIA-032 no voice on this platform is Unavailable`() =
        runTest {
            val voice = controller(available = false)
            assertEquals(Failure.Unavailable(FeatureId("voice")).failed(), voice.start(tap))
            assertEquals(MicPermissionStatus.PermanentlyDenied, MicPermission.Refused.request())
        }

    @Test
    fun `B-MEDIA-017 with the opt-in, 1_5 s of quiet after speech stops the recording and says why`() =
        runTest {
            recognizer.result = { "run it".ok() }
            val voice = controller()
            voice.start(VoiceStart(VoiceMode.Tap, stopOnSilence = true))
            runCurrent()
            capture.speak(1, 0.2f)
            capture.speak(1, 0.001f)
            runCurrent()
            assertIs<VoiceState.Listening>(voice.state.value)
            capture.speak(1, 0.001f)
            advanceUntilIdle()
            assertEquals(VoiceState.Done("run it", VoiceEnd.Silence), voice.state.value)
        }

    @Test
    fun `B-MEDIA-017 without the opt-in a long pause never stops it`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(1, 0.2f)
            capture.speak(25, 0.001f)
            advanceUntilIdle()
            assertIs<VoiceState.Listening>(voice.state.value)
        }

    @Test
    fun `B-MEDIA-028 the recognizer is told the right language and B-MEDIA-033 the CPU policy`() =
        runTest {
            source.language = VoiceLanguage.Auto
            source.cpus = 2
            val voice = controller(app = "ar")
            voice.start(tap)
            runCurrent()
            assertEquals(listOf("ar"), recognizer.languages)
            assertEquals(listOf(1), recognizer.threads)
            voice.cancel(keepWords = false)
            voice.releaseModel()
            source.language = VoiceLanguage.Detect
            voice.start(tap)
            runCurrent()
            assertEquals("", recognizer.languages.last())
        }

    @Test
    fun `B-MEDIA-010 M2 phones under 6 GB or low on memory close the session between chunks`() =
        runTest {
            source.ramMb = 4_000
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(90)
            runCurrent()
            assertEquals(3, recognizer.opens)
            assertEquals(3, recognizer.closes)
            voice.cancel(false)

            source.ramMb = 8_000
            memory.memory = MemoryNow(availMb = 300, thresholdMb = 200, low = false) // under threshold + 256 MiB
            voice.start(tap)
            runCurrent()
            capture.speak(60)
            runCurrent()
            assertEquals(5, recognizer.opens)
        }

    @Test
    fun `B-MEDIA-010 M2 the warm session is reused by the next recording and closed 20 s after the last`() =
        runTest {
            val voice = controller()
            voice.start(tap)
            runCurrent()
            capture.speak(5)
            runCurrent()
            voice.finish()
            advanceTimeBy(10.seconds)
            voice.start(tap)
            runCurrent()
            capture.speak(5)
            runCurrent()
            voice.finish()
            assertEquals(1, recognizer.opens)
            assertEquals(0, recognizer.closes)
            advanceTimeBy(21.seconds)
            assertEquals(1, recognizer.closes)
        }

    @Test
    fun `B-MEDIA-023 a model that fails to load is re-checked in full and reported broken`() =
        runTest {
            recognizer.openFails = true
            val voice = controller()
            voice.start(tap)
            advanceUntilIdle()
            assertEquals(VoiceState.Failed(MediaProblem.SpeechModelBroken, ""), voice.state.value)
            assertEquals(1, source.verifies)
            assertEquals(1, audio.endCalls)
        }
}
