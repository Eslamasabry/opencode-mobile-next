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
    private val owners = StatusOwners()
    private val registry = ChannelRegistry(manager, words).also { it.ensure() }

    private fun run(profile: String = "p1", session: String = "ses_1", turn: String = "turn_1") =
        ReplyRun(ConversationRef(ProfileId(profile), SessionId(session)), turn)

    private fun content(run: ReplyRun) = StatusContent(StatusKind.Working, "Working", conversation = run.conversation, turnId = run.turnId)

    private inner class Source : ReplyRunSource {
        override val runs = MutableStateFlow(setOf(run()))
        override val progressing = MutableStateFlow(true)
        override val status = MutableStateFlow<StatusContent?>(content(run()))
        override val pendingAlerts = MutableStateFlow<List<ReplyAlert>>(emptyList())
        private var nextMoment = 0

        fun emit(alert: CodingAlert) {
            pendingAlerts.value = pendingAlerts.value + ReplyAlert("moment_${nextMoment++}", alert)
        }

        override fun acknowledgeAlert(id: String) {
            pendingAlerts.value = pendingAlerts.value.filterNot { it.id == id }
        }
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
                app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source, owners,
                scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
            )
        val service = Robolectric.buildService(ShellService::class.java).create().get()
        assertEquals(Service.START_NOT_STICKY, body.onStartCommand(service))
        return body to service
    }

    private val finished =
        CodingAlert(AlertKind.Complete, "ses_1", AlertKeys.status("p1", "ses_1"), profileId = "p1", chatTitle = "Fix login", detail = "Login works again.")

    @Test
    fun `B-ANDR-040 notify finding 6 pending moments reconcile on startup and acknowledged moments never replay`() = runTest {
        val source = Source()
        source.emit(finished)
        val (body, _) = start(source)
        assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
        assertTrue(source.pendingAlerts.value.isEmpty())
        body.onDestroy()
        manager.cancel(NotificationIds.alert(finished.key))
        start(source)
        runCurrent()
        assertNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
    }

    @Test
    fun `S11-9 notify finding 4 dismissal names a turn and preserves other runs and owners`() = runTest {
        val first = run()
        val next = run(turn = "turn_2")
        val other = run(profile = "p2")
        val source = Source().apply { runs.value = setOf(first, other) }
        val (body, service) = start(source)
        runCurrent()
        val oldDelete = shadowOf(shadowOf(service).lastForegroundNotification.deleteIntent).savedIntent
        body.onRowDismissed(first)
        assertTrue(body.active)
        source.status.value = content(other)
        advanceTimeBy(2_500)
        assertTrue(shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        source.status.value = content(first)
        advanceTimeBy(2_500)
        assertFalse(shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        source.runs.value = setOf(next)
        source.status.value = content(next)
        advanceTimeBy(2_500)
        assertTrue(shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals(first, oldDelete.statusRun())
        body.onRowDismissed(first)
        assertTrue(body.active)
        owners.begin(StatusOwner.Live, StatusContent(StatusKind.Idle, "Live"))
        body.onRowDismissed(next)
        assertTrue(body.active)
        owners.end(StatusOwner.Live) { }
        body.onRowDismissed(next)
        assertFalse(body.active)
    }

    @Test
    fun `S11-4 notify finding 3 live and sign-in co-owners survive timeout and destruction`() = runTest {
        for (owner in listOf(StatusOwner.Live, StatusOwner.SignIn)) {
            val (body, sharedService) = start(Source())
            runCurrent()
            val remaining = StatusContent(StatusKind.Idle, owner.name)
            owners.begin(owner, remaining)
            body.onTimeout()
            assertFalse(owners.has(StatusOwner.Reply))
            assertFalse(shadowOf(sharedService).isStoppedBySelf)
            assertFalse(shadowOf(sharedService).isForegroundStopped)
            assertEquals(owner.name, shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            owners.end(owner) { }

            val (last, _) = start(Source())
            runCurrent()
            owners.begin(owner, remaining)
            owners.end(owner) { }
            last.onDestroy()
            assertFalse(owners.has(StatusOwner.Reply))
            assertNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
        }
    }

    @Test
    fun `S11-4 notify finding 3 a server arriving during watch retains its row after reply leaves`() = runTest {
        val source = Source()
        val (body, service) = start(source)
        runCurrent()
        source.serverHoldsRow.value = true
        val remaining = StatusContent(StatusKind.Idle, "Server ready")
        owners.begin(StatusOwner.Server, remaining)
        manager.notify(NotificationIds.STATUS, StatusRenderer(app, words).build(remaining, true, null))
        source.appVisible.value = true
        advanceTimeBy(1_500)
        assertFalse(body.active)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertNotNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
        assertEquals("Server ready", shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `S11-9 notify finding 4 an unrelated delayed dismissal preserves the current watch`() = runTest {
        val source = Source().apply { status.value = StatusContent(StatusKind.Working, "Working", conversation = ConversationRef(ProfileId("p1"), SessionId("ses_1")), turnId = "turn_1") }
        val (body, _) = start(source)
        runCurrent()
        body.onRowDismissed(run(session = "ses_9"))
        assertTrue(body.active)
        source.emit(finished)
        runCurrent()
        assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
    }

    @Test
    fun `B-ANDR-040 notify finding 6 an immediate finished notice survives watch startup`() = runTest {
        val source = Source()
        start(source)
        source.emit(finished)
        runCurrent()
        assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
    }

    @Test
    fun `plan 11 9_2 promise first - the status row is in the foreground before any watching`() =
        runTest {
            val (_, service) = start(Source().apply { status.value = null })
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
            source.emit(finished)
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
            source.emit(CodingAlert(AlertKind.Permission, "ses_1", AlertKeys.input("p1", "ses_1"), requestId = "per_1", profileId = "p1"))
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
            source.emit(finished)
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
            d.onRowDismissed(run())
            assertEquals(ReplyWatchEnd.Dismissed, d.ended.value)
        }

    @Test
    fun `S11-9 a dismissed run is not promoted again`() =
        runTest {
            val source = Source()
            val (body, _) = start(source)
            runCurrent()
            body.onRowDismissed(run())
            source.status.value = StatusContent(StatusKind.Working, "Working on Fix login", conversation = ConversationRef(ProfileId("p1"), SessionId("ses_1")), turnId = "turn_1")
            val again = Robolectric.buildService(ShellService::class.java).create().get()
            body.onStartCommand(again)
            assertFalse(shadowOf(again).lastForegroundNotification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
            // Another run is still promoted.
            body.onDestroy()
            source.runs.value = setOf(run(session = "ses_2"))
            source.status.value = content(run(session = "ses_2"))
            body.onRowDismissed(run(session = "ses_9"))
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
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source, owners,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            owners.begin(StatusOwner.Server, StatusContent(StatusKind.Idle, "Server ready"))
            body.joinServerRow()
            runCurrent()
            source.emit(finished)
            runCurrent()
            assertNotNull(shadowOf(manager).getNotification(NotificationIds.alert(finished.key)))
            assertNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
            source.serverHoldsRow.value = false
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.ServerLeft, body.ended.value)
        }
}
