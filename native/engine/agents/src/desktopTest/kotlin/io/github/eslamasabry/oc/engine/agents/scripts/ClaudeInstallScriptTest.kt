package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Port of test/claude_install_script_test.dart against [ClaudeScripts] and [SetupPrelude]. */
class ClaudeInstallScriptTest {
    private lateinit var temp: File

    @BeforeTest
    fun setUp() {
        temp = tempDir("claude-install-")
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun isolatedScript() = ClaudeScripts.install
        .replace("/opt/oc-claude", "${temp.path}/install")
        .replace("/usr/local/bin", "${temp.path}/bin")

    private fun binSh(script: String) = run(listOf("/bin/sh", "-c", script))

    @Test
    fun `B-LINUX-053 both ABIs request the exact pinned glibc payload`() {
        for ((uname, expected) in mapOf("aarch64" to ("arm64" to ClaudeScripts.ARM64_SHA256), "x86_64" to ("x64" to ClaudeScripts.X64_SHA256))) {
            val result = binSh(
                "${SetupPrelude.with("")}\n" +
                    "uname() { echo $uname; }\n" +
                    "getconf() { echo 'glibc 2.39'; }\n" +
                    "oc_stage() { :; }\n" +
                    "oc_download() { printf '%s\\n' \"\$1\" \"\$3\"; return 23; }\n" +
                    "${isolatedScript()}\n",
            )
            assertEquals(23, result.exitCode, result.stderr)
            assertTrue("/2.1.316/linux-${expected.first}/claude" in result.stdout, result.stdout)
            assertTrue(expected.second in result.stdout)
            assertFalse(File("${temp.path}/bin/claude").exists())
        }
    }

    @Test
    fun `B-LINUX-053 bad checksum never executes payload or replaces previous install`() {
        val previous = File("${temp.path}/install/claude")
        previous.parentFile.mkdirs()
        previous.writeText("previous")
        val result = binSh(
            "${SetupPrelude.with("")}\n" +
                "uname() { echo aarch64; }\n" +
                "getconf() { echo 'glibc 2.39'; }\n" +
                "# Exercise real oc_download checksum handling without any network request.\n" +
                "curl() {\n" +
                "  case \"\$1\" in\n" +
                "    -fsSIL) printf 'Content-Length: 1\\n' ;;\n" +
                "    *) printf '#!/bin/sh\\ntouch ${temp.path}/executed\\n' > \"\$oc_file\" ;;\n" +
                "  esac\n" +
                "}\n" +
                "${isolatedScript()}\n",
        )
        assertNotEquals(0, result.exitCode)
        assertTrue("did not match its checksum" in result.stderr, result.stderr)
        assertFalse(File("${temp.path}/executed").exists())
        assertEquals("previous", previous.readText())
        assertFalse(File("${temp.path}/install/claude.new").exists())
    }

    @Test
    fun `B-LINUX-053 unsupported architecture fails before downloading`() {
        val result = binSh("uname() { echo riscv64; }; ${isolatedScript()}")
        assertNotEquals(0, result.exitCode)
        assertTrue("requires ARM64 or x64 Ubuntu" in result.stderr)
        assertFalse(File("${temp.path}/install").exists())
    }

    private fun update(activeFails: Boolean) {
        val previous = File("${temp.path}/install/claude").also { it.parentFile.mkdirs(); it.writeText("previous") }
        File("${temp.path}/bin").mkdirs()
        link("${temp.path}/bin/claude", previous.path)
        val failing = if (activeFails) "case \"\$0\" in\n  *.new) ;;\n  *) exit 44 ;;\nesac" else ""
        val result = binSh(
            "${SetupPrelude.with("")}\n" +
                "uname() { echo aarch64; }\n" +
                "getconf() { echo 'glibc 2.39'; }\n" +
                "oc_download() {\n" +
                "  cat > \"\$2\" <<'CLAUDE_FIXTURE'\n" +
                "#!/bin/sh\n" +
                "$failing\n" +
                "echo '${ClaudeScripts.VERSION} (Claude Code)'\n" +
                "CLAUDE_FIXTURE\n" +
                "}\n" +
                "${isolatedScript()}\n",
        )
        val link = "${temp.path}/bin/claude"
        if (activeFails) {
            assertNotEquals(0, result.exitCode)
            assertTrue("Claude could not finish updating. Run setup again." in result.stderr, result.stderr)
            assertEquals("previous", previous.readText())
        } else {
            assertEquals(0, result.exitCode, result.stderr)
            assertEquals("previous", File("${previous.path}.oc-good").readText())
            assertEquals(previous.path, linkTarget("$link.oc-good"))
            assertFalse(exists("${previous.path}.oc-pending"))
            assertFalse(exists("$link.oc-pending"))
        }
        assertEquals(previous.path, linkTarget(link))
    }

    @Test
    fun `B-LINUX-053 successful Claude update retains the previous good generation`() = update(activeFails = false)

    @Test
    fun `B-LINUX-053 activated Claude failure restores program and command link`() = update(activeFails = true)

    @Test
    fun `B-LINUX-053 the ported installer and preludes equal the Flutter source byte for byte`() {
        assertEquals(dartRawStrings("lib/builtin/setup/setup_scripts.dart").getValue("setupPrelude"), SetupPrelude.SETUP_PRELUDE)
        assertEquals(
            dartRawStrings("lib/builtin/setup/component_updates.dart").getValue("componentUpdatePrelude"),
            SetupPrelude.COMPONENT_UPDATE_PRELUDE,
        )
        val dart = File(repoRoot, "lib/builtin/setup/claude_scripts.dart").readText()
        val body = Regex("static const install =\\s*'''\\n(.*?)''';", RegexOption.DOT_MATCHES_ALL).find(dart)!!.groupValues[1]
        val evaluated = body.replace("\\$", "\u0001").replace("\\\\", "\\")
            .replace("\$version", ClaudeScripts.VERSION)
            .replace("\$arm64Sha256", ClaudeScripts.ARM64_SHA256)
            .replace("\$x64Sha256", ClaudeScripts.X64_SHA256)
            .replace("\u0001", "$")
        assertEquals(evaluated, ClaudeScripts.install)
        assertTrue("static const version = '${ClaudeScripts.VERSION}'" in dart)
        assertTrue("'${ClaudeScripts.ARM64_SHA256}'" in dart && "'${ClaudeScripts.X64_SHA256}'" in dart)
    }
}
