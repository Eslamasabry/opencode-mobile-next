package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneAgentCompileCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private fun launch(home: File): Pair<Int, String> {
        val helper = folder.newFile().apply {
            writeText("#!/bin/sh\nprintf '%s' \"${'$'}{NODE_COMPILE_CACHE:-unset}\"\n")
            setExecutable(true)
        }
        val script = hostScript(4099)
            .replace("id -u", "printf 1000")
            .replace("/home/oc/.local/bin/paseo", helper.absolutePath)
        val child = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).apply {
            environment()["HOME"] = home.absolutePath
            environment().remove("NODE_COMPILE_CACHE")
        }.start()
        child.outputStream.use { it.write("fixture\n".toByteArray()) }
        assertTrue(child.waitFor(10, TimeUnit.SECONDS))
        return child.exitValue() to child.inputStream.bufferedReader().readText()
    }

    @Test fun `B-AGENT-162 helper launch creates and inherits the profile home compile cache`() {
        val home = folder.newFolder("profile")
        val cache = File(home, ".cache/oc-ncc")
        assertEquals(0 to cache.absolutePath, launch(home))
        assertTrue(cache.isDirectory)
    }

    @Test fun `B-AGENT-162 an unavailable compile cache never blocks helper launch`() {
        val home = folder.newFolder("profile")
        File(home, ".cache").writeText("blocked")
        assertEquals(0 to "unset", launch(home))
    }
}
