package io.github.eslamasabry.oc.engine.attention.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMonitorAttentionTest {
    private fun failedRun(id: String, now: kotlin.time.Instant, taskId: String? = null) =
        MonitorAttentionItem(id, MonitorAttentionKind.FailedRun, MonitorWorkPhase.Failed, now, sessionId = "same-session", taskId = taskId)

    @Test
    fun `B-ANDR-056 an existing policy of off forbids transport even when the monitor rule is on`() =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent()
            h.consent.set("profile-1", false)
            val monitor = h.create(this)
            monitor.refresh()
            assertEquals(0, h.factories)
            assertEquals(MonitorStatus.Disabled, monitor.snapshotFor("profile-1").status)
            monitor.setRules("profile-1", monitor.rulesFor("profile-1").copy(notifications = false))
            monitor.refresh()
            assertEquals(0, h.factories, "an unrelated edit cannot revive a revoked policy")
            assertFalse(h.consent.allows("profile-1"))
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(1, h.factories)
            assertTrue(h.consent.allows("profile-1"))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-056 two phone defaults are not consent and cannot create transports`() =
        runTest {
            val h = Harness()
            h.profiles.list.indices.forEach { i -> h.profiles.list[i] = h.profiles.list[i].copy(baseUrl = "http://127.0.0.1:4096") }
            val monitor = h.create(this)
            assertTrue(monitor.rulesFor("profile-1").enabled, "the old two-phone-server default still reads as on")
            monitor.refresh()
            assertEquals(0, h.factories)
            assertFalse(h.consent.hasRecord("profile-1"))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-056 revoking the policy during a Wi-Fi check forbids the following transport`() =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent(rules = MonitorNotifyRules(enabled = true, wifiOnly = true))
            val wifi = CompletableDeferred<Boolean?>()
            val entered = CompletableDeferred<Unit>()
            h.wifi = {
                entered.complete(Unit)
                wifi.await()
            }
            val monitor = h.create(this)
            val read = async { monitor.refresh() }
            entered.await()
            h.consent.set("profile-1", false)
            wifi.complete(true)
            read.await()
            assertEquals(0, h.factories)
            assertEquals(MonitorStatus.Disabled, monitor.snapshotFor("profile-1").status)
            monitor.dispose()
        }

    private fun interruptTest(interruption: String) =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent()
            val late = CompletableDeferred<List<MonitorPending>>()
            val gateway = TestGateway(pendingRead = late)
            h.gateway = { gateway }
            var enriched = 0
            h.readAttention =
                MonitorAttentionReading { _, _, _, _, _ ->
                    enriched++
                    MonitorAttentionDetails(emptyList(), complete = true)
                }
            val monitor = h.create(this)
            val read = async { monitor.refresh() }
            runCurrent()
            assertEquals(1, h.factories)
            when (interruption) {
                "policy" -> {
                    h.consent.touchOther("profile-1")
                    assertFalse(gateway.isClosed, "another behaviour's change does not interrupt")
                    h.consent.set("profile-1", false)
                }
                "runtime" -> {
                    monitor.setRuntime(foreground = false, backgroundAllowed = false)
                    monitor.setRuntime(foreground = true, backgroundAllowed = false)
                }
                else -> monitor.removeProfile("profile-1")
            }
            assertTrue(gateway.isClosed)
            late.complete(listOf(pending(1)))
            read.await()
            assertEquals(0, enriched)
            assertFalse(monitor.snapshotFor("profile-1").isCurrent)
            assertTrue(monitor.snapshotFor("profile-1").requests.isEmpty())
            if (interruption == "remove") assertFalse("profile-1" in monitor.snapshots())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-057 a policy revocation interrupts an in-flight read permanently`() = interruptTest("policy")

    @Test
    fun `B-ANDR-057 a runtime pause interrupts an in-flight read permanently`() = interruptTest("runtime")

    @Test
    fun `B-ANDR-057 a profile removal interrupts an in-flight read permanently`() = interruptTest("remove")

    @Test
    fun `B-ANDR-057 failed and partial reads keep evidence stale and a location change clears it`() =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent()
            h.clock.now = at("2026-09-28T12:00:00Z")
            val firstObserved = h.clock.now
            var fail = false
            var enrichFail = false
            var clearFailure = false
            h.gateway = { TestGateway(requests = listOf(pending(1)), failure = fail) }
            h.readAttention =
                MonitorAttentionReading { _, _, _, _, _ ->
                    if (enrichFail) error("fixture technical detail")
                    if (clearFailure) {
                        MonitorAttentionDetails(emptyList(), complete = false, checkedSessionIds = setOf("same-session"))
                    } else {
                        MonitorAttentionDetails(listOf(failedRun("failed", h.clock.now)), complete = true)
                    }
                }
            val monitor = h.create(this)
            monitor.refresh()
            assertTrue(monitor.snapshotFor("profile-1").attentionComplete)
            h.clock.advance(2.minutes)
            enrichFail = true
            monitor.refresh()
            var snapshot = monitor.snapshotFor("profile-1")
            assertTrue(snapshot.isCurrent)
            assertFalse(snapshot.attentionComplete)
            assertFalse(snapshot.attention.single().isFresh)
            assertEquals(firstObserved, snapshot.attention.single().observedAt)
            h.clock.advance(2.minutes)
            fail = true
            monitor.refresh()
            snapshot = monitor.snapshotFor("profile-1")
            assertEquals(MonitorStatus.Unavailable, snapshot.status)
            assertEquals("request-1", snapshot.requests.single().id)
            assertEquals(firstObserved, snapshot.attention.single().observedAt)
            assertFalse(snapshot.isCurrent)
            fail = false
            enrichFail = false
            clearFailure = true
            h.clock.advance(3.minutes)
            monitor.refresh()
            assertTrue(monitor.snapshotFor("profile-1").attention.isEmpty())
            h.profiles.locations["profile-1"] = MonitorLocation(directory = "/another")
            assertTrue(monitor.snapshotFor("profile-1").requests.isEmpty())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-057 age expiry and a paused runtime retain rows without claiming current`() =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent()
            h.gateway = { TestGateway(requests = listOf(pending(1))) }
            val monitor = h.create(this)
            monitor.refresh()
            h.clock.advance(3.minutes)
            assertEquals(MonitorStatus.Waiting, monitor.snapshotFor("profile-1").status)
            assertEquals(1, monitor.snapshotFor("profile-1").requests.size)
            monitor.setRuntime(foreground = false, backgroundAllowed = false)
            assertEquals(MonitorStatus.Paused, monitor.snapshotFor("profile-1").status)
            assertEquals(1, monitor.snapshotFor("profile-1").requests.size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-057 session recovery keeps team evidence and team recovery clears it`() =
        runTest {
            val h = Harness(count = 1)
            h.legacyConsent()
            h.clock.now = at("2026-09-28T12:00:00Z")
            var phase = 0
            h.readAttention =
                MonitorAttentionReading { _, _, _, _, _ ->
                    MonitorAttentionDetails(
                        items = if (phase == 0) listOf(failedRun("session-failure", h.clock.now), failedRun("team-failure", h.clock.now, taskId = "task")) else emptyList(),
                        complete = phase == 0,
                        checkedSessionIds = if (phase == 1) setOf("same-session") else emptySet(),
                        teamComplete = phase == 2,
                    )
                }
            val monitor = h.create(this)
            monitor.refresh()
            assertEquals(2, monitor.snapshotFor("profile-1").attention.size)
            h.clock.advance(2.minutes)
            phase = 1
            monitor.refresh()
            assertEquals("task", monitor.snapshotFor("profile-1").attention.single().taskId)
            assertFalse(monitor.snapshotFor("profile-1").attention.single().isFresh)
            h.clock.advance(2.minutes)
            phase = 2
            monitor.refresh()
            assertTrue(monitor.snapshotFor("profile-1").attention.isEmpty())
            monitor.dispose()
        }
}
