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
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.update
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
        // A download asks first, naming its size and the network (VoiceConfirm.Download); nothing starts before.
        assertTrue(presenter.state.value.confirm is VoiceConfirm.Download)
        assertFalse(hub.models.calls.any { it.startsWith("download") })
        presenter.confirm()
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
        presenter.confirm()
        runCurrent()
        assertEquals(MediaProblem.DownloadChecksum, presenter.state.value.problem)
        presenter.dismissProblem()
        hub.models.nextFailure = Failure.Media(MediaProblem.DownloadBusy)
        presenter.downloadSelected()
        runCurrent()
        presenter.confirm()
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
        presenter.confirm()
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
        // Choosing a voice that is not downloaded yet asks to download it first.
        withEngine.setNaturalVoice(true)
        runCurrent()
        assertTrue(withEngine.state.value.confirm is VoiceConfirm.Download)
        withEngine.dismissConfirm()
        // Once its pack is on the phone, choosing turns it on.
        ready.models.state.update {
            it.copy(packs = (it.packs + (ready.models.naturalPack.id to PackStatus.Installed)).toImmutableMap())
        }
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

    @Test
    fun `B-MEDIA-013 a download that fails while the page is open says why and offers Try again`() = runTest {
        val hub = FakeHub()
        val gate = CompletableDeferred<io.github.eslamasabry.oc.core.common.Outcome<Unit>>()
        hub.models.downloadGate = gate
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        presenter.downloadSelected()
        runCurrent()
        presenter.confirm()
        runCurrent()
        assertTrue(presenter.state.value.busy)
        // The model manager gives up (a stall or a timeout) and records why.
        hub.models.state.update {
            it.copy(job = null, lastProblem = MediaProblem.DownloadTimedOut, lastProblemPack = PackId("base"))
        }
        gate.complete(Failure.Media(MediaProblem.DownloadTimedOut).let { io.github.eslamasabry.oc.core.common.Outcome.Failed(it) })
        runCurrent()
        val ui = presenter.state.value
        assertFalse(ui.busy, "never a progress bar left after the job ended")
        assertEquals(MediaProblem.DownloadTimedOut, ui.problem)
        assertEquals(PackId("base"), ui.retryPack)
        presenter.retry()
        runCurrent()
        assertNull(presenter.state.value.problem)
        val again = presenter.state.value.confirm
        assertTrue(again is VoiceConfirm.Download && again.pack == PackId("base"), "Try again asks again, then downloads")
    }

    @Test
    fun `B-MEDIA-013 a download that failed while the page was closed is reported when it opens`() = runTest {
        val hub = FakeHub(naturalVoiceAvailable = true)
        hub.models.state.update {
            it.copy(lastProblem = MediaProblem.DownloadTimedOut, lastProblemPack = PackId("kokoro-en"))
        }
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        assertEquals(MediaProblem.DownloadTimedOut, presenter.state.value.problem)
        assertEquals(PackId("kokoro-en"), presenter.state.value.retryPack)
        presenter.dismissProblem()
        runCurrent()
        assertNull(presenter.state.value.problem)
        assertNull(hub.models.state.value.lastProblem, "Not now clears it for every page")
    }

    @Test
    fun `B-MEDIA-026 a running download hides an older problem and a stall reported by the manager replaces the bar`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        hub.models.progress(PackId("base"), io.github.eslamasabry.oc.engine.voice.state.PackJobPhase.Downloading(0, 100))
        runCurrent()
        assertTrue(presenter.state.value.busy)
        assertNull(presenter.state.value.problem)
        hub.models.state.update {
            it.copy(job = null, lastProblem = MediaProblem.DownloadTimedOut, lastProblemPack = PackId("base"))
        }
        runCurrent()
        assertFalse(presenter.state.value.busy)
        assertEquals(MediaProblem.DownloadTimedOut, presenter.state.value.problem)
    }

    @Test
    fun `B-MEDIA-019 a connection that changes to one that may cost data asks again with its words`() = runTest {
        val hub = FakeHub()
        val presenter = VoiceSettingsPresenter(hub, backgroundScope) {}
        runCurrent()
        presenter.downloadSelected()
        runCurrent()
        // Asked on Wi-Fi; the download then pauses because the phone moved to mobile data (NotifyingVoiceModels).
        hub.models.network = io.github.eslamasabry.oc.engine.voice.models.SetupNetwork.Metered
        hub.models.nextFailure = Failure.Network(io.github.eslamasabry.oc.core.common.NetworkWhy.Unknown)
        presenter.confirm()
        runCurrent()
        val asked = presenter.state.value.confirm
        assertTrue(asked is VoiceConfirm.Download && asked.network == io.github.eslamasabry.oc.engine.voice.models.SetupNetwork.Metered)
        assertNull(presenter.state.value.problem, "a pause is not a failure")
    }
}
