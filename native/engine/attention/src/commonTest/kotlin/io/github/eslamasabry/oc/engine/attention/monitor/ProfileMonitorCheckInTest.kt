package io.github.eslamasabry.oc.engine.attention.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMonitorCheckInTest {
    private val rules = MonitorNotifyRules(enabled = true, checkInAfterMinutes = 30)

    private class CheckIn(statuses: Map<String, String> = mapOf("ses-1" to "busy")) {
        val h = Harness(count = 1)
        val statuses = statuses.toMutableMap()
        var statusFailure = false

        init {
            h.clock.now = at("2026-09-08T09:00:00Z")
            h.gateway = { TestGateway(statuses = this.statuses.toMap(), statusFailure = statusFailure) }
        }

        fun create(scope: TestScope) = h.create(scope, backgroundInterval = 1.minutes)

        fun busy() = h.prefs.getString(ProfileMonitor.busyIntervalsKey("profile-1"))

        fun lastToken(): String = parseObject(h.prefs.getString(ProfileMonitor.routesKey("profile-1")) ?: "{}")!!.keys.last()
    }

    private suspend fun ProfileMonitor.pollEvery(c: CheckIn, times: Int, step: Duration = 15.minutes) {
        repeat(times) {
            refresh()
            c.h.clock.advance(step)
        }
    }

    @Test
    fun `B-ANDR-058 the check-in rule is off by default and round-trips through JSON`() {
        assertNull(MonitorNotifyRules().checkInAfterMinutes)
        assertNull(MonitorNotifyRules().checkInAfter)
        val decoded = MonitorNotifyRules.fromJson(rules.toJson())!!
        assertEquals(30, decoded.checkInAfterMinutes)
        assertEquals(30.minutes, decoded.checkInAfter)
        assertNull(MonitorNotifyRules.fromJson("""{"enabled":true}""")!!.checkInAfterMinutes)
        assertNull(MonitorNotifyRules.fromJson("""{"checkInAfterMinutes":-5}""")!!.checkInAfterMinutes)
        assertNull(rules.copy(checkInAfterMinutes = null).checkInAfterMinutes)
    }

    @Test
    fun `B-ANDR-058 a busy session becomes an observed interval and reminds once`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            var snapshot = monitor.snapshotFor("profile-1")
            assertEquals("ses-1", snapshot.busyIntervals.single().sessionId)
            assertEquals("Title of ses-1", snapshot.busyIntervals.single().title)
            assertEquals(Duration.ZERO, snapshot.busyIntervals.single().observedFor)
            assertTrue(snapshot.dueCheckIns(rules).isEmpty())
            assertEquals(0, snapshot.pendingCount, "busy sessions are not pending attention")
            assertTrue(c.h.alerts.requests.isEmpty())

            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(14.minutes)
            monitor.refresh()
            assertTrue(c.h.alerts.requests.isEmpty(), "29 minutes is not due")

            c.h.clock.advance(1.minutes)
            monitor.refresh()
            snapshot = monitor.snapshotFor("profile-1")
            assertEquals("ses-1", snapshot.dueCheckIns(rules).single().sessionId)
            val alert = c.h.alerts.requests.single()
            assertEquals(MonitoredRequestKind.CheckIn, alert.kind)
            assertEquals("ses-1", alert.sessionId)
            assertEquals("Title of ses-1", alert.title)
            assertEquals(MonitoredRequestKind.CheckIn, monitor.routeForToken("profile-1", c.lastToken())?.kind)

            c.h.clock.advance(5.minutes)
            monitor.refresh()
            c.h.clock.advance(5.minutes)
            monitor.refresh()
            assertEquals(1, c.h.alerts.requests.size)
            assertEquals(40.minutes, monitor.snapshotFor("profile-1").busyIntervals.single().observedFor)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-058 observed idle ends the interval and retires its reminder and a later busy interval reminds again`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            assertEquals(1, c.h.alerts.requests.size)
            val firstKey = ProfileMonitor.alertKey("profile-1", c.h.alerts.requests.single())

            c.statuses["ses-1"] = "idle"
            c.h.clock.advance(1.minutes)
            monitor.refresh()
            assertTrue(monitor.snapshotFor("profile-1").busyIntervals.isEmpty())
            assertTrue(firstKey in c.h.alerts.dismissed)
            assertNull(c.busy())

            c.statuses["ses-1"] = "busy"
            c.h.clock.advance(1.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            assertEquals(2, c.h.alerts.requests.size)
            assertNotEquals(firstKey, ProfileMonitor.alertKey("profile-1", c.h.alerts.requests.last()))
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-058 a failed poll neither ends nor extends the interval and absence ends it`() =
        runTest {
            val c = CheckIn(mapOf("ses-1" to "busy", "ses-2" to "busy"))
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.refresh()
            val started = c.h.clock.now

            c.statusFailure = true
            c.h.clock.advance(10.minutes)
            monitor.refresh()
            assertEquals(MonitorStatus.Unavailable, monitor.snapshotFor("profile-1").status)

            c.statusFailure = false
            c.statuses.remove("ses-2")
            c.h.clock.now = started + 13.minutes
            monitor.refresh()
            val interval = monitor.snapshotFor("profile-1").busyIntervals.single()
            assertEquals("ses-1", interval.sessionId)
            assertEquals(started, interval.firstObservedBusyAt)
            assertEquals(13.minutes, interval.observedFor)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-058 a gap beyond the observation limit restarts the interval`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            c.h.clock.advance(20.minutes)
            monitor.refresh()
            assertEquals(20.minutes, monitor.snapshotFor("profile-1").busyIntervals.single().observedFor)
            c.h.clock.advance(ProfileMonitor.BUSY_OBSERVATION_GAP + 1.minutes)
            monitor.refresh()
            val restarted = monitor.snapshotFor("profile-1").busyIntervals.single()
            assertEquals(c.h.clock.now, restarted.firstObservedBusyAt)
            assertEquals(Duration.ZERO, restarted.observedFor)
            assertTrue(c.h.alerts.requests.isEmpty())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-058 intervals and posted reminders survive a restart without reminding twice`() =
        runTest {
            val c = CheckIn()
            var monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            assertEquals(1, c.h.alerts.requests.size)
            monitor.dispose()

            monitor = c.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            c.h.clock.advance(5.minutes)
            monitor.refresh()
            assertEquals(35.minutes, monitor.snapshotFor("profile-1").busyIntervals.single().observedFor)
            assertEquals(1, c.h.alerts.requests.size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-059 no reminder in the foreground, during quiet hours or when the rule is off`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            assertEquals(1, monitor.snapshotFor("profile-1").dueCheckIns(rules).size, "due in the foreground")
            assertTrue(c.h.alerts.requests.isEmpty())

            monitor.setRules("profile-1", rules.copy(quietStart = 9 * 60, quietEnd = 10 * 60))
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            c.h.clock.advance(1.minutes)
            monitor.refresh()
            assertTrue(c.h.alerts.requests.isEmpty(), "quiet hours")

            monitor.setRules("profile-1", rules.copy(checkInAfterMinutes = null))
            c.h.clock.advance(1.minutes)
            monitor.refresh()
            val snapshot = monitor.snapshotFor("profile-1")
            assertEquals(1, snapshot.busyIntervals.size)
            assertTrue(snapshot.dueCheckIns(monitor.rulesFor("profile-1")).isEmpty())
            assertTrue(c.h.alerts.requests.isEmpty())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-059 turning the rule off retires only check-in reminders`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            val key = ProfileMonitor.alertKey("profile-1", c.h.alerts.requests.single())
            assertTrue(ProfileMonitor.isCheckInAlertKey(key))
            assertFalse(ProfileMonitor.isCheckInAlertKey(ProfileMonitor.alertKey("profile-1", MonitoredRequest("request-1", "ses-1", MonitoredRequestKind.Permission))))
            monitor.setRules("profile-1", rules.copy(checkInAfterMinutes = null))
            assertTrue(key in c.h.alerts.dismissed)
            assertFalse(key in c.h.prefs.getStringList(ProfileMonitor.alertsKey("profile-1")).orEmpty())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-059 disabling monitoring forgets the intervals`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.refresh()
            assertNotNull(c.busy())
            monitor.setEnabled("profile-1", false)
            assertNull(c.busy())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-059 toggling the rule to clear notification keys does not repeat its interval`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            monitor.setRules("profile-1", rules.copy(checkInAfterMinutes = null))
            monitor.setRules("profile-1", rules.copy(checkInAfterMinutes = 15))
            monitor.refresh()
            assertEquals(1, c.h.alerts.requests.size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-059 the same session in another workspace starts a new observation`() =
        runTest {
            val c = CheckIn()
            c.h.profiles.locations["profile-1"] = MonitorLocation("/project", "a")
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.profiles.locations["profile-1"] = MonitorLocation("/project", "b")
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            val interval = monitor.snapshotFor("profile-1").busyIntervals.single()
            assertEquals("b", interval.workspace)
            assertEquals(Duration.ZERO, interval.observedFor)
            assertTrue(c.h.alerts.requests.isEmpty())
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-060 the claim is durable before native dispatch and a refused alert is not retried after restart`() =
        runTest {
            val c = CheckIn()
            c.h.alerts.result = {
                val saved = c.busy()!!
                assertTrue(saved.contains("\"reminderClaimed\":true"))
                assertFalse(saved.contains("Title of"))
                false
            }
            var monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            assertEquals(1, c.h.alerts.requests.size)
            monitor.dispose()
            monitor = c.create(this)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            assertEquals(1, c.h.alerts.requests.size)
            assertEquals(1, monitor.snapshotFor("profile-1").dueCheckIns(rules).size)
            monitor.dispose()
        }

    private fun foregroundReturn(claim: Boolean) =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.refresh()
            c.h.clock.advance(15.minutes)
            monitor.refresh()
            c.h.clock.advance(15.minutes)

            var hold = true
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            c.h.prefs.beforeWrite = { key, value ->
                val target =
                    if (claim) key == ProfileMonitor.busyIntervalsKey("profile-1") && value.toString().contains("\"reminderClaimed\":true")
                    else key == ProfileMonitor.routesKey("profile-1")
                if (hold && target) {
                    hold = false
                    entered.complete(Unit)
                    release.await()
                }
            }
            val read = async { monitor.refresh() }
            entered.await()
            monitor.setRuntime(foreground = true, backgroundAllowed = true)
            release.complete(Unit)
            read.await()
            monitor.refresh()
            assertTrue(c.h.alerts.requests.isEmpty())
            val stored = c.busy().orEmpty()
            assertEquals(claim, stored.contains("\"reminderClaimed\":true"))
            assertEquals(1, monitor.snapshotFor("profile-1").dueCheckIns(rules).size)

            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            c.h.clock.advance(monitor.foregroundInterval)
            monitor.refresh()
            // A saved claim is never undone; an aborted route write had not claimed, so later delivery stays possible.
            assertEquals(if (claim) 0 else 1, c.h.alerts.requests.size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-060 a foreground return during the route write prevents dispatch and keeps the interval claimable`() = foregroundReturn(claim = false)

    @Test
    fun `B-ANDR-060 a foreground return during the claim write prevents dispatch and the claim is never undone`() = foregroundReturn(claim = true)

    @Test
    fun `B-ANDR-060 a refused durable claim keeps the due row but sends no native alert`() =
        runTest {
            val c = CheckIn()
            c.h.prefs.refuse = { key, value -> key == ProfileMonitor.busyIntervalsKey("profile-1") && value.toString().contains("\"reminderClaimed\":true") }
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            monitor.refresh()
            assertTrue(c.h.alerts.requests.isEmpty())
            assertEquals(1, monitor.snapshotFor("profile-1").dueCheckIns(rules).size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-060 failed dispatch claims do not starve later sessions past the batch limit`() =
        runTest {
            val c = CheckIn((0 until 9).associate { "ses-$it" to "busy" })
            c.h.alerts.result = { false }
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            assertEquals(8, c.h.alerts.requests.size)
            monitor.refresh()
            assertEquals(9, c.h.alerts.requests.size)
            assertEquals(9, c.h.alerts.requests.map { it.sessionId }.toSet().size)
            monitor.dispose()
        }

    @Test
    fun `B-ANDR-060 profile deletion sweeps the interval, rules and route data`() =
        runTest {
            val c = CheckIn()
            val monitor = c.create(this)
            monitor.setRules("profile-1", rules)
            monitor.setRuntime(foreground = false, backgroundAllowed = true)
            monitor.pollEvery(c, 3)
            monitor.removeProfile("profile-1")
            monitor.drain("profile-1")
            monitor.dispose()
            // The deletion sweep removes every registered per-profile key, plus the consent record.
            (ProfileMonitor.profileScopedKeys("profile-1") + c.h.consent.key("profile-1")).forEach { c.h.prefs.remove(it) }
            assertTrue(c.h.prefs.values.keys.none { it.endsWith(".profile-1") }, c.h.prefs.values.keys.toString())
        }
}
