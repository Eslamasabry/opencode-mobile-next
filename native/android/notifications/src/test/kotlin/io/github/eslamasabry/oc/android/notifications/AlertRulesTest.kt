package io.github.eslamasabry.oc.android.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure alert rules: validity, posting policy, copy and buttons (plan 11 §10.2, §10.4). */
class AlertRulesTest {
    private val facts =
        AlertFacts(
            liveModeActive = true, replyWatchActive = false, appVisible = false, permissionGranted = true,
            serverAlertsOn = true, quietHours = false, onWifi = true, wifiOnly = false,
        )

    private fun permission(detail: String = "npm install --save x", request: String = "per_1") =
        CodingAlert(
            AlertKind.Permission, "ses_1", AlertKeys.input("p1", "ses_1"), requestId = request, profileId = "p1",
            detail = detail, ask = PermissionAsk(AskAction.Run, "npm install"),
        )

    private fun of(kind: AlertKind) = CodingAlert(kind, "ses_1", AlertKeys.status("p1", "ses_1"), profileId = "p1")

    @Test
    fun `plan 11 10_2 alert ids are copied verbatim so Flutter-era alerts are dismissed by id`() {
        val key = "input:p1:ses_1"
        assertEquals(6000 + (key.hashCode() and 0x0fffffff), NotificationIds.alert(key))
        assertEquals(4747, NotificationIds.STATUS)
        assertEquals(5998, NotificationIds.NEEDS_YOU_SUMMARY)
        assertEquals(5999, NotificationIds.FINISHED_SUMMARY)
        assertEquals(NotificationIds.alert(key) * 4 + 2, NotificationIds.actionRequestCode(NotificationIds.alert(key), 1))
    }

    @Test
    fun `B-ANDR-013 coding alerts are never posted when neither live mode nor reply watch is on`() {
        val off = facts.copy(liveModeActive = false, replyWatchActive = false)
        assertEquals(AlertRoute.Skip(SkipWhy.NoWatch), AlertPolicy.route(permission(), off))
        assertTrue(AlertPolicy.route(permission(), off.copy(replyWatchActive = true)) is AlertRoute.Post)
    }

    @Test
    fun `B-ANDR-013 a test notification ignores the live-mode gate but not the platform permission`() {
        val off = facts.copy(liveModeActive = false)
        assertEquals(AlertRoute.Post(NotifyChannel.CodingAction, null), AlertPolicy.route(of(AlertKind.Test), off))
        assertEquals(AlertRoute.Skip(SkipWhy.NoPermission), AlertPolicy.route(of(AlertKind.Test), off.copy(permissionGranted = false)))
    }

    @Test
    fun `B-ANDR-039 in quiet hours requests and failures post silently and a finished reply is not posted`() {
        val quiet = facts.copy(quietHours = true)
        assertEquals(AlertRoute.Post(NotifyChannel.CodingActionQuiet, AlertGroups.NEEDS_YOU), AlertPolicy.route(permission(), quiet))
        assertEquals(AlertRoute.Post(NotifyChannel.CodingActionQuiet, null), AlertPolicy.route(of(AlertKind.Error), quiet))
        assertEquals(AlertRoute.Skip(SkipWhy.QuietHours), AlertPolicy.route(of(AlertKind.Complete), quiet))
        // Outside quiet hours requests are not silenced.
        assertEquals(AlertRoute.Post(NotifyChannel.CodingAction, AlertGroups.NEEDS_YOU), AlertPolicy.route(permission(), facts))
        assertEquals(AlertRoute.Post(NotifyChannel.CodingStatus, AlertGroups.FINISHED), AlertPolicy.route(of(AlertKind.Complete), facts))
    }

    @Test
    fun `B-ANDR-059 no check-in reminder in the foreground or during quiet hours`() {
        assertEquals(AlertRoute.Skip(SkipWhy.AppVisible), AlertPolicy.route(of(AlertKind.CheckIn), facts.copy(appVisible = true)))
        assertEquals(AlertRoute.Skip(SkipWhy.QuietHours), AlertPolicy.route(of(AlertKind.CheckIn), facts.copy(quietHours = true)))
        assertEquals(AlertRoute.Post(NotifyChannel.CodingStatus, null), AlertPolicy.route(of(AlertKind.CheckIn), facts))
    }

    @Test
    fun `B-ANDR-097 quota alerts wait for Wi-Fi when set and unknown never counts as Wi-Fi`() {
        val token = "a".repeat(64)
        val quota = CodingAlert(AlertKind.Quota, "quota", "quota:p1", profileId = "p1", monitorToken = token)
        val wifiOnly = facts.copy(wifiOnly = true)
        assertEquals(AlertRoute.Skip(SkipWhy.NotOnWifi), AlertPolicy.route(quota, wifiOnly.copy(onWifi = null)))
        assertEquals(AlertRoute.Skip(SkipWhy.NotOnWifi), AlertPolicy.route(quota, wifiOnly.copy(onWifi = false)))
        assertEquals(AlertRoute.Post(NotifyChannel.CodingStatus, null), AlertPolicy.route(quota, wifiOnly))
    }

    @Test
    fun `B-ANDR-011 sub-agent sessions stay quiet and the open app gets no alert`() {
        assertEquals(AlertRoute.Skip(SkipWhy.SubAgent), AlertPolicy.route(of(AlertKind.Complete), facts.copy(subAgentSession = true)))
        assertEquals(AlertRoute.Skip(SkipWhy.AppVisible), AlertPolicy.route(permission(), facts.copy(appVisible = true)))
        assertEquals(AlertRoute.Skip(SkipWhy.ServerAlertsOff), AlertPolicy.route(permission(), facts.copy(serverAlertsOn = false)))
    }

    @Test
    fun `plan 11 10_4 validity needs a session and key, and quota needs the quota session and a 64-hex token`() {
        assertFalse(CodingAlert(AlertKind.Complete, " ", "k").isValid)
        assertFalse(CodingAlert(AlertKind.Complete, "s", "").isValid)
        assertFalse(CodingAlert(AlertKind.Quota, "ses_1", "k", profileId = "p", monitorToken = "a".repeat(64)).isValid)
        assertFalse(CodingAlert(AlertKind.Quota, "quota", "k", profileId = "p", monitorToken = "A".repeat(64)).isValid)
        assertTrue(CodingAlert(AlertKind.Quota, "quota", "k", profileId = "p", monitorToken = "0f".repeat(32)).isValid)
        assertEquals(AlertRoute.Skip(SkipWhy.Invalid), AlertPolicy.route(CodingAlert(AlertKind.Complete, "", "k"), facts))
    }

    @Test
    fun `plan 11 10_4 only a validated short agent name enters a template`() {
        assertEquals("Claude 4.1", of(AlertKind.CheckIn).copy(agentName = "Claude 4.1").safeAgentName)
        assertEquals("OpenCode", of(AlertKind.CheckIn).copy(agentName = "<b>evil</b>").safeAgentName)
        assertEquals("OpenCode", of(AlertKind.CheckIn).copy(agentName = "x".repeat(33)).safeAgentName)
    }

    @Test
    fun `B-SEC-184 Allow once needs unlock and Reject does not, and Always allow is never offered`() {
        val buttons = AlertButtons.plan(permission())
        assertEquals(
            listOf(
                AlertButton(ShadeDecision.Allow, AlertButton.Label.AllowOnce, authRequired = true),
                AlertButton(ShadeDecision.Deny, AlertButton.Label.Reject, authRequired = false),
            ),
            buttons,
        )
        assertTrue(ShadeDecision.Allow.needsUnlock && ShadeDecision.Reply.needsUnlock && ShadeDecision.Option.needsUnlock)
        assertFalse(ShadeDecision.Deny.needsUnlock)
    }

    @Test
    fun `B-SEC-105 a permission whose command the shade cannot show only opens the app`() {
        val open = listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open))
        assertEquals(open, AlertButtons.plan(permission(detail = "")))
        assertEquals(open, AlertButtons.plan(permission(request = "")))
        assertEquals(open, AlertButtons.plan(permission().copy(ask = null)))
        assertEquals(open, AlertButtons.plan(permission().copy(allowActions = false)))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Ask), AlertCopy.of(permission(detail = "")))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Run, "npm install", "npm install --save x"), AlertCopy.of(permission()))
    }

    @Test
    fun `B-ANDR-038 as amended by CR11-16 questions only open the app and three short options are one line`() {
        val q = of(AlertKind.Question).copy(detail = "Which database?", options = listOf("Postgres", "SQLite", "None"))
        assertEquals(listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open)), AlertButtons.plan(q))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Literal, "Which database?", "Postgres · SQLite · None"), AlertCopy.of(q))
        assertEquals("", AlertCopy.choices(listOf("a", "b", "c", "d")))
        assertEquals("", AlertCopy.choices(listOf("a", "a")))
        assertEquals("", AlertCopy.choices(listOf("x".repeat(25))))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Question), AlertCopy.of(of(AlertKind.Question)))
    }

    @Test
    fun `B-ANDR-040 a finished reply names its chat`() {
        val done = of(AlertKind.Complete).copy(chatTitle = "Add combo counter", detail = "All tests pass.")
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Finished, "Add combo counter", "All tests pass."), AlertCopy.of(done))
        assertEquals("Add combo counter", AlertCopy.summaryLine(done, "Finished: Add combo counter"))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.FinishedAny, body = "x"), AlertCopy.of(of(AlertKind.Complete).copy(detail = "x")))
    }

    @Test
    fun `B-SEC-106 alerts never print their words in logs`() {
        val alert = permission(detail = "deploy --token sk-abcdefghijk")
        assertFalse("deploy" in alert.toString())
        assertFalse("sk-" in alert.toString())
        assertFalse("hello" in ShadeReply.Text("hello").toString())
    }
}
