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
 * The preload run for real by Node, with a stand-in `esbuild` module that counts its calls, laid out like the phone:
 * a pinned install folder holding the helper's scripts and its built-in plug-ins, and the hook's own folder beside
 * a file naming that install. The repo has no JS test runner, so Node runs the shipped bytes; a machine without Node
 * skips these (the launch-script tests do not).
 */
class PhoneAgentEsbuildHookTest {
    @get:Rule val folder = TemporaryFolder()

    private val node: String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "node") }.firstOrNull { it.canExecute() }?.absolutePath

    private lateinit var work: File
    private lateinit var pin: File
    private lateinit var scripts: File
    private lateinit var plugins: File
    private lateinit var home: File
    private lateinit var hook: File

    private fun setUp() {
        assumeTrue("node is not installed here", node != null)
        work = folder.newFolder("work")
        pin = File(work, "pin")
        scripts = File(pin, "node_modules/@getpaseo/server/dist/scripts").apply { mkdirs() }
        plugins = File(pin, "node_modules/@getpaseo/server/dist/server/builtin-plugins").apply { mkdirs() }
        File(plugins, "one").mkdirs()
        File(plugins, "one/index.server.ts").writeText("export const a = 1")
        File(pin, "node_modules/esbuild").mkdirs()
        File(pin, "node_modules/esbuild/index.js").writeText(ESBUILD_STUB)
        home = File(work, "oc-esbuild/0.16.1-key").apply { mkdirs() }
        File(home, "pin-root").writeText(pin.absolutePath)
        hook = File(home, "hook.cjs").apply { writeText(EsbuildCacheHook.SOURCE) }
        File(work, "project").mkdirs()
        File(work, "project/main.ts").writeText("export const p = 1")
        File(scripts, "build.cjs").writeText(BUILD_SCRIPT)
    }

    private val entry get() = File(plugins, "one/index.server.ts").absolutePath

    private fun run(
        script: File,
        env: Map<String, String> = emptyMap(),
        options: String? = "--require \"${hook.absolutePath}\"",
        args: List<String> = emptyList(),
    ): String {
        val command = listOf(node!!, script.absolutePath) + args
        val child = ProcessBuilder(command).directory(work).redirectErrorStream(true).apply {
            environment().keys.filter { it.startsWith("OC_") || it == "NODE_OPTIONS" }
                .forEach { environment().remove(it) }
            environment()["CALLS"] = File(work, "calls.txt").absolutePath
            environment()["ENTRY"] = entry
            environment()["HOOK"] = hook.absolutePath
            if (options != null) environment()["NODE_OPTIONS"] = options
            environment().putAll(env)
        }.start()
        assertTrue(child.waitFor(60, TimeUnit.SECONDS))
        val out = child.inputStream.bufferedReader().readText().trim()
        assertEquals(out, 0, child.exitValue())
        return out
    }

    private fun build(env: Map<String, String> = emptyMap()) = run(File(scripts, "build.cjs"), env)

    private fun builds() = File(work, "calls.txt").takeIf { it.exists() }?.readText()?.count { it == 'b' } ?: 0

    private fun cacheEntries() = File(home, "cache").listFiles().orEmpty().filter { it.name.startsWith("b-") }

    @Test fun `B-AGENT-459 a second build of a built-in plug-in is served from the cache without calling esbuild`() {
        setUp()
        val first = build()
        assertEquals(1, builds())
        assertEquals(first, build())
        assertEquals("esbuild must not build again", 1, builds())
    }

    @Test fun `B-AGENT-460 a corrupt cache entry is ignored, deleted and rebuilt by the real esbuild`() {
        setUp()
        build()
        val evil = "{\"text\":\"evil\",\"sum\":\"nope\",\"metafile\":{},\"path\":\"/o.js\"}"
        cacheEntries().forEach { it.writeText(evil) }
        val out = build()
        assertFalse(out, out.contains("evil"))
        assertEquals(2, builds())
        assertTrue(cacheEntries().all { !it.readText().contains("evil") })
    }

    @Test fun `B-AGENT-461 a half-written cache entry is ignored and rebuilt`() {
        setUp()
        build()
        val entry = cacheEntries().first()
        entry.writeText(entry.readText().take(10))
        build()
        assertEquals(2, builds())
    }

    @Test fun `B-AGENT-462 a cache that cannot be written still returns the real build`() {
        setUp()
        File(home, "cache").writeText("a file, not a folder")
        val out = build()
        assertTrue(out, out.contains("export const a = 1"))
        assertEquals(1, builds())
    }

    @Test fun `B-AGENT-463 a hook step that fails inside still returns the real build`() {
        setUp()
        // The key step reads the plug-ins folder; make it unreadable so that step throws.
        File(plugins, "one").setReadable(false)
        try {
            assumeTrue("a root user can still read it", !File(plugins, "one").canRead())
            val out = build()
            assertTrue(out, out.contains("export const a = 1"))
            assertEquals(1, builds())
        } finally {
            File(plugins, "one").setReadable(true)
        }
    }

    @Test fun `B-AGENT-464 a missing esbuild module fails as it would without the hook`() {
        setUp()
        File(pin, "node_modules/esbuild").deleteRecursively()
        fun exit(options: String?): Int {
            val child = ProcessBuilder(node!!, File(scripts, "build.cjs").absolutePath).directory(work).apply {
                environment().remove("NODE_OPTIONS")
                environment()["ENTRY"] = entry
                if (options != null) environment()["NODE_OPTIONS"] = options
            }.start()
            assertTrue(child.waitFor(60, TimeUnit.SECONDS))
            return child.exitValue()
        }
        assertEquals(exit(null), exit("--require \"${hook.absolutePath}\""))
    }

    @Test fun `B-AGENT-465 a changed plug-in file or another pin misses the cache`() {
        setUp()
        build()
        build()
        assertEquals(1, builds())
        File(plugins, "one/index.server.ts").writeText("export const a = 2")
        assertTrue(build().contains("a = 2"))
        assertEquals(2, builds())
        val other = File(work, "oc-esbuild/0.17.0-other").apply { mkdirs() }
        File(other, "pin-root").writeText(pin.absolutePath)
        File(other, "hook.cjs").writeText(EsbuildCacheHook.SOURCE)
        run(File(scripts, "build.cjs"), options = "--require \"${File(other, "hook.cjs").absolutePath}\"")
        assertEquals("another pin folder keeps its own cache", 3, builds())
    }

    @Test fun `B-AGENT-466 the cache holds only esbuild output, never the environment`() {
        setUp()
        build(mapOf("OC_TEST_SECRET" to "hunter2-token", "PASEO_PASSWORD" to "hunter2-password"))
        val all = File(home, "cache").listFiles()!!.joinToString("\n") { it.readText() }
        assertTrue(all.isNotEmpty())
        assertFalse(all.contains("hunter2"))
        assertTrue(File(home, "cache").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    private fun options(extra: String? = null) =
        "--require \"${hook.absolutePath}\"" + (extra?.let { " $it" } ?: "")

    @Test fun `B-AGENT-467 a process outside the pinned install gets nothing from the hook, even if it still has it`() {
        setUp()
        val outside = File(work, "project/build.cjs").apply { writeText(BUILD_SCRIPT) }
        pin.resolve("node_modules/esbuild").copyRecursively(File(work, "project/node_modules/esbuild"))
        val env = mapOf("ENTRY" to File(work, "project/main.ts").absolutePath)
        val out = run(outside, options = options("--max-old-space-size=300"), env = env)
        assertTrue(out, out.contains("export const p = 1"))
        run(outside, options = options("--max-old-space-size=300"), env = env)
        assertEquals("nothing is cached outside the install", 2, builds())
        assertTrue(File(home, "cache").listFiles().orEmpty().isEmpty())
    }

    @Test fun `B-AGENT-470 the hook takes itself out of NODE_OPTIONS and restores exactly what was there`() {
        setUp()
        val show = File(scripts, "show.cjs").apply { writeText(SHOW_SCRIPT) }
        val other = "--max-old-space-size=300"
        val cases = mapOf(
            options() to "null",
            options(other) to "\"$other\"",
            "$other ${options()}" to "\"$other\"",
            "--stack-trace-limit=5 ${options()} $other" to "\"--stack-trace-limit=5 $other\"",
        )
        for ((given, expected) in cases) {
            assertEquals(given, expected, run(show, options = given))
        }
    }

    @Test fun `B-AGENT-471 no agent child process gets the hook or any variable of ours`() {
        setUp()
        val agents = File(scripts, "agents.cjs").apply { writeText(AGENTS_SCRIPT) }
        val outside = File(work, "project/outside.cjs").apply { writeText(PROBE) }
        val out = run(
            agents,
            options = options("--max-old-space-size=300"),
            env = mapOf("PROBE" to PROBE, "OUTSIDE" to outside.absolutePath),
        )
        for (kind in listOf("spawn", "spawnEnv", "execFile", "exec", "shell", "outsideScript")) {
            assertTrue("$kind: $out", out.contains("\"$kind\":[false,\"--max-old-space-size=300\",[]]"))
        }
    }

    @Test fun `B-AGENT-472 the helper's own scripts and workers get the hook, agent children do not`() {
        setUp()
        File(scripts, "own.cjs").writeText(OWN_SCRIPT)
        File(scripts, "parent.cjs").writeText(PARENT_SCRIPT)
        File(scripts, "wk.cjs").writeText(WORKER_SCRIPT)
        val parent = File(scripts, "parent.cjs")
        val env = mapOf("PROBE" to PROBE)
        val first = run(parent, options = options("--max-old-space-size=300"), env = env)
        assertEquals(first, 3, Regex("\\[true,\"--max-old-space-size=300\"]").findAll(first).count())
        assertTrue(first, first.contains("grandchild [false,\"--max-old-space-size=300\",[]]"))
        assertTrue(first, first.contains("worker [false,true]"))
        assertEquals("the worker thread built once for real", 1, builds())
        run(parent, options = options(), env = env)
        assertEquals("the second worker run is served from the cache", 1, builds())
    }

    @Test fun `B-AGENT-473 an esbuild call whose entry is outside the built-in plug-ins is never cached`() {
        setUp()
        // The helper's own process builds things that are not its plug-ins: none of it may be cached.
        val own = File(scripts, "user-build.cjs").apply { writeText(BUILD_SCRIPT) }
        val project = File(work, "project/main.ts")
        assertTrue(run(own, mapOf("ENTRY" to project.absolutePath)).contains("export const p = 1"))
        project.writeText("export const p = 2")
        val fresh = run(own, mapOf("ENTRY" to project.absolutePath))
        assertTrue("a changed project file must give fresh output: $fresh", fresh.contains("export const p = 2"))
        assertEquals(2, builds())
        // A folder of the install that is not the built-in plug-ins is not cached either.
        val user = File(pin, "node_modules/@getpaseo/server/dist/user-plugins/two").apply { mkdirs() }
        File(user, "index.server.ts").writeText("export const u = 1")
        repeat(2) { run(own, mapOf("ENTRY" to File(user, "index.server.ts").absolutePath)) }
        assertEquals(4, builds())
        assertTrue(File(home, "cache").listFiles().orEmpty().isEmpty())
    }

    @Test fun `B-AGENT-474 a relative entry point or a working folder outside the plug-ins passes straight through`() {
        setUp()
        val own = File(scripts, "user-build.cjs").apply { writeText(BUILD_SCRIPT) }
        val relative = "{\"entryPoints\":[\"index.server.ts\"],\"absWorkingDir\":\"${plugins.absolutePath}/one\"}"
        val elsewhere = "{\"absWorkingDir\":\"${work.absolutePath}/project\"}"
        repeat(2) { run(own, mapOf("OPTS" to relative)) }
        repeat(2) { run(own, mapOf("OPTS" to elsewhere)) }
        assertEquals(4, builds())
        assertTrue(File(home, "cache").listFiles().orEmpty().isEmpty())
    }

    @Test fun `B-AGENT-475 a build that read a file outside the pinned install is not stored`() {
        setUp()
        build(mapOf("EXTRA_INPUT" to File(work, "project/main.ts").absolutePath))
        assertTrue(cacheEntries().isEmpty())
        build()
        assertEquals(1, cacheEntries().size)
    }

    @Test fun `B-AGENT-476 a build with its working folder inside the plug-ins is cached`() {
        setUp()
        val inside = "{\"absWorkingDir\":\"${plugins.absolutePath}\"}"
        repeat(2) { build(mapOf("OPTS" to inside)) }
        assertEquals(1, builds())
    }
}

private const val PROBE =
    """console.log(JSON.stringify([Object.keys(require.cache).some(k=>k.endsWith('hook.cjs')),""" +
        """process.env.NODE_OPTIONS||null,Object.keys(process.env).filter(k=>k.startsWith('OC_'))]))"""

private const val ESBUILD_STUB = """
const fs=require('fs');const c=process.env.CALLS;
exports.build=async(o)=>{fs.appendFileSync(c,'b');
const inputs={[o.entryPoints[0]]:{}};if(process.env.EXTRA_INPUT)inputs[process.env.EXTRA_INPUT]={};
let t='';try{t=fs.readFileSync(o.entryPoints[0],'utf8');}catch(e){}
return {errors:[],warnings:[],metafile:{inputs},outputFiles:[{path:'/o.js',text:'T'+t}]};};
exports.transform=async(c0)=>{fs.appendFileSync(c,'t');return {code:'C'+c0,warnings:[]};};
exports.version='stub';
"""

private const val BUILD_SCRIPT = """
const e=require('esbuild');(async()=>{
const o=Object.assign({entryPoints:[process.env.ENTRY],write:false,bundle:true},
process.env.OPTS?JSON.parse(process.env.OPTS):{});
const r=await e.build(o);console.log(r.outputFiles[0].text);
await e.transform('x',{});})();
"""

private const val SHOW_SCRIPT =
    "console.log(JSON.stringify(process.env.NODE_OPTIONS===undefined?null:process.env.NODE_OPTIONS))"

/** What an agent's child could be: every way a helper's agent starts a program, each printing what it sees. */
private const val AGENTS_SCRIPT = """
const cp=require('child_process');
const probe=process.env.PROBE;
const quoted='"'+process.execPath+'" -e "'+probe+'"';
const run={
 spawn:()=>cp.spawnSync(process.execPath,['-e',probe],{encoding:'utf8'}).stdout,
 spawnEnv:()=>cp.spawnSync(process.execPath,['-e',probe],{encoding:'utf8',env:Object.assign({},process.env)}).stdout,
 execFile:()=>cp.execFileSync(process.execPath,['-e',probe],{encoding:'utf8'}),
 exec:()=>cp.execSync(quoted,{encoding:'utf8'}),
 shell:()=>cp.spawnSync('/bin/sh',['-c',quoted],{encoding:'utf8'}).stdout,
 outsideScript:()=>cp.spawnSync(process.execPath,[process.env.OUTSIDE],{encoding:'utf8'}).stdout,
};
const out={};
for(const k of Object.keys(run)){try{out[k]=JSON.parse(run[k]());}catch(e){out[k]=String(e);}}
console.log(JSON.stringify(out));
"""

private const val OWN_SCRIPT = """
console.log(JSON.stringify([Object.keys(require.cache).some(k=>k.endsWith('hook.cjs')),process.env.NODE_OPTIONS||null]));
if(process.env.GRANDCHILD){const cp=require('child_process');
console.log('grandchild '+cp.spawnSync(process.execPath,['-e',process.env.PROBE],{encoding:'utf8'}).stdout.trim());}
"""

private const val PARENT_SCRIPT = """
const cp=require('child_process'),path=require('path');const own=path.join(__dirname,'own.cjs');
const o={stdio:'inherit',env:Object.assign({},process.env,{GRANDCHILD:'1'})};
cp.spawnSync(process.execPath,[own],o);cp.spawnSync(process.execPath,[own],{stdio:'inherit'});
const f=cp.fork(own,[],o);f.on('exit',()=>{const {Worker}=require('worker_threads');
new Worker(path.join(__dirname,'wk.cjs'));});
"""

private const val WORKER_SCRIPT = """
const {isMainThread}=require('worker_threads');
console.log('worker '+JSON.stringify([isMainThread,Object.keys(require.cache).some(k=>k.endsWith('hook.cjs'))]));
require('esbuild').build({entryPoints:[process.env.ENTRY],write:false})
.then(r=>console.log('worker built '+r.outputFiles[0].text));
"""
