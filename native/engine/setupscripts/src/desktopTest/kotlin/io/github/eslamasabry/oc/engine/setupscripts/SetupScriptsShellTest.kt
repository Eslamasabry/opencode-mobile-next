package io.github.eslamasabry.oc.engine.setupscripts

import io.github.eslamasabry.oc.core.common.Outcome
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Port of test/setup_scripts_test.dart: the prelude run in real dash and bash, oc_download against a local HTTP server
 * (loopback only) that honours Range and sends slowly enough to watch, oc_apt_install against a fake apt-get, and the
 * combined checks. Needs python3, curl, dash, bash and sha256sum (all present on the build machines).
 */
class SetupScriptsShellTest {
    // Hermetic: every path the scripts touch is inside this fresh temp folder (checked below), apt-get and dpkg are
    // PATH-first stubs, and cleanup never follows a link (Kotlin's deleteRecursively does).
    private val dir: File =
        Files.createTempDirectory("oc-setup-").toFile().canonicalFile.also {
            val tmp = File(System.getProperty("java.io.tmpdir")).canonicalPath
            check(it.path != "/" && it.path.startsWith("$tmp/oc-setup-")) { "refusing to run outside a temp folder" }
        }
    private val servers = mutableListOf<Process>()

    @AfterTest
    fun cleanUp() {
        servers.forEach { it.destroyForcibly() }
        deleteWithoutFollowingLinks(dir.toPath())
    }

    private fun deleteWithoutFollowingLinks(root: java.nio.file.Path) {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(root).use { children -> children.forEach(::deleteWithoutFollowingLinks) }
        }
        Files.delete(root)
    }

    private class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(shell: String, args: List<String>, env: Map<String, String>?): Result {
        val builder = ProcessBuilder(listOf(shell) + args)
        env?.let { builder.environment().putAll(it) }
        val process = builder.start()
        val out = StringBuilder()
        val err = StringBuilder()
        val outReader = Thread { out.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
        val errReader = Thread { err.append(process.errorStream.bufferedReader().readText()) }.apply { start() }
        check(process.waitFor(120, TimeUnit.SECONDS)) { "shell timed out" }
        outReader.join()
        errReader.join()
        return Result(process.exitValue(), out.toString(), err.toString())
    }

    /** The prelude, then [script], under `set -eu`; every test but the progress ones polls fast. */
    private fun sh(script: String, shell: String = "dash", env: Map<String, String>? = null, slowPoll: Boolean = false) =
        run(shell, listOf("-c", "set -eu\n${withSetupPrelude(script)}"), env ?: if (slowPoll) null else mapOf("OC_POLL_SECONDS" to "0.05"))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- oc_download -------------------------------------------------------------------------------------------------

    private val www = File(dir, "www").apply { mkdirs() }
    private val blob = File(www, "blob.bin").apply { writeBytes(ByteArray(1_200_000) { ((it * 31 + 7) % 251).toByte() }) }
    private val sha = sha256(blob.readBytes())
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var port = 0

    private fun startServer(rate: String) {
        servers.forEach { it.destroyForcibly().waitFor() }
        servers.clear()
        requests.clear()
        val server = ProcessBuilder("python3", "-c", SERVER, www.path, rate).start()
        servers += server
        val lines = server.inputStream.bufferedReader()
        port = lines.readLine().trim().toInt()
        Thread { lines.lineSequence().forEach { requests += it } }.apply { isDaemon = true; start() }
    }

    private fun requestsSettled(expected: Int): List<String> {
        repeat(50) { if (requests.size >= expected) return requests.toList(); Thread.sleep(20) }
        return requests.toList()
    }

    private fun bytesLines(out: String): List<Pair<Long, Long>> =
        out.split('\n').filter { it.startsWith("::oc bytes ") }.map { it.split(' ').let { p -> p[2].toLong() to p[3].toLong() } }

    private fun url(name: String = "blob.bin") = "http://127.0.0.1:$port/$name"

    @Test
    fun `B-LINUX-212 dash and bash report real byte progress about twice a second and verify the checksum`() {
        for (shell in listOf("dash", "bash")) {
            startServer("1000000") // 1.2 MB at 1 MB/s: a little over a second
            val target = File(dir, "out-$shell/blob.bin")
            val result = sh("oc_download ${url()} ${target.path} $sha", shell = shell, slowPoll = true)
            assertEquals(0, result.exitCode, result.stderr)
            val bytes = bytesLines(result.stdout)
            assertTrue(bytes.size in 3..8, "$shell: $bytes")
            assertTrue(bytes.all { it.second == 1_200_000L })
            val done = bytes.map { it.first }
            assertEquals(done.sorted(), done)
            assertTrue(done.any { it in 1 until 1_200_000 })
            assertEquals(1_200_000L, done.last())
            assertEquals(1_200_000L, target.length())
        }
    }

    @Test
    fun `B-LINUX-212 a partial file resumes with a Range request`() {
        startServer("400000000")
        val target = File(dir, "out/blob.bin").apply { parentFile.mkdirs(); writeBytes(blob.readBytes().copyOfRange(0, 700_000)) }
        val result = sh("oc_download ${url()} ${target.path} $sha")
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals(listOf("GET 700000"), requestsSettled(1))
        assertTrue(bytesLines(result.stdout).first().first >= 700_000)
        assertEquals(sha, sha256(target.readBytes()))
    }

    @Test
    fun `B-LINUX-212 a truncated response is retried from the newest byte offset`() {
        startServer("1000000")
        File(www, "drop-once").writeText("")
        val target = File(dir, "out/blob.bin").apply { parentFile.mkdirs(); writeBytes(blob.readBytes().copyOfRange(0, 100_000)) }
        val result = sh("oc_download ${url()} ${target.path} $sha", env = mapOf("OC_POLL_SECONDS" to "0.02", "OC_RETRY_SECONDS" to "0"))
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals(listOf("GET 100000", "GET 150000"), requestsSettled(2))
        assertEquals(sha, sha256(target.readBytes()))
        val done = bytesLines(result.stdout).map { it.first }
        assertEquals(done.sorted(), done)
    }

    @Test
    fun `B-LINUX-212 transient HTTP errors retry without discarding the partial file`() {
        startServer("400000000")
        File(www, "http-once").writeText("")
        val target = File(dir, "blob.bin").apply { writeBytes(blob.readBytes().copyOfRange(0, 100_000)) }
        val result = sh("oc_download ${url()} ${target.path} $sha", env = mapOf("OC_POLL_SECONDS" to "0.02", "OC_RETRY_SECONDS" to "0"))
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals(listOf("GET 100000", "GET 100000"), requestsSettled(2))
        assertEquals(sha, sha256(target.readBytes()))
    }

    @Test
    fun `B-LINUX-212 a refused Range keeps the existing bytes for the next retry`() {
        startServer("400000000")
        File(www, "no-range").writeText("")
        val partial = blob.readBytes().copyOfRange(0, 100_000)
        val target = File(dir, "blob.bin").apply { writeBytes(partial) }
        val result = sh("oc_download ${url()} ${target.path} $sha")
        assertEquals(33, result.exitCode)
        assertEquals(listOf("GET 100000"), requestsSettled(1))
        assertTrue(partial.contentEquals(target.readBytes()))
    }

    @Test
    fun `B-LINUX-213 an intact file from an earlier run is not downloaded again`() {
        startServer("400000000")
        val target = File(dir, "blob.bin").apply { writeBytes(blob.readBytes()) }
        val result = sh("oc_download ${url()} ${target.path} $sha")
        assertEquals(0, result.exitCode)
        Thread.sleep(100)
        assertTrue(requests.isEmpty())
        assertEquals(listOf(1_200_000L to 1_200_000L), bytesLines(result.stdout))
    }

    @Test
    fun `B-LINUX-213 a checksum mismatch fails and deletes the file`() {
        startServer("400000000")
        val target = File(dir, "out/blob.bin")
        val result = sh("oc_download ${url()} ${target.path} ${"0".repeat(64)}\necho not-reached")
        assertNotEquals(0, result.exitCode)
        assertFalse("not-reached" in result.stdout)
        assertTrue("did not match its checksum" in result.stderr)
        assertFalse(target.exists())
    }

    @Test
    fun `B-LINUX-213 a whole-length file with the wrong bytes starts over`() {
        startServer("400000000")
        val target = File(dir, "blob.bin").apply { writeBytes(ByteArray(1_200_000)) }
        val result = sh("oc_download ${url()} ${target.path} $sha")
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals(listOf("GET -"), requestsSettled(1))
        assertEquals(sha, sha256(target.readBytes()))
    }

    @Test
    fun `B-LINUX-213 a failed download keeps the partial file for next time and names curl's code`() {
        startServer("400000000")
        val target = File(dir, "out/missing.bin")
        val result = sh("oc_download ${url("missing.bin")} ${target.path} ${"0".repeat(64)}")
        assertEquals(22, result.exitCode, "curl -f on a 404")
        assertTrue("Download failed (curl exit 22)" in result.stderr)
    }

    // --- oc_apt_install ----------------------------------------------------------------------------------------------

    private val bin = File(dir, "bin")
    private val lists = File(dir, "lists")
    private val aptLog = File(dir, "apt.log")
    private val stamp = File(dir, "stamp/apt-updated")

    private fun aptEnv(): Map<String, String> {
        bin.mkdirs()
        lists.mkdirs()
        File(bin, "apt-get").apply { writeText(FAKE_APT); setExecutable(true) }
        File(bin, "dpkg").apply { writeText(FAKE_DPKG); setExecutable(true) }
        return mapOf(
            "PATH" to "${bin.path}:${System.getenv("PATH")}",
            "APT_LOG" to aptLog.path,
            "OC_APT_STAMP" to stamp.path,
            "OC_APT_LISTS" to lists.path,
        )
    }

    private fun protocol(out: String) = out.split('\n').filter { it.startsWith("::oc ") }

    @Test
    fun `B-LINUX-214 apt status becomes stages and percents, lists refresh only when stale, dpkg is finished first`() {
        val env = aptEnv()
        val first = sh("oc_apt_install git curl", env = env)
        assertEquals(0, first.exitCode, first.stderr)
        assertEquals(
            listOf(
                "::oc stage Updating package lists", "::oc percent 40", "::oc percent 100",
                "::oc stage Downloading packages", "::oc percent 0", "::oc percent 33", "::oc percent 66",
                "::oc stage Installing packages", "::oc percent 0", "::oc percent 12", "::oc percent 87",
            ),
            protocol(first.stdout),
        )
        assertTrue("Setting up git" in first.stdout)
        val log = aptLog.readLines()
        assertEquals("dpkg --configure -a", log[0])
        assertEquals("dpkg --audit", log[1])
        assertTrue("update" in log[2])
        assertTrue("-o APT::Status-Fd=3 install -y" in log[3])
        assertTrue(log[3].endsWith("git curl"))
        assertEquals(listOf("dpkg -s git", "dpkg -s curl"), log.subList(4, log.size))
        val second = sh("oc_apt_install git", env = env)
        assertEquals(0, second.exitCode)
        assertEquals(1, aptLog.readLines().count { "update" in it })
    }

    @Test
    fun `B-LINUX-214 an install that fails on stale lists refreshes them once and retries`() {
        val env = aptEnv()
        stamp.parentFile.mkdirs()
        stamp.writeText("")
        File(lists, "x_Packages").writeText("")
        val result = sh("oc_apt_install git", env = env + ("FAIL_INSTALL_ONCE" to "1"))
        assertEquals(0, result.exitCode, result.stderr)
        assertTrue("Unable to locate package" in result.stdout)
        val log = aptLog.readLines()
        assertEquals(2, log.count { " install " in it })
        assertEquals(1, log.count { " update " in it })
    }

    @Test
    fun `B-LINUX-214 apt failing fails the script with apt's code`() {
        val env = aptEnv()
        File(bin, "apt-get").writeText("#!/bin/sh\necho \"E: broken\" >&2\nexit 100\n")
        val result = sh("oc_apt_install git\necho not-reached", env = env)
        assertEquals(100, result.exitCode)
        assertFalse("not-reached" in result.stdout)
    }

    // --- checks ------------------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-215 the combined check script runs each check alone and reads back versions and failures`() {
        val script =
            combinedCheckScript(
                linkedMapOf(
                    "good" to "echo noise\necho 1.2.3",
                    "bad" to "set -e\nfalse\necho unreachable",
                    "exits" to "exit 3",
                    "after" to "echo 9",
                ),
            )
        val parsed = parseCombinedChecks(run("dash", listOf("-c", script), null).stdout)
        assertEquals(
            mapOf(
                "good" to SetupCheckResult(true, "1.2.3"),
                "bad" to SetupCheckResult(false, null),
                "exits" to SetupCheckResult(false, null),
                "after" to SetupCheckResult(true, "9"),
            ),
            parsed,
        )
    }

    @Test
    fun `B-LINUX-215 the OpenCode check fails on another version or runtime`() {
        bin.mkdirs()
        File(bin, "opencode").apply { writeText("#!/bin/sh\necho 1.19.0\n"); setExecutable(true) }
        val two = File(bin, "opencode2").apply { writeText("#!/bin/sh\necho \"opencode2 v2.0.9\"\n"); setExecutable(true) }
        val env = mapOf("PATH" to "${bin.path}:/usr/bin:/bin")
        fun check(params: Map<String, String>): Result? =
            when (val script = OpenCodeChecks.openCodeCheck(params)) {
                is Outcome.Ok -> run("dash", listOf("-c", script.value), env)
                is Outcome.Failed -> null
            }
        assertNotEquals(0, check(emptyMap())!!.exitCode, "a fresh setup requires pinned OC2, not installed OC1")
        assertIs<Outcome.Failed>(OpenCodeChecks.openCodeCheck(mapOf("runtime" to "opencode1")))
        assertIs<Outcome.Failed>(OpenCodeChecks.openCodeCheck(mapOf("runtime" to "opencode3")))
        assertIs<Outcome.Failed>(OpenCodeChecks.openCodeCheck(mapOf("runtime" to "opencode2", "version" to "2.0; rm -rf /")))
        assertNotEquals(0, check(mapOf("version" to "1.18.30"))!!.exitCode)
        assertNotEquals(0, check(mapOf("runtime" to "opencode2"))!!.exitCode) // OC2 there, not at the pin: update due
        val pinned = check(mapOf("runtime" to "opencode2", "version" to "2.0.9"))!!
        assertEquals(0, pinned.exitCode)
        assertEquals("2.0.9", pinned.stdout.trim())
        two.delete()
        assertNotEquals(0, check(mapOf("runtime" to "opencode2", "version" to "2.0.9"))!!.exitCode)
    }

    @Test
    fun `B-LINUX-215 every setup script of this module parses in dash and bash`() {
        val scripts =
            mapOf(
                "prelude" to SETUP_PRELUDE,
                "prelude+updates" to withSetupPrelude("oc_stage 'x'\n"),
                "combined" to combinedCheckScript(mapOf("a" to "echo 1", "b" to "exit 2")),
                "opencode check" to (OpenCodeChecks.openCodeCheck(emptyMap()) as Outcome.Ok).value,
            )
        for ((name, script) in scripts) {
            for (shell in listOf("dash", "bash")) {
                val result = run(shell, listOf("-n", "-c", script), null)
                assertEquals(0, result.exitCode, "$name in $shell: ${result.stderr}")
            }
        }
    }

    @Test
    fun `B-LINUX-215 the prelude twins match the Flutter source byte for byte`() {
        var root: File? = File("").absoluteFile
        while (root != null && !File(root, "lib/builtin/setup/setup_scripts.dart").isFile) root = root.parentFile
        if (root == null) return // the Flutter tree is gone after the switch; the twin then stands alone
        fun raw(path: String, name: String): String {
            val text = File(root, path).readText()
            val start = text.indexOf("const $name = r'''") + "const $name = r'''".length
            return text.substring(start, text.indexOf("''';", start))
        }
        assertEquals(raw("lib/builtin/setup/setup_scripts.dart", "setupPrelude"), SETUP_PRELUDE)
        assertEquals(raw("lib/builtin/setup/component_updates.dart", "componentUpdatePrelude"), COMPONENT_UPDATE_PRELUDE)
    }

    private companion object {
        /** Serves one folder, honours `Range: bytes=N-` (206/416), logs each request's range, sends `rate` bytes a second. */
        val SERVER =
            """
import http.server, os, re, sys, time
dropped = False
root, rate = sys.argv[1], int(sys.argv[2])
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def path_of(self):
        p = os.path.join(root, self.path.lstrip('/'))
        return p if os.path.isfile(p) else None
    def do_HEAD(self):
        p = self.path_of()
        if not p: return self.send_error(404)
        self.send_response(200)
        self.send_header('Content-Length', str(os.path.getsize(p)))
        self.end_headers()
    def do_GET(self):
        global dropped
        p = self.path_of()
        if not p: return self.send_error(404)
        size, start = os.path.getsize(p), 0
        m = re.match(r'bytes=(\d+)-', self.headers.get('Range', ''))
        print('GET', m.group(1) if m else '-', flush=True)
        if os.path.isfile(os.path.join(root, 'http-once')) and not dropped:
            dropped = True
            return self.send_error(503)
        if os.path.isfile(os.path.join(root, 'no-range')): m = None
        if m:
            start = int(m.group(1))
            if start >= size:
                self.send_response(416)
                self.send_header('Content-Range', 'bytes */%d' % size)
                self.send_header('Content-Length', '0')
                return self.end_headers()
            self.send_response(206)
            self.send_header('Content-Range', 'bytes %d-%d/%d' % (start, size - 1, size))
        else:
            self.send_response(200)
        self.send_header('Content-Length', str(size - start))
        self.end_headers()
        with open(p, 'rb') as f:
            f.seek(start)
            while True:
                chunk = f.read(max(1, rate // 20))
                if not chunk: break
                try:
                    self.wfile.write(chunk); self.wfile.flush()
                except Exception:
                    return
                if os.path.isfile(os.path.join(root, 'drop-once')) and not dropped:
                    dropped = True
                    self.close_connection = True
                    return
                time.sleep(0.05)
s = http.server.ThreadingHTTPServer(('127.0.0.1', 0), H)
print(s.server_address[1], flush=True)
s.serve_forever()
"""

        val FAKE_APT =
            """#!/bin/sh
echo "apt-get ${'$'}*" >> "${'$'}APT_LOG"
case " ${'$'}* " in
  *" update "*)
    echo 'dlstatus:1:40.5:Retrieving file 1 of 2' >&3
    echo 'dlstatus:2:100:Retrieving file 2 of 2' >&3
    touch "${'$'}OC_APT_LISTS/archive_noble_main_binary-amd64_Packages" ;;
  *" install "*)
    if [ -n "${'$'}{FAIL_INSTALL_ONCE:-}" ] && [ ! -e "${'$'}APT_LOG.failed" ]; then
      touch "${'$'}APT_LOG.failed"
      echo 'E: Unable to locate package git' >&2
      exit 100
    fi
    echo 'Reading package lists...'
    echo 'dlstatus:1:0:Retrieving file 1 of 3' >&3
    echo 'dlstatus:2:33.3333:Retrieving file 2 of 3' >&3
    echo 'dlstatus:3:66.6667:Retrieving file 3 of 3' >&3
    echo 'pmstatus:dpkg-exec:0:Running dpkg' >&3
    echo 'pmstatus:libc6:amd64:12.5:Preparing libc6 (amd64)' >&3
    echo 'pmstatus:git:87.5:Installing git' >&3
    echo 'pmstatus:git:87.9:Installing git' >&3
    echo 'Setting up git (1:2.43.0-1ubuntu7) ...' ;;
esac
"""

        val FAKE_DPKG =
            """#!/bin/sh
echo "dpkg ${'$'}*" >> "${'$'}APT_LOG"
case "${'$'}1" in
  -s)
    echo "Package: ${'$'}2"
    echo 'Status: install ok installed' ;;
esac
"""
    }
}
