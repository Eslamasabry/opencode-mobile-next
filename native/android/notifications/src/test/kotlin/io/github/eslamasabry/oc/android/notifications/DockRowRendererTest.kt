package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The conversations row's throttle and buttons: named labels, a Stop that targets its own conversation. */
class DockRowThrottleTest {
    private fun content(n: Int) = StatusContent(StatusKind.Working, "t$n")

    @Test
    fun `the dock gap is a second, so Android's five-a-second limit is never reached`() {
        assertEquals(1_000L, StatusThrottle.DOCK_GAP_MS)
        val throttle = StatusThrottle(StatusThrottle.DOCK_GAP_MS)
        var posts = 0
        var now = 0L
        // 120 updates in 3 seconds (every 25 ms), each with new content.
        for (i in 1..120) {
            now += 25
            when (val step = throttle.offer(content(i), now)) {
                is StatusThrottle.Step.Post -> posts++
                else -> throttle.due(now)?.let { posts++ }
            }
        }
        assertTrue(posts <= 4, "posted $posts times in 3 s")
    }

    @Test
    fun `a burst always ends with the newest state`() {
        val throttle = StatusThrottle(StatusThrottle.DOCK_GAP_MS)
        assertEquals(StatusThrottle.Step.Post(content(1)), throttle.offer(content(1), 0))
        for (i in 2..50) throttle.offer(content(i), i * 5L)
        assertNull(throttle.due(999))
        assertEquals(content(50), throttle.due(1_000))
        assertNull(throttle.due(10_000))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DockRowRendererTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val words = NotifyWords(app) { "en" }
    private val renderer = StatusRenderer(app, words)
    private val open = ConversationRef(ProfileId("p1"), SessionId("needs"))
    private val stop = ConversationRef(ProfileId("p1"), SessionId("busy"))

    private val content =
        StatusContent(
            StatusKind.NeedsYou, "Fix login needs you",
            conversation = open, turnId = "dock:2", stopConversation = stop,
            openLabel = "Open Fix login", stopLabel = "Stop Docs",
            actions = listOf(StatusAction.Open, StatusAction.StopReply),
            publicTitle = "OpenCode · 1 working · 1 needs you",
        )

    @Test
    fun `the buttons name their conversations and Stop targets the working one`() {
        val n = renderer.build(content, ongoing = true, chip = null)
        assertEquals(listOf("Open Fix login", "Stop Docs"), n.actions.map { it.title.toString() })
        val openIntent = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals("needs", openIntent.data?.getQueryParameter("session"))
        val stopIntent = shadowOf(n.actions[1].actionIntent).savedIntent
        assertEquals("busy", stopIntent.data?.getQueryParameter("session"))
        assertEquals("busy", stopIntent.getStringExtra(FrozenExtras.EXTRA_STATUS_SESSION))
        assertNotEquals(openIntent.data, stopIntent.data)
    }

    @Test
    fun `without labels the plain words stay, and Stop falls back to the content's conversation`() {
        val plain = content.copy(openLabel = "", stopLabel = "", stopConversation = null)
        val n = renderer.build(plain, ongoing = true, chip = null)
        assertEquals(listOf("Open", "Stop the reply"), n.actions.map { it.title.toString() })
        assertEquals("needs", shadowOf(n.actions[1].actionIntent).savedIntent.data?.getQueryParameter("session"))
    }

    @Test
    fun `the row and its lock-screen version are on the private conversations channel and the public one has counts only`() {
        val n = renderer.build(content, ongoing = true, chip = null)
        assertEquals("opencode_live_conversations", n.channelId)
        assertEquals("opencode_live_conversations", n.publicVersion.channelId)
        assertEquals("OpenCode · 1 working · 1 needs you", n.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, n.visibility)
        // The delete intent carries the pulse count so a swipe away can be remembered.
        assertEquals("dock:2", shadowOf(n.deleteIntent).savedIntent.getStringExtra(FrozenExtras.EXTRA_STATUS_TURN))
    }

    @Test
    fun `an Arabic row uses the Arabic labels from the content and the Arabic channel words`() {
        val ar = NotifyWords(app) { "ar" }
        assertTrue(ar.text(R.string.notify_status_action_open_named, "x").contains("x"))
        assertNotEquals(words.text(R.string.notify_dock_channel), ar.text(R.string.notify_dock_channel))
    }
}
