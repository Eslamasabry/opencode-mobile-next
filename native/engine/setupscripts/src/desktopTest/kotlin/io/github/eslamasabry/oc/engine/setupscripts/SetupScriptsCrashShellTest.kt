package io.github.eslamasabry.oc.engine.setupscripts

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of the Flutter shell tests for issue #117 (test/setup_scripts_test.dart, "when curl crashes with a signal" and
 * "oc_untar"): curl or tar under proot died with SIGSEGV (exit 139) on one ROM, so the prelude asks the app
 * (`::oc fetch`, `::oc unpack`) and the app answers through marker files next to the file. Real dash runs the real
 * prelude with a fake curl, wget or tar first on PATH; the test plays the app. Needs dash, tar, sh and sha256sum.
 */
class SetupScriptsCrashShellTest {
    private val dir: File =
        Files.createTempDirectory("oc-crash-").toFile().canonicalFile.also {
            val tmp = File(System.getProperty("java.io.tmpdir")).canonicalPath
            check(it.path.startsWith("$tmp/oc-crash-")) { "refusing to run outside a temp folder" }
        }
    private val bin = File(dir, "fakebin").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class Ran(val code: Int, val out: String, val err: String, val requests: List<String>)

    private fun fake(name: String, body: String) {
        File(bin, name).apply {
            writeText("#!/bin/sh\n$body\n")
            setExecutable(true)
        }
    }

    /**
     * Runs [script] after the prelude in dash. [app] plays the app: it gets each line that starts with `::oc [verb] `
     * and answers it; when null nothing answers.
     */
    private fun runDash(script: String, verb: String, env: Map<String, String>, app: ((List<String>) -> Unit)?): Ran {
        val builder = ProcessBuilder("dash", "-c", "set -eu\n${withSetupPrelude(script)}")
        builder.environment().putAll(
            mapOf(
                "PATH" to "${bin.path}:${System.getenv("PATH")}",
                "OC_POLL_SECONDS" to "0.05",
                "OC_RETRY_SECONDS" to "0",
            ) + env,
        )
        val process = builder.start()
        process.outputStream.close()
        val out = StringBuilder()
        val requests = mutableListOf<String>()
        val reader =
            Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    out.appendLine(line)
                    if (line.startsWith("::oc $verb ")) {
                        requests += line
                        app?.invoke(line.split(' '))
                    }
                }
            }.apply { start() }
        val err = StringBuilder()
        val errReader = Thread { err.append(process.errorStream.bufferedReader().readText()) }.apply { start() }
        check(process.waitFor(120, TimeUnit.SECONDS)) { "dash timed out" }
        reader.join()
        errReader.join()
        return Ran(process.exitValue(), out.toString(), err.toString(), requests)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- curl crashes -------------------------------------------------------------------------------------------------

    private val blob = ByteArray(300_000) { ((it * 31 + 7) % 251).toByte() }
    private val sha = sha256(blob)
    private val real = File(dir, "blob.bin").apply { writeBytes(blob) }
    private val url = "http://127.0.0.1:1/blob.bin"

    /** A curl that dies with SIGSEGV; with `--http1.1` it works (copies the blob), as on the ROM in the report. */
    private fun crashingCurl(http1Works: Boolean) = fake(
        "curl",
        (
            if (http1Works) {
                "out=; prev=\nfor a in \"\$@\"; do [ \"\$prev\" = -o ] && out=\$a; prev=\$a; done\n" +
                    "case \" \$* \" in *\" --http1.1 \"*) cp '${real.path}' \"\$out\"; printf 200; exit 0 ;; esac\n"
            } else ""
        ) + "exit 139",
    )

    /** The app's side of the fetch handshake with [bytes] as the downloaded file. */
    private fun appWriting(bytes: ByteArray): (List<String>) -> Unit = { request ->
        val dest = request[3]
        File("$dest.oc-fetch-ack").writeText("1")
        File(dest).writeBytes(bytes)
        File("$dest.oc-fetch-done").writeText("ok")
    }

    private fun download(target: File, env: Map<String, String> = emptyMap(), app: ((List<String>) -> Unit)? = null) =
        runDash("oc_download $url ${target.path} $sha", "fetch", env, app)

    @Test
    fun `B-LINUX-212 a crashing curl hands the download to the app and the checksum is still checked`() {
        crashingCurl(http1Works = false)
        val target = File(dir, "out/blob.bin")
        val ran = download(target, app = appWriting(blob))
        assertEquals(0, ran.code, ran.err)
        assertEquals(listOf("::oc fetch $url ${target.path} $sha"), ran.requests)
        assertTrue("The download tool crashed (signal 11)" in ran.out)
        assertEquals(sha, sha256(target.readBytes()))
        assertFalse(File("${target.path}.oc-fetch-done").exists())
    }

    @Test
    fun `B-LINUX-212 a wrong file from the app still fails the checksum`() {
        crashingCurl(http1Works = false)
        val target = File(dir, "out/blob.bin")
        val ran = download(target, app = appWriting(ByteArray(blob.size) { 1 }))
        assertEquals(1, ran.code)
        assertTrue("did not match its checksum" in ran.err)
        assertFalse(target.exists())
    }

    @Test
    fun `B-LINUX-212 an app that says it failed leads to curl http1 1`() {
        crashingCurl(http1Works = true)
        val target = File(dir, "out/blob.bin")
        val ran =
            download(target) { request ->
                File("${request[3]}.oc-fetch-ack").writeText("1")
                File("${request[3]}.oc-fetch-done").writeText("fail the connection failed")
            }
        assertEquals(0, ran.code, ran.err)
        assertTrue("The app could not download it" in ran.out)
        assertEquals(sha, sha256(target.readBytes()))
    }

    @Test
    fun `B-LINUX-212 no app at all - http1 1 takes over after the wait`() {
        crashingCurl(http1Works = true)
        val target = File(dir, "out/blob.bin")
        val ran = download(target, env = mapOf("OC_ACK_POLLS" to "3"))
        assertEquals(0, ran.code, ran.err)
        assertTrue("The app did not take over" in ran.out)
        assertEquals(sha, sha256(target.readBytes()))
    }

    @Test
    fun `B-LINUX-212 every way crashing says so in plain words`() {
        crashingCurl(http1Works = false)
        fake("wget", "exit 139")
        val ran = download(File(dir, "out/blob.bin"), env = mapOf("OC_ACK_POLLS" to "2"))
        assertEquals(139, ran.code)
        assertTrue("the download tool crashed (signal 11)" in ran.err)
    }

    // --- tar crashes --------------------------------------------------------------------------------------------------

    private val realTar: String =
        ProcessBuilder("sh", "-c", "command -v tar").start().inputStream.bufferedReader().readText().trim()

    private val archive: File by lazy {
        val src = File(dir, "src/pkg-1/bin").apply { mkdirs() }
        File(src, "tool").writeText("hello")
        File(dir, "src/pkg-1/other").writeText("x")
        File(dir, "pkg.tar.gz").also {
            ProcessBuilder(realTar, "-czf", it.path, "-C", File(dir, "src").path, "pkg-1").start().waitFor()
        }
    }

    private fun untar(script: String, tarBody: String, app: Boolean = true): Ran {
        fake("tar", tarBody)
        return runDash(script, "unpack", mapOf("OC_ACK_POLLS" to "4"), if (app) ::appUnpacking else null)
    }

    /** The app's side: unpack with the real tar, then answer through the marker files. */
    private fun appUnpacking(request: List<String>) {
        val (archivePath, dest, strip, member) = request.drop(2)
        File(dest).mkdirs()
        val extra = if (member == "-") emptyList() else listOf(member)
        val ok =
            ProcessBuilder(listOf(realTar, "-xzf", archivePath, "-C", dest, "--strip-components=$strip") + extra)
                .redirectErrorStream(true)
                .start()
                .let {
                    it.inputStream.readBytes()
                    it.waitFor() == 0
                }
        File("$archivePath.oc-unpack-ack").writeText("1")
        File("$archivePath.oc-unpack-done").writeText(if (ok) "ok" else "fail bad")
    }

    private fun dest(name: String) = File(dir, name).apply { mkdirs() }

    @Test
    fun `B-LINUX-212 a healthy tar is used and the app is not asked`() {
        val dest = dest("ok")
        val ran = untar("oc_untar ${archive.path} ${dest.path} 1", "exec $realTar \"\$@\"")
        assertEquals(0, ran.code, ran.err)
        assertTrue(ran.requests.isEmpty())
        assertEquals("hello", File(dest, "bin/tool").readText())
    }

    @Test
    fun `B-LINUX-212 a crashing tar hands the unpacking to the app`() {
        val dest = dest("crash")
        val ran = untar("oc_untar ${archive.path} ${dest.path} 1", "exit 139")
        assertEquals(0, ran.code, ran.err)
        assertEquals(listOf("::oc unpack ${archive.path} ${dest.path} 1 -"), ran.requests)
        assertTrue("The unpacking tool crashed (signal 11)" in ran.out)
        assertEquals("hello", File(dest, "bin/tool").readText())
        assertFalse(File("${archive.path}.oc-unpack-done").exists())
    }

    @Test
    fun `B-LINUX-212 a member is passed on to the app`() {
        val dest = dest("member")
        val ran = untar("oc_untar ${archive.path} ${dest.path} 2 pkg-1/bin/tool", "exit 139")
        assertEquals(0, ran.code, ran.err)
        assertEquals(listOf("::oc unpack ${archive.path} ${dest.path} 2 pkg-1/bin/tool"), ran.requests)
        assertEquals("hello", File(dest, "tool").readText())
        assertFalse(File(dest, "other").exists())
    }

    @Test
    fun `B-LINUX-212 no app - the failure keeps the tar crash status`() {
        val ran = untar("oc_untar ${archive.path} ${dest("noapp").path} 1", "exit 139", app = false)
        assertEquals(139, ran.code)
        assertTrue("the unpacking tool crashed" in ran.err)
    }

    @Test
    fun `B-LINUX-212 a plain tar failure is not handed to the app`() {
        val ran = untar("oc_untar ${archive.path} ${dest("plain").path} 1", "exit 2")
        assertEquals(2, ran.code)
        assertTrue(ran.requests.isEmpty())
    }

    @Test
    fun `B-LINUX-212 an app that fails ends the step with the crash status`() {
        val bad = File(dir, "bad.tar.gz").apply { writeText("not an archive") }
        val ran = untar("oc_untar ${bad.path} ${dest("badapp").path} 1", "exit 139")
        assertEquals(139, ran.code)
        assertTrue("The app could not unpack it" in ran.out)
    }
}
