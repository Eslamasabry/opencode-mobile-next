package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** B-620: automation notifications rendering, grouping, capping, and Pause this automation without opening app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationAlertShadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var tag: String? = "en"
    private val words = NotifyWords(app) { tag }
    private val channels = ChannelRegistry(manager, words).also { it.ensure() }
    private val shade = AndroidAlertShade(app, words, channels)

    private val baseFacts = AlertFacts(
        liveModeActive = true,
        replyWatchActive = false,
        appVisible = false,
        permissionGranted = true,
        serverAlertsOn = true,
        quietHours = false,
        onWifi = true,
        wifiOnly = false,
    )

    private fun automationAlert(
        kind: AlertKind = AlertKind.AutomationMessage,
        automationId: String = "aut_1",
        name: String = "Nightly check",
        sessionId: String = "ses_1",
        slot: Int = 0,
        detail: String = "Found 2 failing tests",
        coveredRuns: Int = 1,
        silentUpdate: Boolean = false,
        token: String = "tok_1",
    ): CodingAlert {
        val group = AlertGroups.automation("p1", automationId)
        return CodingAlert(
            kind = kind,
            sessionId = sessionId,
            key = AlertKeys.automation("p1", automationId, slot),
            requestId = automationId,
            profileId = "p1",
            subtext = "Home server",
            chatTitle = name,
            detail = NotificationText.redact(detail),
            allowActions = automationId.isNotBlank(),
            actionToken = token,
            automationGroup = group,
            automationCoveredRuns = coveredRuns,
            automationSilentUpdate = silentUpdate,
        )
    }

    @AfterTest
    fun noTrampoline() {
        assertNull(shadowOf(app).nextStartedActivity, "shade pause must never start an activity")
    }

    @Test
    fun `B-620 automation alert names automation and what happened with Open and Pause this automation actions`() {
        val alert = automationAlert(detail = "Found issue with token=sk-secret99999999")
        val route = AlertPolicy.route(alert, baseFacts) as AlertRoute.Post
        assertEquals(AlertGroups.automation("p1", "aut_1"), route.group)
        assertEquals(AlertShown.Shown, shade.show(alert, route))

        val posted = shadowOf(manager).getNotification(NotificationIds.alert(alert.key))
        assertNotNull(posted)
        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("Nightly check" in title, title)
        assertTrue("Found issue" in text, text)
        assertFalse("sk-secret" in title + text)
        // Lock screen stays fixed and nameless (B-SEC-105).
        val publicTitle = posted.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertFalse("Nightly check" in publicTitle)

        assertEquals(listOf("Open", "Pause this automation"), posted.actions.map { it.title.toString() })
        assertTrue(shadowOf(posted.actions[0].actionIntent).isActivityIntent)
        val pauseAction = posted.actions[1]
        assertTrue(pauseAction.isAuthenticationRequired)
        val pausePending = shadowOf(pauseAction.actionIntent)
        assertTrue(pausePending.isBroadcastIntent)
        assertTrue(pausePending.isImmutable)
        val pauseIntent = pausePending.savedIntent
        assertEquals(FrozenComponents.CODING_ACTION_RECEIVER, pauseIntent.component!!.className)
        assertEquals(
            ShadeDecision.PauseAutomation.wire,
            pauseIntent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_DECISION),
        )
        assertEquals("aut_1", pauseIntent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID))
        assertEquals("tok_1", pauseIntent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_TOKEN))
    }

    @Test
    fun `B-620 failed and paused automation alerts name automation and what happened`() {
        val failed = automationAlert(kind = AlertKind.AutomationFailed, detail = "")
        val failRoute = AlertPolicy.route(failed, baseFacts) as AlertRoute.Post
        assertEquals(NotifyChannel.CodingAction, failRoute.channel)
        assertEquals(AlertShown.Shown, shade.show(failed, failRoute))
        val failN = shadowOf(manager).getNotification(NotificationIds.alert(failed.key))
        assertEquals("Nightly check failed", failN.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("The automation run failed.", failN.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

        val paused = automationAlert(kind = AlertKind.AutomationPaused, slot = 1, detail = "")
        val pauseRoute = AlertPolicy.route(paused, baseFacts) as AlertRoute.Post
        assertEquals(AlertShown.Shown, shade.show(paused, pauseRoute))
        val pauseN = shadowOf(manager).getNotification(NotificationIds.alert(paused.key))
        assertEquals("Nightly check paused", pauseN.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("This automation was paused.", pauseN.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `B-620 noisy automation groups by automation, caps child notifications, and summary states covered runs`() {
        val group1 = AlertGroups.automation("p1", "aut_1")
        for (run in 1..6) {
            val slot = (run - 1).coerceAtMost(2)
            val alert = automationAlert(
                automationId = "aut_1",
                name = "Nightly check",
                slot = slot,
                detail = "Finding #$run",
                coveredRuns = run,
                silentUpdate = run > 3,
                token = "tok_$run",
            )
            val route = AlertPolicy.route(alert, baseFacts) as AlertRoute.Post
            assertEquals(AlertShown.Shown, shade.show(alert, route))
        }
        val group1Children = manager.activeNotifications.filter {
            it.notification.group == group1 && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        assertEquals(3, group1Children.size, "capped at 3 child notifications for one automation")

        val summary = shadowOf(manager).getNotification(NotificationIds.automationSummary(group1))
        assertNotNull(summary)
        assertTrue((summary.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
        assertEquals("Nightly check · 6 runs", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        // Another automation has its own separate group.
        val other = automationAlert(automationId = "aut_2", name = "PR triage", slot = 0, coveredRuns = 1)
        val otherRoute = AlertPolicy.route(other, baseFacts) as AlertRoute.Post
        assertEquals(AlertShown.Shown, shade.show(other, otherRoute))
        assertTrue(otherRoute.group != group1)
    }

    @Test
    fun `B-620 Pause this automation calls pause API without opening app and dismisses group`() = runTest {
        val alert = automationAlert(automationId = "aut_1", coveredRuns = 2, slot = 0)
        val alert2 = automationAlert(automationId = "aut_1", coveredRuns = 2, slot = 1, token = "tok_2")
        shade.show(alert, AlertPolicy.route(alert, baseFacts) as AlertRoute.Post)
        shade.show(alert2, AlertPolicy.route(alert2, baseFacts) as AlertRoute.Post)

        val posted = shadowOf(manager).getNotification(NotificationIds.alert(alert2.key))
        val pauseIntent = shadowOf(posted.actions[1].actionIntent).savedIntent

        val pausedCalls = mutableListOf<Triple<ProfileId, String, String>>()
        val lock = object : DeviceLock {
            override val sdkInt: Int = 35
            override fun isLocked(): Boolean = false
        }
        val body = NotificationActionBody(
            shade = shade,
            answerer = { Unit.ok() },
            stopper = { Unit.ok() },
            lock = lock,
            automationPauser = { profile, automationId, token ->
                pausedCalls += Triple(profile, automationId, token)
                Unit.ok()
            },
        )
        assertEquals(ShadeOutcome.AutomationPaused, body.onCodingAction(pauseIntent))
        assertEquals(listOf(Triple(ProfileId("p1"), "aut_1", "tok_2")), pausedCalls)
        assertNull(shadowOf(app).nextStartedActivity)

        val remaining = manager.activeNotifications.filter {
            it.notification.group == AlertGroups.automation("p1", "aut_1")
        }
        assertTrue(remaining.isEmpty(), "pausing from notification dismisses the automation group")

        // Locked phone on API 30 refuses pause without calling API and reposts open-only.
        shade.show(alert, AlertPolicy.route(alert, baseFacts) as AlertRoute.Post)
        val lockedRow = shadowOf(manager).getNotification(NotificationIds.alert(alert.key))
        val lockedIntent = shadowOf(lockedRow.actions[1].actionIntent).savedIntent
        val lockedBody = NotificationActionBody(
            shade = shade,
            answerer = { Unit.ok() },
            stopper = { Unit.ok() },
            lock = object : DeviceLock {
                override val sdkInt: Int = 30
                override fun isLocked(): Boolean = true
            },
            automationPauser = { _, _, _ -> error("must not pause while locked on API 30") },
        )
        assertEquals(ShadeOutcome.RepostedOpen, lockedBody.onCodingAction(lockedIntent))

        // Offline / network failure reposts open-only instead of pretending paused.
        shade.show(alert, AlertPolicy.route(alert, baseFacts) as AlertRoute.Post)
        val offlineRow = shadowOf(manager).getNotification(NotificationIds.alert(alert.key))
        val offlineIntent = shadowOf(offlineRow.actions[1].actionIntent).savedIntent
        val offlineBody = NotificationActionBody(
            shade = shade,
            answerer = { Unit.ok() },
            stopper = { Unit.ok() },
            lock = lock,
            automationPauser = { _, _, _ -> Outcome.Failed(Failure.Network()) },
        )
        assertEquals(ShadeOutcome.RepostedOpen, offlineBody.onCodingAction(offlineIntent))
    }
}
