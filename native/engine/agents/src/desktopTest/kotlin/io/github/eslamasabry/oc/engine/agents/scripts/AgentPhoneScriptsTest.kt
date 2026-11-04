package io.github.eslamasabry.oc.engine.agents.scripts

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifactFormat
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentResumeReason
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun fixtureAgent(
    hash: String,
    version: String = "1.2.3",
    format: AgentArtifactFormat = AgentArtifactFormat.Executable,
    member: String? = null,
    id: String = "fixture",
) = AgentDescriptor(
    id = AgentId(id),
    name = "Fixture",
    iconKey = "fixture",
    route = AgentRoute.PaseoNative,
    providerId = "fixture",
    signInMethod = AgentSignInMethod.None,
    limitation = AgentLimitation.NeedsHostSetup,
    resumeReason = AgentResumeReason.ResumeUnverified,
    recipe = AgentInstallRecipe(
        version = version,
        executable = id,
        artifacts = mapOf(
            AgentArchitecture.X64 to AgentArtifact(
                url = "https://example.invalid/pinned",
                sha256 = hash,
                format = format,
                archiveMember = member,
            ),
        ),
    ),
)

private fun q(text: String) = "'" + text.replace("'", "'\\''") + "'"

/**
 * An isolated fake guest: rewrite the fixed home only in test input. No curl/network or root paths. This container
 * keeps python3 in /usr/local/bin, so the script's own `export PATH=…:/usr/bin:/bin` is widened by the same rewrite.
 */
private class PhoneGuest(val root: File) {
    val home get() = "${root.path}/home"
    val bin get() = "$home/.local/bin"
    val payload get() = "${root.path}/artifact"
    val ran get() = "${root.path}/executed"

    fun prepare(version: String = "1.2.3", architecture: String = "x86_64", uid: String = "1000") {
        mkdirs(bin)
        File("$bin/id").writeText("#!/bin/sh\nprintf \"%s\\n\" ${q(uid)}\n")
        File("$bin/uname").writeText("#!/bin/sh\nprintf \"%s\\n\" ${q(architecture)}\n")
        run(listOf("chmod", "700", "$bin/id", "$bin/uname"))
        File(payload).writeText("#!/bin/sh\nprintf x >> ${q(ran)}\nprintf \"%s\\n\" ${q(version)}\n")
    }

    val hash: String
        get() = MessageDigest.getInstance("SHA-256").digest(File(payload).readBytes()).joinToString("") { "%02x".format(it) }

    fun run(script: String, downloader: String? = null): ShResult = run(
        listOf(
            "bash",
            "-c",
            "${downloader ?: "oc_download() { cp ${q(payload)} \"\$2\"; }"}\n" +
                "oc_stage() { :; }\n" +
                "oc_version() { printf '::oc version %s\\n' \"\$1\"; }\n" +
                script.replace("/home/oc", home).replace("/usr/local/bin", "$home/usr-local-bin")
                    .replace("export PATH=$home/.local/bin:/usr/bin:/bin", "export PATH=$home/.local/bin:$TEST_PATH") +
                "\n",
        ),
        environment = mapOf("HOME" to home, "PATH" to "$bin:$TEST_PATH"),
        workingDirectory = root,
        timeoutSeconds = 120,
    )
}

/** Port of test/phone_agent_scripts_test.dart against [AgentPhoneScripts]. */
class AgentPhoneScriptsTest {
    private lateinit var temp: File
    private lateinit var guest: PhoneGuest

    @BeforeTest
    fun setUp() {
        temp = tempDir("oc-agent-scripts-")
        guest = PhoneGuest(temp)
        guest.prepare()
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private val agents get() = "${guest.home}/.local/share/oc-agents"

    private fun archive(python: String) {
        assertEquals(0, run(listOf("python3", "-c", python, guest.payload, guest.ran)).exitCode)
    }

    @Test
    fun `B-LINUX-119 the Terminal can run the agent by name, as the agent user`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        val result = guest.run(AgentPhoneScripts.install(agent))
        assertEquals(0, result.exitCode, result.stderr)
        val launcher = File("${guest.home}/usr-local-bin/claude")
        val text = launcher.readText()
        assertTrue(text.startsWith("#!/bin/sh\n# oc-terminal-launcher\n"))
        assertTrue("su -l oc" in text)
        assertTrue("cd \"\$0\"" in text)
        assertTrue("${guest.home}/.local/bin/claude" in text)
        val perms = Files.getPosixFilePermissions(launcher.toPath())
        assertTrue(
            perms.containsAll(
                listOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE),
            ),
            "executable",
        )
        assertEquals(0, sh(listOf("-n", launcher.path)).exitCode)
        // Installing again keeps working and a file we did not write is left be.
        launcher.writeText("#!/bin/sh\necho mine\n")
        val again = guest.run(AgentPhoneScripts.install(agent))
        assertEquals(0, again.exitCode, again.stderr)
        assertTrue("echo mine" in launcher.readText())
    }

    @Test
    fun `B-LINUX-119 interrupted agent download survives extraction cleanup and resumes`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        val size = File(guest.payload).length()
        val first = guest.run(
            AgentPhoneScripts.install(agent),
            downloader = "oc_download() { head -c 12 ${q(guest.payload)} > \"\$2\"; return 18; }",
        )
        assertEquals(18, first.exitCode)
        val cache = File("$agents/claude/.downloads")
        val artifact = cache.listFiles()!!.filter { it.isFile }.single()
        assertEquals(12L, artifact.length())
        val resumed = guest.run(
            AgentPhoneScripts.install(agent),
            downloader = "oc_download() { [ \"\$(stat -c %s \"\$2\")\" = 12 ] || return 99; tail -c +13 ${q(guest.payload)} >> \"\$2\"; }",
        )
        assertEquals(0, resumed.exitCode, resumed.stderr)
        assertEquals(size, File("$agents/claude/1.2.3/payload/claude").length())
        assertFalse(artifact.exists())
    }

    @Test
    fun `B-LINUX-120 check rejects changed executable even when its version and marker match`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        val payload = File("$agents/claude/1.2.3/payload/claude")
        payload.writeText("${payload.readText()}\n# altered\n")
        assertNotEquals(0, guest.run(AgentPhoneScripts.check(agent)).exitCode)
    }

    @Test
    fun `B-LINUX-122 rollback checks retained official payload and rebuilds its launcher`() {
        guest.prepare(version = "2.1.283")
        val oldHash = guest.hash
        val old = fixtureAgent(oldHash, id = "claude", version = "2.1.283")
        assertEquals(0, guest.run(AgentPhoneScripts.install(old)).exitCode)
        guest.prepare(version = "2.1.294")
        val current = fixtureAgent(guest.hash, id = "claude", version = "2.1.294")
        assertEquals(0, guest.run(AgentPhoneScripts.install(current)).exitCode)
        fun fixture(script: String) =
            script.replace(AgentPhoneScripts.previousClaudeRecipe.artifacts.getValue(AgentArchitecture.X64).sha256, oldHash)
        val prior = "$agents/claude/2.1.283"
        File("$prior/launch").writeText("#!/bin/sh\nexit 99\n")
        val available = guest.run(fixture(AgentPhoneScripts.previousVersion(current)))
        assertEquals(0, available.exitCode, available.stderr)
        assertEquals("2.1.283\n", available.stdout)
        val restored = guest.run(fixture(AgentPhoneScripts.usePreviousVersion(current, "2.1.283")))
        assertEquals(0, restored.exitCode, restored.stderr)
        assertEquals(0, guest.run(AgentPhoneScripts.check(old)).exitCode)
        assertTrue(File("$agents/claude/2.1.294").isDirectory)
        File("$prior/payload/claude").writeText("#!/bin/sh\necho 2.1.283\n")
        assertNotEquals(0, guest.run(fixture(AgentPhoneScripts.previousVersion(current))).exitCode)
        assertNotEquals(0, guest.run(fixture(AgentPhoneScripts.usePreviousVersion(current, "2.1.283"))).exitCode)
    }

    @Test
    fun `B-LINUX-121 agent cache and rollback refuse symlinked parent directories`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        val base = "$agents/claude"
        mkdirs(base)
        val outside = File("${temp.path}/outside").also { it.mkdirs() }
        val sentinel = File("${outside.path}/keep").also { it.writeText("private") }
        link("$base/.downloads", outside.path)
        assertNotEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        assertEquals("private", sentinel.readText())
        Files.delete(Paths.get("$base/.downloads"))
        Files.delete(Paths.get(base))
        link(base, outside.path)
        assertNotEquals(0, guest.run(AgentPhoneScripts.previousVersion(agent)).exitCode)
        assertNotEquals(0, guest.run(AgentPhoneScripts.usePreviousVersion(agent, "2.1.283")).exitCode)
        assertEquals("private", sentinel.readText())
    }

    @Test
    fun `B-LINUX-122 Claude check restores interrupted code before probing its link`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        val finalPath = "$agents/claude/1.2.3"
        assertTrue(File(finalPath).renameTo(File("$finalPath.oc-good")))
        mkdirs(finalPath)
        File("$finalPath/launch").writeText("#!/bin/sh\nexit 2\n")
        File("$finalPath.oc-pending").writeText("existing\n")
        val result = guest.run(AgentPhoneScripts.check(agent))
        assertEquals(0, result.exitCode, result.stderr)
        assertFalse(exists("$finalPath.oc-pending"))
        assertTrue(exists("$finalPath/payload/claude"))
    }

    @Test
    fun `B-LINUX-122 Claude check refuses another installer lock without changing code`() {
        val agent = fixtureAgent(guest.hash, id = "claude")
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        val lock = File("$agents/.lock-claude").also { it.mkdirs() }
        val sentinel = File("${lock.path}/owner").also { it.writeText("fixture") }
        val result = guest.run(AgentPhoneScripts.check(agent))
        assertNotEquals(0, result.exitCode)
        assertTrue("Another Claude install may still be running" in result.stderr, result.stderr)
        assertEquals("fixture", sentinel.readText())
    }

    @Test
    fun `B-LINUX-122 failed Claude active probe restores the prior pinned executable`() {
        val original = File(guest.payload).readText()
        val agent = fixtureAgent(guest.hash, id = "claude")
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        File(guest.payload).writeText("#!/bin/sh\ncase \"\$0\" in *\".new/\"*) printf \"1.2.3\\n\";; *) exit 2;; esac\n")
        val update = fixtureAgent(guest.hash, id = "claude")
        val result = guest.run(AgentPhoneScripts.install(update))
        assertNotEquals(0, result.exitCode)
        assertTrue("Claude could not finish updating. Run setup again." in result.stderr, result.stderr)
        val finalPath = "$agents/claude/1.2.3"
        assertEquals(original, File("$finalPath/payload/claude").readText())
        assertEquals(0, guest.run(AgentPhoneScripts.check(agent)).exitCode)
    }

    @Test
    fun `B-LINUX-121 every catalog recipe generates valid shell without executing downloads`() {
        for (agent in AgentCatalog.builtIn.agents) {
            val file = File("${temp.path}/script.sh")
            file.writeText(AgentPhoneScripts.install(agent))
            assertEquals(0, run(listOf("bash", "-n", file.path)).exitCode, agent.id.raw)
            file.writeText(AgentPhoneScripts.check(agent))
            assertEquals(0, run(listOf("bash", "-n", file.path)).exitCode, agent.id.raw)
        }
    }

    @Test
    fun `B-LINUX-123 oc bootstrap accepts PRoot identity mapping without requiring real chown`() {
        val result = guest.run(
            """
id() { case "${'$'}*" in '-u') printf '0\n';; '-u oc'|'-g oc') printf '1000\n';; 'oc') return 0;; *) return 1;; esac; }
getent() { case "${'$'}*" in 'group oc') printf 'oc:x:1000:\n';; 'passwd oc') printf 'oc:x:1000:1000::/home/oc:/bin/bash\n';; *) return 1;; esac; }
install() { return 0; }
stat() { printf '0\n'; }
""".removePrefix("\n") + AgentPhoneScripts.bootstrapUser,
        )
        assertEquals(0, result.exitCode, result.stderr)
    }

    @Test
    fun `B-LINUX-120 a checksum mismatch never executes the candidate or activates it`() {
        val result = guest.run(AgentPhoneScripts.install(fixtureAgent("0".repeat(64))))
        assertNotEquals(0, result.exitCode)
        assertFalse(exists(guest.ran))
        assertFalse(isLink("${guest.bin}/fixture"))
    }

    @Test
    fun `B-LINUX-120 version mismatch never activates and suppresses raw CLI output`() {
        guest.prepare(version = "synthetic-secret 1.2.30")
        val result = guest.run(AgentPhoneScripts.install(fixtureAgent(guest.hash)))
        assertNotEquals(0, result.exitCode)
        assertTrue(exists(guest.ran)) // Pin passed before the probe.
        assertFalse(isLink("${guest.bin}/fixture"))
        assertFalse("synthetic-secret" in result.stdout + result.stderr)
    }

    @Test
    fun `B-LINUX-123 valid executable installs atomically, checks, and repeats idempotently`() {
        val agent = fixtureAgent(guest.hash)
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        assertEquals("$agents/fixture/1.2.3/launch", linkTarget("${guest.bin}/fixture"))
        assertEquals(0, guest.run(AgentPhoneScripts.check(agent)).exitCode)
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        assertFalse(File("$agents/fixture/1.2.3.new").isDirectory)
        assertFalse(File("$agents/.lock-fixture").isDirectory)
    }

    @Test
    fun `B-LINUX-120 root execution and unknown architecture fail before download`() {
        guest.prepare(uid = "0")
        assertNotEquals(0, guest.run(AgentPhoneScripts.install(fixtureAgent(guest.hash))).exitCode)
        assertFalse(exists(guest.ran))
        guest.prepare(architecture = "armv7l")
        assertNotEquals(0, guest.run(AgentPhoneScripts.install(fixtureAgent(guest.hash))).exitCode)
        assertFalse(exists(guest.ran))
    }

    @Test
    fun `B-LINUX-122 concurrent installer lock does not damage held installation files`() {
        mkdirs("$agents/.lock-fixture")
        val held = File("$agents/held").also { it.writeText("keep") }
        assertNotEquals(0, guest.run(AgentPhoneScripts.install(fixtureAgent(guest.hash))).exitCode)
        assertEquals("keep", held.readText())
        assertFalse(exists(guest.ran))
    }

    @Test
    fun `B-LINUX-121 tar path traversal and archive links are refused before extraction execution`() {
        for (mode in listOf("traversal", "link")) {
            archive(
                "import io, sys, tarfile\n" +
                    "with tarfile.open(sys.argv[1], 'w:gz') as t:\n" +
                    " m=tarfile.TarInfo('../escape' if '$mode' == 'traversal' else 'fixture')\n" +
                    " if '$mode' == 'link':\n" +
                    "  m.type=tarfile.SYMTYPE; m.linkname=sys.argv[2]; t.addfile(m)\n" +
                    " else:\n" +
                    "  data=b'malicious'; m.size=len(data); t.addfile(m, io.BytesIO(data))\n",
            )
            val result = guest.run(
                AgentPhoneScripts.install(fixtureAgent(guest.hash, format = AgentArtifactFormat.TarGz, member = "fixture")),
            )
            assertNotEquals(0, result.exitCode, mode)
            assertFalse(exists(guest.ran))
            assertFalse(exists("$agents/fixture/escape"))
        }
    }

    @Test
    fun `B-LINUX-123 bundled archive retains sibling files and validates entry version`() {
        archive(
            """import io, sys, tarfile
with tarfile.open(sys.argv[1], 'w:bz2') as t:
 for name, data in [('bundle/data', b'1.2.3'), ('bundle/fixture', b'#!/bin/sh\ncat "${'$'}(dirname "${'$'}0")/data"\n')]:
  m=tarfile.TarInfo(name); m.size=len(data); m.mode=0o700; t.addfile(m, io.BytesIO(data))
""",
        )
        val agent = fixtureAgent(guest.hash, format = AgentArtifactFormat.TarBz2, member = "bundle/fixture")
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        assertEquals("1.2.3", File("$agents/fixture/1.2.3/payload/bundle/data").readText())
    }

    @Test
    fun `B-LINUX-123 npm bundle wrapper keeps siblings under the pinned private Node`() {
        val node = File("${guest.home}/.local/node/bin/node")
        node.parentFile.mkdirs()
        node.writeText("#!/bin/sh\nif [ \"\$1\" = --version ]; then echo v24.21.0; else exec /bin/sh \"\$@\"; fi\n")
        run(listOf("chmod", "700", node.path))
        archive(
            """import io, sys, tarfile
with tarfile.open(sys.argv[1], 'w:gz') as t:
 for name, data in [('package/bundle/data', b'1.2.3'), ('package/bundle/entry.js', b'#!/bin/sh\ncat "${'$'}(dirname "${'$'}0")/data"\n')]:
  m=tarfile.TarInfo(name); m.size=len(data); m.mode=0o700; t.addfile(m, io.BytesIO(data))
""",
        )
        val agent = fixtureAgent(guest.hash, format = AgentArtifactFormat.NpmTarGz, member = "package/bundle/entry.js")
        val installed = guest.run(AgentPhoneScripts.install(agent))
        assertEquals(0, installed.exitCode, installed.stderr)
        assertEquals(0, guest.run(AgentPhoneScripts.check(agent)).exitCode)
        assertTrue("${guest.home}/.local/node/bin/node" in File("$agents/fixture/1.2.3/launch").readText())
    }

    @Test
    fun `B-LINUX-120 check refuses an altered pin marker before executing the CLI`() {
        val agent = fixtureAgent(guest.hash)
        assertEquals(0, guest.run(AgentPhoneScripts.install(agent)).exitCode)
        File(guest.ran).delete()
        File("$agents/fixture/1.2.3/.oc-pin").writeText("not-the-pin")
        assertNotEquals(0, guest.run(AgentPhoneScripts.check(agent)).exitCode)
        assertFalse(exists(guest.ran))
    }

    @Test
    fun `B-LINUX-121 shell metacharacters in recipe version or archive member fail generation`() {
        assertFailsWith<IllegalArgumentException> { AgentPhoneScripts.install(fixtureAgent("0".repeat(64), version = "\$(id)")) }
        assertFailsWith<IllegalArgumentException> {
            AgentPhoneScripts.install(fixtureAgent("0".repeat(64), format = AgentArtifactFormat.TarGz, member = "package/\$(id)"))
        }
    }

    @Test
    fun `B-LINUX-121 zip symlink payload is refused`() {
        archive(
            "import stat, sys, zipfile\n" +
                "with zipfile.ZipFile(sys.argv[1], 'w') as z:\n" +
                " m=zipfile.ZipInfo('fixture'); m.create_system=3; m.external_attr=(stat.S_IFLNK | 0o777) << 16; z.writestr(m, sys.argv[2])\n",
        )
        val result = guest.run(
            AgentPhoneScripts.install(fixtureAgent(guest.hash, format = AgentArtifactFormat.Zip, member = "fixture")),
        )
        assertNotEquals(0, result.exitCode)
        assertFalse(exists(guest.ran))
    }

    @Test
    fun `B-LINUX-121 the ported raw script blocks equal the Flutter source byte for byte`() {
        val dart = dartRawStrings("lib/builtin/agents/agent_scripts.dart")
        assertEquals(dart.getValue("bootstrapUser"), AgentPhoneScripts.bootstrapUser)
        assertEquals(dart.getValue("_probe"), AgentPhoneScripts.PROBE)
        assertEquals(dart.getValue("_claudeUpdateProbe"), AgentPhoneScripts.CLAUDE_UPDATE_PROBE)
        assertEquals(dart.getValue("_terminalLauncher"), AgentPhoneScripts.TERMINAL_LAUNCHER)
        assertEquals(dart.getValue("_extract"), AgentPhoneScripts.EXTRACT_PY)
    }

    @Test
    fun `B-LINUX-121 every literal line of the Flutter templates appears in the generated scripts`() {
        val source = File(repoRoot, "lib/builtin/agents/agent_scripts.dart").readText()
        // Non-raw ''' sections, after the auth probe (not part of this port).
        val sections = mutableListOf<String>()
        var at = source.indexOf("static const bootstrapUser")
        while (true) {
            val open = source.indexOf("'''", at)
            if (open < 0) break
            val close = source.indexOf("'''", open + 3)
            if (source[open - 1] != 'r') sections += source.substring(open + 3, close)
            at = close + 3
        }
        val claude = AgentCatalog.builtIn.byId(AgentId("claude"))!!
        val codex = AgentCatalog.builtIn.byId(AgentId("codex"))!!
        val generated = listOf(
            AgentPhoneScripts.install(claude), AgentPhoneScripts.install(codex),
            AgentPhoneScripts.check(claude), AgentPhoneScripts.check(codex),
            AgentPhoneScripts.usePreviousVersion(claude, "2.1.283"),
        ).joinToString("\n")
        val lines = sections.flatMap { it.split('\n') }
            .map { it.replace("\\$", "\u0001").replace("\\\\", "\\").replace("\\'", "'") }
            .filter { it.isNotBlank() && '$' !in it && "''}" !in it } // `''' : ''}` closes a Dart ternary
            .map { it.replace('\u0001', '$') }
        assertTrue(lines.size > 80, "${lines.size}")
        for (line in lines) assertTrue(line in generated, line)
    }
}
