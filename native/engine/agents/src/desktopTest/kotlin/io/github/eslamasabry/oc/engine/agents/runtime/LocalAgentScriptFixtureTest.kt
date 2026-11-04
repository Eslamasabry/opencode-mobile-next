package io.github.eslamasabry.oc.engine.agents.runtime

import com.sun.net.httpserver.HttpServer
import io.github.eslamasabry.oc.engine.agents.scripts.ShResult
import io.github.eslamasabry.oc.engine.agents.scripts.run
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private const val NODE_VERSION = "v24.0.0"

private val PROOT_STUB = """#!/bin/bash
# proot-distro stub: records its own arguments and runs the command natively.
printf 'proot-distro %s\n' "${'$'}*" >> "${'$'}OC_TEST_ROOT/proot-calls.log"
[ "${'$'}{1:-}" = login ] || exit 0
shift
workdir=''
while [ ${'$'}# -gt 0 ]; do
  case "${'$'}1" in
    --work-dir) workdir="${'$'}2"; shift 2 ;;
    --) shift; break ;;
    *) shift ;;
  esac
done
[ -f "${'$'}OC_TEST_ROOT/proot-broken" ] && exit 1
if [ -n "${'$'}workdir" ]; then mkdir -p "${'$'}workdir"; cd "${'$'}workdir"; fi
exec "${'$'}@"
"""

private val NODE_STUB = """#!/bin/bash
case "${'$'}{1:-}" in --version) echo "__VERSION__" ;; esac
exit 0
"""

private val NPM_STUB = """#!/bin/bash
# npm stub: `install -g … --prefix P … <spec>` drops a paseo or claude stub.
printf 'npm %s\n' "${'$'}*" >> "${'$'}OC_TEST_ROOT/npm-calls.log"
prefix=''
spec=''
while [ ${'$'}# -gt 0 ]; do
  case "${'$'}1" in
    --prefix) prefix="${'$'}2"; shift 2 ;;
    --cache) shift 2 ;;
    *) spec="${'$'}1"; shift ;;
  esac
done
if [ -f "${'$'}OC_TEST_ROOT/npm-fail" ]; then
  cat "${'$'}OC_TEST_ROOT/npm-fail"
  exit 1
fi
mkdir -p "${'$'}prefix/bin"
case "${'$'}spec" in
  @getpaseo/cli@*) cp "${'$'}OC_TEST_ROOT/paseo-stub" "${'$'}prefix/bin/paseo" ;;
  @anthropic-ai/claude-code@*) cp "${'$'}OC_TEST_ROOT/claude-stub" "${'$'}prefix/bin/claude" ;;
esac
echo "added 1 package"
"""

/** The fixture's paseo stub, minus the HTTP daemon: these rows never start the daemon. */
private val PASEO_STUB = """#!/bin/bash
if [ "${'$'}{1:-}" = --version ]; then echo '0.8.0'; exit 0; fi
printf '%s\n' "${'$'}*" >> "${'$'}OC_TEST_ROOT/paseo-args.log"
exit 1
"""

private val CLAUDE_STUB = """#!/bin/bash
case "${'$'}{1:-}" in
  --version) echo '2.1.278 (Claude Code)' ;;
  --help) printf 'Usage: claude\n\nCommands:\n  auth       Manage sign-in\n' ;;
esac
"""

private val WAKE_STUB = """#!/bin/bash
echo "${'$'}(basename "${'$'}0")" >> "${'$'}OC_TEST_ROOT/wake.log"
"""

/**
 * Port of the `_Fixture` of test/local_agent_runtime_test.dart: a temp HOME and PREFIX, a `proot-distro` stub that
 * runs the "inside Ubuntu" command natively, a served Node.js tarball whose node and npm are stubs.
 */
private class Fixture private constructor(val root: File, val server: HttpServer, val port: Int) {
    @Volatile var downloads = 0

    @Volatile var serveNotFound = false
    lateinit var tarball: ByteArray

    val home get() = "${root.path}/home"
    val prefix get() = "${root.path}/prefix"
    val stubs get() = "${root.path}/stubs"
    val ocDir get() = "$home/.oc"
    val claudeDir get() = "$ocDir/claude"
    val script get() = "$ocDir/claude.sh"
    val ubuntu get() = "${root.path}/ubuntu"
    val ubuntuHome get() = "$ubuntu/root"
    val nodeDir get() = "$ubuntu/opt/oc-node"
    val agentsDir get() = "$ubuntu/opt/oc-agents"
    val rootfs get() = "$prefix/var/lib/proot-distro/containers/opencode-ubuntu/rootfs"

    companion object {
        fun create(ubuntu: Boolean = true): Fixture {
            val shm = File("/dev/shm")
            val root = Files.createTempDirectory(if (shm.isDirectory) shm.toPath() else File(System.getProperty("java.io.tmpdir")).toPath(), "oc-claude-").toFile()
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val fx = Fixture(root, server, port)
            server.createContext("/") { exchange ->
                val expected = "/dist/$NODE_VERSION/node-$NODE_VERSION-linux-x64.tar.gz"
                if (fx.serveNotFound || exchange.requestURI.path != expected) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    fx.downloads++
                    exchange.sendResponseHeaders(200, fx.tarball.size.toLong())
                    exchange.responseBody.use { it.write(fx.tarball) }
                }
                exchange.close()
            }
            server.start()
            for (dir in listOf(fx.home, fx.stubs, fx.ocDir, fx.ubuntuHome)) File(dir).mkdirs()
            if (ubuntu) File(fx.rootfs).mkdirs()
            executable(fx.script, LocalAgentsScript.SCRIPT)
            executable("${fx.stubs}/proot-distro", PROOT_STUB)
            executable(
                "${fx.stubs}/df",
                "#!/bin/bash\necho \"Filesystem 1024-blocks Used Available Capacity Mounted on\"\necho \"/dev/x 99999999 1 90000000 1% /\"\n",
            )
            executable("${fx.stubs}/termux-wake-lock", WAKE_STUB)
            executable("${fx.stubs}/termux-wake-unlock", WAKE_STUB)
            executable("${root.path}/paseo-stub", PASEO_STUB)
            executable("${root.path}/claude-stub", CLAUDE_STUB)
            fx.tarball = fx.buildTarball()
            fx.writePins()
            return fx
        }

        fun executable(path: String, content: String) {
            File(path).also { it.parentFile.mkdirs() }.writeText(content)
            run(listOf("chmod", "755", path))
        }
    }

    private fun buildTarball(): ByteArray {
        val name = "node-$NODE_VERSION-linux-x64"
        val stage = "${root.path}/stage/$name"
        executable("$stage/bin/node", NODE_STUB.replace("__VERSION__", NODE_VERSION))
        executable("$stage/bin/npm", NPM_STUB)
        val archive = "${root.path}/$name.tar.gz"
        val result = run(listOf("tar", "-czf", archive, "-C", "${root.path}/stage", name))
        assertEquals(0, result.exitCode, result.stderr)
        return File(archive).readBytes()
    }

    fun writePins(sha: String? = null, claude: String? = null) {
        File("$ocDir/claude-pins").writeText(
            LocalAgentsScript.pinsFile(
                linkedMapOf(
                    "node_version" to NODE_VERSION,
                    "node_base_url" to "http://127.0.0.1:${server.address.port}/dist",
                    "node_sha256_arm64" to "0".repeat(64),
                    "node_sha256_x64" to (sha ?: sha256(tarball)),
                    "paseo_version" to "0.8.0",
                    "claude_version" to (claude ?: "2.1.278"),
                ),
            ),
        )
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    val environment: Map<String, String>
        get() = mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "PATH" to "$stubs:${System.getenv("PATH")}",
            "OC_TEST_ROOT" to root.path,
            "OC_CLAUDE_ROOTFS" to "",
            "OC_CLAUDE_NODE_DIR" to nodeDir,
            "OC_CLAUDE_AGENTS_DIR" to agentsDir,
            "OC_CLAUDE_UBUNTU_HOME" to ubuntuHome,
            // The stub runs commands as the host user, which therefore already exists; no `useradd` runs here.
            "OC_CLAUDE_USER" to (System.getenv("USER") ?: "root"),
            "OC_CLAUDE_PORT" to "$port",
            "OC_CLAUDE_ARCH" to "x86_64",
            "OC_CLAUDE_HEALTH_TIMEOUT" to "8",
            "OC_CLAUDE_POLL" to "0.1",
            "OC_CLAUDE_ALLOW_LOOPBACK_PINS" to "1",
        )

    fun verb(args: List<String>, env: Map<String, String> = emptyMap()): ShResult =
        run(listOf("bash", script) + args, environment + env, root, timeoutSeconds = 120)

    fun status(): LocalAgentStatus {
        val result = verb(listOf("status"))
        assertEquals(0, result.exitCode, result.stdout + result.stderr)
        return LocalAgentStatus.parse(result.stdout)
    }

    fun waitIdle(): LocalAgentStatus {
        repeat(400) {
            val current = status()
            if (!current.busy) return current
            Thread.sleep(100)
        }
        fail("a verb stayed busy")
    }

    fun runVerb(args: List<String>): LocalAgentStatus {
        verb(args)
        return waitIdle()
    }

    fun read(path: String): String = File(path).takeIf { it.exists() }?.readText() ?: ""

    val log get() = read("$claudeDir/install.log")

    fun dispose() {
        verb(listOf("stop"))
        server.stop(0)
        root.deleteRecursively()
    }
}

/** Port of the "behaviour against fixtures" group of test/local_agent_runtime_test.dart, for this slice's rows. */
class LocalAgentScriptFixtureTest {
    private var fx: Fixture? = null

    @AfterTest
    fun tearDown() {
        fx?.dispose()
    }

    @Test
    fun `B-AGENT-123 without Ubuntu status says so and install refuses`() {
        val fx = Fixture.create(ubuntu = false).also { fx = it }
        assertEquals(LocalAgentPhase.NeedsUbuntu, fx.status().phase)
        val status = fx.runVerb(listOf("install"))
        assertEquals(LocalAgentPhase.Failed, status.phase)
        assertEquals(LocalAgentFailureKind.NeedsUbuntu, status.failureKind)
        assertTrue("On this phone setup" in status.message, status.message)
        assertEquals(0, fx.downloads)
    }

    @Test
    fun `B-AGENT-123 install records verified Node and both packages and a second run downloads nothing`() {
        val fx = Fixture.create().also { fx = it }
        assertEquals(LocalAgentPhase.Absent, fx.status().phase)
        val status = fx.runVerb(listOf("install"))
        assertEquals(LocalAgentPhase.Installed, status.phase, fx.log)
        assertTrue(status.installed)
        assertEquals(NODE_VERSION, status.nodeVersion)
        assertEquals("0.8.0", status.paseoVersion)
        assertEquals("2.1.278", status.claudeVersion)
        assertEquals(NODE_VERSION, fx.read("${fx.nodeDir}/.oc-node-version"))
        assertFalse(File("${fx.claudeDir}/tmp").exists())
        val npm = fx.read("${fx.root.path}/npm-calls.log")
        assertTrue("--prefix ${fx.agentsDir}" in npm)
        assertTrue("@getpaseo/cli@0.8.0" in npm)
        assertTrue("@anthropic-ai/claude-code@2.1.278" in npm)
        assertEquals(1, fx.downloads)

        val again = fx.runVerb(listOf("install"))
        assertEquals(LocalAgentPhase.Installed, again.phase, fx.log)
        assertEquals(1, fx.downloads)
    }

    @Test
    fun `B-AGENT-121 sign-in status names nothing, and remove keeps the sign-in`() {
        val fx = Fixture.create().also { fx = it }
        fx.runVerb(listOf("install"))
        var result = fx.verb(listOf("signin-status"))
        assertEquals("signed_in=no\n", result.stdout)
        File("${fx.ubuntuHome}/.claude").mkdirs()
        File("${fx.ubuntuHome}/.claude/.credentials.json").writeText("{\"claudeAiOauth\":{\"accessToken\":\"sk-secret\"}}")
        result = fx.verb(listOf("signin-status"))
        assertEquals("signed_in=yes\n", result.stdout)
        assertEquals(LocalAgentSignIn.Yes, fx.status().signedIn)

        File("${fx.ubuntuHome}/projects/keep").mkdirs()
        var status = fx.runVerb(listOf("remove"))
        assertEquals(LocalAgentPhase.Absent, status.phase)
        assertFalse(File(fx.nodeDir).exists())
        assertFalse(File(fx.agentsDir).exists())
        assertFalse(File(fx.claudeDir).exists())
        assertTrue(File("${fx.ubuntuHome}/.claude/.credentials.json").exists())
        assertTrue(File("${fx.ubuntuHome}/projects/keep").isDirectory)

        status = fx.runVerb(listOf("remove", "--forget-signin"))
        assertEquals(LocalAgentPhase.Absent, status.phase)
        assertFalse(File("${fx.ubuntuHome}/.claude").exists())
        assertTrue(File("${fx.ubuntuHome}/projects/keep").isDirectory)
    }

    @Test
    fun `B-AGENT-126 projects - the first one is created with git init`() {
        val fx = Fixture.create().also { fx = it }
        var result = fx.verb(listOf("projects"))
        assertEquals("project=${fx.ubuntuHome}/projects/my-first-project\n", result.stdout)
        assertTrue(File("${fx.ubuntuHome}/projects/my-first-project/.git").isDirectory)
        result = fx.verb(listOf("ensure-project", "${fx.ubuntuHome}/projects/second"))
        assertEquals(0, result.exitCode)
        result = fx.verb(listOf("projects"))
        assertEquals(
            listOf("project=${fx.ubuntuHome}/projects/my-first-project", "project=${fx.ubuntuHome}/projects/second"),
            result.stdout.trim().split('\n'),
        )
        assertEquals(64, fx.verb(listOf("ensure-project", "../escape")).exitCode)
        assertEquals(64, fx.verb(listOf("ensure-project", "/root/../etc")).exitCode)
    }

    @Test
    fun `B-AGENT-122 the bridge dispatch writes this build's script and pins and a queued verb already reads as busy`() {
        val fx = Fixture.create().also { fx = it }
        File(fx.script).delete()
        val dispatch = run(listOf("bash", "-c", LocalAgentsScript.verbScript("status")), fx.environment, fx.root)
        assertEquals(0, dispatch.exitCode, dispatch.stderr)
        assertEquals(LocalAgentPhase.Absent, LocalAgentStatus.parse(dispatch.stdout).phase)
        assertEquals("${LocalAgentsScript.SCRIPT}\n", fx.read(fx.script))
        assertEquals(LocalAgentsScript.pinsFile(), fx.read("${fx.ocDir}/claude-pins"))

        val queued = fx.verb(listOf("queue", "install"))
        assertEquals("claude-queued:install\n", queued.stdout)
        val status = fx.status()
        assertEquals(LocalAgentPhase.Installing, status.phase)
        assertTrue(status.busy)
        assertEquals(64, fx.verb(listOf("queue", "signin")).exitCode)
    }

    @Test
    fun `B-AGENT-122 the ported claude sh equals the Flutter source byte for byte`() {
        val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "lib/domain/agent_catalog.dart").isFile }
        val dart = File(repo, "lib/termux/scripts/local_agents_script.dart").readText()
        val body = Regex("const termuxLocalAgentsScript = r'''(.*?)''';", RegexOption.DOT_MATCHES_ALL).find(dart)!!.groupValues[1]
        assertEquals(body, LocalAgentsScript.SCRIPT)
        for ((key, value) in LocalAgentsScript.pins) assertTrue("'$key': '$value'" in dart.replace(Regex(":\\s+'"), ": '"), key)
    }
}
