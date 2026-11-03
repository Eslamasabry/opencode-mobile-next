package io.github.eslamasabry.oc.feature.voice

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ComposerVoicePresenterTest {
    private class Rig(scope: TestScope, autoSend: Boolean = false, unavailable: Boolean = false) {
        val hub = FakeHub(models = FakeModels(autoSend), unavailable = unavailable)
        val field = FakeField()
        var settingsOpened = 0
        val presenter = ComposerVoicePresenter(hub, SchedulerClock(scope), scope.backgroundScope) { settingsOpened++ }
        val dictation get() = hub.dictation

        init {
            presenter.attach(field)
        }

        fun listening(words: String) {
            dictation.state.value = VoiceState.Listening(Instant.fromEpochMilliseconds(0), VoiceMode.Tap, 0, words)
        }
    }

    @Test
    fun `B-MEDIA-017 auto-send is off by default so a tap never stops on a pause and a hold never does`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(VoiceMode.Tap, rig.dictation.starts.single().mode)
        assertFalse(rig.dictation.starts.single().stopOnSilence)

        val on = Rig(this, autoSend = true)
        runCurrent()
        on.presenter.tapMic()
        runCurrent()
        assertTrue(on.dictation.starts.single().stopOnSilence, "with the setting on, a tap stops after the pause")
        on.dictation.state.value = VoiceState.Idle
        val hold = Rig(this, autoSend = true)
        runCurrent()
        hold.presenter.holdStart()
        runCurrent()
        assertEquals(VoiceMode.Hold, hold.dictation.starts.single().mode)
        assertFalse(hold.dictation.starts.single().stopOnSilence, "holding the mic never sends by itself")
    }

    @Test
    fun `B-MEDIA-010 the live transcript shows while listening and lands in the field on Done`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(MicPhase.Starting, rig.presenter.state.value.phase)
        rig.listening("hello")
        runCurrent()
        assertEquals(MicPhase.Listening, rig.presenter.state.value.phase)
        assertEquals("hello", rig.presenter.state.value.words)
        rig.listening("hello there")
        runCurrent()
        assertEquals("hello there", rig.presenter.state.value.words)
        rig.presenter.done()
        runCurrent()
        assertEquals(1, rig.dictation.finishes)
        rig.dictation.state.value = VoiceState.Writing("hello there")
        runCurrent()
        assertEquals(MicPhase.Writing, rig.presenter.state.value.phase)
        rig.dictation.state.value = VoiceState.Done("hello there", VoiceEnd.Person)
        runCurrent()
        assertEquals("hello there", rig.field.value)
        assertEquals(MicPhase.Idle, rig.presenter.state.value.phase)
        assertTrue(rig.field.sent.isEmpty(), "Done never sends")
    }

    @Test
    fun `B-MEDIA-024 dictated words merge around the field's selection`() = runTest {
        val rig = Rig(this)
        rig.field.value = "fix the bug"
        rig.field.sel = 8..11 // "bug"
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("crash")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("crash", VoiceEnd.Person)
        runCurrent()
        assertEquals("fix the crash", rig.field.value)
    }

    @Test
    fun `B-MEDIA-017 a pause with the setting on counts down three seconds then sends through the one send path`() = runTest {
        val rig = Rig(this, autoSend = true)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("run the tests")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("run the tests", VoiceEnd.Silence)
        runCurrent()
        val ui = rig.presenter.state.value
        assertEquals(MicPhase.Countdown, ui.phase)
        assertEquals(3, ui.secondsLeft)
        assertEquals("run the tests", rig.field.value)
        advanceTimeBy(1_100)
        assertEquals(2, rig.presenter.state.value.secondsLeft)
        assertTrue(rig.field.sent.isEmpty())
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(listOf("run the tests"), rig.field.sent)
        assertEquals(MicPhase.Idle, rig.presenter.state.value.phase)
        assertNull(rig.presenter.state.value.secondsLeft)
    }

    @Test
    fun `B-MEDIA-017 Send now sends at once and Don't send keeps the words unsent`() = runTest {
        val rig = Rig(this, autoSend = true)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("ship it")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("ship it", VoiceEnd.Silence)
        runCurrent()
        rig.presenter.sendNow()
        assertEquals(listOf("ship it"), rig.field.sent)

        val keep = Rig(this, autoSend = true)
        runCurrent()
        keep.presenter.tapMic()
        runCurrent()
        keep.listening("not yet")
        runCurrent()
        keep.dictation.state.value = VoiceState.Done("not yet", VoiceEnd.Silence)
        runCurrent()
        keep.presenter.dontSend()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(keep.field.sent.isEmpty())
        assertEquals("not yet", keep.field.value)
        assertEquals(MicPhase.Idle, keep.presenter.state.value.phase)
    }

    @Test
    fun `B-MEDIA-017 changing the field during the countdown cancels the send`() = runTest {
        val rig = Rig(this, autoSend = true)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("deploy")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("deploy now", VoiceEnd.Silence)
        runCurrent()
        rig.field.value = "deploy now please"
        rig.presenter.fieldChanged(rig.field.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(rig.field.sent.isEmpty())
        assertEquals(MicPhase.Idle, rig.presenter.state.value.phase)
    }

    @Test
    fun `B-MEDIA-016 a spoken slash command is never auto-sent and the pause is explained`() = runTest {
        val rig = Rig(this, autoSend = true)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("/undo")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("/undo", VoiceEnd.Silence)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(rig.field.sent.isEmpty())
        assertEquals("/undo", rig.field.value)
        assertEquals(VoiceNote.StoppedAfterPause, rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-016 a rejected transcript never auto-sends the draft already in the box`() = runTest {
        val rig = Rig(this, autoSend = true)
        rig.field.value = "Please refactor the parser"
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("a")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("a", VoiceEnd.Silence)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(rig.field.sent.isEmpty())
        assertEquals(null, rig.presenter.state.value.secondsLeft)
        assertEquals(VoiceNote.StoppedAfterPause, rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-023 sliding away and letting go of a held mic throws the words away`() = runTest {
        val rig = Rig(this)
        rig.field.value = "draft"
        runCurrent()
        rig.presenter.holdStart()
        runCurrent()
        assertTrue(rig.presenter.state.value.holding)
        rig.listening("never mind")
        runCurrent()
        advanceTimeBy(2_000)
        rig.presenter.holdArmed(true)
        assertTrue(rig.presenter.state.value.cancelArmed)
        rig.presenter.holdEnd(cancelled = true)
        runCurrent()
        assertEquals(listOf(false), rig.dictation.cancels)
        assertEquals("draft", rig.field.value)
        assertEquals(MicPhase.Idle, rig.presenter.state.value.phase)
        assertNull(rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-023 a held touch shorter than 400 ms is a slip, not a message`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.holdStart()
        runCurrent()
        rig.listening("")
        runCurrent()
        advanceTimeBy(200)
        rig.presenter.holdEnd(cancelled = false)
        runCurrent()
        assertEquals(listOf(false), rig.dictation.cancels)
        assertEquals(0, rig.dictation.finishes)
    }

    @Test
    fun `B-MEDIA-023 letting go of a held mic after speaking writes the words down`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.holdStart()
        runCurrent()
        rig.listening("add a test")
        runCurrent()
        advanceTimeBy(1_500)
        rig.presenter.holdEnd(cancelled = false)
        runCurrent()
        assertEquals(1, rig.dictation.finishes)
        rig.dictation.state.value = VoiceState.Done("add a test", VoiceEnd.Person)
        runCurrent()
        assertEquals("add a test", rig.field.value)
        assertTrue(rig.field.sent.isEmpty(), "a hold never sends by itself")
    }

    @Test
    fun `B-MEDIA-023 letting go while the microphone is still starting acts once it listens`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.holdStart()
        runCurrent()
        rig.presenter.holdEnd(cancelled = true)
        rig.listening("")
        runCurrent()
        assertEquals(listOf(false), rig.dictation.cancels)
    }

    @Test
    fun `B-MEDIA-023 a stale result from an earlier recording never becomes a draft`() = runTest {
        val rig = Rig(this)
        rig.dictation.state.value = VoiceState.Done("old words", VoiceEnd.Person)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        // The fake moved to Starting; a replayed Done before any listening is ignored.
        assertEquals("", rig.field.value)
        assertEquals(MicPhase.Starting, rig.presenter.state.value.phase)
    }

    @Test
    fun `voice mode nothing heard leaves the field as it was and says so`() = runTest {
        val rig = Rig(this)
        rig.field.value = "keep"
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("", VoiceEnd.Person)
        runCurrent()
        assertEquals("keep", rig.field.value)
        assertEquals(VoiceNote.NothingHeard, rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-032 a refused microphone asks again or opens Android settings when blocked`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.dictation.startResult = mediaFailure(MediaProblem.MicDenied)
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(VoiceNote.MicNeeded(blocked = false), rig.presenter.state.value.note)
        rig.presenter.allowMic()
        runCurrent()
        assertEquals(2, rig.dictation.starts.size, "Allow microphone asks again")

        rig.dictation.startResult = mediaFailure(MediaProblem.MicDeniedForever)
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(VoiceNote.MicNeeded(blocked = true), rig.presenter.state.value.note)
        rig.presenter.allowMic()
        assertEquals(1, rig.hub.settingsOpened)
    }

    @Test
    fun `a missing offline model points to Voice settings`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.dictation.startResult = mediaFailure(MediaProblem.SpeechModelMissing)
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(VoiceNote.NeedsModel, rig.presenter.state.value.note)
        rig.presenter.openSettings()
        assertEquals(1, rig.settingsOpened)
        assertNull(rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-023 an error mid-recording keeps the words written so far and shows a plain note`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("first part")
        runCurrent()
        rig.dictation.state.value = VoiceState.Failed(MediaProblem.RecordingInterrupted, "first part")
        runCurrent()
        assertEquals("first part", rig.field.value)
        assertEquals(VoiceNote.Failed(MediaProblem.RecordingInterrupted), rig.presenter.state.value.note)
    }

    @Test
    fun `B-MEDIA-032 where voice cannot start the mic is absent, never dead`() = runTest {
        val rig = Rig(this, unavailable = true)
        runCurrent()
        assertFalse(rig.presenter.state.value.available)
        rig.presenter.tapMic()
        runCurrent()
        assertTrue(rig.dictation.starts.isEmpty())
    }

    @Test
    fun `leaving the app ends the countdown and tells the recognizer`() = runTest {
        val rig = Rig(this, autoSend = true)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("later")
        runCurrent()
        rig.dictation.state.value = VoiceState.Done("send later", VoiceEnd.Silence)
        runCurrent()
        rig.presenter.hostChanged(inForeground = false)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(rig.field.sent.isEmpty())
        assertTrue(rig.dictation.hosts.isNotEmpty())
    }
}
