package io.github.eslamasabry.oc.engine.termuxhost

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** test/host_script_pin_test.dart: the checkout, the guides and every shown command (read only, nothing runs). */
class HostScriptPinFilesTest {
    private val root = Shell.repoRoot

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `B-SEC-092 the checkout holds the pinned bytes of the front script`() {
        assertEquals(HostScripts.front.sha256, sha(File(root, HostScripts.front.path).readBytes()))
    }

    @Test
    fun `B-SEC-092 the checksum matches the pinned commit's bytes when git has that commit`() {
        val process =
            ProcessBuilder("git", "cat-file", "blob", "${HostScripts.COMMIT}:${HostScripts.front.path}")
                .directory(root)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        val bytes = process.inputStream.readBytes()
        if (process.waitFor() != 0) return // shallow clone without the pinned commit: Flutter skips too
        assertEquals(HostScripts.front.sha256, sha(bytes))
    }

    @Test
    fun `B-SEC-092 the guides show the same pinned, checked commands`() {
        val ubuntu = File(root, "docs/ubuntu-host.md").readText()
        assertTrue(ubuntu.contains(HostScripts.install(4096)))
        val team = File(root, "docs/ai-team-host.md").readText()
        assertTrue(team.contains(HostScripts.front.verifiedDownload))
        for (guide in listOf(ubuntu, team)) {
            assertFalse(guide.contains("/master/scripts/"))
            assertFalse(guide.contains("/master/tool/"))
        }
    }

    @Test
    fun `B-SEC-092 the repository script installs a pinned, checked OpenCode`() {
        val text = File(root, "scripts/host/ubuntu-opencode.sh").readText()
        assertFalse(HostScripts.PipedToShell.containsMatchIn(text))
        assertFalse(text.contains("opencode.ai/install"))
        assertFalse(text.contains("\" upgrade"))
        assertTrue(text.contains("readonly OPENCODE_VERSION=\"${TermuxRuntime.OpenCode2.pinnedVersion}\""))
        assertTrue(text.contains("https://registry.npmjs.org/@opencode"))
        for (arch in listOf("X64", "ARM64")) {
            assertTrue(Regex("readonly OPENCODE_${arch}_SHA256=\"[0-9a-f]{64}\"").containsMatchIn(text), arch)
        }
        val check = text.indexOf("if [[ \"\$actual\" != \"\$expected\" ]]; then")
        val unpack = text.indexOf("tar -xzf \"\$WORK_DIR/\$asset\"")
        assertTrue(check > 0)
        assertTrue(unpack > check)
    }

    @Test
    fun `B-SEC-092 nothing the app or its guides show pipes a download into a shell`() {
        val sources =
            buildList {
                File(root, "lib").walkTopDown().filter { it.isFile && it.name.endsWith(".dart") && "app_localizations" !in it.path }
                    .forEach(::add)
                add(File(root, "lib/l10n/app_en.arb"))
                File(root, "docs").listFiles()!!.filter { it.isFile && it.name.endsWith(".md") }.forEach(::add)
                for (dir in listOf("scripts", "tool/host")) {
                    File(root, dir).walkTopDown().filter { it.isFile && "__pycache__" !in it.path }.forEach(::add)
                }
                // The native app's own copy: every Kotlin source and string resource under native/.
                File(root, "native").walkTopDown()
                    .onEnter { it.name != "build" && it.name != ".gradle" }
                    .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".xml")) && "/test" !in it.path.substringAfter("native/") && "Test/" !in it.path }
                    .forEach(::add)
            }
        val offenders = mutableListOf<String>()
        for (file in sources) {
            file.readLines().forEachIndexed { i, line ->
                if (HostScripts.PipedToShell.containsMatchIn(line)) offenders += "${file.path}:${i + 1}: ${line.trim()}"
            }
        }
        assertEquals(emptyList(), offenders)
    }
}
