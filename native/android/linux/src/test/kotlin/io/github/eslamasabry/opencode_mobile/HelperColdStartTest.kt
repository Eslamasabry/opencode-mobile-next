package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cold start's double helper start (Galaxy, build 10057: the helper's process at +3 s, gone at +10 s, the final one
 * at +14 s). A fake phone on a fake clock: the helper start takes its ticket, starts its process at once, checks the
 * ticket again when the daemon starts (after the version check) and when it is tracked (after the 1.5 s grace), as
 * `PhoneAgentHost.launchHost` and `BuiltinLinux` do; a refused check stops the process and the home's start loop tries
 * again 5 s later (`HelperJoins.QUICK_RETRY_MS`). The revision and the admission rule are the real ones.
 */
class HelperColdStartTest {
    private class Phone {
        val revision = AgentHostRevision()
        var resumed = false
        var epoch = 0L
        var now = 0L
        val started = mutableListOf<Long>()
        val dropped = mutableListOf<Long>()
        var upAt: Long? = null
        /** A start runs: another request for the helper joins it (`AppLocalAgentHostStarter`'s one lane). */
        var inFlight = false
        private val events = sortedMapOf<Long, MutableList<() -> Unit>>()

        fun at(time: Long, action: () -> Unit) {
            events.getOrPut(time) { mutableListOf() } += action
        }

        fun run(until: Long = 120_000L) {
            while (events.isNotEmpty() && events.firstKey() <= until) {
                val time = events.firstKey()
                val actions = events.remove(time)!!
                now = time
                actions.forEach { it() }
            }
        }

        fun resume(on: Boolean) {
            resumed = on
            epoch++
        }

        private fun current() =
            NativeAgentHostAdmission.Snapshot(
                available = true,
                activityResumed = resumed,
                activityEpoch = epoch,
                wantedRevision = revision.value,
                owner = "phone",
                wanted = true,
                userStopped = false,
                supervisionEnabled = true,
                serverLive = false,
                migrationValid = false,
                policyValid = false,
                helperOwner = null,
                idle = NativeIdleState.Snapshot(),
            )

        fun admitted(ticket: NativeAgentHostAdmission.Ticket) = NativeAgentHostAdmission.admitted(ticket, current())

        /** One try of the helper start (`captureAgentHostStart`, launch, tracking); retried 5 s after a refusal. */
        fun startHelper() {
            if (upAt != null || inFlight) return
            val ticket = NativeAgentHostAdmission.Ticket("phone", "phone", revision.value, epoch, null)
            if (!admitted(ticket)) {
                at(now + RETRY_MS) { startHelper() }
                return
            }
            val start = now
            inFlight = true
            started += start
            for (check in listOf(start + DAEMON_AFTER_MS, start + TRACKED_AFTER_MS)) {
                at(check) {
                    if (start !in dropped && upAt == null && !admitted(ticket)) {
                        dropped += start
                        inFlight = false
                        at(now + RETRY_MS) { startHelper() }
                    }
                }
            }
            at(start + TRACKED_AFTER_MS) {
                if (start !in dropped && upAt == null) {
                    upAt = now
                    inFlight = false
                }
            }
        }

        /** This phone's OpenCode start (`startService(SERVER)` → `setServerWanted(true)`). */
        fun startServer() = revision.serverWanted(true)
    }

    private fun coldStart(): Phone =
        Phone().apply {
            at(500) { resume(true) }
            at(3_000) { startHelper() }
            // The app-open start of the phone's server lands inside the helper's start.
            at(5_000) { startServer() }
        }

    @Test
    fun `LT-1 a cold start starts the helper once, and the server's own start does not drop it`() {
        val phone = coldStart()
        phone.run()
        assertEquals("helper processes started", listOf(3_000L), phone.started)
        assertEquals("none dropped", emptyList<Long>(), phone.dropped)
        assertEquals("up when its one start ends", 3_000L + TRACKED_AFTER_MS, phone.upAt)
    }

    @Test
    fun `LT-2 with the service alive, a conversation opened at once waits for one helper start only`() {
        val phone =
            Phone().apply {
                // The service runs (the server was wanted before); the app comes to the front and the helper is wanted.
                startServer()
                at(0) { resume(true) }
                at(200) { startHelper() }
                // The conversation opened at once joins the start that runs (one lane); This phone's start runs again.
                at(1_000) { startHelper() }
                at(1_500) { startServer() }
            }
        phone.run()
        assertEquals("helper processes started", listOf(200L), phone.started)
        assertEquals("usable when the first start ends", 200L + TRACKED_AFTER_MS, phone.upAt)
    }

    @Test
    fun `LT-3 every stop still revokes a helper start in flight`() {
        val stops: Map<String, (Phone) -> Unit> =
            mapOf(
                "setServerWanted(false)" to { it.revision.serverWanted(false) },
                "requestServerStop (the person's Stop, Android's stop)" to { it.revision.stopped() },
                "idle stop" to { it.revision.stopped() },
            )
        for ((name, stop) in stops) {
            val phone =
                Phone().apply {
                    at(500) { resume(true) }
                    at(3_000) { startHelper() }
                    at(5_000) { stop(this) }
                }
            phone.run(until = 9_000)
            assertEquals("$name: the start in flight is stopped", listOf(3_000L), phone.dropped)
            assertEquals("$name: not up", null, phone.upAt)
        }
    }

    @Test
    fun `LT-4 a refused case stays refused - in the background and across a pause`() {
        val background = Phone().apply { at(3_000) { startHelper() } }
        background.run(until = 20_000)
        assertTrue("no helper start while the app is not in front", background.started.isEmpty())

        val paused =
            Phone().apply {
                at(500) { resume(true) }
                at(3_000) { startHelper() }
                at(4_000) { resume(false) }
                at(4_500) { resume(true) }
            }
        paused.run(until = 9_000)
        assertEquals("a pause and return revoke the start in flight", listOf(3_000L), paused.dropped)

        val ticket = NativeAgentHostAdmission.Ticket("phone", "phone", 0, 1, null)
        val phone = Phone().apply { resume(true) }
        phone.revision.serverWanted(true)
        assertTrue("a server start keeps the ticket", phone.admitted(ticket))
        phone.revision.stopped()
        assertFalse("a stop ends it", phone.admitted(ticket))
    }

    private companion object {
        const val RETRY_MS = 5_000L

        /** The version check (a Node start in proot) before the daemon starts. */
        const val DAEMON_AFTER_MS = 5_500L

        /** The daemon's 1.5 s grace, then tracking. */
        const val TRACKED_AFTER_MS = 7_000L
    }
}
