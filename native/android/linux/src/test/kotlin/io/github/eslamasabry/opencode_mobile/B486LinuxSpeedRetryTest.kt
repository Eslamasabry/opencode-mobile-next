package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-486: proves Settings > Help one-tap retry clears the compatibility fallback marker, probes
 * fast (normal) proot mode, and safely restores compatible mode on hang, failure, or cancel.
 */
class B486LinuxSpeedRetryTest {
    private class FakeRunner(
        private val rootfs: File,
        private val onAttempt: (LinuxProotProbe.Mode) -> LinuxProotProbe.Attempt,
    ) : LinuxProotProbe.Runner {
        val modesTried = mutableListOf<LinuxProotProbe.Mode>()

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
            return onAttempt(mode)
        }
    }

    private fun withRootfs(block: (File) -> Unit) {
        val rootfs = Files.createTempDirectory("b486-speed-rootfs").toFile()
        try {
            block(rootfs)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `B-LINUX-486 retryFast clears compat marker and restores fast mode when normal answers`() =
        withRootfs { rootfs ->
            NodeCompatState.writeProotCompat(rootfs)
            assertTrue(NodeCompatState.prootCompat(rootfs))
            val runner = FakeRunner(rootfs) { LinuxProotProbe.Attempt.Answered }

            val result = LinuxProotProbe.retryFast(runner)

            assertTrue(result is LinuxProotProbe.Result.Ready)
            assertEquals(LinuxProotProbe.Mode.NORMAL, (result as LinuxProotProbe.Result.Ready).mode)
            assertEquals(listOf(LinuxProotProbe.Mode.NORMAL), runner.modesTried)
            assertFalse("compat marker must stay cleared after fast probe passes", NodeCompatState.prootCompat(rootfs))
        }

    @Test
    fun `B-LINUX-486 retryFast falls back and restores compat marker when normal hangs`() =
        withRootfs { rootfs ->
            NodeCompatState.writeProotCompat(rootfs)
            val runner = FakeRunner(rootfs) { mode ->
                if (mode == LinuxProotProbe.Mode.NORMAL) LinuxProotProbe.Attempt.Hung(991)
                else LinuxProotProbe.Attempt.Answered
            }

            val result = LinuxProotProbe.retryFast(runner)

            assertTrue(result is LinuxProotProbe.Result.Ready)
            val ready = result as LinuxProotProbe.Result.Ready
            assertEquals(LinuxProotProbe.Mode.COMPAT, ready.mode)
            assertTrue(ready.switchedToCompat)
            assertEquals(listOf(LinuxProotProbe.Mode.NORMAL, LinuxProotProbe.Mode.COMPAT), runner.modesTried)
            assertTrue("compat marker must be restored on fallback", NodeCompatState.prootCompat(rootfs))
        }

    @Test
    fun `B-LINUX-486 retryFast preserves prior compat marker when cancelled or failed`() =
        withRootfs { rootfs ->
            NodeCompatState.writeProotCompat(rootfs)
            val cancelled = FakeRunner(rootfs) { LinuxProotProbe.Attempt.Cancelled }
            assertEquals(LinuxProotProbe.Result.Cancelled, LinuxProotProbe.retryFast(cancelled))
            assertTrue("compat marker restored after cancel", NodeCompatState.prootCompat(rootfs))

            val failed = FakeRunner(rootfs) { LinuxProotProbe.Attempt.Exited(1) }
            assertTrue(LinuxProotProbe.retryFast(failed) is LinuxProotProbe.Result.Failed)
            assertTrue("compat marker restored after failure", NodeCompatState.prootCompat(rootfs))
        }

    @Test
    fun `B-LINUX-486 isCompatibleFallback flags persisted marker or live ptrace mode`() {
        assertFalse(LinuxProotProbe.isCompatibleFallback(compatMarker = false, liveProotMode = "seccomp"))
        assertFalse(LinuxProotProbe.isCompatibleFallback(compatMarker = false, liveProotMode = "unknown"))
        assertFalse(LinuxProotProbe.isCompatibleFallback(compatMarker = false, liveProotMode = null))
        assertTrue(LinuxProotProbe.isCompatibleFallback(compatMarker = true, liveProotMode = "unknown"))
        assertTrue(LinuxProotProbe.isCompatibleFallback(compatMarker = false, liveProotMode = "ptrace"))
    }

    private fun running(openCode: Boolean, helper: Boolean) =
        LinuxFastRetry.Running(openCodeCompatible = openCode, helperCompatible = helper)

    private fun plan(probe: Boolean, openCode: Boolean = false, helper: Boolean = false) =
        LinuxFastRetry.Plan(probe = probe, restartOpenCode = openCode, restartHelper = helper)

    @Test
    fun `B-LINUX-486 a busy phone refuses the retry while OpenCode or the helper runs in compatible mode`() {
        val refused = plan(probe = false)
        assertEquals(refused, LinuxFastRetry.plan(running(openCode = true, helper = false), idle = false))
        assertEquals(refused, LinuxFastRetry.plan(running(openCode = false, helper = true), idle = false))
        assertEquals(refused, LinuxFastRetry.plan(running(openCode = true, helper = true), idle = false))
    }

    @Test
    fun `B-LINUX-486 an idle phone tests fast mode and restarts everything that ran in compatible mode`() {
        assertEquals(
            plan(probe = true, openCode = true, helper = true),
            LinuxFastRetry.plan(running(openCode = true, helper = true), idle = true),
        )
        assertEquals(
            plan(probe = true, helper = true),
            LinuxFastRetry.plan(running(openCode = false, helper = true), idle = true),
        )
        assertEquals(
            plan(probe = true, openCode = true),
            LinuxFastRetry.plan(running(openCode = true, helper = false), idle = true),
        )
    }

    @Test
    fun `B-LINUX-486 with nothing running in compatible mode the retry tests and restarts nothing`() {
        val nothing = running(openCode = false, helper = false)
        assertEquals(plan(probe = true), LinuxFastRetry.plan(nothing, idle = true))
        assertEquals(plan(probe = true), LinuxFastRetry.plan(nothing, idle = false))
    }

    @Test
    fun `B-LINUX-486 the row reads Fast only when everything that ran compatible was restarted`() {
        val both = plan(probe = true, openCode = true, helper = true)
        assertTrue(LinuxFastRetry.fastReached(both, openCodeRestarted = true, helperRestarted = true))
        assertFalse(
            "a helper still in compatible mode is not Fast",
            LinuxFastRetry.fastReached(both, openCodeRestarted = true, helperRestarted = false),
        )
        assertFalse(
            "an OpenCode still in compatible mode is not Fast",
            LinuxFastRetry.fastReached(both, openCodeRestarted = false, helperRestarted = true),
        )
        assertTrue(LinuxFastRetry.fastReached(plan(probe = true), openCodeRestarted = false, helperRestarted = false))
        assertFalse(LinuxFastRetry.fastReached(plan(probe = false), openCodeRestarted = false, helperRestarted = false))
    }

    @Test
    fun `B-LINUX-486 what was started under the saved compatible mode counts as running compatible`() {
        // The kernel does not always say how OpenCode runs (no filter count before Linux 5.9): the saved mode decides.
        assertEquals(
            running(openCode = true, helper = true),
            LinuxFastRetry.running(
                compatMarker = true,
                liveProotMode = "unknown",
                openCodeRunning = true,
                helperRunning = true,
            ),
        )
        assertEquals(
            running(openCode = true, helper = false),
            LinuxFastRetry.running(
                compatMarker = false,
                liveProotMode = "ptrace",
                openCodeRunning = true,
                helperRunning = true,
            ),
        )
        assertEquals(
            running(openCode = false, helper = false),
            LinuxFastRetry.running(
                compatMarker = false,
                liveProotMode = "seccomp",
                openCodeRunning = true,
                helperRunning = true,
            ),
        )
        assertEquals(
            running(openCode = false, helper = false),
            LinuxFastRetry.running(
                compatMarker = true,
                liveProotMode = "ptrace",
                openCodeRunning = false,
                helperRunning = false,
            ),
        )
    }
}
