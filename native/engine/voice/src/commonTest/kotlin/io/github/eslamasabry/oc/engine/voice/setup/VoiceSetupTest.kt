package io.github.eslamasabry.oc.engine.voice.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.voice.dictation.workScope
import io.github.eslamasabry.oc.engine.voice.models.FakeFetcher
import io.github.eslamasabry.oc.engine.voice.models.GatedProbe
import io.github.eslamasabry.oc.engine.voice.models.ModelPacks
import io.github.eslamasabry.oc.engine.voice.models.PackInstaller
import io.github.eslamasabry.oc.engine.voice.models.SchedulerClock
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.models.SetupNetworkSource
import io.github.eslamasabry.oc.engine.voice.models.SystemModelFiles
import io.github.eslamasabry.oc.engine.voice.models.bodiesOf
import io.github.eslamasabry.oc.engine.voice.models.bytesOf
import io.github.eslamasabry.oc.engine.voice.models.device
import io.github.eslamasabry.oc.engine.voice.models.tempRoot
import io.github.eslamasabry.oc.engine.voice.models.testPack
import io.github.eslamasabry.oc.engine.voice.prefs.InMemoryVoicePrefs
import io.github.eslamasabry.oc.engine.voice.prefs.VoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeNetwork(var now: SetupNetwork = SetupNetwork.Unmetered) : SetupNetworkSource {
    var reads = 0
    val events = MutableSharedFlow<SetupNetwork>(extraBufferCapacity = 8)

    override suspend fun current(): SetupNetwork {
        reads++
        return now
    }

    override val changes: Flow<SetupNetwork> = events
}

private class FakeHost(var inForeground0: Boolean = true) : VoiceSetupHost {
    var starts = 0
    var cancels = 0
    var notificationAsks = 0
    var notificationsThrow = false
    var startResult: Outcome<Unit> = Unit.ok()

    override suspend fun startListening(): Outcome<Unit> {
        starts++
        return startResult
    }

    override suspend fun cancelListening() {
        cancels++
    }

    override suspend fun askNotifications() {
        notificationAsks++
        if (notificationsThrow) throw IllegalStateException("no notification permission bridge")
    }

    override val inForeground: Boolean get() = inForeground0
}

class VoiceSetupTest {
    private val contents = listOf(bytesOf(3_000, 1), bytesOf(5_000, 2))
    private val base = testPack("base", contents).copy(minTotalRamMb = 1536)
    private val small = testPack("small", contents).copy(minTotalRamMb = 3400)
    private val tiny = testPack("tiny", contents).copy(minTotalRamMb = 1024)
    private val root = tempRoot()
    private val fetcher = FakeFetcher((bodiesOf(base, contents) + bodiesOf(small, contents) + bodiesOf(tiny, contents)).toMutableMap())
    private val probe = GatedProbe(device(ramMb = 8_000))
    private val prefs = InMemoryVoicePrefs()
    private val network = FakeNetwork()
    private val host = FakeHost()

    private fun TestScope.setup(): Pair<VoiceSetup, ModelPacks> {
        val installer = PackInstaller(root, SystemModelFiles(), fetcher, TestDispatchers(StandardTestDispatcher(testScheduler)), SchedulerClock(testScheduler))
        val packs = ModelPacks(installer, probe, prefs, workScope(), listOf(base, small, tiny))
        return VoiceSetup(packs, network, workScope()) to packs
    }

    private fun consentOf(run: VoiceSetupRun) = assertIs<VoiceSetupStage.NeedsConsent>(run.stage.value)

    @Test
    fun `B-MEDIA-019 creating the setup without a mic tap does no work`() =
        runTest {
            val (setup, _) = setup()
            val run = setup.begin(host)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Idle, run.stage.value)
            assertEquals(0, probe.calls)
            assertEquals(0, network.reads)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `B-MEDIA-019 RAM picks the pack before consent, then download, verify, ready and listening`() =
        runTest {
            val (setup, packs) = setup()
            val run = setup.begin(host)
            run.start()
            val consent = consentOf(run)
            assertEquals(PackId("small"), consent.pack)
            assertEquals(small.totalBytes, consent.bytesLeft)
            assertEquals("small", prefs.values[VoicePrefs.SELECTED_PACK])
            assertTrue(fetcher.requests.isEmpty())
            assertEquals(0, host.starts)
            run.consent(consent.attemptId, allowMetered = false)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
            assertEquals(1, host.starts)
            assertEquals(1, host.notificationAsks)
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("small")])
        }

    @Test
    fun `B-MEDIA-019 mobile data needs explicit consent and a cancelled consent cannot authorize`() =
        runTest {
            network.now = SetupNetwork.Metered
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            val first = consentOf(run)
            assertEquals(SetupNetwork.Metered, first.network)
            run.consent(first.attemptId, allowMetered = false)
            val again = consentOf(run)
            assertTrue(again.attemptId != first.attemptId)
            run.consent(first.attemptId, allowMetered = true) // the old sheet's consent
            assertIs<VoiceSetupStage.NeedsConsent>(run.stage.value)
            assertTrue(fetcher.requests.isEmpty())
            run.consent(again.attemptId, allowMetered = true)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
        }

    @Test
    fun `B-MEDIA-019 offline first use blocks and retries after reconnect`() =
        runTest {
            network.now = SetupNetwork.Offline
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.DownloadOffline), run.stage.value)
            network.now = SetupNetwork.Unmetered
            run.start()
            assertIs<VoiceSetupStage.NeedsConsent>(run.stage.value)
        }

    @Test
    fun `B-MEDIA-019 a network that turns metered after the notification prompt asks again`() =
        runTest {
            val (setup, _) = setup()
            val run = setup.begin(object : VoiceSetupHost by host {
                override suspend fun askNotifications() {
                    network.now = SetupNetwork.Unknown
                }
            })
            run.start()
            run.consent(consentOf(run).attemptId, allowMetered = false)
            assertEquals(SetupNetwork.Unknown, consentOf(run).network)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `B-MEDIA-019 N15 Wi-Fi turning into mobile data during the download pauses it and keeps the part`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (setup, packs) = setup()
            val run = setup.begin(host)
            run.start()
            val consenting = launch { run.consent(consentOf(run).attemptId, allowMetered = false) }
            runCurrent()
            assertIs<VoiceSetupStage.Downloading>(run.stage.value)
            network.events.emit(SetupNetwork.Metered)
            advanceUntilIdle()
            consenting.join()
            val resume = consentOf(run)
            assertTrue(resume.resuming)
            assertEquals(SetupNetwork.Metered, resume.network)
            assertEquals(small.totalBytes - 1_000, resume.bytesLeft)
            assertEquals(1_000, packs.partialBytes(PackId("small")))
        }

    @Test
    fun `B-MEDIA-020 a notification denial or error still allows setup`() =
        runTest {
            host.notificationsThrow = true
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            run.consent(consentOf(run).attemptId, allowMetered = false)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
        }

    @Test
    fun `B-MEDIA-020 a verification failure blocks listening and a retry asks consent again`() =
        runTest {
            fetcher.bodies[small.urlFor(small.files[1])] = bytesOf(5_000, 77)
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            run.consent(consentOf(run).attemptId, allowMetered = false)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.DownloadChecksum), run.stage.value)
            assertEquals(0, host.starts)
            run.start()
            assertIs<VoiceSetupStage.NeedsConsent>(run.stage.value)
        }

    @Test
    fun `B-MEDIA-020 a mic failure recovers without a re-download`() =
        runTest {
            host.startResult = Failure.Media(MediaProblem.MicDenied).failed()
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            run.consent(consentOf(run).attemptId, allowMetered = false)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.MicDenied), run.stage.value)
            val fetched = fetcher.requests.size
            host.startResult = Unit.ok()
            run.start()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
            assertEquals(fetched, fetcher.requests.size)
        }

    @Test
    fun `B-MEDIA-020 a download that finishes in the background waits at ready`() =
        runTest {
            host.inForeground0 = false
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            run.consent(consentOf(run).attemptId, allowMetered = false)
            advanceUntilIdle()
            assertEquals(VoiceSetupStage.Ready(PackId("small")), run.stage.value)
            assertEquals(0, host.starts)
            host.inForeground0 = true
            run.listen()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
        }

    @Test
    fun `B-MEDIA-020 duplicate taps and a cancel during the download are safe`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (setup, packs) = setup()
            val run = setup.begin(host)
            run.start()
            run.start() // a second tap while consent is shown
            val consenting = launch { run.consent(consentOf(run).attemptId, allowMetered = false) }
            runCurrent()
            run.close()
            advanceUntilIdle()
            consenting.join()
            assertEquals(null, packs.downloading)
            assertEquals(1_000, packs.partialBytes(PackId("small"))) // closing the sheet keeps the parts (M3)
            assertEquals(0, host.starts)
        }

    @Test
    fun `B-MEDIA-021 a handed-off recording survives closing the sheet, otherwise closing stops it`() =
        runTest {
            val (setup, _) = setup()
            val handed = setup.begin(host)
            handed.start()
            handed.consent(consentOf(handed).attemptId, false)
            advanceUntilIdle()
            handed.handOff()
            handed.close()
            assertEquals(0, host.cancels)
            val other = setup.begin(host)
            other.start() // installed now: listens at once
            assertEquals(VoiceSetupStage.Listening, other.stage.value)
            other.close()
            assertEquals(1, host.cancels)
        }

    @Test
    fun `B-MEDIA-021 a download started elsewhere stays its owner's`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (setup, packs) = setup()
            val elsewhere = workScope().launch { packs.download(PackId("small")) }
            runCurrent()
            val run = setup.begin(host)
            run.start()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.DownloadBusy), run.stage.value)
            run.close()
            runCurrent()
            assertEquals(PackId("small"), packs.downloading)
            elsewhere.cancel()
        }

    @Test
    fun `B-MEDIA-021 the automatic selection survives a restart and deleting it asks consent again`() =
        runTest {
            val (setup, packs) = setup()
            val run = setup.begin(host)
            run.start()
            run.consent(consentOf(run).attemptId, false)
            advanceUntilIdle()
            val (again, packsAgain) = setup() // a restart: same prefs, same files
            assertEquals(PackId("small"), packsAgain.state.value.selected)
            assertEquals(Unit.ok(), packs.remove(PackId("small")))
            val after = again.begin(host)
            after.start()
            assertIs<VoiceSetupStage.NeedsConsent>(after.stage.value)
        }

    @Test
    fun `B-MEDIA-011 no microphone offers no pack, and a pack short of storage stops before consent`() =
        runTest {
            probe.result = device(mic = false)
            val (setup, _) = setup()
            val run = setup.begin(host)
            run.start()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.NoMicrophone), run.stage.value)

            probe.result = device(ramMb = 8_000, storage = 1_000)
            val (setup2, _) = setup()
            val tight = setup2.begin(host)
            tight.start()
            val problem = assertIs<VoiceSetupStage.Problem>(tight.stage.value)
            assertEquals(MediaProblem.PackStorageTooSmall, problem.problem)
            assertEquals("small", prefs.values[VoicePrefs.SELECTED_PACK]) // never a different pack

            probe.result = device(ramMb = null)
            val (setup3, _) = setup()
            val unknown = setup3.begin(host)
            unknown.start()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.MemoryUnknown), unknown.stage.value)

            probe.result = device(ramMb = 900)
            val (setup4, _) = setup()
            val weak = setup4.begin(host)
            weak.start()
            assertEquals(VoiceSetupStage.Problem(MediaProblem.PackMemoryTooSmall), weak.stage.value)
        }

    @Test
    fun `B-MEDIA-011 an installed selected eligible pack starts even offline`() =
        runTest {
            val (setup, packs) = setup()
            assertIs<Outcome.Ok<*>>(packs.download(PackId("tiny")))
            packs.select(PackId("tiny"))
            network.now = SetupNetwork.Offline
            network.reads = 0
            val run = setup.begin(host)
            run.start()
            assertEquals(VoiceSetupStage.Listening, run.stage.value)
            assertEquals(0, network.reads)
        }
}
