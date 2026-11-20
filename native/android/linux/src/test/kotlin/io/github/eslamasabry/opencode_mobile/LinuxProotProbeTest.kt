package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One bounded liveness probe through proot picks compatibility mode on a hang (issue #151).
 */
class LinuxProotProbeTest {
    private class FakeProbeRunner(
        private val rootfs: File,
        private val onAttempt: (LinuxProotProbe.Mode, Long, (Int) -> Unit) -> LinuxProotProbe.Attempt,
    ) : LinuxProotProbe.Runner {
        val modesTried = mutableListOf<LinuxProotProbe.Mode>()
        val timeouts = mutableListOf<Long>()

        override fun hasCompatMarker(): Boolean = NodeCompatState.prootCompat(rootfs)

        override fun writeCompatMarker() {
            NodeCompatState.writeProotCompat(rootfs)
        }

        override fun clearCompatMarker() {
            NodeCompatState.clearProotCompat(rootfs)
        }

        override fun runOnce(
            mode: LinuxProotProbe.Mode,
            timeoutMs: Long,
            killPid: (Int) -> Unit,
        ): LinuxProotProbe.Attempt {
            modesTried += mode
            timeouts += timeoutMs
            return onAttempt(mode, timeoutMs, killPid)
        }
    }

    private fun withRootfs(block: (File) -> Unit) {
        val rootfs = Files.createTempDirectory("oc-probe-rootfs").toFile()
        try {
            block(rootfs)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `B-SETUP-001 probe answers at once in normal mode without writing compat marker`() = withRootfs { rootfs ->
        val killed = mutableListOf<Int>()
        val stages = mutableListOf<Boolean>()
        val runner = FakeProbeRunner(rootfs) { _, _, _ -> LinuxProotProbe.Attempt.Answered }

        val result = LinuxProotProbe.run(
            runner = runner,
            onStage = { stages += it },
            killPid = { killed += it },
        )

        assertTrue("expected Ready, got $result", result is LinuxProotProbe.Result.Ready)
        val ready = result as LinuxProotProbe.Result.Ready
        assertEquals(LinuxProotProbe.Mode.NORMAL, ready.mode)
        assertFalse(ready.switchedToCompat)
        assertFalse(ready.skippedNormal)
        assertTrue(ready.detailLine.contains("mode=normal"))
        assertEquals(listOf(LinuxProotProbe.Mode.NORMAL), runner.modesTried)
        assertEquals(listOf(LinuxProotProbe.TIMEOUT_MS), runner.timeouts)
        assertEquals(listOf(false), stages)
        assertTrue(killed.isEmpty())
        assertFalse(NodeCompatState.prootCompat(rootfs))
        assertFalse(SetupStepRetry.effectiveCompat(requestedCompat = false, markerPresent = NodeCompatState.prootCompat(rootfs)))
    }

    @Test
    fun `B-SETUP-002 normal hang writes compat marker and later steps and checks run in compat`() = withRootfs { rootfs ->
        val killed = mutableListOf<Int>()
        val stages = mutableListOf<Boolean>()
        val runner = FakeProbeRunner(rootfs) { mode, _, killPid ->
            if (mode == LinuxProotProbe.Mode.NORMAL) {
                killPid(4242)
                LinuxProotProbe.Attempt.Hung(4242)
            } else {
                // Marker must already be on disk when the compat probe runs.
                assertTrue(NodeCompatState.prootCompat(rootfs))
                LinuxProotProbe.Attempt.Answered
            }
        }

        val result = LinuxProotProbe.run(
            runner = runner,
            onStage = { stages += it },
            killPid = { killed += it },
        )

        assertTrue("expected Ready, got $result", result is LinuxProotProbe.Result.Ready)
        val ready = result as LinuxProotProbe.Result.Ready
        assertEquals(LinuxProotProbe.Mode.COMPAT, ready.mode)
        assertTrue(ready.switchedToCompat)
        assertFalse(ready.skippedNormal)
        assertTrue(ready.detailLine.contains("mode=compat"))
        assertEquals(listOf(LinuxProotProbe.Mode.NORMAL, LinuxProotProbe.Mode.COMPAT), runner.modesTried)
        assertEquals(listOf(4242), killed)
        assertEquals(listOf(false, true), stages)
        assertTrue(NodeCompatState.prootCompat(rootfs))
        // Subsequent setup steps and checks read the marker and run in compat mode.
        assertTrue(SetupStepRetry.effectiveCompat(requestedCompat = false, markerPresent = NodeCompatState.prootCompat(rootfs)))
    }

    @Test
    fun `B-SETUP-003 hangs in both modes returns failure line with no retry loop`() = withRootfs { rootfs ->
        val killed = mutableListOf<Int>()
        val runner = FakeProbeRunner(rootfs) { mode, _, killPid ->
            val pid = if (mode == LinuxProotProbe.Mode.NORMAL) 1001 else 1002
            killPid(pid)
            LinuxProotProbe.Attempt.Hung(pid)
        }

        val result = LinuxProotProbe.run(
            runner = runner,
            killPid = { killed += it },
        )

        assertTrue("expected Failed, got $result", result is LinuxProotProbe.Result.Failed)
        val failed = result as LinuxProotProbe.Result.Failed
        assertEquals(LinuxProotProbe.FAILURE_DETAIL, failed.failure)
        assertTrue(failed.detailLine.contains("normal=timeout"))
        assertTrue(failed.detailLine.contains("compat=timeout"))
        assertEquals(listOf(LinuxProotProbe.Mode.NORMAL, LinuxProotProbe.Mode.COMPAT), runner.modesTried)
        assertEquals(listOf(1001, 1002), killed)
        // Must not trigger SetupStepRetry's interruption retry loop.
        assertFalse(SetupStepRetry.interrupted(failed.failure))
        assertFalse(SetupStepRetry.again(attempt = 1, cancelled = false, interrupted = SetupStepRetry.interrupted(failed.failure)))
        assertFalse(NodeCompatState.prootCompat(rootfs))
    }

    @Test
    fun `B-SETUP-004 timeout kill uses only the exact verified pid`() {
        val sent = mutableListOf<Pair<Int, Int>>()
        val stopped = LinuxProotProbe.stopExactProcess(
            pid = 7731,
            isSameProcess = { it == 7731 },
            waitExited = { false },
            signal = { pid, sig -> sent += pid to sig },
            sigTerm = 15,
            sigKill = 9,
        )
        assertTrue(stopped)
        assertEquals(listOf(7731 to 15, 7731 to 9), sent)

        val reused = mutableListOf<Pair<Int, Int>>()
        val skipped = LinuxProotProbe.stopExactProcess(
            pid = 7731,
            isSameProcess = { false },
            waitExited = { false },
            signal = { pid, sig -> reused += pid to sig },
            sigTerm = 15,
            sigKill = 9,
        )
        assertFalse(skipped)
        assertTrue("must never signal a reused pid: $reused", reused.isEmpty())
    }

    @Test
    fun `B-SETUP-005 existing compat marker skips the normal mode probe`() = withRootfs { rootfs ->
        val state = File(rootfs, NodeCompatState.STATE_PATH).apply { parentFile.mkdirs() }
        state.writeText("single\nproot-compat\n")
        val killed = mutableListOf<Int>()
        val runner = FakeProbeRunner(rootfs) { mode, _, _ ->
            check(mode == LinuxProotProbe.Mode.COMPAT) { "normal probe must be skipped when marker exists" }
            LinuxProotProbe.Attempt.Answered
        }

        val result = LinuxProotProbe.run(
            runner = runner,
            killPid = { killed += it },
        )

        assertTrue("expected Ready, got $result", result is LinuxProotProbe.Result.Ready)
        val ready = result as LinuxProotProbe.Result.Ready
        assertEquals(LinuxProotProbe.Mode.COMPAT, ready.mode)
        assertFalse(ready.switchedToCompat)
        assertTrue(ready.skippedNormal)
        assertTrue(ready.detailLine.contains("mode=compat"))
        assertEquals(listOf(LinuxProotProbe.Mode.COMPAT), runner.modesTried)
        assertTrue(killed.isEmpty())
        assertEquals("single\nproot-compat\n", state.readText())
    }

    @Test
    fun `B-SETUP-006 probe command prints known word and marker write refuses symlinks`() = withRootfs { rootfs ->
        val process = ProcessBuilder("/bin/sh", "-c", LinuxProotProbe.COMMAND).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        assertTrue("probe command must print token, got code=$code out=$output", LinuxProotProbe.matchedOutput(code, output))
        assertFalse(LinuxProotProbe.matchedOutput(1, output))
        assertFalse(LinuxProotProbe.matchedOutput(0, "something else"))

        val target = File(rootfs, "outside").apply { writeText("keep") }
        val state = File(rootfs, NodeCompatState.STATE_PATH).apply { parentFile.mkdirs() }
        Files.createSymbolicLink(state.toPath(), target.toPath())
        assertFalse(NodeCompatState.writeProotCompat(rootfs))
        assertEquals("keep", target.readText())
    }
}
