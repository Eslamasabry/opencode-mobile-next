package io.github.eslamasabry.oc.engine.runtime.components

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of the POSIX-shell parse checks in test/setup_component_removal_test.dart, plus real runs of the probes. */
class ComponentScriptSyntaxTest {
    private val shells = listOfNotNull("/bin/sh", shellOnPath("dash"))

    private fun parses(shell: String, script: String) = runShell(shell, script.let { "set -n\n$it" }).exitCode

    @Test
    fun `B-LINUX-188 registered presence scripts parse as POSIX shell`() {
        val scripts = RemovableComponents.builtin.mapNotNull { c -> c.presenceScript?.let { c.id to it } }
        assertTrue(scripts.size >= 5)
        for (shell in shells) {
            for ((id, script) in scripts) {
                val result = ProcessBuilder(shell, "-n", "-c", script).start()
                assertEquals(0, result.waitFor(), "$shell $id")
            }
        }
    }

    @Test
    fun `B-LINUX-188 registered size and remove scripts parse as POSIX shell`() {
        val scripts =
            RemovableComponents.builtin.flatMap { c ->
                listOfNotNull(c.sizeScript?.let { "${c.id} size" to it }, c.removeScript?.let { "${c.id} remove" to it })
            }
        assertEquals(4, scripts.size)
        for (shell in shells) {
            for ((id, script) in scripts) {
                assertEquals(0, parses(shell, script), "$shell $id")
            }
        }
    }

    @Test
    fun `B-LINUX-188 the AI Team size script counts kilobytes of what exists and nothing for missing paths`() {
        // The script names absolute phone paths; on this machine none of them exist, so it measures nothing (0),
        // which freedBytes turns into no figure.
        val result = runShell("/bin/sh", AiTeamRemovalScripts.SIZE_SCRIPT)
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals("0", result.stdout.trim().lines().last())
    }

    @Test
    fun `B-LINUX-184 the Python presence probe exits 2 (unknown) when dpkg's status cannot be read`() {
        // A machine without /var/lib/dpkg/status must answer unknown, not absent.
        if (File("/var/lib/dpkg/status").canRead()) {
            val fakeRoot = Files.createTempDirectory("oc-presence-").toFile()
            try {
                val script = ComponentRemovalScripts.PYTHON_PRESENCE.replace("/var/lib/dpkg/status", "${fakeRoot.path}/status")
                assertEquals(2, runShell("/bin/sh", script).exitCode)
            } finally {
                fakeRoot.deleteRecursively()
            }
        } else {
            assertEquals(2, runShell("/bin/sh", ComponentRemovalScripts.PYTHON_PRESENCE).exitCode)
        }
    }
}
