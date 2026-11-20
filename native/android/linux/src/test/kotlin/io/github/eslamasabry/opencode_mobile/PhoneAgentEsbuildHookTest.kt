package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The preload run for real by Node with a stand-in `esbuild` module that counts its calls. The repo has no JS test
 * runner, so Node itself runs the shipped bytes; a machine without Node skips these (the launch-script tests do not).
 */
class PhoneAgentEsbuildHookTest {
    @get:Rule val folder = TemporaryFolder()

    private val node: String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "node") }.firstOrNull { it.canExecute() }?.absolutePath

    private lateinit var work: File
    private lateinit var cache: File
    private lateinit var hook: File

    private fun setUp() {
        assumeTrue("node is not installed here", node != null)
        work = folder.newFolder("work")
        cache = File(work, "cache")
        hook = File(work, "hook.cjs").apply { writeText(EsbuildCacheHook.SOURCE) }
        File(work, "node_modules/esbuild").mkdirs()
        File(work, "node_modules/esbuild/index.js").writeText(
            "const fs=require('fs');const path=require('path');const c=path.join(__dirname,'..','..','calls.txt');\n" +
                "const n=()=>{fs.appendFileSync(c,'x');return fs.readFileSync(c,'utf8').length;};\n" +
                "exports.build=async(o)=>({errors:[],warnings:[],metafile:{n:1},\n" +
                "outputFiles:[{path:'/o.js',text:'T'+n()}]});\n" +
                "exports.transform=async(code)=>({code:'C'+n(),map:'',warnings:[]});\n" +
                "exports.version='stub';\n",
        )
        File(work, "plugins/one").mkdirs()
        File(work, "plugins/one/index.ts").writeText("export const a = 1")
        File(work, "driver.cjs").writeText(
            "const e=require('esbuild');(async()=>{\n" +
                "const b=await e.build({entryPoints:[process.argv[2]],write:false,bundle:true});\n" +
                "const t=await e.transform('const a = 1',{loader:'ts'});\n" +
                "console.log(JSON.stringify({b:b.outputFiles[0].text,t:t.code,v:e.version}));\n" +
                "})();\n",
        )
    }

    private fun run(
        env: Map<String, String> = emptyMap(),
        cacheDir: String? = cache.absolutePath,
        preload: Boolean = true,
    ): String {
        val entry = File(work, "plugins/one/index.ts").absolutePath
        val command = mutableListOf(node!!)
        if (preload) command += listOf("--require", hook.absolutePath)
        command += listOf(File(work, "driver.cjs").absolutePath, entry)
        val child = ProcessBuilder(command).directory(work).redirectErrorStream(true).apply {
            environment().remove("OC_ESBUILD_CACHE_DIR")
            if (cacheDir != null) environment()["OC_ESBUILD_CACHE_DIR"] = cacheDir
            environment()["OC_ESBUILD_CACHE_SALT"] = "pin-1"
            environment().putAll(env)
        }.start()
        assertTrue(child.waitFor(30, TimeUnit.SECONDS))
        val out = child.inputStream.bufferedReader().readText().trim()
        assertEquals(out, 0, child.exitValue())
        return out
    }

    private fun calls() = File(work, "calls.txt").takeIf { it.exists() }?.readText()?.length ?: 0

    @Test fun `B-AGENT-459 a second build and transform are served from the cache without calling esbuild`() {
        setUp()
        val first = run()
        assertEquals(2, calls())
        assertEquals(first, run())
        assertEquals("esbuild must not run again", 2, calls())
    }

    @Test fun `B-AGENT-460 a corrupt cache entry is ignored, deleted and rebuilt by the real esbuild`() {
        setUp()
        run()
        val entries = cache.listFiles()!!.filter { it.name.startsWith("b-") }
        entries.forEach { it.writeText("{\"text\":\"evil\",\"sum\":\"nope\"") }
        cache.listFiles()!!.filter { it.name.startsWith("t-") }
            .forEach { it.writeText("{\"code\":\"evil\",\"sum\":\"wrong\"}") }
        val out = run()
        assertFalse(out, out.contains("evil"))
        assertEquals(4, calls())
        assertTrue(cache.listFiles()!!.all { !it.readText().contains("evil") })
    }

    @Test fun `B-AGENT-461 a half-written cache entry is ignored and rebuilt`() {
        setUp()
        run()
        val entry = cache.listFiles()!!.first { it.name.startsWith("b-") }
        entry.writeText(entry.readText().take(10))
        run()
        assertEquals(3, calls())
    }

    @Test fun `B-AGENT-462 a cache that cannot be written still returns the real build`() {
        setUp()
        val blocked = File(work, "blocked").apply { writeText("a file, not a folder") }
        val out = run(cacheDir = File(blocked, "inside").absolutePath)
        assertTrue(out, out.contains("\"b\":\"T1\""))
        assertEquals(2, calls())
    }

    @Test fun `B-AGENT-463 a hook that fails inside still returns the real build`() {
        setUp()
        // The key step reads the plug-in folder; make it unreadable so that step throws.
        File(work, "plugins/one").setReadable(false)
        try {
            assumeTrue("a root user can still read it", !File(work, "plugins/one").canRead())
            val out = run()
            assertTrue(out, out.contains("\"b\":\"T1\""))
        } finally {
            File(work, "plugins/one").setReadable(true)
        }
    }

    @Test fun `B-AGENT-464 a missing esbuild module fails as it would without the hook`() {
        setUp()
        File(work, "node_modules/esbuild").deleteRecursively()
        val entry = File(work, "plugins/one/index.ts").absolutePath
        val plain = ProcessBuilder(node!!, File(work, "driver.cjs").absolutePath, entry).directory(work).start()
        val hooked = ProcessBuilder(node, "--require", hook.absolutePath, File(work, "driver.cjs").absolutePath, entry)
            .directory(work).apply { environment()["OC_ESBUILD_CACHE_DIR"] = cache.absolutePath }.start()
        assertTrue(plain.waitFor(30, TimeUnit.SECONDS) && hooked.waitFor(30, TimeUnit.SECONDS))
        assertEquals(plain.exitValue(), hooked.exitValue())
    }

    @Test fun `B-AGENT-465 a changed plug-in file or another pin misses the cache`() {
        setUp()
        run()
        run()
        assertEquals(2, calls())
        File(work, "plugins/one/index.ts").writeText("export const a = 2")
        run()
        assertEquals("only the build depends on the plug-in files", 3, calls())
        run(env = mapOf("OC_ESBUILD_CACHE_SALT" to "pin-2"))
        assertEquals(5, calls())
    }

    @Test fun `B-AGENT-466 the cache holds only esbuild output, never the environment`() {
        setUp()
        run(env = mapOf("OC_TEST_SECRET" to "hunter2-token", "PASEO_PASSWORD" to "hunter2-password"))
        val all = cache.listFiles()!!.joinToString("\n") { it.readText() }
        assertTrue(all.isNotEmpty())
        assertFalse(all.contains("hunter2"))
        assertTrue(cache.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `B-AGENT-467 without a cache folder the hook does nothing`() {
        setUp()
        run(cacheDir = null)
        run(cacheDir = null)
        assertEquals(4, calls())
        assertFalse(cache.exists())
    }
}
