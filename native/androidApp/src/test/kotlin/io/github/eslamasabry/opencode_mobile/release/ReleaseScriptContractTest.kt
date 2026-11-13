package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-BUILD-029: what `scripts/native-release.sh` promises. It is run for real inside a throw-away git repository (the
 * refusals all happen before any build), and its text is read for the parts a run cannot reach (the gates, the order of
 * tag and push, that nothing is ever published from it).
 */
class ReleaseScriptContractTest {
    private val script by lazy { RepoFiles.rootText("scripts/native-release.sh") }

    private class Outcome(val exit: Int, val output: String)

    private fun git(dir: File, vararg args: String) {
        val p = ProcessBuilder("git", "-c", "user.name=t", "-c", "user.email=t@example.invalid", *args)
            .directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(1, TimeUnit.MINUTES) && p.exitValue() == 0) { "git ${args.toList()} failed: $out" }
    }

    /** A repo shaped like the real one (script, version.properties, allocator stub), committed on `master`. */
    private fun repo(): File {
        val dir = Files.createTempDirectory("native-release-contract").toFile()
        File(dir, "scripts").mkdirs()
        File(dir, "native/tool").mkdirs()
        RepoFiles.root.resolve("scripts/native-release.sh").copyTo(File(dir, "scripts/native-release.sh"))
        File(dir, "native/version.properties").writeText("versionName=9.9.9\nversionCode=100\n")
        File(dir, "native/tool/version_code.sh").apply {
            writeText("#!/usr/bin/env bash\necho 110\n")
            setExecutable(true)
        }
        git(dir, "init", "-q", "-b", "master")
        git(dir, "add", "-A")
        git(dir, "commit", "-q", "-m", "base")
        return dir
    }

    private fun run(dir: File, vararg args: String): Outcome {
        val p = ProcessBuilder("bash", "scripts/native-release.sh", *args)
            .directory(dir).redirectErrorStream(true)
            .apply { environment()["OC_RELEASE_REMOTE"] = File(dir, "no-such-remote").path }
            .start()
        val out = StringBuilder()
        val reader = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }.apply { start() }
        val done = p.waitFor(2, TimeUnit.MINUTES)
        if (!done) p.destroyForcibly()
        reader.join(5_000)
        return Outcome(if (done) p.exitValue() else -1, out.toString())
    }

    private fun tags(dir: File): String {
        val p = ProcessBuilder("git", "tag").directory(dir).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }

    @Test
    fun `B-BUILD-029 a dirty tree, an untracked file included, is refused`() {
        val dir = repo()
        File(dir, "stray.txt").writeText("x")
        val r = run(dir, "--no-build")
        assertEquals(1, r.exit, r.output)
        assertTrue(r.output.contains("working tree is not clean"), r.output)
    }

    @Test
    fun `B-BUILD-029 a branch other than master is refused, and so is a detached head`() {
        val dir = repo()
        git(dir, "checkout", "-q", "-b", "feature")
        val onBranch = run(dir, "--no-build")
        assertEquals(1, onBranch.exit, onBranch.output)
        assertTrue(onBranch.output.contains("master only"), onBranch.output)
        git(dir, "checkout", "-q", "--detach")
        val detached = run(dir, "--no-build")
        assertEquals(1, detached.exit, detached.output)
        assertTrue(detached.output.contains("Detached HEAD"), detached.output)
    }

    @Test
    fun `B-BUILD-029 a clean master passes the checks and stops at the remote it cannot reach, never tagging`() {
        val dir = repo()
        val r = run(dir, "--no-build")
        assertEquals(2, r.exit, r.output)
        assertTrue(r.output.contains("cannot fetch"), r.output)
        assertEquals("", tags(dir).trim())
    }

    @Test
    fun `B-BUILD-029 go needs a build, a dirty tree stays refused with it, and nothing is tagged`() {
        val dir = repo()
        val noBuild = run(dir, "--go", "--no-build")
        assertEquals(1, noBuild.exit, noBuild.output)
        assertTrue(noBuild.output.contains("--go always builds and gates"), noBuild.output)
        File(dir, "stray.txt").writeText("x")
        val dirty = run(dir, "--go")
        assertEquals(1, dirty.exit, dirty.output)
        assertEquals("", tags(dir).trim())
    }

    @Test
    fun `B-BUILD-029 an unknown flag or lineage does nothing`() {
        val dir = repo()
        assertEquals(2, run(dir, "--publish").exit)
        assertEquals(2, run(dir, "--lineage", "other").exit)
    }

    @Test
    fun `B-BUILD-029 the dry run is the default and only the go flag tags, in the order gates then tag then push`() {
        assertTrue(script.contains("GO=false BUILD=true LINEAGE=public"), "the default is a dry run")
        assertTrue(script.contains("--go) GO=true; shift ;;"), "only the exact --go flag arms a release")
        val gates = listOf(
            ":androidApp:assembleSwitchRelease", ":androidApp:lintSwitchRelease", ":androidApp:testSwitchDebugUnitTest",
            ":quality:test -Poc.mergedManifest=required", "kept_classes_check.sh", "identity_gate.sh",
        )
        gates.forEach { assertTrue(script.contains(it), "the script must run $it") }
        val tag = script.indexOf("git tag -a")
        val push = script.indexOf("git push \"\$REMOTE\"")
        assertTrue(tag > script.indexOf("identity_gate.sh") && push > tag, "tag and push come after every gate")
        assertTrue(script.contains("set -euo pipefail"))
        assertTrue(script.contains("readonly RELEASE_BRANCH=\"master\""))
        assertTrue(script.contains("git status --porcelain=v1 --untracked-files=all"))
        assertTrue(script.contains("is not synchronized with"))
    }

    @Test
    fun `B-BUILD-029 the script never publishes, and pins both signing lineages`() {
        assertFalse(script.contains("gh release") || script.contains("gh api"), "a person publishes the draft by hand")
        assertTrue(script.contains("842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"))
        assertTrue(script.contains("1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"))
        assertTrue(script.contains("Key values are never read or printed"))
    }
}
