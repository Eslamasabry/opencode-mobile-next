package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What `AndroidAlertShade` actually puts in the shade (plan 11 §10.4, §10.6; 16 §1.8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertShadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var tag: String? = "en"
    private val words = NotifyWords(app) { tag }
    private val registry = ChannelRegistry(manager, words).also { it.ensure() }
    private val shade = AndroidAlertShade(app, words, registry)
    private val needsYou = AlertRoute.Post(NotifyChannel.CodingAction, AlertGroups.NEEDS_YOU)
    private val finished = AlertRoute.Post(NotifyChannel.CodingStatus, AlertGroups.FINISHED)

    private fun permission(session: String = "ses_1", request: String = "per_1") =
        CodingAlert(
            AlertKind.Permission, session, AlertKeys.input("p1", session), requestId = request, profileId = "p1",
            subtext = "MacBook · api", detail = NotificationText.redact("npm install --token sk-abcdefghijklmnop"),
            ask = PermissionAsk(AskAction.Run, NotificationText.commandHead("npm install --token sk-abcdefghijklmnop")),
        )

    private fun posted(key: String): Notification = shadowOf(manager).getNotification(NotificationIds.alert(key))

    @Test
    fun `B-SEC-105 a permission shows the command on the shade only, never a secret, with server and project`() {
        val alert = permission()
        assertEquals(AlertShown.Shown, shade.show(alert, needsYou))
        val n = posted(alert.key)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals("Run npm install?", title)
        assertEquals("npm install --token •••", text)
        assertFalse("sk-abc" in title + text)
        assertEquals("MacBook · api", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        // B-ANDR-047: the lock screen shows fixed words, never the command.
        assertEquals("A request needs you", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("opencode_coding_action", n.channelId)
        assertEquals(AlertGroups.NEEDS_YOU, n.group)
    }

    @Test
    fun `B-SEC-184 Allow once requires unlock and carries only the exact request's routing ids`() {
        val alert = permission()
        shade.show(alert, needsYou)
        val n = posted(alert.key)
        assertEquals(listOf("Allow once", "Reject"), n.actions.map { it.title.toString() })
        assertTrue(n.actions[0].isAuthenticationRequired)
        assertFalse(n.actions[1].isAuthenticationRequired)
        val id = NotificationIds.alert(alert.key)
        n.actions.forEachIndexed { index, action ->
            val pending = shadowOf(action.actionIntent)
            assertTrue(pending.isBroadcastIntent)
            assertTrue(pending.isImmutable)
            assertEquals(id * 4 + index + 1, pending.requestCode)
            val intent: Intent = pending.savedIntent
            assertEquals(FrozenComponents.CODING_ACTION_RECEIVER, intent.component!!.className)
            val decision = if (index == 0) "allow" else "deny"
            assertEquals("opencode://coding-alert/${alert.key}/$decision", intent.dataString)
            assertEquals("per_1", intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID))
            assertEquals(decision, intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_DECISION))
            // No request words travel in the intent.
            assertFalse(intent.extras!!.keySet().any { intent.extras!!.get(it).toString().contains("npm") })
        }
    }

    @Test
    fun `B-ANDR-038 as amended a question opens the app, lists three short options in one line and never offers reply`() {
        val q =
            CodingAlert(
                AlertKind.Question, "ses_2", AlertKeys.input("p1", "ses_2"), requestId = "que_1", profileId = "p1",
                detail = "Which database?", options = listOf("Postgres", "SQLite", "None"),
            )
        shade.show(q, needsYou)
        val n = posted(q.key)
        assertEquals("Which database?", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Postgres · SQLite · None", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Open"), n.actions.map { it.title.toString() })
        assertNull(n.actions[0].remoteInputs)
        assertTrue(shadowOf(n.actions[0].actionIntent).isActivityIntent)
    }

    @Test
    fun `B-ANDR-040 a finished reply names its chat, times out after an hour and two of them get one summary`() {
        val first = CodingAlert(AlertKind.Complete, "ses_1", AlertKeys.status("p1", "ses_1"), profileId = "p1", chatTitle = "Add combo counter", detail = "All tests pass.")
        val second = first.copy(sessionId = "ses_2", key = AlertKeys.status("p1", "ses_2"), chatTitle = "Fix login")
        shade.show(first, finished)
        val n = posted(first.key)
        assertEquals("Finished: Add combo counter", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(60 * 60 * 1000L, n.timeoutAfter)
        assertEquals("A conversation finished", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(shadowOf(manager).getNotification(NotificationIds.FINISHED_SUMMARY))
        shade.show(second, finished)
        val summary = shadowOf(manager).getNotification(NotificationIds.FINISHED_SUMMARY)
        assertNotNull(summary)
        assertEquals("2 conversations finished", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        val lines = summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertEquals(setOf("Add combo counter", "Fix login"), lines.toSet())
        // Dismissing one leaves a single alert, which takes its summary with it.
        shade.dismiss(second.key)
        assertNull(shadowOf(manager).getNotification(NotificationIds.alert(second.key)))
        assertNull(shadowOf(manager).getNotification(NotificationIds.FINISHED_SUMMARY))
    }

    @Test
    fun `B-SEC-104 a stopped chat says why in a plain sentence, never the server's text`() {
        val error = CodingAlert(AlertKind.Error, "ses_1", AlertKeys.status("p1", "ses_1"), chatTitle = "Deploy", reason = StopReason.Context)
        shade.show(error, AlertRoute.Post(NotifyChannel.CodingAction, null))
        val n = posted(error.key)
        assertEquals("Stopped: Deploy", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("The conversation is too long for the model.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(NotificationCompat.CATEGORY_ERROR, n.category)
    }

    @Test
    fun `P0_6 a channel the person turned off reports Blocked instead of posting`() {
        manager.getNotificationChannel("opencode_coding_action").let {
            it.importance = NotificationManager.IMPORTANCE_NONE
            manager.createNotificationChannel(it)
        }
        assertEquals(AlertShown.Blocked(BlockedWhy.ChannelOff), shade.show(permission(), needsYou))
        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(AlertShown.Blocked(BlockedWhy.Permission), shade.show(permission(), finished))
        assertEquals(AlertShown.Invalid, shade.show(permission().copy(key = ""), needsYou))
    }

    @Test
    fun `plan 11 10_6 the open-only repost has plain words, no buttons, and opens the exact request`() {
        val tap = ShadeTap(AlertKind.Permission, "ses_1", AlertKeys.input("p1", "ses_1"), ShadeDecision.Allow, "per_9", "p1", null)
        assertEquals(AlertShown.Shown, shade.showOpenOnly(tap))
        val n = posted(tap.key)
        assertEquals("Open OpenCode to answer", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(0, n.actions?.size ?: 0)
        val content = shadowOf(n.contentIntent)
        assertTrue(content.isActivityIntent)
        assertTrue(content.isImmutable)
        assertEquals(FrozenComponents.MAIN_ACTIVITY, content.savedIntent.component!!.className)
        assertEquals("per_9", content.savedIntent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID))
    }

    @Test
    fun `B-ANDR-047 Arabic copy is used when the app language is Arabic`() {
        tag = "ar"
        val alert = permission()
        shade.show(alert, needsYou)
        val n = posted(alert.key)
        assertEquals(NotifyWords(app) { "ar" }.text(R.string.notify_allow_once), n.actions[0].title.toString())
        assertEquals(NotifyWords(app) { "ar" }.text(R.string.notify_ask_run, "npm install"), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }
}
