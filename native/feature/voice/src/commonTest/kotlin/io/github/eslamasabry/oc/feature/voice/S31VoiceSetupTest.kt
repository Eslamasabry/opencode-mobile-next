package io.github.eslamasabry.oc.feature.voice

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupStage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * S31 story 3: the mic without a speech model only said "A local model is required". Flutter opens the "Voice
 * typing" sheet right there (`showVoiceAutomaticSetupSheet`): the model picked for this phone, its download with the
 * data cost, and listening once it is ready.
 */
class S31VoiceSetupTest {
    private class Rig(scope: TestScope, withSetup: Boolean = true) {
        val setup = if (withSetup) FakeMicSetup() else null
        val hub = FakeHub(setup = setup)
        val field = FakeField()
        var settingsOpened = 0
        val presenter = ComposerVoicePresenter(hub, SchedulerClock(scope), scope.backgroundScope) { settingsOpened++ }
        val dictation get() = hub.dictation

        init {
            presenter.attach(field)
            dictation.startResult = Failure.Media(MediaProblem.SpeechModelMissing).failed()
        }
    }

    @Test
    fun `B-MEDIA-052 a mic tap without a speech model opens the download offer, not only a note`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        val ui = rig.presenter.state.value
        assertNull(ui.note, "no bare 'A local model is required' note")
        assertEquals(VoiceSetupStage.Checking, ui.setup)
        assertEquals(1, rig.setup!!.run.starts)
        assertEquals(MicPhase.Idle, ui.phase)

        rig.setup.run.stage.value = VoiceSetupStage.NeedsConsent(7, PackId("base"), SetupNetwork.Metered, 153_000_000, false)
        runCurrent()
        assertTrue(rig.presenter.state.value.setup is VoiceSetupStage.NeedsConsent)
        // "Download 153 MB on mobile data" is the consent to use it.
        rig.presenter.setupDownload()
        runCurrent()
        assertEquals(listOf(7L to true), rig.setup.run.consents)
    }

    @Test
    fun `B-MEDIA-053 once the model is ready listening starts in the composer and the sheet closes`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.dictation.startResult = Outcome.Ok(Unit)
        // The setup asks the composer to listen (its host), then reports Listening.
        val started = rig.setup!!.host!!.startListening()
        assertTrue(started is Outcome.Ok)
        assertEquals(VoiceMode.Tap, rig.dictation.starts.last().mode)
        rig.setup.run.stage.value = VoiceSetupStage.Listening
        runCurrent()
        assertNull(rig.presenter.state.value.setup)
        assertTrue(rig.setup.run.handedOff, "the recording now belongs to the composer")
        rig.dictation.state.value = VoiceState.Listening(kotlin.time.Instant.fromEpochMilliseconds(0), VoiceMode.Tap, 0, "hello")
        runCurrent()
        assertEquals(MicPhase.Listening, rig.presenter.state.value.phase)
        assertEquals("hello", rig.presenter.state.value.words)
    }

    @Test
    fun `B-MEDIA-054 Not now closes the sheet and the run, and Choose another speech model opens Voice settings`() =
        runTest {
            val rig = Rig(this)
            runCurrent()
            rig.presenter.tapMic()
            runCurrent()
            rig.presenter.closeSetup()
            runCurrent()
            assertNull(rig.presenter.state.value.setup)
            assertTrue(rig.setup!!.run.closed)

            val other = Rig(this)
            runCurrent()
            other.presenter.tapMic()
            runCurrent()
            other.presenter.setupOpenSettings()
            runCurrent()
            assertEquals(1, other.settingsOpened)
            assertNull(other.presenter.state.value.setup)
        }

    @Test
    fun `B-MEDIA-055 without a setup (the phone's own recognizer) the note stays the way to Voice settings`() = runTest {
        val rig = Rig(this, withSetup = false)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        assertEquals(VoiceNote.NeedsModel, rig.presenter.state.value.note)
        assertNull(rig.presenter.state.value.setup)
        assertNotNull(rig.hub)
    }
}
