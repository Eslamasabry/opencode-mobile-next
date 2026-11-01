package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PackInstallerTest {
    private val contents = listOf(bytesOf(3_000, 1), bytesOf(5_000, 2))
    private val pack = testPack(contents = contents)
    private val root = tempRoot()
    private val files = FlakyFiles()
    private val fetcher = FakeFetcher(bodiesOf(pack, contents))

    private fun TestScope.installer(digests: DigestFactory = DigestFactory.Pure) =
        PackInstaller(
            root,
            files,
            fetcher,
            TestDispatchers(StandardTestDispatcher(testScheduler)),
            SchedulerClock(testScheduler),
            digests,
        )

    private fun marker() = files.readText("$root/${pack.dirName}/.installed.json")

    private fun part(index: Int) = "$root/${pack.dirName}.installing/${pack.files[index].name}.part"

    private fun problem(outcome: Outcome<*>) = (outcome.failureOrNull() as Failure.Media).problem

    @Test
    fun `B-MEDIA-025 a download is checksum-verified before the atomic install marker, in Flutter's shape`() =
        runTest {
            val installer = installer()
            val result = installer.install(pack)
            val installed = assertIs<Outcome.Ok<InstalledPack>>(result).value
            assertEquals("$root/${pack.dirName}/enc.int8.onnx", installed.path("encoder"))
            assertEquals(
                """{"pack":"base","revision":"rev1","files":[{"name":"enc.int8.onnx","length":3000,"sha256":"${pack.files[0].sha256}"},""" +
                    """{"name":"dec.int8.onnx","length":5000,"sha256":"${pack.files[1].sha256}"}]}""",
                marker(),
            )
            assertNotNull(installer.installed(pack))
            assertFalse(files.isDirectory(installer.stagingDir(pack)))
            assertTrue(fetcher.requests.all { it.userAgent == "OpenCode-Mobile/voice-model" && it.rangeStart == null })
        }

    @Test
    fun `B-MEDIA-025 a corrupt body never creates a marker and its part is deleted`() =
        runTest {
            fetcher.bodies[pack.urlFor(pack.files[1])] = bytesOf(5_000, 99)
            val result = installer().install(pack)
            assertEquals(MediaProblem.DownloadChecksum, problem(result))
            assertNull(marker())
            assertNull(files.size(part(1)))
            assertEquals(3_000, files.size(part(0))) // the good file stays for the next attempt
        }

    @Test
    fun `B-MEDIA-025 a partial response resumes only with the exact Content-Range`() =
        runTest {
            files.createDirectories("$root/${pack.dirName}.installing")
            files.openAppend(part(0)).apply { write(contents[0], 0, 1_200); close() }
            val result = installer().install(pack)
            assertIs<Outcome.Ok<InstalledPack>>(result)
            assertEquals(1_200L, fetcher.requests.first().rangeStart)
        }

    @Test
    fun `B-MEDIA-025 a 206 with the wrong Content-Range is refused and leaves no marker`() =
        runTest {
            files.createDirectories("$root/${pack.dirName}.installing")
            files.openAppend(part(0)).apply { write(contents[0], 0, 1_200); close() }
            fetcher.script = { request ->
                if (request.rangeStart != null) {
                    FakeResponse(206, contents[0].copyOfRange(1_200, 3_000), contentRange = "bytes 1200-2999/3001").ok()
                } else {
                    null
                }
            }
            val result = installer().install(pack)
            assertEquals(MediaProblem.DownloadHttp, problem(result))
            assertNull(marker())
        }

    @Test
    fun `B-MEDIA-025 a 200 answer to a resume restarts the file from zero`() =
        runTest {
            files.createDirectories("$root/${pack.dirName}.installing")
            files.openAppend(part(0)).apply { write(ByteArray(1_200) { 7 }, 0, 1_200); close() } // wrong bytes
            fetcher.script = { request ->
                if (request.rangeStart != null) FakeResponse(200, contents[0]).ok() else null
            }
            assertIs<Outcome.Ok<InstalledPack>>(installer().install(pack))
        }

    @Test
    fun `B-MEDIA-025 a cancelled download creates no marker and keeps the part for resume`() =
        runTest {
            fetcher.stallAfter = 2_000
            val installer = installer()
            val job = launch { installer.install(pack) }
            runCurrent()
            job.cancel()
            runCurrent()
            assertNull(marker())
            assertEquals(2_000L, files.size(part(0)))
            assertTrue(fetcher.responses.all { it.closed })
        }

    @Test
    fun `B-MEDIA-012 a stopped install keeps the partial download and resumes it`() =
        runTest {
            fetcher.stallAfter = 2_000
            val installer = installer()
            val job = launch { installer.install(pack) }
            runCurrent()
            job.cancel()
            runCurrent()
            assertEquals(2_000L, installer.partialBytes(pack))
            fetcher.stallAfter = null
            fetcher.requests.clear()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            assertEquals(2_000L, fetcher.requests.first().rangeStart)
            // Installing again is idempotent: no request at all.
            fetcher.requests.clear()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `B-MEDIA-025 N15 no Content-Length still installs, a wrong one fails, a body past the pin aborts`() =
        runTest {
            fetcher.script = { r -> if (r.url.endsWith("enc.int8.onnx")) FakeResponse(200, contents[0], contentLength = null).ok() else null }
            assertIs<Outcome.Ok<InstalledPack>>(installer().install(pack))

            val other = testPack("tiny", contents)
            fetcher.bodies.putAll(bodiesOf(other, contents))
            fetcher.script = { FakeResponse(200, contents[0], contentLength = 2_999).ok() }
            assertEquals(MediaProblem.DownloadLength, problem(installer().install(other)))

            fetcher.script = { FakeResponse(200, contents[0] + ByteArray(10), contentLength = null).ok() }
            assertEquals(MediaProblem.DownloadLength, problem(installer().install(other)))
            assertNull(files.readText("$root/${other.dirName}/.installed.json"))
        }

    @Test
    fun `B-MEDIA-025 redirects are followed only over HTTPS and at most five times`() =
        runTest {
            val first = pack.urlFor(pack.files[0])
            fetcher.script = { r ->
                if (r.url == first) FakeResponse(302, location = "/cdn/enc").ok()
                else if (r.url == "https://huggingface.co/cdn/enc") FakeResponse(200, contents[0]).ok()
                else null
            }
            assertIs<Outcome.Ok<InstalledPack>>(installer().install(pack))

            val other = testPack("tiny", contents)
            fetcher.bodies.putAll(bodiesOf(other, contents))
            fetcher.script = { FakeResponse(301, location = "http://cdn.example/enc").ok() }
            assertEquals(MediaProblem.DownloadUnsafeRedirect, problem(installer().install(other)))
            fetcher.script = { FakeResponse(302, location = "https://cdn.example/loop").ok() }
            assertEquals(MediaProblem.DownloadRedirect, problem(installer().install(other)))
            fetcher.script = { FakeResponse(302).ok() } // no Location
            assertEquals(MediaProblem.DownloadRedirect, problem(installer().install(other)))
        }

    @Test
    fun `B-MEDIA-026 a stalled body fails after the 20 s idle timeout`() =
        runTest {
            fetcher.stallAfter = 1_000
            val installer = installer()
            val result = async { installer.install(pack) }
            advanceTimeBy(19.seconds)
            assertFalse(result.isCompleted)
            advanceTimeBy(2.seconds)
            assertEquals(MediaProblem.DownloadTimedOut, problem(result.await()))
            assertEquals(1_000L, files.size(part(0)))
        }

    @Test
    fun `B-MEDIA-026 cancelling a stalled TLS handshake or body returns promptly`() =
        runTest {
            fetcher.script = { awaitCancellation() }
            val installer = installer()
            val handshake = launch { installer.install(pack) }
            runCurrent()
            handshake.cancel()
            runCurrent()
            assertTrue(handshake.isCompleted)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun `B-MEDIA-026 a handshake that never answers times out after 15 s`() =
        runTest {
            fetcher.script = { awaitCancellation() }
            val installer = installer()
            val result = async { installer.install(pack) }
            advanceTimeBy(16.seconds)
            assertEquals(MediaProblem.DownloadTimedOut, problem(result.await()))
        }

    @Test
    fun `B-MEDIA-026 a failing progress listener still completes the download`() =
        runTest {
            val result = installer().install(pack) { throw IllegalStateException("listener broke") }
            assertIs<Outcome.Ok<InstalledPack>>(result)
        }

    @Test
    fun `B-MEDIA-026 cancel during finalization cleans the partial`() =
        runTest {
            lateinit var job: kotlinx.coroutines.Job
            var calls = 0
            val cancelling = DigestFactory { if (++calls == 1) job.cancel(); PureSha256() }
            val installer = installer(cancelling)
            job = launch(start = CoroutineStart.LAZY) { installer.install(pack) }
            job.start()
            runCurrent()
            assertTrue(job.isCancelled)
            assertFalse(files.isDirectory(installer.stagingDir(pack)))
            assertNull(marker())
        }

    @Test
    fun `B-MEDIA-027 a failed re-download keeps the verified old model and rolls back every file`() =
        runTest {
            val installer = installer()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            files.failMove = { from, to -> from.endsWith(".part") && to.endsWith("/dec.int8.onnx") }
            val result = installer.install(pack, replaceExisting = true)
            assertEquals(MediaProblem.DownloadIncomplete, problem(result))
            files.failMove = null
            assertNotNull(installer.installed(pack))
            assertTrue(installer.verifyFully(pack))
            assertTrue(files.list("$root/${pack.dirName}").none { it.endsWith(".voice-backup") })
        }

    @Test
    fun `B-MEDIA-027 a first install whose publishing fails leaves no model and no marker`() =
        runTest {
            files.failMove = { from, to -> from.endsWith(".part") && to.endsWith("/dec.int8.onnx") }
            val installer = installer()
            assertEquals(MediaProblem.DownloadIncomplete, problem(installer.install(pack)))
            assertNull(marker())
            assertNull(files.size("$root/${pack.dirName}/enc.int8.onnx"))
        }

    @Test
    fun `B-MEDIA-027 same-pack installs serialize`() =
        runTest {
            val installer = installer()
            val one = async { installer.install(pack) }
            val two = async { installer.install(pack) }
            assertIs<Outcome.Ok<InstalledPack>>(one.await())
            assertIs<Outcome.Ok<InstalledPack>>(two.await())
            assertEquals(2, fetcher.requests.size) // one request per file, once
        }

    @Test
    fun `B-MEDIA-027 delete waits for an in-flight install and removes every owned path`() =
        runTest {
            fetcher.stallAfter = 1_000
            val installer = installer()
            val install = launch { installer.install(pack) }
            runCurrent()
            val removal = async { installer.remove(pack) }
            runCurrent()
            assertFalse(removal.isCompleted)
            install.cancel()
            runCurrent()
            assertEquals(Unit.ok(), removal.await())
            assertFalse(files.isDirectory(installer.stagingDir(pack)))
            assertFalse(files.isDirectory(installer.packDir(pack)))
        }

    @Test
    fun `B-MEDIA-027 a cleanup failure still installs and releases the lock`() =
        runTest {
            val installer = installer()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            files.failDelete = { it.endsWith(".voice-backup") || it.endsWith(".installing") }
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack, replaceExisting = true))
            files.failDelete = null
            assertFalse(installer.isBusy(pack))
            assertEquals(Unit.ok(), installer.remove(pack))
            assertNull(installer.installed(pack))
        }

    @Test
    fun `B-MEDIA-013 too little space is reported as no space`() =
        runTest {
            files.fullAfterBytes = 2_500
            assertEquals(MediaProblem.DownloadNoSpace, problem(installer().install(pack)))
            assertNull(marker())
        }

    @Test
    fun `B-MEDIA-013 remove deletes the pack and its partials`() =
        runTest {
            val installer = installer()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            files.createDirectories(installer.stagingDir(pack))
            files.openAppend(part(0)).apply { write(contents[0], 0, 10); close() }
            assertEquals(Unit.ok(), installer.remove(pack))
            assertEquals(emptyList(), files.list(root))
        }

    @Test
    fun `B-MEDIA-025 M4 start-up checks the stamp only and a changed edge byte invalidates the pack`() =
        runTest {
            val installer = installer()
            assertIs<Outcome.Ok<InstalledPack>>(installer.install(pack))
            val stamp = "$root/${pack.dirName}/.native-verified.json"
            assertNotNull(files.readText(stamp))
            files.delete(stamp)
            assertNotNull(installer.installed(pack)) // a missing stamp: one full pass, then rewritten
            assertNotNull(files.readText(stamp))
            // Same length, different last byte: the stamp sees it; the marker goes, so the pack is Missing.
            val path = "$root/${pack.dirName}/dec.int8.onnx"
            val changed = contents[1].copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
            files.delete(path)
            files.openAppend(path).apply { write(changed, 0, changed.size); close() }
            assertNull(installer.installed(pack))
            assertNull(marker())
        }
}

class PackInstallerTransportTest {
    @Test
    fun `B-MEDIA-026 a transport failure mid-body comes back as its kind and keeps the part`() =
        runTest {
            val contents = listOf(bytesOf(3_000, 1), bytesOf(5_000, 2))
            val pack = testPack(contents = contents)
            val root = tempRoot()
            val fetcher = FakeFetcher(bodiesOf(pack, contents))
            fetcher.script = {
                object : FetchResponse by FakeResponse(200, contents[0]) {
                    var reads = 0

                    override suspend fun read(buffer: ByteArray): Int {
                        if (reads++ == 1) throw FetchFailure(MediaProblem.DownloadClosed)
                        contents[0].copyInto(buffer, 0, 0, 1_000)
                        return 1_000
                    }
                }.ok()
            }
            val installer = PackInstaller(root, SystemModelFiles(), fetcher, TestDispatchers(StandardTestDispatcher(testScheduler)), SchedulerClock(testScheduler))
            assertEquals(Failure.Media(MediaProblem.DownloadClosed, Redacted.shape(FetchFailure(MediaProblem.DownloadClosed))), installer.install(pack).failureOrNull())
            assertEquals(1_000L, installer.partialBytes(pack))
        }
}
