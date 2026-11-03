package io.github.eslamasabry.oc.android.widgets.runner

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of test/widget_action_runner_test.dart (the cold widget tap with no live connection). */
class WidgetActionRunnerRowsTest {
    private val fingerprint = "fp-profile"

    private fun tap(kind: String = "allowOnce") =
        WidgetTap(
            id = "tap",
            action = kind,
            profileId = "profile",
            scopeToken = "scope",
            sessionId = "session",
            requestId = "permission",
            expectedStartedAt = 1,
        )

    private fun snapshot(connected: Boolean = false): Map<String, Any?> =
        mapOf(
            "version" to 2,
            "profileID" to "profile",
            "scopeToken" to "scope",
            "connected" to connected,
            "pendingCount" to 1,
            "runningCount" to 1,
            "onDemand" to mapOf("fingerprint" to fingerprint),
            "sessions" to
                listOf(
                    mapOf(
                        "id" to "session",
                        "busy" to true,
                        "startedAt" to 1L,
                        "step" to "Running shell",
                        "requestID" to "permission",
                        "runMarker" to "user-1",
                        "onDemandActions" to listOf("allowOnce", "reject", "stop"),
                    )
                ),
        )

    private fun privateTarget(): Map<String, Any?> =
        mapOf(
            "profileID" to "profile",
            "scopeToken" to "scope",
            "fingerprint" to fingerprint,
            "directory" to "/private/project",
            "workspace" to "private-workspace",
        )

    private class World(
        val transport: FakeTransport,
        var current: () -> Boolean = { true },
        var target: () -> Map<String, Any?>? = { null },
        val openFails: Boolean = false,
    ) : WidgetActionWorld {
        var opened = 0
        var routed: Map<String, Any?>? = null

        override suspend fun loadProfile(profileId: String) = WidgetRunProfile("profile", "fp-profile")

        override suspend fun loadTarget(tap: WidgetTap, fingerprint: String) = target()

        override suspend fun isCurrent(tap: WidgetTap, fingerprint: String) = current()

        override suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>): WidgetOpen {
            check(!openFails) { "must not connect" }
            opened++
            routed = target
            return WidgetOpen.Opened(transport)
        }
    }

    private fun TestScope.clock(): () -> Long = { 1_000_000L + testScheduler.currentTime }

    private fun TestScope.deadline() = clock()() + 30_000

    private fun TestScope.runner(
        world: World,
        settle: Duration = 5.seconds,
        now: (() -> Long)? = null,
    ): WidgetActionRunner {
        if (world.target() == null) world.target = { privateTarget() }
        return WidgetActionRunner(world, now ?: clock(), settle, 1.milliseconds)
    }

    @Suppress("UNCHECKED_CAST")
    private fun row(result: WidgetRunResult): Map<String, Any?> =
        (result.snapshot!!["sessions"] as List<Map<String, Any?>>).single()

    // ---- B-ANDR-104: confirm the exact request, answer, reconcile status, keep acknowledged commands accepted ----

    @Test
    fun `B-ANDR-104 allowOnce and reject cold taps confirm the exact pending request and close the transport`() =
        runTest {
            for (kind in listOf("allowOnce", "reject")) {
                val gateway = FakeTransport()
                val result = runner(World(gateway)).execute(tap(kind), snapshot(), deadline())
                assertEquals("accepted", result.status)
                assertEquals(listOf("permission:${if (kind == "allowOnce") "once" else "reject"}:session"), gateway.mutations)
                assertTrue(gateway.closed)
                assertFalse(row(result).containsKey("requestID"))
                assertEquals("scope", result.snapshot!!["scopeToken"])
            }
        }

    @Test
    fun `B-ANDR-104 every action reconciles idle, missing and busy-then-idle server status before closing`() = runTest {
        for (kind in listOf("allowOnce", "reject", "stop")) {
            for (statuses in listOf(listOf("idle"), listOf(null), listOf("busy", "retry", "idle"))) {
                val gateway = FakeTransport().apply { settledStatuses = statuses.toMutableList() }
                val input = snapshot(connected = true)
                val result = runner(World(gateway)).execute(tap(kind), input, deadline())
                assertEquals("accepted", result.status)
                val row = row(result)
                assertEquals(false, row["busy"])
                assertEquals(0L, row["startedAt"])
                assertEquals("", row["step"])
                assertFalse((row["onDemandActions"] as List<*>).contains("stop"))
                assertEquals(0, result.snapshot!!["runningCount"])
                assertEquals(false, result.snapshot!!["connected"])
                assertEquals("$kind $statuses", statuses.size, gateway.settlementReads)
                assertEquals(1, gateway.mutations.size)
                assertTrue(gateway.closed)
                assertEquals(true, (input["sessions"] as List<Map<*, *>>).single()["busy"])
            }
        }
    }

    @Test
    fun `B-ANDR-104 a busy, retry or unknown status when settlement ends is kept as disconnected`() = runTest {
        for (kind in listOf("allowOnce", "reject", "stop")) {
            for (status in listOf("busy", "retry", "unknown")) {
                val gateway = FakeTransport().apply { settledStatuses = mutableListOf(status) }
                val result =
                    runner(World(gateway), settle = 100.milliseconds).execute(tap(kind), snapshot(true), deadline())
                assertEquals("accepted", result.status)
                val row = row(result)
                assertEquals(true, row["busy"])
                assertEquals(1L, row["startedAt"])
                assertEquals(1, result.snapshot!!["runningCount"])
                assertEquals(false, result.snapshot!!["connected"])
                assertTrue(gateway.settlementReads >= 1)
                assertEquals(1, gateway.mutations.size)
                assertTrue(gateway.closed)
            }
        }
    }

    @Test
    fun `B-ANDR-104 a failed status read keeps the acknowledged command accepted without replay`() = runTest {
        for (kind in listOf("allowOnce", "reject", "stop")) {
            val gateway = FakeTransport().apply { failStatus = true }
            val instance = runner(World(gateway))
            val result = instance.execute(tap(kind), snapshot(), deadline())
            assertEquals("accepted", result.status)
            assertEquals(true, row(result)["busy"])
            assertEquals(false, result.snapshot!!["connected"])
            assertFalse(result.toMap().toString().contains("private status failure"))
            instance.execute(tap(kind), snapshot(), deadline())
            assertEquals(1, gateway.mutations.size)
            assertTrue(gateway.closed)
        }
    }

    @Test
    fun `B-ANDR-104 the transport stays open until the status read after the reply resolves`() = runTest {
        val gateway = FakeTransport().apply { statusGate = CompletableDeferred() }
        val result = async { runner(World(gateway)).execute(tap("reject"), snapshot(), deadline()) }
        runCurrent()
        assertEquals(listOf("permission:reject:session"), gateway.mutations)
        assertEquals(1, gateway.settlementReads)
        assertFalse(gateway.closed)
        gateway.statusGate!!.complete(Unit)
        assertEquals(false, row(result.await())["busy"])
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-104 a hung status read times out with the acknowledged command accepted`() = runTest {
        val gateway = FakeTransport().apply { statusGate = CompletableDeferred() }
        val instance = runner(World(gateway), settle = 5.milliseconds)
        val result = instance.execute(tap("reject"), snapshot(), deadline())
        assertEquals("accepted", result.status)
        assertEquals(false, result.snapshot!!["connected"])
        assertEquals(true, row(result)["busy"])
        assertEquals(1, gateway.settlementReads)
        assertTrue(gateway.closed)
        instance.execute(tap("reject"), snapshot(), deadline())
        assertEquals(listOf("permission:reject:session"), gateway.mutations)
        // A response arriving after the lease cannot replace the published state.
        gateway.statusGate!!.complete(Unit)
        runCurrent()
        assertEquals(true, row(result)["busy"])
    }

    @Test
    fun `B-ANDR-104 near the native deadline the status read is skipped and the tap reads accepted, detached`() = runTest {
        val gateway = FakeTransport()
        val instance = runner(World(gateway), now = { 1000L })
        val result = instance.execute(tap("reject"), snapshot(true), 1499)
        assertEquals("accepted", result.status)
        val row = row(result)
        assertEquals(false, result.snapshot!!["connected"])
        assertEquals(true, row["busy"])
        assertFalse(row.containsKey("requestID"))
        assertEquals(0, gateway.settlementReads)
        assertTrue(gateway.closed)
        instance.execute(tap("reject"), snapshot(), 1499)
        assertEquals(listOf("permission:reject:session"), gateway.mutations)
        assertEquals(0, gateway.settlementReads)
    }

    // ---- B-ANDR-105: health first; never answer on an unhealthy, cancelled, failed or deadline-hit connection ----

    @Test
    fun `B-ANDR-105 cold approval waits for health before reading or answering`() = runTest {
        val gateway = FakeTransport().apply { healthGate = CompletableDeferred() }
        val result = async { runner(World(gateway)).execute(tap(), snapshot(), deadline()) }
        runCurrent()
        assertEquals(1, gateway.healthReads)
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
        gateway.healthGate!!.complete(Unit)
        assertEquals("accepted", result.await().status)
        assertEquals(1, gateway.permissionReads)
        assertEquals(listOf("permission:once:session"), gateway.mutations)
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-105 an unhealthy cold transport never reads or answers permissions`() = runTest {
        val gateway = FakeTransport().apply { healthy = false }
        val result = runner(World(gateway)).execute(tap(), snapshot(), deadline())
        assertEquals("rejected", result.status)
        assertEquals("unavailable", result.error)
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-105 failed health is sanitized and never answers permissions`() = runTest {
        val gateway = FakeTransport().apply { failHealth = true }
        val result = runner(World(gateway)).execute(tap(), snapshot(), deadline())
        assertEquals("rejected", result.status)
        assertEquals("unavailable", result.error)
        assertFalse(result.toMap().toString().contains("private health failure"))
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-105 cancel during cold health closes without answering permissions`() = runTest {
        val gateway = FakeTransport().apply { healthGate = CompletableDeferred() }
        val instance = runner(World(gateway))
        val result = async { instance.execute(tap(), snapshot(), deadline()) }
        runCurrent()
        assertEquals(1, gateway.healthReads)
        instance.cancel()
        assertTrue(gateway.closed)
        gateway.healthGate!!.complete(Unit)
        assertEquals("rejected", result.await().status)
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `B-ANDR-105 a deadline reached during cold health never reads or answers permissions`() = runTest {
        var clock = 100L
        val gateway = FakeTransport().apply { afterHealth = { clock = 201 } }
        val result = runner(World(gateway), now = { clock }).execute(tap(), snapshot(), 200)
        assertEquals("rejected", result.status)
        assertEquals("unavailable", result.error)
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    // ---- B-SEC-180: profile switch / deletion, private targets, expired taps, another owner's request ----

    @Test
    fun `B-SEC-180 a profile switch during settlement cannot publish the old profile snapshot`() = runTest {
        var current = true
        val gateway = FakeTransport().apply { afterStatus = { current = false } }
        val result = runner(World(gateway, current = { current })).execute(tap("reject"), snapshot(), deadline())
        assertNull(result.snapshot)
        assertEquals("uncertain", result.status)
        assertEquals(listOf("permission:reject:session"), gateway.mutations)
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-SEC-180 a profile switch during cold health prevents permission dispatch`() = runTest {
        var current = true
        val gateway = FakeTransport().apply { afterHealth = { current = false } }
        val result = runner(World(gateway, current = { current })).execute(tap(), snapshot(), deadline())
        assertEquals("staleRequest", result.error)
        assertEquals(0, gateway.permissionReads)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-SEC-180 cold dispatch uses the private target without exposing it in its result`() = runTest {
        val gateway = FakeTransport()
        val world = World(gateway)
        val result = runner(world).execute(tap(), snapshot(), deadline())
        assertEquals("accepted", result.status)
        assertEquals("/private/project", world.routed?.get("directory"))
        assertEquals("private-workspace", world.routed?.get("workspace"))
        assertFalse(result.toMap().toString().contains("/private/project"))
        assertFalse(result.toMap().toString().contains("private-workspace"))
    }

    @Test
    fun `B-SEC-180 a missing or stale private target never builds a transport`() = runTest {
        for (target in listOf(null, privateTarget() + ("scopeToken" to "old"))) {
            val gateway = FakeTransport()
            val world = World(gateway, target = { target }, openFails = true)
            val run = WidgetActionRunner(world, clock(), 5.seconds, 1.milliseconds)
            val result = run.execute(tap(), snapshot(), deadline())
            assertEquals("staleRequest", result.error)
            assertEquals(0, world.opened)
            assertTrue(gateway.mutations.isEmpty())
        }
    }

    @Test
    fun `B-SEC-180 an expired tap never builds or dispatches a transport`() = runTest {
        val gateway = FakeTransport()
        val world = World(gateway)
        val result = runner(world).execute(tap(), snapshot(), 0)
        assertEquals("rejected", result.status)
        assertEquals(0, world.opened)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `B-SEC-180 a deadline reached during the pending read never dispatches afterwards`() = runTest {
        var clock = 100L
        val gateway = FakeTransport().apply { afterRead = { clock = 201 } }
        val result = runner(World(gateway), now = { clock }).execute(tap(), snapshot(), 200)
        assertEquals("rejected", result.status)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-SEC-180 cancel while a read is in flight prevents a late dispatch and closes`() = runTest {
        val gateway = FakeTransport().apply { readGate = CompletableDeferred() }
        val instance = runner(World(gateway))
        val result = async { instance.execute(tap(), snapshot(), deadline()) }
        runCurrent()
        instance.cancel()
        gateway.readGate!!.complete(Unit)
        assertEquals("rejected", result.await().status)
        assertTrue(gateway.mutations.isEmpty())
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-SEC-180 profile deletion during a read prevents the mutation`() = runTest {
        var current = true
        val gateway = FakeTransport().apply { afterRead = { current = false } }
        val result = runner(World(gateway, current = { current })).execute(tap(), snapshot(), deadline())
        assertEquals("staleRequest", result.error)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `B-SEC-180 a pending request of another owner is never answered`() = runTest {
        val gateway = FakeTransport().apply { pending = mutableListOf(WidgetPendingAsk("permission", "another-session")) }
        val result = runner(World(gateway)).execute(tap(), snapshot(), deadline())
        assertEquals("staleRequest", result.error)
        assertTrue(gateway.mutations.isEmpty())
    }

    // ---- B-ANDR-106: once; stop checks identity and busy; replacement and idle runs; unsupported first ----

    @Test
    fun `B-ANDR-106 a duplicate tap is dispatched once`() = runTest {
        val gateway = FakeTransport().apply { readGate = CompletableDeferred() }
        val instance = runner(World(gateway))
        val first = async { instance.execute(tap(), snapshot(), deadline()) }
        val second = async { instance.execute(tap(), snapshot(), deadline()) }
        runCurrent()
        gateway.readGate!!.complete(Unit)
        assertSame(first.await(), second.await())
        assertEquals(1, gateway.mutations.size)
    }

    @Test
    fun `B-ANDR-106 stop checks the server user-message identity and busy status`() = runTest {
        val gateway = FakeTransport()
        val result = runner(World(gateway)).execute(tap("stop"), snapshot(), deadline())
        assertEquals("accepted", result.status)
        assertEquals(listOf("stop:session"), gateway.mutations)
        assertEquals(false, row(result)["busy"])
    }

    @Test
    fun `B-ANDR-106 an old run cannot stop a replacement run`() = runTest {
        val gateway = FakeTransport().apply { marker = "replacement-user-message" }
        val result = runner(World(gateway)).execute(tap("stop"), snapshot(), deadline())
        assertEquals("staleRequest", result.error)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `B-ANDR-106 an idle run cannot be stopped`() = runTest {
        val gateway = FakeTransport().apply { status = "idle" }
        val result = runner(World(gateway)).execute(tap("stop"), snapshot(), deadline())
        assertEquals("staleRequest", result.error)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `B-ANDR-106 an unsupported row action is rejected before opening a connection`() = runTest {
        for (kind in listOf("voice", "steer", "resume", "approvePlan")) {
            val gateway = FakeTransport()
            val world = World(gateway)
            val withAction =
                snapshot() +
                    ("sessions" to listOf((snapshot()["sessions"] as List<Map<String, Any?>>).single() +
                        ("onDemandActions" to listOf(kind)) + mapOf("team" to mapOf("runID" to "team"))))
            val result =
                runner(world).execute(tap(kind).copy(runId = if (kind in setOf("resume", "approvePlan")) "team" else ""), withAction, deadline())
            assertEquals(kind, "unsupported", result.error)
            assertEquals(0, world.opened)
            assertFalse(gateway.closed)
            assertTrue(gateway.mutations.isEmpty())
        }
        val gateway = FakeTransport()
        val result = runner(World(gateway)).execute(tap("voice"), snapshot(), deadline())
        assertEquals("unsupported", result.error)
        assertFalse(gateway.closed)
    }

    // ---- B-ANDR-107 (amended): a mutation error is uncertain, sanitized, closed and never retried ----

    @Test
    fun `B-ANDR-107 a mutation error is uncertain, sanitized, closed and never retried`() = runTest {
        val gateway = FakeTransport().apply { failAnswer = true }
        val instance = runner(World(gateway))
        val result = instance.execute(tap(), snapshot(), deadline())
        assertEquals("uncertain", result.status)
        assertEquals("uncertainDelivery", result.error)
        assertFalse(result.toMap().toString().contains("credential"))
        instance.execute(tap(), snapshot(), deadline())
        assertEquals(1, gateway.mutations.size)
        assertTrue(gateway.closed)
    }

    @Test
    fun `B-ANDR-107 uncertainty after a sent stop is never a retryable rejection`() = runTest {
        var current = true
        val gateway = FakeTransport().apply { afterStatus = { current = false } }
        val instance = runner(World(gateway, current = { current }))
        val result = instance.execute(tap("stop"), snapshot(), deadline())
        assertEquals("uncertain", result.status)
        assertEquals("uncertainDelivery", result.error)
        assertEquals(listOf("stop:session"), gateway.mutations)
    }
}
