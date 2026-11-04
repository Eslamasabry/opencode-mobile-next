package io.github.eslamasabry.oc.android.background

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/background_pause_native_test.dart:41 (the eleven BackgroundPauseStore harness scenarios of
// test/native/background_pause_harness.kt) and test/bb8_main_callback_native_test.dart:39 (B-ANDR-017).
class PauseReceiptTest {
    /** The receipt file in memory, shared across "reopened" stores like the harness's Context.reopen(). */
    private class MemoryFile : PauseReceiptFile {
        val values = mutableMapOf<String, Any>()
        var commits = 0
        var failCommit = false

        override fun phase(): String? = values["phase"] as? String

        override fun reason(): String? = values["reason"] as? String

        override fun at(): Long = values["at"] as? Long ?: 0L

        override fun commit(receipt: PauseReceipt): Boolean {
            commits++
            if (failCommit) return false
            values["phase"] = receipt.phase
            values["reason"] = receipt.reason
            values["at"] = receipt.at
            return true
        }

        override fun recordTaskRemoval(at: Long) {
            values["taskRemovalHintAt"] = at
        }

        fun running() {
            values += mapOf("phase" to "running", "reason" to "none", "at" to 1000L)
        }
    }

    private class Facts : PauseSystemFacts {
        var now = 1_000_000L
        var restricted = false
        var exit: PauseExit? = null

        /** Null = "booted at the epoch": no reboot is ever inferred (the harness default). */
        var elapsed: Long? = null
        var exitReads = 0

        override fun now(): Long = now

        override fun bootTime(): Long = now - (elapsed ?: now)

        override fun backgroundRestricted(): Boolean = restricted

        override fun latestExit(): PauseExit? {
            exitReads++
            return exit
        }
    }

    private val file = MemoryFile()
    private val facts = Facts()
    private val flutter = FakeOptIn()

    private fun store() = PauseReceiptStore(file, facts, flutter)

    @Test
    fun `B-ANDR-017 native receipt timeout-reopen keeps the time-limit pause across a reopen`() = runTest {
        val store = store()
        assertTrue(store.confirmedStarted())
        assertEquals(true, flutter.value)
        assertTrue(store.timeout())
        assertEquals(false, flutter.value)
        assertEquals(2, file.commits)
        val state = store().status(false)
        assertTrue(state.paused)
        assertEquals(PauseReason.TimeLimit, state.reason)
        assertTrue((state.atMs ?: 0L) > 0L)
        assertFalse(state.active)
        assertTrue(state.canResume)
    }

    @Test
    fun `B-ANDR-017 native receipt task-removal is a hint that never replaces a running receipt`() = runTest {
        val store = store()
        assertTrue(store.confirmedStarted())
        store.recordTaskRemoved()
        val state = store.status(true)
        assertTrue(state.active)
        assertFalse(state.paused)
        assertEquals(PauseReason.None, state.reason)
        assertEquals("running", file.values["phase"])
    }

    @Test
    fun `B-ANDR-017 native receipt current-restriction is not kept as a historical cause`() = runTest {
        file.running()
        facts.restricted = true
        val observed = store().status(false)
        assertEquals(PauseReason.BatteryRestricted, observed.reason)
        assertTrue((observed.atMs ?: 0L) > 1000L)
        facts.restricted = false
        assertEquals(PauseReason.Interrupted, store().status(false).reason)
    }

    @Test
    fun `B-ANDR-017 native receipt user-stop takes the exit time and reads no other file`() = runTest {
        file.running()
        // AndroidPauseFacts drops helper-process exits; the main process's own record is what reaches the policy.
        facts.exit = PauseExit(10, 2000L)
        val state = store().status(false)
        assertEquals(PauseReason.UserStopped, state.reason)
        assertEquals(2000L, state.atMs)
        assertEquals(1, facts.exitReads)
    }

    @Test
    fun `B-ANDR-017 native receipt old-exit-interruption ignores an exit before the running period`() = runTest {
        file.running()
        facts.exit = PauseExit(10, 999L)
        assertEquals(PauseReason.Interrupted, store().status(false).reason)
    }

    @Test
    fun `B-ANDR-017 native receipt intentional-clear leaves no pause and no restore`() = runTest {
        val store = store()
        assertTrue(store.confirmedStarted())
        assertTrue(store.timeout())
        assertTrue(store.clear())
        val state = store().status(false)
        assertFalse(state.paused)
        assertEquals(PauseReason.None, state.reason)
        assertNull(state.atMs)
        assertEquals(false, flutter.value)
    }

    @Test
    fun `B-ANDR-017 native receipt failed-commit still turns restore off and reads as unreadable`() = runTest {
        val store = store()
        assertTrue(store.confirmedStarted())
        file.failCommit = true
        assertFalse(store.timeout())
        assertEquals(false, flutter.value)
        assertFalse(store().status(false).supported)
    }

    @Test
    fun `B-ANDR-017 native receipt confirmed-start clears an earlier time-limit pause`() = runTest {
        val store = store()
        assertTrue(store.confirmedStarted())
        assertTrue(store.timeout())
        assertEquals("timeLimit", file.values["reason"])
        assertTrue(store.confirmedStarted())
        val state = store().status(true)
        assertFalse(state.paused)
        assertTrue(state.active)
        assertEquals("running", file.values["phase"])
        assertEquals(true, flutter.value)
    }

    @Test
    fun `B-ANDR-017 native receipt reason-eleven counts as a user stop`() = runTest {
        file.running()
        facts.exit = PauseExit(11, 2000L)
        assertEquals(PauseReason.UserStopped, store().status(false).reason)
    }

    @Test
    fun `B-ANDR-017 native receipt app-update is a known cause kept across a reopen`() = runTest {
        file.running()
        facts.exit = PauseExit(16, 2000L)
        val state = store().status(false)
        assertEquals(PauseReason.AppUpdated, state.reason)
        assertEquals(2000L, state.atMs)
        assertEquals(PauseReason.AppUpdated, store().status(false).reason)
    }

    @Test
    fun `B-ANDR-017 native receipt device-restart is inferred from a receipt older than the boot`() = runTest {
        file.running()
        facts.elapsed = 5000L
        val state = store().status(false)
        assertEquals(PauseReason.DeviceRestarted, state.reason)
        assertTrue(state.paused)
        assertEquals(PauseReason.DeviceRestarted, store().status(false).reason)
    }

    @Test
    fun `B-ANDR-017 a damaged receipt reads as unreadable, never as a pause or a default`() = runTest {
        for (damaged in listOf(
            mapOf("phase" to "sleeping", "reason" to "none", "at" to 1L),
            mapOf("phase" to "paused", "reason" to "private-password", "at" to 1L),
            mapOf("phase" to "running", "reason" to "none", "at" to 0L),
            mapOf("phase" to "paused", "reason" to "none", "at" to 1L),
        )) {
            file.values.clear()
            file.values += damaged
            assertFalse(store().status(false).supported, damaged.toString())
        }
    }

    @Test
    fun `B-ANDR-017 the main-thread callback always returns to the framework and reports the action's result`() {
        for (scenario in listOf("callback-success", "callback-failure")) {
            var actionRan = false
            var frameworkCompleted = false
            val result =
                MainCallback.run(
                    sync = { callback ->
                        callback.run()
                        frameworkCompleted = true
                    },
                    action = {
                        actionRan = true
                        if (scenario == "callback-failure") error("Synthetic callback failure")
                    },
                )
            assertTrue(actionRan, "Authored callback did not run")
            assertTrue(frameworkCompleted, "Callback prevented framework completion")
            assertEquals(scenario == "callback-success", result, "Callback result did not reflect action success")
        }
    }
}
