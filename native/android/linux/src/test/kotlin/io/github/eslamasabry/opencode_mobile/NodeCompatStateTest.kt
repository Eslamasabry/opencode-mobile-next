package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server proot follows the Node install's saved safe mode (#117/#119). */
class NodeCompatStateTest {
    @Test fun aSecondProotLineAsksForCompatibilityMode() {
        assertTrue(NodeCompatState.prootCompat("none\nproot-compat\n"))
        assertTrue(NodeCompatState.prootCompat("both\r\nproot-compat"))
        assertFalse(NodeCompatState.prootCompat("armcap\n"))
        assertFalse(NodeCompatState.prootCompat("proot-compat\n"))
        assertFalse(NodeCompatState.prootCompat(""))
        assertFalse(NodeCompatState.prootCompat(null))
    }

    @Test fun theStateFileIsReadOnlyWhenItIsAPlainSmallFile() {
        val rootfs = Files.createTempDirectory("oc-rootfs").toFile()
        try {
            assertFalse(NodeCompatState.prootCompat(rootfs))
            val state = File(rootfs, NodeCompatState.STATE_PATH).apply { parentFile.mkdirs() }
            state.writeText("single\nproot-compat\n")
            assertTrue(NodeCompatState.prootCompat(rootfs))
            state.writeText("single\n")
            assertFalse(NodeCompatState.prootCompat(rootfs))
            state.writeText("single\nproot-compat\n" + "x".repeat(512))
            assertFalse(NodeCompatState.prootCompat(rootfs))
            val elsewhere = File(rootfs, "elsewhere").apply { writeText("none\nproot-compat\n") }
            state.delete()
            Files.createSymbolicLink(state.toPath(), elsewhere.toPath())
            assertFalse(NodeCompatState.prootCompat(rootfs))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test fun theServerExportsOnlyTheSavedVariables() {
        val dir = Files.createTempDirectory("oc-node-env").toFile()
        try {
            val env = File(dir, "node-compat.env").apply {
                writeText("OPENSSL_armcap=0\nUV_THREADPOOL_SIZE=1\nLD_PRELOAD=/x.so\n")
            }
            val script = "set -eu\n" +
                NodeCompatState.SERVER_ENV.replace("/etc/oc-setup/node-compat.env", env.absolutePath) +
                "echo \"\${OPENSSL_armcap:-}|\${UV_THREADPOOL_SIZE:-}|\${LD_PRELOAD:-}\"\n"
            val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            assertEquals("0|1|\n", output)
        } finally {
            dir.deleteRecursively()
        }
    }
}
