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

    @Test
    fun `B1 only a watch that ran out of progress starts again when a run moves while the app stays hidden`() {
        assertTrue(ReplyWatchPolicy.rearms(ReplyWatchEnd.NoProgress))
        for (end in ReplyWatchEnd.entries - ReplyWatchEnd.NoProgress) assertFalse(ReplyWatchPolicy.rearms(end), end.name)
    }
}

/** `ReplyProgressTracker` (plan 11 §9.3): what "a run is progressing" means for the start and the end rules. */
class ReplyProgressTrackerTest {
    private val key = AlertKeys.input("p1", "ses_1")

    @Test
    fun `S11-4 a running reply progresses, nothing running does not`() {
        val tracker = ReplyProgressTracker()
        assertTrue(tracker.progressing(listOf(ReplyRunFact(key, running = true, waiting = false)), emptySet(), true, 0))
        assertFalse(tracker.progressing(listOf(ReplyRunFact(key, running = false, waiting = false)), emptySet(), true, 0))
        assertFalse(tracker.progressing(emptyList(), emptySet(), true, 0))
    }

    @Test
    fun `S11-4 a reply waiting for the person counts until its alert is shown`() {
        val tracker = ReplyProgressTracker()
        val waiting = listOf(ReplyRunFact(key, running = false, waiting = true))
        assertTrue(tracker.progressing(waiting, emptySet(), true, 1_000))
        assertFalse(tracker.progressing(waiting, setOf(key), true, 2_000))
    }

    @Test
    fun `S11-4 with request alerts off a waiting reply never holds the watch`() {
        assertFalse(
            ReplyProgressTracker().progressing(listOf(ReplyRunFact(key, running = false, waiting = true)), emptySet(), false, 0),
        )
    }

    @Test
    fun `S11-4 an alert that cannot be shown stops counting after the grace, a new wait starts a new grace`() {
        val tracker = ReplyProgressTracker()
        val waiting = listOf(ReplyRunFact(key, running = false, waiting = true))
        val grace = ReplyProgressTracker.UNSHOWN_REQUEST_GRACE_MS
        assertTrue(tracker.progressing(waiting, emptySet(), true, 10_000))
        assertTrue(tracker.progressing(waiting, emptySet(), true, 10_000 + grace - 1))
        assertFalse(tracker.progressing(waiting, emptySet(), true, 10_000 + grace))
        // Answered elsewhere, then a new request on the same conversation.
        assertFalse(tracker.progressing(emptyList(), emptySet(), true, 50_000))
        assertTrue(tracker.progressing(waiting, emptySet(), true, 60_000))
    }

    @Test
    fun `S11-4 another conversation still running keeps progress while one waits on a shown alert`() {
        val tracker = ReplyProgressTracker()
        val facts =
            listOf(
                ReplyRunFact(key, running = false, waiting = true),
                ReplyRunFact(AlertKeys.input("p2", "ses_2"), running = true, waiting = false),
            )
        assertTrue(tracker.progressing(facts, setOf(key), true, 0))
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
        var rowsReturned = 0

        /** Stands in for the phone server re-posting its own row (`BuiltinLinux.repostStatusRow`). */
        override fun returnRowToServer() {
            rowsReturned++
            manager.notify(NotificationIds.STATUS, StatusRenderer(app, words).build(StatusContent(StatusKind.Idle, "Server ready"), true, null))
        }

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
            // dd80caae38 pinned "no row" here; that was the B1 fault (no "Still working" while the server ran).
            assertEquals("Working", shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            source.serverHoldsRow.value = false
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.ServerLeft, body.ended.value)
        }

    @Test
    fun `S11-4 a watch joined to the phone server's own row gives that row back when it ends`() =
        runTest {
            // The server's service (:android:linux) holds row 4747 outside this owner set.
            val source = Source().apply { serverHoldsRow.value = true }
            val body =
                ReplyWatchServiceBody(
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source, owners,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            manager.notify(NotificationIds.STATUS, StatusRenderer(app, words).build(StatusContent(StatusKind.Idle, "Server ready"), true, null))
            body.joinServerRow()
            runCurrent()
            assertTrue(body.active)
            source.appVisible.value = true
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.AppVisible, body.ended.value)
            assertFalse(owners.has(StatusOwner.Reply))
            assertEquals("Server ready", shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(1, source.rowsReturned)
        }

    @Test
    fun `B1 with the phone server running a joined watch shows Still working at once and the server's row comes back after`() =
        runTest {
            // Device run 5 (B1): the server's own row was not showing (posted before notifications were allowed).
            val source = Source().apply { serverHoldsRow.value = true; status.value = null }
            val body =
                ReplyWatchServiceBody(
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source, owners,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            body.joinServerRow()
            runCurrent()
            val row = shadowOf(manager).getNotification(NotificationIds.STATUS)
            assertNotNull(row)
            assertEquals("Still working on your reply", row.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(row.flags and Notification.FLAG_ONGOING_EVENT != 0)
            advanceTimeBy(60_000)
            assertTrue(body.active)
            assertEquals("Still working on your reply", shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            source.progressing.value = false
            advanceTimeBy(11_500)
            assertEquals(ReplyWatchEnd.NoProgress, body.ended.value)
            assertEquals(1, source.rowsReturned)
            assertEquals("Server ready", shadowOf(manager).getNotification(NotificationIds.STATUS).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        }

    @Test
    fun `B1 a joined watch whose server left takes its working row away`() =
        runTest {
            val source = Source().apply { serverHoldsRow.value = true }
            val body =
                ReplyWatchServiceBody(
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), source, owners,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            body.joinServerRow()
            runCurrent()
            assertNotNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
            source.serverHoldsRow.value = false
            advanceTimeBy(1_500)
            assertEquals(ReplyWatchEnd.ServerLeft, body.ended.value)
            assertEquals(0, source.rowsReturned)
            assertNull(shadowOf(manager).getNotification(NotificationIds.STATUS))
        }

    @Test
    fun `S11-4 no join without a server holding the row`() =
        runTest {
            val body =
                ReplyWatchServiceBody(
                    app, words, StatusRenderer(app, words), AndroidAlertShade(app, words, registry), Source(), owners,
                    scope = backgroundScope, elapsedMs = { testScheduler.currentTime },
                )
            body.joinServerRow()
            assertFalse(body.active)
        }

    @Test
    fun `S11-4 live mode starting during the watch takes the service over without a stop`() =
        runTest {
            val (body, service) = start(Source())
            runCurrent()
            owners.begin(StatusOwner.Live, StatusContent(StatusKind.Idle, "Live"))
            body.yieldToLive()
            assertEquals(ReplyWatchEnd.LiveModeOn, body.ended.value)
            assertFalse(body.holds(service))
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertFalse(shadowOf(service).isForegroundStopped)
            owners.end(StatusOwner.Live) { }
        }

    @Test
    fun `S11-9 the row's delete intent ends the watch for the run it named`() =
        runTest {
            val (body, service) = start(Source())
            runCurrent()
            assertTrue(body.holds(service))
            val delete = shadowOf(shadowOf(service).lastForegroundNotification.deleteIntent).savedIntent
            body.onRowDismissed(Intent(delete).putExtra(FrozenExtras.EXTRA_STATUS_ACTION, "open"))
            assertTrue(body.active)
            body.onRowDismissed(delete)
            assertEquals(ReplyWatchEnd.Dismissed, body.ended.value)
        }
}
