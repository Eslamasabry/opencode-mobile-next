package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.prefs.InMemoryVoicePrefs
import io.github.eslamasabry.oc.engine.voice.prefs.KeyValueVoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A probe whose answer the test releases (cancel or dispose during device preflight, B-MEDIA-030). */
internal class GatedProbe(var result: DeviceProbeResult = device()) : DeviceProbe {
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun probe(): DeviceProbeResult {
        calls++
        gate?.await()
        return result
    }

    override fun memoryNow(): MemoryNow? = null
}

class ModelPacksTest {
    private val speechContents = listOf(bytesOf(3_000, 1), bytesOf(5_000, 2))
    private val base = testPack("base", speechContents).copy(minTotalRamMb = 1536)
    private val small = testPack("small", speechContents).copy(minTotalRamMb = 3400)
    private val natural =
        testPack("kokoro-en", speechContents).copy(kind = PackKind.NaturalVoice, minTotalRamMb = 2000, natural = ModelCatalog.kokoroEn.natural)
    private val root = tempRoot()
    private val fetcher = FakeFetcher((bodiesOf(base, speechContents) + bodiesOf(small, speechContents) + bodiesOf(natural, speechContents)).toMutableMap())
    private val probe = GatedProbe()
    private val prefs = InMemoryVoicePrefs()

    private fun TestScope.packs(): Pair<ModelPacks, PackInstaller> {
        val installer = PackInstaller(root, SystemModelFiles(), fetcher, TestDispatchers(StandardTestDispatcher(testScheduler)), SchedulerClock(testScheduler))
        return ModelPacks(installer, probe, prefs, backgroundScope, listOf(base, small, natural)) to installer
    }

    private fun problem(outcome: Outcome<*>) = (outcome.failureOrNull() as? Failure.Media)?.problem

    @Test
    fun `B-MEDIA-012 an installed supported selection is ready and one above RAM is not`() =
        runTest {
            val (packs, installer) = packs()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(base))
            assertNotNull(packs.readySpeechPack())
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(small))
            assertEquals(Unit.ok(), packs.select(PackId("small")))
            probe.result = device(ramMb = 2000)
            packs.refresh()
            assertNull(packs.readySpeechPack())
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("small")])
        }

    @Test
    fun `B-MEDIA-012 install downloads and verifies through the manager and is idempotent`() =
        runTest {
            val (packs, _) = packs()
            val phases = mutableListOf<PackJobPhase?>()
            val watcher = backgroundScope.async { packs.state.collect { phases += it.job?.phase } }
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")))
            runCurrent()
            assertTrue(phases.any { it is PackJobPhase.Downloading })
            assertTrue(phases.contains(PackJobPhase.Verifying))
            assertNull(packs.state.value.job)
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("base")])
            val before = fetcher.requests.size
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")))
            assertEquals(before, fetcher.requests.size)
            watcher.cancel()
        }

    @Test
    fun `B-MEDIA-013 remove deletes packs and partials but is refused while that pack downloads`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (packs, installer) = packs()
            val download = async { packs.download(PackId("base")) }
            runCurrent()
            assertEquals(PackId("base"), packs.downloading)
            assertEquals(MediaProblem.DownloadBusy, problem(packs.remove(PackId("base"))))
            assertEquals(MediaProblem.DownloadBusy, problem(packs.discardPartial(PackId("base"))))
            assertEquals(MediaProblem.DownloadBusy, problem(packs.download(PackId("small"))))
            assertEquals(MediaProblem.DownloadBusy, problem(packs.select(PackId("small"))))
            packs.cancelDownload()
            assertTrue(download.isCancelled)
            assertEquals(PackStatus.Partial(1_000, base.totalBytes), packs.state.value.packs[PackId("base")])
            assertEquals(Unit.ok(), packs.remove(PackId("base")))
            assertEquals(0, installer.partialBytes(base))
            assertEquals(PackStatus.Missing, packs.state.value.packs[PackId("base")])
        }

    @Test
    fun `B-MEDIA-013 no platform means unavailable, never a download`() =
        runTest {
            probe.result = DeviceProbeResult.Unsupported
            val (packs, _) = packs()
            assertEquals(Failure.Unavailable(FeatureId("voice")), packs.download(PackId("base")).failureOrNull())
            assertTrue(fetcher.requests.isEmpty())
            assertEquals(MediaProblem.PackMemoryTooSmall, problem(packs().first.also { probe.result = device(ramMb = 1000) }.download(PackId("base"))))
        }

    @Test
    fun `B-MEDIA-030 a deletion or new selection during readiness work never publishes the stale rows`() =
        runTest {
            val (packs, installer) = packs()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(base))
            probe.gate = CompletableDeferred()
            val refresh = async { packs.refresh() }
            runCurrent()
            assertEquals(Unit.ok(), packs.remove(PackId("base")))
            probe.gate!!.complete(Unit)
            refresh.await()
            assertEquals(PackStatus.Missing, packs.state.value.packs[PackId("base")])
        }

    @Test
    fun `B-MEDIA-030 dispose or cancel during device preflight drops the device info`() =
        runTest {
            val (packs, _) = packs()
            probe.gate = CompletableDeferred()
            val first = async { packs.device() }
            runCurrent()
            packs.close()
            probe.gate!!.complete(Unit)
            first.await()
            assertTrue(packs.state.value.packs.isEmpty())
            probe.gate = null
            packs.device()
            assertEquals(2, probe.calls) // the stale probe was not cached
        }

    @Test
    fun `B-MEDIA-030 a superseded download leaves no running job behind`() =
        runTest {
            fetcher.stallAfter = 500
            val (packs, _) = packs()
            val download = async { packs.download(PackId("small")) }
            runCurrent()
            packs.cancelDownload()
            assertTrue(download.isCancelled)
            assertNull(packs.downloading)
            assertNull(packs.state.value.job)
            fetcher.stallAfter = null
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("small")))
        }

    @Test
    fun `B-MEDIA-022 the chosen pack and language persist across restarts`() =
        runTest {
            val backend = InMemoryPrefsBackend()
            fun store() =
                PrefsKeyValueStore(backend, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler))
            val installer = PackInstaller(root, SystemModelFiles(), fetcher, TestDispatchers(StandardTestDispatcher(testScheduler)), SchedulerClock(testScheduler))
            val first = ModelPacks(installer, probe, KeyValueVoicePrefs(store(), WriterLane("voice")), backgroundScope)
            assertEquals(PackId("base"), first.state.value.selected)
            assertEquals(VoiceLanguage.Auto, first.state.value.language)
            assertFalse(first.state.value.autoSend)
            assertEquals(Unit.ok(), first.select(PackId("tiny")))
            assertEquals(Unit.ok(), first.setLanguage(VoiceLanguage.Arabic))
            assertEquals(Unit.ok(), first.setAutoSend(true))

            val restarted = ModelPacks(installer, probe, KeyValueVoicePrefs(store(), WriterLane("voice")), backgroundScope)
            assertEquals(PackId("tiny"), restarted.state.value.selected)
            assertEquals(VoiceLanguage.Arabic, restarted.state.value.language)
            assertTrue(restarted.state.value.autoSend)
            assertEquals("arabic", (backend.load()["flutter.voice.language"] as? io.github.eslamasabry.oc.core.data.kv.RawPref.Str)?.value)
        }

    @Test
    fun `B-MEDIA-005 choosing the natural voice downloads first and turns it on only once installed`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (packs, _) = packs()
            val choosing = async { packs.setNaturalVoice(true) }
            runCurrent()
            assertFalse(packs.state.value.naturalVoice)
            assertFalse(prefs.readAloudNatural())
            // Phone's voice during the download cancels it and keeps the partial.
            assertEquals(Unit.ok(), packs.setNaturalVoice(false))
            assertTrue(choosing.isCancelled)
            assertEquals(PackStatus.Partial(1_000, natural.totalBytes), packs.state.value.packs[PackId("kokoro-en")])
            fetcher.stallAfter = null
            assertEquals(Unit.ok(), packs.setNaturalVoice(true))
            assertTrue(packs.state.value.naturalVoice)
            assertTrue(prefs.readAloudNatural())
            assertEquals(Unit.ok(), packs.remove(PackId("kokoro-en")))
            assertFalse(prefs.readAloudNatural())
        }

    @Test
    fun `B-MEDIA-005 the natural voice is refused below its 2000 MB RAM gate`() =
        runTest {
            probe.result = device(ramMb = 1999)
            val (packs, _) = packs()
            assertEquals(MediaProblem.PackMemoryTooSmall, problem(packs.setNaturalVoice(true)))
            advanceUntilIdle()
            assertTrue(fetcher.requests.isEmpty())
        }
}
