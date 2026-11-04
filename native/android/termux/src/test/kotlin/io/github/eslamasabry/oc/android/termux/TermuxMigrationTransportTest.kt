package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Flutter test/termux_migration_transport_test.dart, case for case. The bridge is a fake; the "real shell" cases run the
// very scripts in this machine's bash with GNU tar, curl and sha256sum against a temp PREFIX/HOME.
class TermuxMigrationTransportTest {
    private val job = "0123456789abcdef0123456789abcdef"

    private fun result(stdout: String) = TermuxCommandResult(stdout = stdout, exitCode = 0)

    private fun transport(run: MigrationTermuxRun) = BridgeTermuxMigrationTransport(run, ThreadDispatchers)

    private fun sha(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    @Test
    fun `B-LINUX-237 the inventory reports only bounded metadata and unknown versions`() = runTest {
        val source = transport { script, timeout ->
            assertEquals(110.seconds, timeout)
            assertContains(script, "timeout -k 2 90")
            result("free|9999999\nitem|projects|4096|3\nitem|config|0|0\nisolated|1")
        }.inspect()
        assertEquals(9999999L, source.freeBytes)
        assertEquals(4096L, source.items.first().bytes)
        assertTrue(source.oc2Isolated)
        assertNull(source.oc1Version)
    }

    @Test
    fun `B-LINUX-237 unsupported unselected categories do not hide valid projects`() = runTest {
        val source = transport { script, _ ->
            assertContains(script, "-name .git -type f")
            result("free|5000\nitem|projects|4096|3\nproblem|sessions|unsupportedEntry")
        }.inspect()
        assertNull(source.items.first().problem)
        assertEquals(TermuxMigrationFailure.unsupportedEntry, source.items.last().problem)
        assertEquals(0L, source.items.last().bytes)
    }

    @Test
    fun `B-LINUX-237 raw bridge credentials are discarded on failure`() = runTest {
        try {
            transport { _, _ -> throw IllegalStateException("test-provider-credential-must-not-escape") }.inspect()
            fail("Expected failure")
        } catch (error: TermuxMigrationException) {
            assertEquals(TermuxMigrationFailure.unavailable, error.code)
            assertFalse(error.toString().contains("test-provider-credential"))
            assertNull(error.cause)
        }
    }

    @Test
    fun `B-LINUX-237 the source rejects links even after prior metadata output`() = runTest {
        expectFailure(TermuxMigrationFailure.unsupportedEntry) {
            transport { _, _ -> result("free|5000\nfailure|unsupportedEntry") }.inspect()
        }
    }

    @Test
    fun `B-LINUX-237 pack has strict ids, USTAR, its own lock, a bounded size and a digest`() = runTest {
        val digest = "a".repeat(64)
        var calls = 0
        val transport = transport { script, _ ->
            calls++
            assertContains(script, "tar --format=ustar --hard-dereference --no-recursion --null")
            assertContains(script, "flock -n 9")
            assertContains(script, "ulimit -f")
            assertFalse(script.contains("rm -rf"))
            result("archive|10240|$digest")
        }
        expectFailure(TermuxMigrationFailure.invalidSelection) { transport.pack("../escape", TermuxMigrationItem.projects, 10240) }
        assertEquals(0, calls)
        assertEquals(digest, transport.pack(job, TermuxMigrationItem.projects, 10240).sha256)
    }

    private fun put(url: String, token: String?, offset: Long, body: ByteArray?, chunked: Boolean = false): Int {
        val connection = URI(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 3000
            connection.readTimeout = 10000
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("x-offset", offset.toString())
            if (body != null) {
                connection.doOutput = true
                if (chunked) connection.setChunkedStreamingMode(4096) else connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `B-LINUX-237 an authenticated stream resumes flushed chunks after a bridge loss`() = runTest {
        val directory = tempDir("migration-transport-test-")
        try {
            val destination = directory.resolve("migrations/$job/archive.tar")
            val content = ByteArray((BridgeTermuxMigrationTransport.CHUNK_BYTES + 4096).toInt()) { (it % 251).toByte() }
            val archive = TermuxMigrationArchive(content.size.toLong(), sha(content))
            val offsets = mutableListOf<Long>()
            var loseSecond = true
            val run = MigrationTermuxRun { script, _ ->
                val offset = Regex("--header 'x-offset: (\\d+)'").find(script)!!.groupValues[1].toLong()
                offsets.add(offset)
                if (offset > 0 && loseSecond) {
                    loseSecond = false
                    throw IllegalStateException("connection lost")
                }
                val token = Regex("Authorization: Bearer ([A-Za-z0-9_=-]+)'").find(script)!!.groupValues[1]
                val url = Regex("'(http://127\\.0\\.0\\.1:\\d+/archive)'").find(script)!!.groupValues[1]
                // A second local app cannot write without the fresh bearer.
                assertEquals(403, put(url, token = null, offset = offset, body = null))
                val end = minOf(offset + BridgeTermuxMigrationTransport.CHUNK_BYTES, content.size.toLong()).toInt()
                assertEquals(204, put(url, token, offset, content.copyOfRange(offset.toInt(), end)))
                result("copied")
            }
            expectFailure(TermuxMigrationFailure.unavailable) {
                transport(run).copy(job, TermuxMigrationItem.projects, archive, destination) { false }
            }
            val resumed = transport(run)
            resumed.copy(job, TermuxMigrationItem.projects, archive, destination) { false }
            assertTrue(Files.readAllBytes(destination).contentEquals(content))
            assertEquals(listOf(0L, BridgeTermuxMigrationTransport.CHUNK_BYTES, BridgeTermuxMigrationTransport.CHUNK_BYTES), offsets)
            resumed.copy(job, TermuxMigrationItem.projects, archive, destination) { false }
            assertEquals(3, offsets.size, "a verified rerun does not transfer again")
        } finally {
            directory.deleteRecursivelyQuietly()
        }
    }

    private fun shell(home: String, prefix: String) = MigrationTermuxRun { script, timeout -> runBash(script, timeout, home, prefix) }

    private fun runBash(script: String, timeout: Duration, home: String, prefix: String): TermuxCommandResult {
        val builder = ProcessBuilder("bash", "-s")
        builder.environment().apply {
            val path = get("PATH") ?: "/usr/bin:/bin"
            clear()
            put("PATH", path)
            put("HOME", home)
            put("PREFIX", prefix)
        }
        builder.redirectError(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
        val process = builder.start()
        process.outputStream.use { it.write(script.toByteArray()) }
        val output = ByteArrayOutputStream()
        process.inputStream.use { it.copyTo(output) }
        if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) process.destroyForcibly()
        return TermuxCommandResult(stdout = output.toString(), exitCode = process.exitValue())
    }

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.write(path, text.toByteArray())
    }

    @Test
    fun `B-LINUX-237 a real host shell packs managed projects and transfers a verified USTAR archive`() = runTest {
        val directory = tempDir("migration shell test-")
        try {
            val prefix = directory.resolve("usr").toString()
            val home = directory.resolve("home")
            val project = Path.of(prefix, "var/lib/proot-distro/containers/opencode-ubuntu/rootfs/root/projects/example")
            Files.createDirectories(project)
            Files.createDirectories(home)
            write(project.resolve("README.md"), "fixture project\n")
            Files.createDirectory(project.resolve(".git"))
            val transport = transport(shell(home.toString(), prefix))
            val projects = transport.inspect().items.single { it.item == TermuxMigrationItem.projects }
            assertNull(projects.problem)
            assertEquals(16L, projects.bytes)
            val archive = transport.pack(job, TermuxMigrationItem.projects, 1024 * 1024)
            val packed = home.resolve(".oc/migration/$job/projects.tar")
            assertEquals(archive.bytes, Files.size(packed))
            val bytes = Files.readAllBytes(packed)
            assertEquals(archive.sha256, sha(bytes))
            assertEquals("ustar", String(bytes, 257, 5, Charsets.US_ASCII))
            val destination = directory.resolve("migrations/$job/received.tar")
            transport.copy(job, TermuxMigrationItem.projects, archive, destination) { false }
            assertTrue(Files.readAllBytes(destination).contentEquals(bytes))
            assertEquals(448, modeOf(destination.parent))
            assertEquals(448, modeOf(destination.parent.parent))
            assertEquals(384, modeOf(destination))
            transport.cancel(job)
            val resumed = transport.pack(job, TermuxMigrationItem.projects, 1024 * 1024)
            assertEquals(archive.sha256, resumed.sha256)
            assertFalse(Files.exists(home.resolve(".oc/migration/$job/cancel")))
        } finally {
            directory.deleteRecursivelyQuietly()
        }
    }

    private class L2sFixture(val directory: Path) {
        val prefix: String = directory.resolve("usr").toString()
        val home: Path = directory.resolve("home")
        private val rootfs = Path.of(prefix, "var/lib/proot-distro/containers/opencode-ubuntu/rootfs")
        val project: Path = rootfs.resolve("root/projects/example")
        val store: Path = rootfs.resolve(".l2s")

        init {
            Files.createDirectories(project)
            Files.createDirectories(store)
            Files.createDirectories(home)
            Files.write(project.resolve("README.md"), "readme\n".toByteArray())
            Files.createDirectories(project.resolve(".git/objects/ab"))
            Files.write(store.resolve(".l2s.tmp_obj_00010001"), "OBJECT".toByteArray())
            Files.createSymbolicLink(project.resolve(".git/objects/ab/cdef"), store.resolve(".l2s.tmp_obj_00010001"))
        }
    }

    private fun l2s(block: suspend (L2sFixture) -> Unit) = runTest {
        val directory = tempDir("migration l2s-")
        try {
            block(L2sFixture(directory))
        } finally {
            directory.deleteRecursivelyQuietly()
        }
    }

    @Test
    fun `B-LINUX-237 link2symlink links are archived as their target contents, not as links`() = l2s { fixture ->
        transport(shell(fixture.home.toString(), fixture.prefix)).pack(job, TermuxMigrationItem.projects, 1024 * 1024)
        val tarFile = fixture.home.resolve(".oc/migration/$job/projects.tar").toString()
        val listing = ProcessBuilder("tar", "-tvf", tarFile).start().inputStream.use { String(it.readBytes()) }
        val entries = listing.trim().lines().associate { line -> line.split(Regex("\\s+")).last() to line.substring(0, 1) }
        assertEquals("-", entries["./example/.git/objects/ab/cdef"])
        assertEquals("-", entries["./example/README.md"])
        val out = Files.createDirectory(fixture.directory.resolve("out"))
        ProcessBuilder("tar", "-xf", tarFile, "-C", out.toString()).start().waitFor()
        val restored = out.resolve("example/.git/objects/ab/cdef")
        assertFalse(Files.isSymbolicLink(restored))
        assertEquals("OBJECT", String(Files.readAllBytes(restored)))
    }

    @Test
    fun `B-LINUX-237 inspect counts link2symlink target bytes and accepts the project`() = l2s { fixture ->
        val projects = transport(shell(fixture.home.toString(), fixture.prefix)).inspect().items.single { it.item == TermuxMigrationItem.projects }
        assertNull(projects.problem)
        assertEquals(("readme\n".length + "OBJECT".length).toLong(), projects.bytes)
    }

    @Test
    fun `B-LINUX-237 an unreadable link2symlink target is named, not migrated half-broken`() = l2s { fixture ->
        Files.delete(fixture.store.resolve(".l2s.tmp_obj_00010001"))
        val run = shell(fixture.home.toString(), fixture.prefix)
        val projects = transport(run).inspect().items.single { it.item == TermuxMigrationItem.projects }
        assertEquals(TermuxMigrationFailure.linkedFileUnreadable, projects.problem)
        expectFailure(TermuxMigrationFailure.linkedFileUnreadable) {
            transport(run).pack(job, TermuxMigrationItem.projects, 1024 * 1024)
        }
    }

    @Test
    fun `B-LINUX-237 an ordinary relative or absolute symlink is still refused`() {
        for (absolute in listOf(false, true)) {
            l2s { fixture ->
                Files.createSymbolicLink(fixture.project.resolve("link"), Path.of(if (absolute) "/usr/bin/env" else "README.md"))
                val projects = transport(shell(fixture.home.toString(), fixture.prefix)).inspect().items.single { it.item == TermuxMigrationItem.projects }
                assertEquals(TermuxMigrationFailure.unsupportedEntry, projects.problem, "absolute=$absolute")
            }
        }
    }

    @Test
    fun `B-LINUX-237 copy refuses a staging link without changing its target`() {
        for (suffix in listOf("", ".offset", ".offset.tmp")) {
            runTest {
                val directory = tempDir("migration-link-test-")
                try {
                    val destination = directory.resolve("migrations/$job/archive.tar")
                    Files.createDirectories(destination.parent)
                    val original = directory.resolve("original")
                    Files.write(original, "preserve".toByteArray())
                    Files.createSymbolicLink(destination.resolveSibling("archive.tar$suffix"), original)
                    val transport = transport { _, _ -> fail("No bridge dispatch for unsafe storage") }
                    expectFailure(TermuxMigrationFailure.destinationConflict) {
                        transport.copy(job, TermuxMigrationItem.projects, TermuxMigrationArchive(1024, "a".repeat(64)), destination) { false }
                    }
                    assertEquals("preserve", String(Files.readAllBytes(original)), "suffix=$suffix")
                } finally {
                    directory.deleteRecursivelyQuietly()
                }
            }
        }
    }

    @Test
    fun `B-LINUX-237 unauthenticated duplicate headers cannot abort the loopback receiver`() = runTest {
        val directory = tempDir("migration-header-")
        try {
            val content = ByteArray(1024)
            val transport = transport { script, _ ->
                val url = Regex("http://127\\.0\\.0\\.1:\\d+/archive").find(script)!!.value
                val token = Regex("Authorization: Bearer ([A-Za-z0-9_=-]+)'").find(script)!!.groupValues[1]
                val parsed = URI(url)
                val rejected = Socket(parsed.host, parsed.port).use { attack ->
                    attack.soTimeout = 2000
                    attack.getOutputStream().write(
                        (
                            "PUT /archive HTTP/1.1\r\nHost: localhost\r\n" +
                                "Authorization: invalid-one\r\nAuthorization: invalid-two\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n"
                            ).toByteArray(),
                    )
                    attack.getOutputStream().flush()
                    String(attack.getInputStream().readBytes())
                }
                assertTrue(rejected.startsWith("HTTP/1.1 403"), rejected)
                // The real upload still lands (sent chunked here, as an HTTP client may).
                assertEquals(204, put(url, token, 0, content, chunked = true))
                result("copied")
            }
            val destination = directory.resolve("migrations/$job/archive.tar")
            transport.copy(job, TermuxMigrationItem.projects, TermuxMigrationArchive(content.size.toLong(), sha(content)), destination) { false }
            assertTrue(Files.readAllBytes(destination).contentEquals(content))
        } finally {
            directory.deleteRecursivelyQuietly()
        }
    }

    @Test
    fun `B-LINUX-237 a cancel before the transfer dispatches nothing`() = runTest {
        val transport = transport { _, _ -> fail("No bridge call expected") }
        expectFailure(TermuxMigrationFailure.cancelled) {
            transport.copy(job, TermuxMigrationItem.projects, TermuxMigrationArchive(1024, "a".repeat(64)), Path.of("/unused")) { true }
        }
    }
}
