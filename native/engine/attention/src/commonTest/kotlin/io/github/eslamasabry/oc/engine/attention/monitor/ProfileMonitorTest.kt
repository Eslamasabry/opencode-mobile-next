package io.github.eslamasabry.oc.engine.attention.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMonitorTest {
    @Test
    fun `B-ANDR-062 enabled legacy Codex rules stay unavailable without transport, Wi-Fi probe or alerts`() =
        runTest {
            val h = Harness(count = 1)
            h.profiles.edit("profile-1") { it.copy(backend = MonitorBackend.Codex) }
            h.legacyConsent()
            var wifiProbes = 0
            h.wifi = {
                wifiProbes++
                true
            }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            assertFalse(monitor.supportsProfile(h.profiles.list.single()))
            assertEquals(0, h.factories)
            assertEquals(0, wifiProbes)
            assertTrue(h.alerts.posted.isEmpty())
            assertEquals(MonitorStatus.Unavailable, monitor.snapshotFor("profile-1").status)
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            assertEquals(1, monitor.unknownProfileCount)
            assertTrue(monitor.rulesFor("profile-1").enabled)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-062 monitoring is off by default, an explicit opt-in persists and zero is a known observation`() =
        runTest {
            val h = Harness()
            var monitor = h.create(this)
            monitor.refresh()
            assertEquals(0, h.factories)
            assertEquals(2, monitor.unknownProfileCount)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(0, monitor.snapshotFor("profile-1").pendingCount)
            assertEquals(1, monitor.unknownProfileCount)
            monitor.dispose()
            monitor = h.create(this)
            assertTrue(monitor.rulesFor("profile-1").enabled)
            monitor.refresh()
            assertEquals(2, h.factories)
            assertFalse(h.prefs.contains("oc.keepLiveInBackground"))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-062 Wi-Fi false and a thrown network policy both pause without opening transport`() =
        runTest {
            val h = Harness(count = 1)
            var throws = false
            h.wifi = {
                if (throws) error("network plugin")
                false
            }
            val monitor = h.create(this)
            monitor.setRules("profile-1", MonitorNotifyRules(enabled = true, wifiOnly = true))
            monitor.refresh()
            throws = true
            monitor.refresh()
            assertEquals(0, h.factories)
            assertEquals(MonitorStatus.WifiRequired, monitor.snapshotFor("profile-1").status)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-062 failure is unknown with backoff, and an expired success or changed location is unknown`() =
        runTest {
            val h = Harness(count = 1)
            var fail = true
            h.gateway = { TestGateway(failure = fail) }
            val monitor = h.create(this)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            assertEquals(MonitorStatus.Unavailable, monitor.snapshotFor("profile-1").status)
            monitor.refresh()
            assertEquals(1, h.factories, "backoff skips the next poll")
            h.clock.advance(3.minutes)
            fail = false
            monitor.refresh()
            assertEquals(2, h.factories)
            assertEquals(0, monitor.snapshotFor("profile-1").pendingCount)
            h.profiles.locations["profile-1"] = MonitorLocation(directory = "/changed")
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            h.profiles.locations.remove("profile-1")
            h.clock.advance(3.minutes)
            assertNull(monitor.snapshotFor("profile-1").pendingCount, "older than twice the interval")
            assertFalse(monitor.snapshots().keys.joinToString().contains("SENSITIVE"))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-062 background needs an existing active service and never enables keep-live`() =
        runTest {
            val h = Harness(count = 1)
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = false)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(0, h.factories)
            assertEquals(MonitorStatus.Paused, monitor.snapshotFor("profile-1").status)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            assertEquals(1, h.factories)
            assertFalse(h.prefs.contains("oc.keepLiveInBackground"))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-063 the same session ids on two profiles get distinct alerts and durable opaque routes`() =
        runTest {
            val h = Harness()
            h.gateway = { TestGateway(requests = listOf(pending(1))) }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            monitor.setEnabled("profile-2", true)
            monitor.refresh()
            val posted = h.alerts.posted
            assertEquals(2, posted.size)
            assertNotEquals(posted[0].second, posted[1].second)
            for ((id, _, token) in posted) {
                assertEquals("same-session", monitor.routeForToken(id, token)?.sessionId)
                assertNull(monitor.routeForToken(if (id == "profile-1") "profile-2" else "profile-1", token))
            }
            val stored = h.prefs.getString(ProfileMonitor.routesKey("profile-1"))!!
            assertFalse(stored.contains("Private title"))
            h.clock.advance(2.days)
            assertNull(monitor.routeForToken(posted[0].first, posted[0].third), "older than a day")
            h.clock.advance((-2).days)
            h.profiles.edit("profile-1") { it.copy(username = "replacement principal") }
            val restored = h.create(this)
            assertNull(restored.routeForToken(posted[0].first, posted[0].third), "a recreated monitor rejects the prior principal's route")
            monitor.dispose()
            restored.dispose()
        }

    @Test
    fun `B-ANDR-063 quiet hours wrap midnight and requests beyond the first eight eventually alert`() =
        runTest {
            val h = Harness(count = 1)
            h.clock.now = at("2026-09-07T23:00:00Z")
            h.gateway = { TestGateway(requests = List(10) { pending(it) }) }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setRules("profile-1", MonitorNotifyRules(enabled = true, quietStart = 22 * 60, quietEnd = 8 * 60))
            monitor.refresh()
            assertTrue(h.alerts.posted.isEmpty())
            h.clock.now = at("2026-09-08T09:00:00Z")
            monitor.refresh()
            assertEquals(8, h.alerts.posted.size)
            h.clock.advance(6.minutes)
            monitor.refresh()
            assertEquals(10, h.alerts.posted.map { it.second }.toSet().size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-063 a consent change during a failed alert stops retries before the next post`() =
        runTest {
            val h = Harness(count = 1)
            var allowed = true
            h.alertsAllowed = { allowed }
            h.gateway = { TestGateway(requests = List(2) { pending(it) }) }
            h.alerts.result = {
                allowed = false
                false
            }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(1, h.alerts.posted.size)
            assertNull(h.prefs.getStringList(ProfileMonitor.alertsKey("profile-1")))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-063 quiet hours starting during a failed alert stop retries before the next post`() =
        runTest {
            val h = Harness(count = 1)
            h.gateway = { TestGateway(requests = List(2) { pending(it) }) }
            h.alerts.result = {
                h.clock.now = at("2026-09-07T23:00:00Z")
                false
            }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setRules("profile-1", MonitorNotifyRules(enabled = true, quietStart = 22 * 60, quietEnd = 8 * 60))
            monitor.refresh()
            assertEquals(1, h.alerts.posted.size)
            assertNull(h.prefs.getStringList(ProfileMonitor.alertsKey("profile-1")))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-063 a failed alert dismissal keeps its key for a later successful retry`() =
        runTest {
            val h = Harness(count = 1)
            h.prefs.putStringList(ProfileMonitor.alertsKey("profile-1"), listOf("monitor:profile-1:stale"))
            h.alerts.dismissResult = { h.alerts.dismissed.size > 1 }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(listOf("monitor:profile-1:stale"), h.alerts.dismissed)
            assertEquals(listOf("monitor:profile-1:stale"), h.prefs.getStringList(ProfileMonitor.alertsKey("profile-1")))
            h.clock.advance(6.minutes)
            monitor.refresh()
            assertEquals(listOf("monitor:profile-1:stale", "monitor:profile-1:stale"), h.alerts.dismissed)
            assertEquals(emptyList(), h.prefs.getStringList(ProfileMonitor.alertsKey("profile-1")))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-064 a timed-out poll cannot publish its late attention result`() =
        runTest {
            val h = Harness(count = 1)
            val late = CompletableDeferred<List<MonitorPending>>()
            h.gateway = { TestGateway(pendingRead = late) }
            val monitor = h.create(this, timeout = 1.milliseconds)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertTrue((h.lastGateway as TestGateway).isClosed)
            assertEquals(MonitorStatus.Unavailable, monitor.snapshotFor("profile-1").status)
            late.complete(listOf(pending(1)))
            monitor.drain("profile-1")
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-064 deletion cancels admission and sweeps settings, alert routing and late results`() =
        runTest {
            val h = Harness(count = 1)
            val late = CompletableDeferred<List<MonitorPending>>()
            h.gateway = { TestGateway(pendingRead = late) }
            val monitor = h.create(this)
            monitor.setEnabled("profile-1", true)
            val read = launch { monitor.refresh() }
            runCurrent()
            monitor.removeProfile("profile-1")
            assertTrue((h.lastGateway as TestGateway).isClosed)
            late.complete(listOf(pending(1)))
            monitor.drain("profile-1")
            read.join()
            assertFalse("profile-1" in monitor.snapshots())
            val scoped = ProfileMonitor.profileScopedKeys("profile-1")
            assertTrue(ProfileMonitor.rulesKey("profile-1") in scoped)
            scoped.forEach { h.prefs.remove(it) }
            assertNull(h.prefs.getString(ProfileMonitor.rulesKey("profile-1")))
            assertTrue(scoped.all { it.startsWith("oc.") && it.endsWith(".profile-1") })
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-064 same-id source edits invalidate rows, backoff and old alert routes`() =
        runTest {
            val h = Harness(count = 1)
            h.gateway = { TestGateway(requests = listOf(pending(1))) }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(1, monitor.snapshotFor("profile-1").pendingCount)
            val oldToken = h.alerts.posted.single().third
            h.profiles.edit("profile-1") { it.copy(baseUrl = "https://replacement.example") }
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            assertNull(monitor.routeForToken("profile-1", oldToken))
            monitor.refresh()
            assertEquals(2, h.factories, "the old source's backoff cannot suppress the new source")
            assertTrue(h.alerts.dismissed.isNotEmpty())
            assertNull(monitor.routeForToken("profile-1", oldToken))
            h.profiles.edit("profile-1") { it.copy(credential = "synthetic replacement credential") }
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            assertNull(monitor.routeForToken("profile-1", h.alerts.posted.last().third))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-064 a clock rollback makes snapshots unknown and refuses future-dated routes`() =
        runTest {
            val h = Harness(count = 1)
            h.gateway = { TestGateway(requests = listOf(pending(1))) }
            val monitor = h.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.setEnabled("profile-1", true)
            monitor.refresh()
            assertEquals(1, monitor.snapshotFor("profile-1").pendingCount)
            val token = h.alerts.posted.single().third
            h.clock.advance((-1).minutes)
            assertNull(monitor.snapshotFor("profile-1").pendingCount)
            assertNull(monitor.routeForToken("profile-1", token))
            monitor.dispose()
        }
}
