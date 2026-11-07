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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
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

    @Test
    fun `B-MEDIA-026 a download that hears nothing from the network for a minute stops with a timeout`() =
        runTest {
            // A connect that ignores cancellation, as HttpURLConnection does on an address that never answers
            // (the owner's phone: IPv6 to the model host black-holed behind the Tailscale VPN, 2026-11-07).
            val socket = CompletableDeferred<Unit>()
            fetcher.script = {
                withContext(NonCancellable) { socket.await() }
                null
            }
            val (packs, _) = packs()
            val download = async { packs.download(PackId("base")) }
            runCurrent()
            assertEquals(PackJobPhase.Downloading(0, base.totalBytes), packs.state.value.job?.phase)
            advanceTimeBy(59.seconds)
            runCurrent()
            assertNotNull(packs.state.value.job, "still trying before the minute is up")
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(MediaProblem.DownloadTimedOut, problem(download.await()))
            assertNull(packs.state.value.job, "the page leaves 0 MB at once, while the socket is still stuck")
            assertNull(packs.downloading)
            assertEquals(MediaProblem.DownloadTimedOut, packs.state.value.lastProblem)
            assertEquals(PackId("base"), packs.state.value.lastProblemPack)
            // The stuck socket letting go later publishes nothing over that, and the next attempt works.
            fetcher.script = null
            socket.complete(Unit)
            advanceUntilIdle()
            assertNull(packs.state.value.job)
            assertEquals(MediaProblem.DownloadTimedOut, packs.state.value.lastProblem)
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")))
            assertNull(packs.state.value.lastProblem, "a new download replaces the old problem")
        }

    @Test
    fun `B-MEDIA-026 a slow download that keeps receiving is never called stalled`() =
        runTest {
            val (packs, _) = packs()
            // Every 1 kB arrives 19 s after the last: 8 reads, over two minutes in all, never a minute of silence.
            var reads = 0
            fetcher.script = { request ->
                val body = fetcher.bodies.getValue(request.url)
                object : FetchResponse {
                    private var position = 0
                    override val status = 200
                    override val contentLength: Long = body.size.toLong()
                    override val contentRange: String? = null
                    override val location: String? = null

                    override suspend fun read(buffer: ByteArray): Int {
                        if (position >= body.size) return -1
                        delay(19.seconds)
                        reads++
                        val n = minOf(1_000, body.size - position)
                        body.copyInto(buffer, 0, position, position + n)
                        position += n
                        return n
                    }

                    override fun close() = Unit
                }.ok()
            }
            val download = async { packs.download(PackId("base")) }
            advanceUntilIdle()
            assertIs<Outcome.Ok<InstalledPack>>(download.await())
            assertEquals(8, reads)
            assertTrue(currentTime >= 150_000, "it took over two minutes")
        }

    @Test
    fun `B-MEDIA-013 an exception nobody expected ends the job as a plain failure instead of leaving it running`() =
        runTest {
            fetcher.script = { throw IllegalStateException("platform surprise") }
            val (packs, _) = packs()
            assertEquals(MediaProblem.DownloadIncomplete, problem(packs.download(PackId("base"))))
            assertNull(packs.state.value.job)
            assertNull(packs.downloading)
            assertEquals(MediaProblem.DownloadIncomplete, packs.state.value.lastProblem)
            fetcher.script = null
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")), "nothing is left busy")
        }

    @Test
    fun `B-MEDIA-013 a dropped connection ends the download with its reason and keeps the partial`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (packs, _) = packs()
            val download = async { packs.download(PackId("base")) }
            runCurrent()
            packs.interruptDownload(MediaProblem.DownloadOffline)
            assertEquals(MediaProblem.DownloadOffline, problem(download.await()))
            advanceUntilIdle()
            assertNull(packs.state.value.job)
            assertEquals(PackStatus.Partial(1_000, base.totalBytes), packs.state.value.packs[PackId("base")])
            assertEquals(MediaProblem.DownloadOffline, packs.state.value.lastProblem)
            packs.clearProblem()
            assertNull(packs.state.value.lastProblem)
        }

    @Test
    fun `B-MEDIA-013 E13-S04 cancelling a re-download leaves the pack Installed and ready`() =
        runTest {
            val (packs, installer) = packs()
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")))
            fetcher.stallAfter = 1_000
            val again = async { packs.download(PackId("base"), replaceExisting = true) }
            runCurrent()
            assertEquals(PackId("base"), packs.downloading)
            assertNotNull(packs.readySpeechPack(), "dictation keeps the installed model during the re-download")
            packs.cancelDownload()
            assertTrue(again.isCancelled)
            advanceUntilIdle()
            assertNull(packs.state.value.job)
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("base")])
            assertEquals(0L, packs.partialBytes(PackId("base")))
            assertNotNull(packs.readySpeechPack())
            packs.refresh()
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("base")])
            assertFalse(installer.isBusy(base))
        }

    @Test
    fun `B-MEDIA-013 E13-S04 a re-download that drops its connection says why and leaves the pack Installed`() =
        runTest {
            val (packs, _) = packs()
            assertIs<Outcome.Ok<InstalledPack>>(packs.download(PackId("base")))
            fetcher.stallAfter = 1_000
            val again = async { packs.download(PackId("base"), replaceExisting = true) }
            runCurrent()
            packs.interruptDownload(MediaProblem.DownloadOffline)
            assertEquals(MediaProblem.DownloadOffline, problem(again.await()))
            advanceUntilIdle()
            assertEquals(PackStatus.Installed, packs.state.value.packs[PackId("base")])
            assertEquals(MediaProblem.DownloadOffline, packs.state.value.lastProblem)
            assertNotNull(packs.readySpeechPack())
        }

    @Test
    fun `B-MEDIA-012 E13-S04 a cancelled first download keeps its partial row for resume`() =
        runTest {
            fetcher.stallAfter = 1_000
            val (packs, _) = packs()
            val first = async { packs.download(PackId("base")) }
            runCurrent()
            packs.cancelDownload()
            assertTrue(first.isCancelled)
            advanceUntilIdle()
            assertEquals(PackStatus.Partial(1_000, base.totalBytes), packs.state.value.packs[PackId("base")])
        }
}
