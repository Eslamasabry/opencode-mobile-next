package io.github.eslamasabry.oc.feature.voice

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConfirm
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class VoiceSettingsPresenterTest {
    @Test
    fun `B-MEDIA-012 the page lists the three speech models and a tap only chooses`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        val ui = presenter.state.value
        assertFalse(ui.loading)
        assertEquals(listOf("base", "small", "tiny"), ui.packs.map { it.id.raw })
        assertEquals(PackId("base"), ui.selected)
        presenter.choose(PackId("small"))
        runCurrent()
        assertEquals(PackId("small"), presenter.state.value.selected)
        assertFalse(hub.models.calls.any { it.startsWith("download") }, "choosing never downloads")
    }

    @Test
    fun `B-MEDIA-020 a download shows progress, then verification, then the model is installed`() = runTest {
        val hub = FakeHub()
        val gate = CompletableDeferred<io.github.eslamasabry.oc.core.common.Outcome<Unit>>()
        hub.models.downloadGate = gate
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        presenter.downloadSelected()
        runCurrent()
        assertTrue(presenter.state.value.busy)
        hub.models.progress(PackId("base"), PackJobPhase.Downloading(50, 100))
        runCurrent()
        assertEquals(0.5f, presenter.state.value.job?.fraction)
        hub.models.progress(PackId("base"), PackJobPhase.Verifying)
        runCurrent()
        assertTrue(presenter.state.value.job!!.verifying)
        assertNull(presenter.state.value.job!!.fraction)
        gate.complete(Unit.ok())
        runCurrent()
        assertNull(presenter.state.value.job)
        assertTrue(presenter.state.value.selectedRow!!.installed)
    }

    @Test
    fun `B-MEDIA-013 a failed download shows its kind as a plain problem and a busy refusal shows nothing`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        hub.models.nextFailure = Failure.Media(MediaProblem.DownloadChecksum)
        presenter.downloadSelected()
        runCurrent()
        assertEquals(MediaProblem.DownloadChecksum, presenter.state.value.problem)
        presenter.dismissProblem()
        hub.models.nextFailure = Failure.Media(MediaProblem.DownloadBusy)
        presenter.downloadSelected()
        runCurrent()
        assertNull(presenter.state.value.problem)
    }

    @Test
    fun `B-MEDIA-013 deleting a model asks first and then removes it`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        presenter.askDelete()
        assertNull(presenter.state.value.confirm, "nothing to delete before it is installed")
        presenter.downloadSelected()
        runCurrent()
        presenter.askDelete()
        val confirm = presenter.state.value.confirm
        assertTrue(confirm is VoiceConfirm.DeletePack)
        presenter.confirm()
        runCurrent()
        assertTrue("remove:base" in hub.models.calls)
        assertEquals(PackStatus.Missing, presenter.state.value.selectedRow!!.status)
    }

    @Test
    fun `B-MEDIA-022 language and Send when I stop talking go to the model manager that persists them`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        assertFalse(presenter.state.value.autoSend, "B-MEDIA-017: off by default")
        presenter.openLanguages()
        assertTrue(presenter.state.value.languagePicking)
        presenter.chooseLanguage(VoiceLanguage.Arabic)
        runCurrent()
        assertFalse(presenter.state.value.languagePicking)
        assertEquals(VoiceLanguage.Arabic, presenter.state.value.language)
        presenter.setAutoSend(true)
        runCurrent()
        assertTrue(presenter.state.value.autoSend)
        assertEquals(listOf("language:arabic", "autoSend:true"), hub.models.calls.filter { !it.startsWith("refresh") })
    }

    @Test
    fun `B-MEDIA-005 the natural voice is not offered as a choice while the offline engine is missing`() = runTest {
        val hub = FakeHub(naturalVoiceAvailable = false)
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        val natural = assertNotNull(presenter.state.value.natural)
        assertFalse(natural.available)
        presenter.setNaturalVoice(true)
        runCurrent()
        assertFalse(hub.models.calls.any { it.startsWith("natural") })

        val ready = FakeHub(naturalVoiceAvailable = true)
        val withEngine = VoiceSettingsPresenter(ready, backgroundScope) {}
        runCurrent()
        withEngine.setNaturalVoice(true)
        runCurrent()
        assertTrue(withEngine.state.value.natural!!.chosen)
    }

    @Test
    fun `voice unavailable on this phone says so instead of drawing dead rows`() = runTest {
        val presenter = VoiceSettingsPresenter(FakeHub(unavailable = true), backgroundScope) {}
        runCurrent()
        assertFalse(presenter.state.value.available)
        assertFalse(presenter.state.value.loading)
    }
}
