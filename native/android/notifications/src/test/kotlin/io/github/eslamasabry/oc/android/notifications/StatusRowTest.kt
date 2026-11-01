package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId


import android.app.Application
import android.app.Notification
import android.content.pm.PackageManager
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

/** The pure half of the status row: validation, throttle and the Live Update policy (plan 11 §10.3, §10.5). */
class StatusRowRulesTest {
    @Test
    fun `B-ANDR-047 a malformed content is refused and equal inputs give equal statuses`() {
        assertNull(StatusContent.of(null, "x"))
        assertNull(StatusContent.of(StatusKind.Working, "  "))
        val a = StatusContent.of(StatusKind.Working, " Fixing login ", lines = listOf("a", null, " ", "b", "c", "d", "e"))!!
        assertEquals("Fixing login", a.title)
        assertEquals(listOf("a", "b", "c", "d"), a.lines)
        assertEquals(listOf(StatusAction.Open), a.actions)
        assertEquals("OpenCode", a.publicTitle)
        assertEquals(a, StatusContent.of(StatusKind.Working, " Fixing login ", lines = listOf("a", null, " ", "b", "c", "d", "e")))
        val capped = StatusContent.of(StatusKind.Working, "t".repeat(400), progress = 140, actions = StatusAction.entries + StatusAction.Open)!!
        assertEquals(240, capped.title.length)
        assertEquals(100, capped.progress)
        assertEquals(3, capped.actions.size)
    }

    @Test
    fun `B-ANDR-032 the first status goes at once, unchanged ones are skipped and a burst becomes one trailing post`() {
        val throttle = StatusThrottle()
        val one = StatusContent(StatusKind.Working, "1")
        val two = StatusContent(StatusKind.Working, "2")
        val three = StatusContent(StatusKind.Working, "3")
        assertEquals(StatusThrottle.Step.Post(one), throttle.offer(one, 0))
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(one, 100))
        assertEquals(StatusThrottle.Step.Wait(1_800), throttle.offer(two, 200))
        assertEquals(StatusThrottle.Step.Wait(1_700), throttle.offer(three, 300))
        assertNull(throttle.due(1_999))
        assertEquals(three, throttle.due(2_000))
        assertNull(throttle.due(5_000))
        // A burst that returns to the shown state sends nothing.
        assertTrue(throttle.offer(one, 2_100) is StatusThrottle.Step.Wait)
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(three, 2_200))
        assertNull(throttle.due(9_000))
    }

    @Test
    fun `plan 11 10_5 only runs the person started are promoted, and a dismissed run never again`() {
        fun chip(kind: StatusKind, count: Int = 1, dismissed: Boolean = false, running: Boolean = true) =
            LiveUpdatePolicy.chipFor(StatusContent(kind, "t", count = count), dismissed, running)
        assertEquals(LiveChip(R.plurals.notify_chip_running, 1), chip(StatusKind.Working))
        assertEquals(LiveChip(R.plurals.notify_chip_running, 3), chip(StatusKind.SeveralWorking, 3))
        assertEquals(LiveChip(R.plurals.notify_chip_pending, 2), chip(StatusKind.NeedsYou, 2))
        assertNull(chip(StatusKind.NeedsYou, running = false))
        assertNull(chip(StatusKind.Working, dismissed = true))
        for (never in listOf(StatusKind.Idle, StatusKind.PausedAndroid, StatusKind.PausedHot, StatusKind.PausedIdle, StatusKind.Setup, StatusKind.Waiting, StatusKind.Reconnecting)) {
            assertNull(chip(never), never.name)
        }
    }
}

/** `PromotableStatusTest` (plan 11 §10.5, proof U11-10): Robolectric at SDK 36 posts promotable rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromotableStatusTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val words = NotifyWords(app) { "en" }
    private val renderer = StatusRenderer(app, words)

    private fun build(kind: StatusKind, lines: List<String> = emptyList(), count: Int = 1, progress: Int? = null): Notification {
        val content = StatusContent(kind, "Working on Fix login", lines = lines, count = count, progress = progress, conversation = ConversationRef(ProfileId("p1"), SessionId("ses_1")), turnId = "turn_1", actions = listOf(StatusAction.Open, StatusAction.StopReply))
        return renderer.build(content, ongoing = true, chip = LiveUpdatePolicy.chipFor(content, dismissedRun = false))
    }

    /**
     * The Live Updates requirements of Android 16 QPR1 (36.1) [developer.android.com Live Updates, 2026-11-01]:
     * ongoing, a content title, no custom views (public version included), not colorized, not a group summary, and
     * the standard style, BigTextStyle, CallStyle, ProgressStyle or MetricStyle.
     */
    private fun promotable(n: Notification): Boolean {
        val template = n.extras.getString(Notification.EXTRA_TEMPLATE)
        val styleOk = template == null || listOf("BigTextStyle", "CallStyle", "ProgressStyle", "MetricStyle").any { template.endsWith("\$$it") }
        val noCustom = listOf(n.contentView, n.bigContentView, n.headsUpContentView, n.publicVersion?.contentView, n.publicVersion?.bigContentView).all { it == null }
        return n.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
            !n.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty() &&
            noCustom && !n.extras.getBoolean(Notification.EXTRA_COLORIZED) &&
            n.flags and Notification.FLAG_GROUP_SUMMARY == 0 && styleOk
    }

    @Test
    fun `B-ANDR-034 the manifest declares the promoted-notification permission`() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue("android.permission.POST_PROMOTED_NOTIFICATIONS" in info.requestedPermissions.orEmpty())
        assertTrue("android.permission.POST_NOTIFICATIONS" in info.requestedPermissions.orEmpty())
    }

    @Test
    fun `plan 11 10_5 working, several working and needs-you rows have promotable characteristics`() {
        for ((kind, lines) in listOf(StatusKind.Working to emptyList(), StatusKind.SeveralWorking to listOf("Fix login", "Add tests"), StatusKind.NeedsYou to listOf("a", "b"))) {
            val n = build(kind, lines, count = 2)
            assertTrue(promotable(n), kind.name)
            // The platform call exists at SDK 36 (no NoSuchMethodError, U11-10); its 36.0 runtime still encodes the
            // pre-release rule (colorized required), so the 36.1 rule is checked by [promotable] instead.
            n.hasPromotableCharacteristics()
            assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING), kind.name)
            assertNotNull(n.shortCriticalText, kind.name)
            // Never InboxStyle on a promoted row: Android does not promote it.
            assertFalse(n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("InboxStyle"), kind.name)
        }
        assertEquals("2 need you", build(StatusKind.NeedsYou, count = 2).shortCriticalText)
        assertEquals("Fix login\nAdd tests", build(StatusKind.SeveralWorking, listOf("Fix login", "Add tests"), 2).extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
    }

    @Test
    fun `plan 11 10_5 idle, paused and setup rows never ask for promotion and keep InboxStyle`() {
        for (kind in listOf(StatusKind.Idle, StatusKind.PausedAndroid, StatusKind.PausedIdle, StatusKind.Setup)) {
            val n = build(kind, listOf("a", "b"))
            assertFalse(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING), kind.name)
            assertNull(n.shortCriticalText, kind.name)
            assertTrue(n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("InboxStyle"), kind.name)
            assertFalse(promotable(n), kind.name)
        }
    }

    @Test
    fun `B-ANDR-042 a progress bar only with a real fraction, never an endless spinner`() {
        assertEquals(0, build(StatusKind.Working).extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        val withTasks = build(StatusKind.Working, progress = 83)
        assertEquals(100, withTasks.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(83, withTasks.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(withTasks.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test
    fun `plan 11 10_3 the lock screen shows counts only and Stop the reply names its chat by frozen intent`() {
        val n = build(StatusKind.Working)
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("OpenCode", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("Open", "Stop the reply"), n.actions.map { it.title.toString() })
        val stop = shadowOf(n.actions[1].actionIntent)
        assertTrue(stop.isBroadcastIntent)
        assertEquals(StatusRenderer.STOP_REPLY_REQUEST, stop.requestCode)
        assertEquals(FrozenExtras.STOP_REPLY_URI, stop.savedIntent.dataString)
        assertEquals("ses_1", stop.savedIntent.getStringExtra(FrozenExtras.EXTRA_STATUS_SESSION))
        assertEquals(FrozenComponents.STATUS_ACTION_RECEIVER, stop.savedIntent.component!!.className)
        val delete = shadowOf(n.deleteIntent)
        assertEquals(StatusRenderer.DISMISSED_ACTION, delete.savedIntent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION))
        assertEquals("opencode_live_connection", n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
