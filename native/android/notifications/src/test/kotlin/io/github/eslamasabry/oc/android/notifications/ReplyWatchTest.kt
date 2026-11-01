package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId


import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** `ReplyWatchPolicyTest` (plan 11 §9.4): the start and end rules of reply watch (S11-4). */
class ReplyWatchPolicyTest {
    private val clean =
        ReplyWatchStartFacts(
            changingConfigurations = false, runProgressing = true, wouldAlert = true, liveModeActive = false,
            userPaused = false, ownRequestCovering = false, serverHoldsRow = false,
        )

    @Test
    fun `S11-4 a clean onStop with a reply running starts the short service`() {
        assertEquals(ReplyWatchStart.StartService, ReplyWatchPolicy.onStop(clean))
    }

    @Test
    fun `S11-4 no start while a picker, permission, settings page or installer of ours covers the app, then a start on the next clean onStop`() {
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.OwnRequestCovering), ReplyWatchPolicy.onStop(clean.copy(ownRequestCovering = true)))
        assertEquals(ReplyWatchStart.StartService, ReplyWatchPolicy.onStop(clean))
    }

    @Test
    fun `S11-4 nothing running, no alert possible, live mode on, the person's own pause or a rotation start nothing`() {
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.NothingRunning), ReplyWatchPolicy.onStop(clean.copy(runProgressing = false)))
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.WouldNotAlert), ReplyWatchPolicy.onStop(clean.copy(wouldAlert = false)))
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.LiveModeOn), ReplyWatchPolicy.onStop(clean.copy(liveModeActive = true)))
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.UserPaused), ReplyWatchPolicy.onStop(clean.copy(userPaused = true)))
        assertEquals(ReplyWatchStart.Skip(ReplyWatchSkip.ConfigurationChange), ReplyWatchPolicy.onStop(clean.copy(changingConfigurations = true)))
    }

    @Test
    fun `S11-4 with the phone server holding the row reply watch joins it with no dataSync start`() {
        assertEquals(ReplyWatchStart.JoinServerRow, ReplyWatchPolicy.onStop(clean.copy(serverHoldsRow = true)))
    }

    @Test
    fun `S11-4 the watch ends 10 s after nothing progresses, at the 2 h cap, when the app shows, or when the server leaves a joined row`() {
        val base = ReplyWatchState(0, 5_000, progressing = true, idleSinceMs = null, appVisible = false, joined = false, serverStillHoldsRow = false)
        assertNull(ReplyWatchPolicy.end(base))
        assertNull(ReplyWatchPolicy.end(base.copy(nowMs = 14_999, progressing = false, idleSinceMs = 5_000)))
        assertEquals(ReplyWatchEnd.NoProgress, ReplyWatchPolicy.end(base.copy(nowMs = 15_000, progressing = false, idleSinceMs = 5_000)))
        assertEquals(ReplyWatchEnd.HardCap, ReplyWatchPolicy.end(base.copy(nowMs = ReplyWatchPolicy.HARD_CAP_MS)))
        assertEquals(ReplyWatchEnd.AppVisible, ReplyWatchPolicy.end(base.copy(appVisible = true)))
        assertEquals(ReplyWatchEnd.ServerLeft, ReplyWatchPolicy.end(base.copy(joined = true, serverStillHoldsRow = false)))
        assertNull(ReplyWatchPolicy.end(base.copy(joined = true, serverStillHoldsRow = true)))
    }
}

/** The reply-watch service body under Robolectric's `ServiceController` (promise first, then the watch). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplyWatchServiceTest {
    /** Stands in for the `BackgroundConnectionService` shell of `:androidApp`. */
    class ShellService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }

    private val app: Application = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val words = NotifyWords(app) { "en" }
    private val registry = ChannelRegistry(manager, words).also { it.ensure() }

    private inner class Source : ReplyRunSource {
        override val progressing = MutableStateFlow(true)
        override val status = MutableStateFlow<StatusContent?>(null)
        override val alerts = MutableSharedFlow<CodingAlert>(extraBufferCapacity = 8)
        override val appVisible = MutableStateFlow(false)
        override val serverHoldsRow = MutableStateFlow(false)
        var quiet = false

        override fun alertFacts(alert: CodingAlert) =
            AlertFacts(
                liveModeActive = false, replyWatchActive = false, appVisible = appVisible.value, permissionGranted = true,
                serverAlertsOn = true, quietHours = quiet, onWifi = true, wifiOnly = false,
            )
    }

    private fun TestScope.start(source: Source): Pair<ReplyWatchServiceBody, ShellService> {
        val body =
            ReplyWatchServiceBody(
                app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source,
                scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
            )
        val service = Robolectric.buildService(ShellService::class.java).create().get()
        assertEquals(Service.START_NOT_STICKY, body.onStartCommand(service))
        return body to service
    }

    private val finished =
        CodingAlert(AlertKind.Complete, "ses_1", AlertKeys.status("p1", "ses_1"), profileId = "p1", chatTitle = "Fix login", detail = "Login works again.")

    @Test
    fun `plan 11 9_2 promise first - the status row is in the foreground before any watching`() =
        runTest {
            val (_, service) = start(Source())
            val shadow = shadowOf(service)
            assertEquals(NotificationIds.STATUS, shadow.lastForegroundNotificationId)
            val row = shadow.lastForegroundNotification
            assertEquals("Still working on your reply", row.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(row.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        }

    @Test
    fun `S11-4 the finished reply is announced by the short service and the watch ends 10 s later`() =
        runTest {
            val source = Source()
            val (body, service) = start(source)
            runCurrent()
            source.status.value = StatusContent(StatusKind.Working, "Working on Fix login", conversation = ConversationRef(ProfileId("p1"), SessionId("ses_1")), turnId = "turn_1")
            advanceTimeBy(3_000)
            source.progressing.value = false
            source.alerts.tryEmit(finished)
            runCurrent()
            val alert = shadowOf(manager).getNotification(NotificationIds.alert(finished.key))
            assertNotNull(alert)
            assertEquals("Finished: Fix login", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            advanceTimeBy(9_000)
            assertTrue(body.active)
            advanceTimeBy(2_500)
            assertFalse(body.active)
            assertEquals(ReplyWatchEnd.NoProgress, body.ended.value)
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertTrue(shadowOf(service).isForegroundStopped)
        }

    @Test
    fun `S11-4 an unanswered request cannot hold the service - not progressing once its alert is shown`() =
        runTest {
            val source = Source()
            val (body, _) = start(source)
            runCurrent()
            // The engine marks a run waiting on a shown needs-you alert as not progressing.
            source.alerts.tryEmit(CodingAlert(AlertKind.Permission, "ses_1", AlertKeys.input("p1", "ses_1"), requestId = "per_1", profileId = "p1"))
            source.progressing.value = false
            advanceTimeBy(11_500)
            assertEquals(ReplyWatchEnd.NoProgress, body.ended.value)
            assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(AlertKeys.input("p1", "ses_1"))))
        }

    @Test
    fun `B-ANDR-039 during quiet hours a finished reply is not posted by reply watch`() =
        runTest {
            val source = Source().apply { quiet = true }
            start(source)
            runCurrent()
            source.alerts.tryEmit(finished)
            runCurrent()
            assertNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
        }

    @Test
    fun `S11-4 the watch ends when the app shows, at the 2 h cap, on onTimeout, and on dismissal for that run only`() =
        runTest {
            val visible = Source()
            val (a, _) = start(visible)
            runCurrent()
            visible.appVisible.value = true
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.AppVisible, a.ended.value)

            val (b, _) = start(Source())
            advanceTimeBy(ReplyWatchPolicy.HARD_CAP_MS + 1_000)
            assertEquals(ReplyWatchEnd.HardCap, b.ended.value)

            val (c, service) = start(Source())
            runCurrent()
            c.onTimeout()
            assertEquals(ReplyWatchEnd.SystemTimeout, c.ended.value)
            assertTrue(shadowOf(service).isStoppedBySelf)

            val (d, _) = start(Source())
            runCurrent()
            d.onRowDismissed("ses_1")
            assertEquals(ReplyWatchEnd.Dismissed, d.ended.value)
        }

    @Test
    fun `S11-9 a dismissed run is not promoted again`() =
        runTest {
            val source = Source()
            val (body, _) = start(source)
            runCurrent()
            body.onRowDismissed("ses_1")
            source.status.value = StatusContent(StatusKind.Working, "Working on Fix login", conversation = ConversationRef(ProfileId("p1"), SessionId("ses_1")), turnId = "turn_1")
            val again = Robolectric.buildService(ShellService::class.java).create().get()
            body.onStartCommand(again)
            assertFalse(shadowOf(again).lastForegroundNotification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
            // Another run is still promoted.
            source.status.value = StatusContent(StatusKind.Working, "Working on Add tests", conversation = ConversationRef(ProfileId("p1"), SessionId("ses_2")), turnId = "turn_1")
            body.onRowDismissed("ses_9")
            val other = Robolectric.buildService(ShellService::class.java).create().get()
            body.onStartCommand(other)
            assertTrue(shadowOf(other).lastForegroundNotification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        }

    @Test
    fun `S11-4 a joined watch posts alerts without a foreground start and ends when the server leaves`() =
        runTest {
            val source = Source().apply { serverHoldsRow.value = true }
            val body =
                ReplyWatchServiceBody(
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            body.joinServerRow()
            runCurrent()
            source.alerts.tryEmit(finished)
            runCurrent()
            assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
            assertNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
            source.serverHoldsRow.value = false
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.ServerLeft, body.ended.value)
        }
}
