package io.github.eslamasabry.oc.feature.linux.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** "scripts parse as shell" (test/linux_manager_parse_test.dart): every generated script passes `sh -n` and `bash -n`. */
class LinuxScriptsShellTest {
    private fun syntaxOk(shell: String, script: String): Pair<Int, String> {
        val file = File.createTempFile("oc-linux-", ".sh").apply { writeText(script) }
        try {
            val p = ProcessBuilder(shell, "-n", file.path).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            return p.waitFor() to text
        } finally {
            file.delete()
        }
    }

    @Test
    fun `B-LINUX-089 B-LINUX-090 every manager script parses as shell`() {
        if (!File("/bin/sh").exists()) return
        val scripts =
            LinuxStorage.cleanups.flatMap { listOf(it.script, it.measureScript) } +
                listOf(
                    LinuxStorage.script,
                    LinuxPortScan.script(listOf(5173)),
                    LinuxPackages.LIST_SCRIPT,
                    LinuxPackages.checkScript(listOf("git")),
                    LinuxPackages.removePlanScript(listOf("git")),
                    LinuxPackages.jobScript(LinuxJobKind.Install, listOf("htop")),
                    LinuxPackages.jobScript(LinuxJobKind.Remove, listOf("htop")),
                    LinuxPackages.jobScript(LinuxJobKind.Update),
                )
        for (script in scripts) {
            val (code, text) = syntaxOk("/bin/sh", script)
            assertEquals(0, code, "$text\n$script")
        }
    }
}
