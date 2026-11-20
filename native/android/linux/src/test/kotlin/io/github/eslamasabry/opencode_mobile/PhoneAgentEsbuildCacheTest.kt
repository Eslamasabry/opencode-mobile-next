package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The helper's esbuild cache, seen through the launch script: what it exports, writes and removes, and that nothing in it stops a start. */
class PhoneAgentEsbuildCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val key = "0.16.1-d5317553e538"

    private class Launch(val code: Int, val options: String, val dir: String, val hookSeen: Boolean)

    private fun launch(home: File, esbuildKey: String?, nodeOptions: String? = null): Launch {
        val helper = folder.newFile().apply {
            writeText(
                "#!/bin/sh\n" +
                    "printf '%s\\n' \"${'$'}{NODE_OPTIONS:-unset}\" \"${'$'}{OC_ESBUILD_CACHE_DIR:-unset}\"\n" +
                    "h=${'$'}{OC_ESBUILD_CACHE_DIR:-/nonexistent}/../hook.cjs\n" +
                    "if [ -s \"${'$'}h\" ]; then echo hook; else echo nohook; fi\n",
            )
            setExecutable(true)
        }
        val script = hostScript(4099, esbuildKey)
            .replace("id -u", "printf 1000")
            .replace("/home/oc/.local/bin/paseo", helper.absolutePath)
        val child = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).apply {
            environment()["HOME"] = home.absolutePath
            environment().remove("NODE_COMPILE_CACHE")
            environment().remove("NODE_OPTIONS")
            if (nodeOptions != null) environment()["NODE_OPTIONS"] = nodeOptions
        }.start()
        child.outputStream.use { it.write("fixture\n".toByteArray()) }
        assertTrue(child.waitFor(10, TimeUnit.SECONDS))
        val lines = child.inputStream.bufferedReader().readText().lines()
        return Launch(child.exitValue(), lines[0], lines[1], lines[2] == "hook")
    }

    private fun root(home: File) = File(home, ".cache/oc-esbuild")

    @Test fun `B-AGENT-451 helper launch loads the esbuild-cache preload from the pin's folder`() {
        val home = folder.newFolder("profile")
        val run = launch(home, key)
        val hook = File(root(home), "$key/hook.cjs")
        assertEquals(0, run.code)
        assertEquals("--require \"${hook.absolutePath}\"", run.options)
        assertEquals(File(root(home), "$key/cache").absolutePath, run.dir)
        assertTrue(run.hookSeen)
        assertEquals(EsbuildCacheHook.SOURCE, hook.readText())
        assertTrue("the shipped hook must not be empty", EsbuildCacheHook.SOURCE.length > 1000)
    }

    @Test fun `B-AGENT-452 an old pin's esbuild cache folder is removed at launch and the current one stays`() {
        val home = folder.newFolder("profile")
        File(root(home), "0.9.2-aaaaaaaaaaaa/cache").mkdirs()
        File(root(home), "0.9.2-aaaaaaaaaaaa/cache/x").writeText("old")
        File(root(home), "$key/cache").mkdirs()
        File(root(home), "$key/cache/b-keep.json").writeText("{}")
        assertEquals(0, launch(home, key).code)
        assertFalse(File(root(home), "0.9.2-aaaaaaaaaaaa").exists())
        assertTrue(File(root(home), "$key/cache/b-keep.json").isFile)
    }

    @Test fun `B-AGENT-453 the preload is appended to an existing NODE_OPTIONS, never replacing it`() {
        val home = folder.newFolder("profile")
        val run = launch(home, key, nodeOptions = "--max-old-space-size=300")
        assertEquals(0, run.code)
        assertTrue(run.options, run.options.startsWith("--require \""))
        assertTrue(run.options, run.options.endsWith(" --max-old-space-size=300"))
    }

    @Test fun `B-AGENT-454 an unwritable cache folder never blocks helper launch`() {
        val home = folder.newFolder("profile")
        File(home, ".cache").mkdirs()
        File(home, ".cache/oc-esbuild").writeText("blocked")
        val run = launch(home, key, nodeOptions = "--foo")
        assertEquals(0, run.code)
        assertEquals("--foo", run.options)
        assertEquals("unset", run.dir)
    }

    @Test fun `B-AGENT-455 a hook that cannot be written starts the helper without the preload`() {
        val home = folder.newFolder("profile")
        File(root(home), "$key/hook.cjs.new").mkdirs()
        val run = launch(home, key)
        assertEquals(0, run.code)
        assertEquals("unset", run.options)
        assertEquals("unset", run.dir)
    }

    @Test fun `B-AGENT-456 no pin folder name means no preload and no cache folder`() {
        val home = folder.newFolder("profile")
        val run = launch(home, null)
        assertEquals(0, run.code)
        assertEquals("unset", run.options)
        assertFalse(root(home).exists())
    }

    @Test fun `B-AGENT-457 a half-written hook from an earlier launch is replaced whole by the shipped one`() {
        val home = folder.newFolder("profile")
        File(root(home), key).mkdirs()
        File(root(home), "$key/hook.cjs").writeText("'use str")
        assertEquals(0, launch(home, key).code)
        assertEquals(EsbuildCacheHook.SOURCE, File(root(home), "$key/hook.cjs").readText())
        assertFalse(File(root(home), "$key/hook.cjs.new").exists())
    }

    @Test fun `B-AGENT-458 an unsafe pin folder name is never put on a command line`() {
        val home = folder.newFolder("profile")
        val run = launch(home, "x'; touch ${home.absolutePath}/pwned; '")
        assertEquals(0, run.code)
        assertEquals("unset", run.options)
        assertFalse(File(home, "pwned").exists())
    }
}
