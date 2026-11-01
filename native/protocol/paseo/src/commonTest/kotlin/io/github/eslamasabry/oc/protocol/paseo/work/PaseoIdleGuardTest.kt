package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.toJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from test/paseo_idle_wake_test.dart (incident 2026-10-27, jitter J10). The Dart tests list through a gateway;
 * here each `listed()` is the guard call the gateway makes for that snapshot. The "opening the chat or a send wakes it"
 * half (`genUiMayLoad` reading the gateway's live/turn sets) is the gateway's; this covers [PaseoIdleGuard]'s part.
 */
class PaseoIdleGuardTest {
    private val asleep = "2026-10-25T17:04:31.547Z"
    private val woken = "2026-10-27T01:17:22.000Z"
    private val agent = mutableMapOf<String, Any?>(
        "id" to "kilo",
        "provider" to "kilo",
        "status" to "idle",
        "createdAt" to "2026-10-25T17:04:30.000Z",
        "updatedAt" to asleep,
        "lastUserMessageAt" to "2026-10-25T17:04:30.000Z",
        "activeTurn" to null,
    )
    private val guard = PaseoIdleGuard()
    private var saved: ((String) -> Long?)? = null

    private fun snapshot(): JsonObject = jsonOf(*agent.toList().toTypedArray())

    private fun ms(iso: String): Long = paseoMillis(toJson(iso))!!

    private fun listed(): Long? {
        val snap = snapshot()
        val lookup = saved
        return guard.settle("kilo", snap, paseoMillis(snap["updatedAt"]), saved = lookup?.let { { it("kilo") } })
    }

    @Test
    fun `B-AGENT-169 a listed idle agent is not awake, a running one and a watched turn are`() {
        listed()
        assertFalse(guard.isAwake("kilo"))
        assertFalse(PaseoIdleGuard.working(snapshot()))
        assertFalse(guard.isAwake("unknown"))
        agent["status"] = "running"
        listed()
        assertTrue(PaseoIdleGuard.working(snapshot()))
        agent["status"] = "idle"
        listed()
        // The turn was seen: its end is still this app's to reconcile.
        assertTrue(guard.isAwake("kilo"))
        // Opening the chat or sending marks it awake too.
        val other = PaseoIdleGuard()
        other.markAwake("kilo")
        assertTrue(other.isAwake("kilo"))
    }

    @Test
    fun `B-AGENT-169 a rise with no prompt and no turn keeps the earlier time`() {
        val before = listed()
        assertEquals(ms(asleep), before)
        agent["updatedAt"] = woken
        assertEquals(before, listed())
    }

    @Test
    fun `B-AGENT-169 a new prompt moves the time`() {
        listed()
        agent["updatedAt"] = woken
        agent["lastUserMessageAt"] = woken
        assertEquals(ms(woken), listed())
    }

    @Test
    fun `B-AGENT-169 a long turn that ends hours later still counts as recent, then an unexplained rise is held`() {
        listed()
        agent["status"] = "running"
        agent["updatedAt"] = "2026-10-25T18:00:00.000Z"
        listed()
        agent["status"] = "idle"
        agent["updatedAt"] = "2026-10-25T21:30:00.000Z"
        assertEquals(ms("2026-10-25T21:30:00.000Z"), listed())
        agent["updatedAt"] = woken
        assertEquals(ms("2026-10-25T21:30:00.000Z"), listed())
    }

    @Test
    fun `B-AGENT-169 a chat the daemon only loaded keeps its time and is not a run`() {
        val before = listed()
        agent["status"] = "initializing"
        agent["updatedAt"] = woken
        assertEquals(before, listed())
        assertFalse(PaseoIdleGuard.turning(snapshot()))
        assertTrue(PaseoIdleGuard.working(snapshot()))
        agent["status"] = "idle"
        agent["updatedAt"] = "2026-10-27T01:17:40.000Z"
        assertEquals(before, listed())
    }

    @Test
    fun `B-AGENT-169 a turn after a load still moves the time`() {
        listed()
        agent["status"] = "initializing"
        agent["updatedAt"] = woken
        listed()
        agent["status"] = "running"
        agent["activeTurn"] = mapOf("turnId" to "t1", "startedAt" to woken)
        assertTrue(PaseoIdleGuard.turning(snapshot()))
        assertEquals(ms(woken), listed())
    }

    @Test
    fun `B-AGENT-169 a first sight keeps the time the list showed last run`() {
        saved = { id -> if (id == "kilo") ms(asleep) else null }
        agent["updatedAt"] = woken
        assertEquals(ms(asleep), listed())
    }

    @Test
    fun `B-AGENT-169 a prompt sent after the saved time moves it`() {
        saved = { id -> if (id == "kilo") ms(asleep) else null }
        agent["updatedAt"] = woken
        agent["lastUserMessageAt"] = woken
        assertEquals(ms(woken), listed())
    }

    @Test
    fun `B-AGENT-169 a first sight is trusted and an earlier time never counts as a rise`() {
        agent["updatedAt"] = woken
        assertEquals(ms(woken), listed())
        agent["updatedAt"] = asleep
        assertEquals(ms(asleep), listed())
    }
}
