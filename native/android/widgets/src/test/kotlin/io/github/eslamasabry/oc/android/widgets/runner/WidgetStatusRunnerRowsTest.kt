package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of test/widget_status_runner_test.dart (the scheduled read-only widget status check). */
class WidgetStatusRunnerRowsTest {
    private val ticket =
        mapOf<String, Any?>(
            "profileID" to "profile",
            "scopeToken" to "scope",
            "sessionID" to "session",
            "fingerprint" to "fp",
            "runMarker" to "user-1",
            "startedAt" to 1L,
            "token" to "ticket",
        )
    private val preview = WidgetRunPreview(Redactor()::redact)

    private class World(val transport: FakeTransport, var current: () -> Boolean = { true }) : WidgetStatusWorld {
        var opened = 0

        override suspend fun loadProfile(profileId: String) = WidgetRunProfile("profile", "fp")

        override suspend fun loadTarget(tap: WidgetTap, fingerprint: String) =
            mapOf<String, Any?>("profileID" to "profile", "scopeToken" to "scope", "fingerprint" to "fp")

        override suspend fun isCurrent(ticket: Map<String, Any?>) = current()

        override suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>): WidgetOpen {
            opened++
            return WidgetOpen.Opened(transport)
        }
    }

    private fun gateway() = FakeTransport().apply { settledOnly = false; pending = mutableListOf() }

    private fun TestScope.now(): () -> Long = { 5_000_000L + testScheduler.currentTime }

    private fun TestScope.deadline() = now()() + 1000

    private fun TestScope.runner(world: World) = WidgetStatusRunner(world, now(), preview::of)

    @Test
    fun `B-ANDR-118 one read reports busy, retry, idle or missing and closes without mutations`() = runTest {
        for (status in listOf("busy", "retry", "idle", null)) {
            val gateway = gateway().apply { this.status = status }
            assertEquals(
                mapOf("status" to if (status == "busy" || status == "retry") "busy" else "idle"),
                runner(World(gateway)).check(ticket, deadline()),
            )
            assertEquals(1, gateway.statusReads)
            assertTrue(gateway.mutations.isEmpty())
            assertTrue(gateway.closed)
        }
    }

    @Test
    fun `B-ANDR-118 an idle result carries the sanitized assistant reply from the existing reads`() = runTest {
        val gateway = gateway().apply { status = null; assistantReply = "Finished password=secret-value" }
        val result = runner(World(gateway)).check(ticket, deadline())
        assertEquals("idle", result["status"])
        assertEquals("Finished password=•••", result["preview"])
        assertEquals(1, gateway.statusReads)
    }

    @Test
    fun `B-ANDR-118 the run identity ignores OpenCode 2 timeline records and keeps real prompts`() {
        val prompt = message("user-1", "session", Role.User, 1, text = "Synthetic")
        val timeline =
            listOf("v2:notice", "v2:switch", "v2:compaction", "v2:future").mapIndexed { i, type ->
                message("notice-$i", "session", Role.User, i + 2L, type = type)
            }
        assertEquals("user-1", widgetRunMarker(listOf(prompt) + timeline))
        assertNull(widgetRunMarker(timeline))
        val next = message("user-2", "session", Role.User, 10)
        assertEquals("user-2", widgetRunMarker(listOf(prompt) + timeline + next))
        assertEquals("user-3", widgetRunMarker(listOf(message("user-3", "session", Role.User, 11))))
    }

    @Test
    fun `B-ANDR-119 a changed server turn rejects the old result before the status read`() = runTest {
        val gateway = gateway().apply { marker = "new-user" }
        assertEquals(mapOf("status" to "stale"), runner(World(gateway)).check(ticket, deadline()))
        assertEquals(0, gateway.statusReads)
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-119 a new turn during the read is never marked idle`() = runTest {
        val gateway = gateway().apply { status = null }
        gateway.afterStatus = { gateway.marker = "new-user" }
        assertEquals(mapOf("status" to "stale"), runner(World(gateway)).check(ticket, deadline()))
    }

    @Test
    fun `B-ANDR-119 a new permission during the read is never cleared`() = runTest {
        val gateway = gateway().apply { status = null; pending = mutableListOf(WidgetPendingAsk("new", "session")) }
        assertEquals(mapOf("status" to "stale"), runner(World(gateway)).check(ticket, deadline()))
    }

    @Test
    fun `B-ANDR-119 cancellation while the status read is pending closes the transport and rejects the patch`() =
        runTest {
            val gateway = gateway().apply { statusGate = CompletableDeferred() }
            val run = runner(World(gateway))
            val result = async { run.check(ticket, deadline()) }
            advanceTimeBy(10)
            run.cancel()
            gateway.statusGate!!.complete(Unit)
            assertEquals(mapOf("status" to "unavailable"), result.await())
            assertTrue(gateway.closed)
        }

    @Test
    fun `B-ANDR-119 the read deadline closes an unresponsive transport`() = runTest {
        val gateway = gateway().apply { statusGate = CompletableDeferred() }
        assertEquals(mapOf("status" to "unavailable"), runner(World(gateway)).check(ticket, now()() + 30))
        assertTrue(gateway.closed)
        assertEquals(1, gateway.statusReads)
    }

    @Test
    fun `B-ANDR-119 a profile scope change during the read discards idle`() = runTest {
        var current = true
        val gateway = gateway().apply { status = null; afterStatus = { current = false } }
        assertEquals(mapOf("status" to "stale"), runner(World(gateway, current = { current })).check(ticket, deadline()))
    }

    @Test
    fun `B-ANDR-119 a stale ticket never opens a transport`() = runTest {
        val gateway = gateway()
        val world = World(gateway, current = { false })
        assertEquals(mapOf("status" to "stale"), runner(world).check(ticket, deadline()))
        assertEquals(0, gateway.statusReads)
        assertEquals(0, world.opened)
        assertFalse(gateway.closed)
        // A ticket missing any identity is stale before anything is read.
        assertEquals(mapOf("status" to "stale"), runner(world).check(ticket - "runMarker", deadline()))
    }

    @Test
    fun `B-ANDR-119 the durable identity permits busy with no buttons but rejects deletion or replacement`() {
        val prefs = statusPrefs()
        val guards = WidgetRunGuards(prefs::get)
        val live = ticket + ("fingerprint" to guards.fingerprintOfSaved())
        assertTrue(guards.statusIsCurrent(live))
        prefs[WidgetRunGuards.FOLLOWUP_PREFIX + "profile"] = """{"session":{"token":"new"}}"""
        assertFalse(guards.statusIsCurrent(live))
        prefs.remove(WidgetRunGuards.FOLLOWUP_PREFIX + "profile")
        assertFalse(guards.statusIsCurrent(live))
    }

    @Test
    fun `B-ANDR-119 profile metadata edits invalidate a stored check`() {
        val prefs = statusPrefs()
        val guards = WidgetRunGuards(prefs::get)
        val live = ticket + ("fingerprint" to guards.fingerprintOfSaved())
        assertTrue(guards.statusIsCurrent(live))
        prefs["oc.profiles"] = """[{"id":"profile","name":"Server","baseUrl":"http://edited","backend":"openCode","flavor":"v2"}]"""
        assertFalse(guards.statusIsCurrent(live))
    }

    private fun WidgetRunGuards.fingerprintOfSaved(): String =
        io.github.eslamasabry.oc.engine.profiles.StoredProfileJson
            .decodeList(statusPrefs()["oc.profiles"])
            .single()
            .let(::widgetProfileFingerprint)
            .also { check(metadataMatches("profile", it)) }

    private fun statusPrefs(): MutableMap<String, String> {
        val profiles = """[{"id":"profile","name":"Server","baseUrl":"http://localhost:4097","backend":"openCode","flavor":"v2"}]"""
        val fp =
            io.github.eslamasabry.oc.engine.profiles.StoredProfileJson.decodeList(profiles).single().let(::widgetProfileFingerprint)
        return mutableMapOf(
            "oc.activeProfile" to "profile",
            "oc.profiles" to profiles,
            WidgetRunGuards.FOLLOWUP_PREFIX + "profile" to """{"session":{"token":"ticket"}}""",
            WidgetRunGuards.SNAPSHOT to
                """{"version":2,"profileID":"profile","scopeToken":"scope","onDemand":{"fingerprint":"$fp"},""" +
                """"sessions":[{"id":"session","busy":true,"runMarker":"user-1","startedAt":1,"onDemandActions":[]}]}""",
        )
    }
}
