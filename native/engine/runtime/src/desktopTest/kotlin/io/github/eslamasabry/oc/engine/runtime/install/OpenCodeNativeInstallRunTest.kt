package io.github.eslamasabry.oc.engine.runtime.install

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Port of test/opencode_native_install_test.dart: the real native install script runs in dash against a local HTTP
 * server (127.0.0.1 only) with fake archives whose `opencode` is a small shell program; [root] keeps every path inside
 * a temp folder. The smoke against the real pinned download (B-LINUX-113) needs the network and is not run here.
 */
class OpenCodeNativeInstallRunTest {
    private lateinit var dir: File
    private lateinit var root: File
    private lateinit var www: File
    private lateinit var fakeBin: File
    private lateinit var server: Process
    private var port = 0
    private val ready = listOf("python3", "curl", "dash", "tar", "sha256sum").all(Sh::has)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("oc-native-").toFile()
        root = File(dir, "root").apply { mkdir() }
        www = File(dir, "www").apply { mkdir() }
        fakeBin = File(dir, "fakebin").apply { mkdir() }
        if (!ready) return
        server = ProcessBuilder("python3", "-c", SERVER, www.path).redirectErrorStream(true).start()
        port = server.inputStream.bufferedReader().readLine().trim().toInt()
    }

    @AfterTest
    fun tearDown() {
        if (ready) {
            server.destroy()
            server.waitFor()
        }
        dir.deleteRecursively()
    }

    private fun fakeUname(machine: String) {
        File(fakeBin, "uname").apply {
            writeText("#!/bin/sh\necho $machine\n")
            Sh.chmod("+x", this)
        }
    }

    /** An archive at www/[name] holding [member] = [program] (a README only when null); its SHA-256. */
    private fun archive(name: String, member: String, program: String?): String {
        val stage = File(dir, "stage-$name").apply { mkdir() }
        val names =
            if (program != null) {
                File(stage, member).apply {
                    parentFile.mkdirs()
                    writeText(program)
                    Sh.chmod("+x", this)
                }
                listOf(member)
            } else {
                File(stage, "README").writeText("nothing here\n")
                listOf("README")
            }
        val out = File(www, name)
        val tar = Sh.run(listOf("tar", "-czf", out.path, "-C", stage.path) + names, mapOf("PATH" to "/usr/bin:/bin"))
        assertEquals(0, tar.exitCode, tar.stderr)
        return Sh.sha256(out)
    }

    private fun asset(name: String, sha: String, member: String) = OpenCodeAsset("http://127.0.0.1:$port/$name", sha, member)

    private fun install(assets: OpenCodeAssets, machine: String = "aarch64", probeSeconds: Int = 150): ShRun {
        fakeUname(machine)
        val script = OpenCodeInstallScripts.nativeInstall(assets = assets, root = root.path, probeSeconds = probeSeconds)
        return Sh.run(
            listOf("dash", "-c", InstallPrelude.withPrelude(script)),
            mapOf(
                "PATH" to "${fakeBin.path}:/usr/bin:/bin",
                "TMPDIR" to dir.path,
                // The download progress poll (0.5 s on the phone), shortened here.
                "OC_POLL_SECONDS" to "0.05",
                "OC_PROBE_POLL" to "0.1",
            ),
            timeoutSeconds = 300,
        )
    }

    private fun check(): ShRun =
        Sh.run(
            listOf("dash", "-c", OpenCodeInstallScripts.nativeCheck(root = root.path)),
            mapOf("PATH" to "${root.path}/usr/local/bin:/usr/bin:/bin"),
        )

    private fun linkTarget(path: String): String = Files.readSymbolicLink(File(path).toPath()).toString()

    @Test
    fun `B-LINUX-111 ARM64 gets the arm64 archive - installed, proven, linked, the old npm wrapper replaced`() {
        if (!ready) return
        val sha = archive("arm.tgz", "opencode", fakeProgram("2.2.5"))
        // What an older build left: npm's wrapper, reporting the right version.
        val npm = File(root, "usr/local/lib/node_modules").apply { mkdirs() }
        val wrapper =
            File(npm, "opencode-ai/bin/opencode2").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\necho 2.2.5\n")
                Sh.chmod("+x", this)
            }
        File(npm, "opencode-linux-arm64").mkdir()
        File(root, "usr/local/bin").mkdirs()
        Files.createSymbolicLink(File(root, "usr/local/bin/opencode2").toPath(), wrapper.toPath())

        assertNotEquals(0, check().exitCode, "the npm wrapper is not the pinned program: Continue redoes it")

        val result =
            install(OpenCodeAssets(arm64 = asset("arm.tgz", sha, "opencode"), x64 = asset("missing-x64.tgz", "0".repeat(64), "opencode")))
        assertEquals(0, result.exitCode, result.stdout + result.stderr)
        assertTrue("::oc stage Checking that OpenCode starts" in result.stdout)
        assertTrue("::oc version 2.2.5" in result.stdout)
        assertEquals("${root.path}/opt/opencode2/bin/opencode2", linkTarget("${root.path}/usr/local/bin/opencode2"))
        assertTrue(File(npm, "opencode-ai").exists())
        assertTrue(File(npm, "opencode-linux-arm64").exists())
        assertFalse(File(root, "opt/opencode2.new").exists())
        assertTrue(File(root, "var/cache/oc-setup").listFiles()!!.isEmpty(), "the archive is deleted once installed")

        val after = check()
        assertEquals(0, after.exitCode, after.stderr)
        assertEquals("2.2.5", after.stdout.trim())
    }

    @Test
    fun `B-LINUX-111 x86_64 gets the x64 archive and the npm platform package's program becomes opencode2`() {
        if (!ready) return
        val sha = archive("x64.tgz", "package/bin/opencode2", fakeProgram("opencode v2.2.5"))
        val result =
            install(
                OpenCodeAssets(
                    arm64 = asset("missing-arm.tgz", "0".repeat(64), "package/bin/opencode2"),
                    x64 = asset("x64.tgz", sha, "package/bin/opencode2"),
                ),
                machine = "x86_64",
            )
        assertEquals(0, result.exitCode, result.stdout + result.stderr)
        assertTrue("::oc version 2.2.5" in result.stdout)
        assertTrue(File(root, "opt/opencode2/bin/opencode2").exists())
        assertFalse(File(root, "opt/opencode2/package").exists())
        val after = check()
        assertEquals(0, after.exitCode, after.stderr)
        assertEquals("2.2.5", after.stdout.trim())
    }

    @Test
    fun `B-LINUX-112 a checksum mismatch stops before anything is unpacked`() {
        if (!ready) return
        archive("arm.tgz", "opencode", fakeProgram("2.2.5"))
        val wrong = asset("arm.tgz", "f".repeat(64), "opencode")
        val result = install(OpenCodeAssets(arm64 = wrong, x64 = wrong))
        assertNotEquals(0, result.exitCode)
        assertTrue("did not match its checksum" in result.lastLine, result.lastLine)
        assertFalse(File(root, "opt").exists())
        assertFalse(Files.exists(File(root, "usr/local/bin/opencode2").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals(OpenCodeInstallReason.Checksum, OpenCodeInstallReason.of(result.lastLine))
    }

    @Test
    fun `B-LINUX-112 an unauthorized health response does not prove the candidate starts`() {
        if (!ready) return
        val previous = File(root, "opt/opencode2/bin/opencode2").apply { parentFile.mkdirs(); writeText("old") }
        val sha = archive("reject.tgz", "opencode", fakeProgram("2.2.5", acceptsAuth = false))
        val pin = asset("reject.tgz", sha, "opencode")
        val result = install(OpenCodeAssets(pin, pin), probeSeconds = 2)
        assertNotEquals(0, result.exitCode)
        assertEquals("old", previous.readText())
        assertEquals(OpenCodeInstallFailure.NoStart.line, result.lastLine)
    }

    @Test
    fun `B-LINUX-112 post-activation failure restores the previous native program and link`() {
        if (!ready) return
        val previous =
            File(root, "opt/opencode2/bin/opencode2").apply {
                parentFile.mkdirs()
                writeText(fakeProgram("2.2.5"))
                Sh.chmod("755", this)
            }
        File(root, "usr/local/bin").mkdirs()
        val link = File(root, "usr/local/bin/opencode2")
        Files.createSymbolicLink(link.toPath(), previous.toPath())
        val sha = archive("activation.tgz", "opencode", fakeProgram("2.2.5", activeFails = true))
        val pin = asset("activation.tgz", sha, "opencode")
        val result = install(OpenCodeAssets(pin, pin))
        assertNotEquals(0, result.exitCode)
        assertEquals("[oc] OpenCode could not finish updating. Run setup again.", result.lastLine)
        assertEquals(fakeProgram("2.2.5"), previous.readText())
        assertEquals(previous.path, linkTarget(link.path))
        assertEquals(0, check().exitCode)
        assertFalse(File(root, "opt/opencode2.oc-pending").exists())
        assertFalse(File("${link.path}.oc-pending").exists())
    }

    @Test
    fun `B-LINUX-112 a missing program, one that does not run and one that does not start keep the previous install`() {
        if (!ready) return
        val previous = File(root, "opt/opencode2/bin/opencode2").apply { parentFile.mkdirs(); writeText("old") }
        var n = 0
        fun run(program: String?): ShRun {
            val name = "a${n++}.tgz"
            val sha = archive(name, "opencode", program)
            val pin = asset(name, sha, "opencode")
            return install(OpenCodeAssets(pin, pin))
        }

        val empty = run(null)
        assertEquals(OpenCodeInstallFailure.NoProgram.line, empty.lastLine)

        val broken = run(fakeProgram("2.2.5", runs = false))
        assertEquals(OpenCodeInstallFailure.WontRun.line, broken.lastLine)
        assertTrue("Exec format error" in broken.stdout)

        val silent = run(fakeProgram("2.2.5", starts = false))
        assertNotEquals(0, silent.exitCode)
        assertTrue("database is locked" in silent.stdout + silent.stderr)
        assertEquals(OpenCodeInstallFailure.NoStart.line, silent.lastLine)

        assertEquals("old", previous.readText())
        assertFalse(File(root, "opt/opencode2.new").exists())
        assertEquals(OpenCodeInstallReason.NoProgram, OpenCodeInstallReason.of(empty.lastLine))
        assertEquals(OpenCodeInstallReason.WontRun, OpenCodeInstallReason.of(broken.lastLine))
        assertEquals(OpenCodeInstallReason.NoStart, OpenCodeInstallReason.of(silent.lastLine))
        for (result in listOf(empty, broken, silent)) assertTrue(result.lastLine.startsWith("[oc] "))
    }

    @Test
    fun `B-LINUX-113 the pinned smoke run's script is the production one - real download needs a real host`() {
        // test/opencode_native_install_test.dart's smoke is opt-in (OC_SMOKE_REAL_OPENCODE=1, ~150 MB from npm).
        // Here: the production script is exactly the pinned one the smoke runs, and dash parses it with the preludes.
        val production = OpenCodeInstallScripts.nativeInstall()
        assertEquals(OpenCodeInstallScripts.component(SetupHost.Builtin).installScript, production)
        assertTrue("oc_url='${OpenCodePins.v2X64.url}'; oc_sha=${OpenCodePins.v2X64.sha256}" in production)
        if (!Sh.has("dash")) return
        val parsed = Sh.run(listOf("dash", "-n", "-c", InstallPrelude.withPrelude(production)), mapOf("PATH" to "/usr/bin:/bin"))
        assertEquals(0, parsed.exitCode, parsed.stderr)
        val check = Sh.run(listOf("dash", "-n", "-c", OpenCodeInstallScripts.nativeCheck()), mapOf("PATH" to "/usr/bin:/bin"))
        assertEquals(0, check.exitCode, check.stderr)
    }

    private companion object {
        val SERVER = """
import http.server, os, sys
root = sys.argv[1]
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
        p = self.path_of()
        if not p: return self.send_error(404)
        self.send_response(200)
        self.send_header('Content-Length', str(os.path.getsize(p)))
        self.end_headers()
        with open(p, 'rb') as f: self.wfile.write(f.read())
s = http.server.ThreadingHTTPServer(('127.0.0.1', 0), H)
print(s.server_address[1], flush=True)
s.serve_forever()
""".trimStart()

        /** A fake `opencode`: prints [version]; `serve` answers the health path (or says why and exits). */
        fun fakeProgram(
            version: String,
            starts: Boolean = true,
            runs: Boolean = true,
            acceptsAuth: Boolean = true,
            activeFails: Boolean = false,
        ): String {
            val d = "$"
            val auth = if (acceptsAuth) "self.headers.get(\"Authorization\") is None" else "True"
            val serve =
                if (starts) {
                    """exec python3 -c '
import http.server, sys
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        self.send_response(401 if $auth else 200)
        self.end_headers()
http.server.HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
' "${d}5""""
                } else {
                    """echo "Error: Failed to start server: database is locked" >&2
    exit 1"""
                }
            val active =
                if (activeFails) {
                    """case "${d}0" in
      *.new/*) ;;
      *) exit 44 ;;
    esac"""
                } else {
                    ""
                }
            return """#!/bin/sh
${if (runs) "" else "echo \"cannot execute binary file: Exec format error\" >&2; exit 126"}
case "${d}1" in
  --version)
    $active
    echo '$version' ;;
  models) exit 0 ;;
  serve)
    $serve
    ;;
esac
"""
        }
    }
}
