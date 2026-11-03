package io.github.eslamasabry.oc.engine.project.repair

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real script under a real `bash`, with a `curl` that only records what it was asked to upload. */
class TermuxScriptShellTest {
    @Test
    fun `B-SEC-091 the Termux script run by bash uploads nothing outside a store`() {
        if (!File("/bin/bash").canExecute()) return
        val root = Files.createTempDirectory("oc-git-repair-script").toFile()
        try {
            val bin = File(root, "bin").apply { mkdirs() }
            val log = File(root, "curl.log")
            File(bin, "curl").apply {
                writeText("#!/bin/sh\necho \"\$@\" >> ${log.absolutePath}\n")
                setExecutable(true)
            }
            val store = "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/opencode-ubuntu/rootfs/.l2s"
            val script = TermuxGitFetchScript.script(port = 1, token = "t", lines = listOf("0|/etc/passwd", "1|$store/.l2s.missing0001"))
            val process =
                ProcessBuilder("bash", "-c", script).redirectErrorStream(true).apply {
                    environment()["PATH"] = "${bin.absolutePath}:/usr/bin:/bin"
                }.start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            assertTrue("done" in out)
            assertFalse(log.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
