package io.github.eslamasabry.oc.engine.runtime.setup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bundled setup scripts are valid shell (B-LINUX-043, amended: AI Team is deferred by D6). Port of
 * test/host_management_screen_test.dart:74 ("the ubuntu host script passes bash syntax validation"). Skips where bash
 * is not installed. The phone's own setup prelude and manager scripts are `internal` to :feature:linux and are parsed
 * there by LinuxScriptsShellTest; the on-phone agent script (Termux bridge) is not part of the native app.
 */
class BundledScriptsShellTest {
    private val bash: String? = listOf("/bin/bash", "/usr/bin/bash").firstOrNull { File(it).canExecute() }

    private val repo: File? =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "scripts/host").isDirectory && File(it, "native").isDirectory }

    private fun run(vararg command: String): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        return process.waitFor() to text
    }

    @Test
    fun `B-LINUX-043 the Ubuntu host script passes bash syntax validation`() {
        val shell = bash ?: return
        val script = repo?.let { File(it, "scripts/host/ubuntu-opencode.sh") }?.takeIf { it.isFile } ?: return
        val (code, text) = run(shell, "-n", script.path)
        assertEquals(0, code, text)
    }

    @Test
    fun `B-LINUX-043 the Ubuntu host script passes shellcheck when it is installed`() {
        val script = repo?.let { File(it, "scripts/host/ubuntu-opencode.sh") }?.takeIf { it.isFile } ?: return
        val shellcheck = listOf("/usr/bin/shellcheck", "/usr/local/bin/shellcheck", "/bin/shellcheck")
            .firstOrNull { File(it).canExecute() } ?: return
        val (code, text) = run(shellcheck, "-S", "error", script.path)
        assertTrue(code == 0, text)
    }
}
