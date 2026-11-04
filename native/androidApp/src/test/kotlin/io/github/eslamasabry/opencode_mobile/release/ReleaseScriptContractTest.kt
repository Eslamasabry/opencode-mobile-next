package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `test/release_script_contract_test.dart` (B-BUILD-029). The native APK has no release script of its own yet: every
 * release, the native same-package build included, goes through the repository's `scripts/release.sh` (and its
 * `release_github.sh` publication half). This runs the same two contract suites the Flutter test runs, then reads the
 * fail-closed rules out of the scripts so a later native release path cannot quietly drop them.
 */
class ReleaseScriptContractTest {
    private fun run(script: String): Pair<Int, String> {
        val process = ProcessBuilder("bash", script)
            .directory(RepoFiles.root)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        val finished = process.waitFor(2, TimeUnit.MINUTES)
        if (!finished) process.destroyForcibly()
        reader.join(5_000)
        return (if (finished) process.exitValue() else -1) to output.toString()
    }

    private val release by lazy { RepoFiles.rootText("scripts/release.sh") }
    private val github by lazy { RepoFiles.rootText("scripts/release_github.sh") }

    @Test
    fun `B-BUILD-029 the release script enforces the fail-closed publish contract`() {
        val (exit, output) = run("test/release_script_test.sh")
        assertEquals(0, exit, output.takeLast(4_000))
        assertTrue(output.contains("PASS: release script safety contract"), output.takeLast(2_000))
    }

    @Test
    fun `B-BUILD-029 GitHub publication verifies source, quality and signed draft assets`() {
        val (exit, output) = run("test/github_release_script_test.sh")
        assertEquals(0, exit, output.takeLast(4_000))
        assertTrue(output.contains("GitHub publication contract cases"), output.takeLast(2_000))
    }

    @Test
    fun `B-BUILD-029 publishing is opt-in, master-only, clean, synced and signer-checked`() {
        assertTrue(release.contains("set -euo pipefail"))
        assertTrue(release.contains("PUBLISH=false"), "the default never publishes")
        assertTrue(release.contains("if [[ \"\$2\" != \"--publish\" ]]; then"), "only the exact --publish flag publishes")
        assertTrue(release.contains("readonly RELEASE_BRANCH=\"master\""))
        assertTrue(release.contains("git status --porcelain=v1 --untracked-files=all"), "a dirty tree blocks")
        assertTrue(release.contains("Detached HEAD is not releasable."))
        assertTrue(release.contains("is not synchronized with"))
        assertTrue(release.contains("apksigner"), "the built APK's signer is verified")
        assertTrue(release.contains("must be a regular file, not a symbolic link."))
        assertTrue(release.contains("the keystore must live outside the repository."))
        assertTrue(release.contains("patch-plan is instructions only; --publish is unavailable."))
        // The public sideload lineage (AGENTS.md "Known signers") is pinned, never rotated.
        assertTrue(release.contains("842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"))
    }

    @Test
    fun `B-BUILD-029 GitHub publication is pinned to this repository, its tag and successful candidate runs`() {
        assertTrue(github.contains("OC_RELEASE_BUILD_RUN_ID") && github.contains("OC_RELEASE_QUALITY_RUN_ID"))
        assertTrue(github.contains("Refusing publication outside Eslamasabry/opencode-mobile-next"))
        assertTrue(github.contains("Local tag, remote tag and synchronized master must identify the same source."))
        assertTrue(github.contains("SHA256SUMS"), "draft assets are checked against their sums")
        assertTrue(github.contains("--draft=false --prerelease=false --latest"), "a verified draft is what gets published")
        assertTrue(File(RepoFiles.root, "test/release_script_test.sh").isFile)
        assertTrue(File(RepoFiles.root, "test/github_release_script_test.sh").isFile)
    }
}
