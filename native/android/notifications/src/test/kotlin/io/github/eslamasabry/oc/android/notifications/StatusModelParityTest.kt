package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every case of the Flutter `test/notification_status_test.dart`, against [StatusModel] with the real copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusModelParityTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val en = AndroidStatusWords(NotifyWords(app) { "en" })
    private val ar = AndroidStatusWords(NotifyWords(app) { "ar" })

    @BeforeTest fun latin() { Numbers.pinned = NumberSymbols.Latin }

    @AfterTest fun unpin() { Numbers.pinned = null }

    private fun at(h: Int, m: Int): Instant = Instant.parse("2026-10-14T%02d:%02d:00Z".format(h, m))

    private val now = at(13, 5)

    private fun clock(t: Instant): String {
        val z = java.time.Instant.ofEpochMilli(t.toEpochMilliseconds()).atZone(ZoneOffset.UTC)
        return "${z.hour}:${z.minute.toString().padStart(2, '0')}"
    }

    private fun build(input: StatusInput, words: StatusWords = en) = StatusModel.build(input, words, now, ::clock)

    private val tests = StatusChat("s1", "UI review and feedback", "IPTV_King", "Running the tests", 5, 6, 4.minutes)
    private val shop = StatusChat("s2", "shopfront", liveLine = "Editing theme.dart")
    private val third = StatusChat("s3", "docs", liveLine = "Reading files")
    private val fourth = StatusChat("s4", "api")

    // ---- one chat working ----

    @Test
    fun `B-ANDR-042 one chat names what it does, with the tasks and a real progress bar`() {
        val s = build(StatusInput(working = listOf(tests)))
        assertEquals(StatusKind.Working, s.kind)
        assertEquals("Running the tests", s.title)
        assertEquals("UI review and feedback · 5 of 6 tasks", s.text)
        assertEquals("IPTV_King · 4 min", s.subText)
        assertEquals(83, s.progress)
        assertTrue(s.lines.isEmpty())
        assertEquals(listOf(StatusAction.Open, StatusAction.StopReply), s.actions)
        assertEquals("s1", s.conversation?.sessionId?.raw)
    }

    @Test
    fun `B-ANDR-042 without tasks there is no bar, never an endless spinner`() {
        val s = build(StatusInput(working = listOf(StatusChat("a", "Fix login", liveLine = "Editing files"))))
        assertNull(s.progress)
        assertEquals("Editing files", s.title)
        assertEquals("Fix login", s.text)
    }

    @Test
    fun `B-ANDR-042 a chat with no live line still says it is working`() {
        assertEquals("Working…", build(StatusInput(working = listOf(fourth))).title)
    }

    @Test
    fun `B-ANDR-042 a long run is told in hours`() {
        assertEquals("2 h", build(StatusInput(working = listOf(StatusChat("a", elapsed = 135.minutes)))).subText)
    }

    // ---- several chats working ----

    @Test
    fun `B-ANDR-042 two chats are two lines`() {
        val s = build(StatusInput(working = listOf(tests, shop)))
        assertEquals(StatusKind.SeveralWorking, s.kind)
        assertEquals("2 conversations working", s.title)
        assertEquals(listOf("UI review and feedback · Running the tests", "shopfront · Editing theme.dart"), s.lines)
        assertNull(s.progress)
        assertEquals(listOf(StatusAction.Open), s.actions)
        assertNull(s.conversation)
        assertEquals(2, s.count)
    }

    @Test
    fun `B-ANDR-042 three chats show two lines and +1 more`() {
        val s = build(StatusInput(working = listOf(tests, shop, third)))
        assertEquals("3 conversations working", s.title)
        assertEquals(listOf("UI review and feedback · Running the tests", "shopfront · Editing theme.dart", "+1 more"), s.lines)
    }

    @Test
    fun `B-ANDR-042 five chats say +3 more`() {
        assertEquals("+3 more", build(StatusInput(working = listOf(tests, shop, third, fourth, fourth))).lines.last())
    }

    // ---- needs you ----

    @Test
    fun `B-ANDR-043 one request says whose it is and offers Open`() {
        val s = build(StatusInput(pendingCount = 1, firstPendingTitle = "IPTV_King", firstPendingProject = "docs"))
        assertEquals(StatusKind.NeedsYou, s.kind)
        assertEquals("1 needs you", s.title)
        assertEquals("IPTV_King · docs", s.text)
        assertEquals(listOf(StatusAction.Open), s.actions)
    }

    @Test
    fun `B-ANDR-043 two requests use the plural`() {
        val s = build(StatusInput(pendingCount = 2))
        assertEquals("2 need you", s.title)
        assertEquals(2, s.count)
    }

    @Test
    fun `B-ANDR-043 needs you beats working and adds how many are still working`() {
        val s = build(StatusInput(pendingCount = 1, firstPendingTitle = "docs", firstPendingProject = "site", working = listOf(tests, shop)))
        assertEquals(StatusKind.NeedsYou, s.kind)
        assertEquals("1 needs you", s.title)
        assertEquals("docs · site · 2 still working", s.text)
        assertEquals(listOf("UI review and feedback · Running the tests", "shopfront · Editing theme.dart"), s.lines)
        assertNull(s.progress)
    }

    @Test
    fun `B-ANDR-043 needs you never repeats the request itself`() {
        val s = build(StatusInput(pendingCount = 1, firstPendingTitle = "IPTV_King"))
        assertFalse("npm" in s.text)
        assertFalse("Run this" in s.text)
    }

    @Test
    fun `B-ANDR-043 needs you beats every pause and the reconnecting state`() {
        for (pause in PauseReason.entries) {
            assertEquals(StatusKind.NeedsYou, build(StatusInput(pendingCount = 1, reconnecting = true, pause = pause)).kind, pause.name)
        }
    }

    // ---- waiting ----

    @Test
    fun `B-ANDR-044 waiting names the provider and the time of the next try`() {
        val s = build(StatusInput(retries = listOf(StatusRetry("s1", "UI review and feedback", "Experiential Labs", at(13, 12)))))
        assertEquals(StatusKind.Waiting, s.kind)
        assertEquals("Waiting · Experiential Labs limit", s.title)
        assertEquals("Tries again at 13:12 · UI review and feedback", s.text)
        assertEquals(listOf(StatusAction.Open, StatusAction.StopReply), s.actions)
        assertEquals("s1", s.conversation?.sessionId?.raw)
        assertNull(s.progress)
    }

    @Test
    fun `B-ANDR-044 without a provider or a time it still says so plainly`() {
        val s = build(StatusInput(retries = listOf(StatusRetry("s1", "Fix login"))))
        assertEquals("Waiting · the provider is busy", s.title)
        assertEquals("Tries again soon · Fix login", s.text)
    }

    @Test
    fun `B-ANDR-044 a time already past reads soon`() {
        assertEquals("Tries again soon", build(StatusInput(retries = listOf(StatusRetry("s1", nextTry = at(12, 0))))).text)
    }

    @Test
    fun `B-ANDR-044 shows the retry that comes first`() {
        val s = build(StatusInput(retries = listOf(StatusRetry("late", provider = "B", nextTry = at(14, 0)), StatusRetry("soon", provider = "A", nextTry = at(13, 10)))))
        assertEquals("soon", s.conversation?.sessionId?.raw)
        assertEquals("Waiting · A limit", s.title)
    }

    @Test
    fun `B-ANDR-044 a working chat wins and notes that another one waits`() {
        val s = build(StatusInput(working = listOf(tests), retries = listOf(StatusRetry("s9", "old"))))
        assertEquals(StatusKind.Working, s.kind)
        assertEquals("UI review and feedback · 5 of 6 tasks · 1 waiting", s.text)
    }

    // ---- paused ----

    @Test
    fun `B-ANDR-045 Android background limit`() {
        val s = build(StatusInput(pause = PauseReason.AndroidLimit))
        assertEquals(StatusKind.PausedAndroid, s.kind)
        assertEquals("Paused · Android's background limit", s.title)
        assertTrue("Open OpenCode" in s.text)
        assertEquals(listOf(StatusAction.Open), s.actions)
    }

    @Test
    fun `B-ANDR-045 the limit beats a chat that was working`() {
        assertEquals(StatusKind.PausedAndroid, build(StatusInput(pause = PauseReason.AndroidLimit, working = listOf(tests))).kind)
    }

    @Test
    fun `B-ANDR-045 phone is hot`() {
        val s = build(StatusInput(pause = PauseReason.PhoneHot))
        assertEquals(StatusKind.PausedHot, s.kind)
        assertEquals("Paused · phone is hot", s.title)
        assertEquals("Resumes when it cools down", s.text)
        assertEquals(listOf(StatusAction.Open), s.actions)
    }

    @Test
    fun `B-ANDR-045 hot does not hide chats that still work`() {
        assertEquals(StatusKind.Working, build(StatusInput(pause = PauseReason.PhoneHot, working = listOf(tests))).kind)
    }

    @Test
    fun `B-ANDR-045 paused while idle offers Start`() {
        val s = build(StatusInput(pause = PauseReason.Idle))
        assertEquals(StatusKind.PausedIdle, s.kind)
        assertEquals("Paused · nothing was running", s.title)
        assertEquals("Tap to start OpenCode again", s.text)
        assertEquals(listOf(StatusAction.Start), s.actions)
    }

    // ---- reconnecting ----

    @Test
    fun `B-ANDR-045 reconnecting to one server does not name it`() {
        val s = build(StatusInput(reconnecting = true, serverName = "PC", reconnectingLastActivity = "Running the tests"))
        assertEquals(StatusKind.Reconnecting, s.kind)
        assertEquals("Reconnecting", s.title)
        assertEquals("Running the tests", s.text)
    }

    @Test
    fun `B-ANDR-045 several servers name the one that dropped`() {
        val s = build(StatusInput(reconnecting = true, serverName = "PC", liveServerCount = 2))
        assertEquals("Reconnecting to PC", s.title)
        assertEquals("Waiting for the connection", s.text)
    }

    @Test
    fun `B-ANDR-045 reconnecting beats working, the chats may not still be running`() {
        assertEquals(StatusKind.Reconnecting, build(StatusInput(reconnecting = true, working = listOf(tests))).kind)
    }

    // ---- idle and setup ----

    @Test
    fun `B-ANDR-046 ready, with the last reply and New chat`() {
        val s = build(StatusInput(lastReplyAt = now - 12.minutes, lastReplyTitle = "UI review and feedback"))
        assertEquals(StatusKind.Idle, s.kind)
        assertEquals("Ready · nothing running", s.title)
        assertEquals("Last reply 12 min ago · UI review and feedback", s.text)
        assertEquals(listOf(StatusAction.NewChat), s.actions)
        assertNull(s.progress)
    }

    @Test
    fun `B-ANDR-046 the phone server adds Stop OpenCode and says where it runs`() {
        val s = build(StatusInput(phoneServerRunning = true, lastReplyAt = now - 20.seconds))
        assertEquals("on this phone", s.subText)
        assertEquals("Last reply just now", s.text)
        assertEquals(listOf(StatusAction.NewChat, StatusAction.StopServer), s.actions)
    }

    @Test
    fun `B-ANDR-046 no reply yet`() {
        assertEquals("Ready when you are", build(StatusInput()).text)
    }

    @Test
    fun `B-ANDR-046 the last reply is told in hours`() {
        assertEquals("Last reply 3 h ago", build(StatusInput(lastReplyAt = now - 3.hours - 5.minutes)).text)
    }

    @Test
    fun `B-ANDR-046 setup shows step and percent, no actions beyond Open`() {
        val s = build(StatusInput(setup = StatusSetup(4, 5, "Installing OpenCode", 60)))
        assertEquals(StatusKind.Setup, s.kind)
        assertEquals("Setup · 4 of 5", s.title)
        assertEquals("Installing OpenCode", s.text)
        assertEquals(60, s.progress)
        assertEquals(listOf(StatusAction.Open), s.actions)
    }

    @Test
    fun `B-ANDR-046 a working chat beats setup, setup beats ready`() {
        val setup = StatusSetup(1, 5)
        assertEquals(StatusKind.Working, build(StatusInput(setup = setup, working = listOf(tests))).kind)
        assertEquals(StatusKind.Setup, build(StatusInput(setup = setup)).kind)
    }

    // ---- several servers, lock screen, payload, language ----

    @Test
    fun `B-ANDR-047 several servers add on server beside the app name`() {
        assertEquals("IPTV_King · 4 min · on PC", build(StatusInput(working = listOf(tests), liveServerCount = 2, serverName = "PC")).subText)
    }

    @Test
    fun `B-ANDR-047 one server never names itself`() {
        assertFalse("PC" in build(StatusInput(working = listOf(tests), serverName = "PC")).subText)
    }

    @Test
    fun `B-ANDR-047 the lock screen shows only counts, never titles, lines or the live line`() {
        val secret = build(StatusInput(pendingCount = 2, firstPendingTitle = "Secret chat title", working = listOf(StatusChat("x", "Secret chat title", liveLine = "Secret task text"))))
        assertEquals("OpenCode · 2 need you", secret.publicTitle)
        assertFalse("Secret" in secret.publicTitle)
        val working = build(StatusInput(working = listOf(StatusChat("x", "Secret chat title", liveLine = "Secret"))))
        assertEquals("1 working", working.publicTitle)
        assertEquals("Waiting", build(StatusInput(retries = listOf(StatusRetry("x", "Secret chat title")))).publicTitle)
        assertEquals("Ready", build(StatusInput()).publicTitle)
    }

    @Test
    fun `B-ANDR-047 the content carries exactly what Android reads, by its frozen wire names`() {
        val s = build(StatusInput(working = listOf(tests), profileId = "p1"))
        assertEquals("working", s.kind.wire)
        assertEquals(listOf("open", "stop_reply"), s.actions.map { it.wire })
        // The fields of the Flutter payload, plus the profile the renderer's Stop intent needs.
        assertEquals("p1", s.conversation?.profileId?.raw)
        assertEquals(
            setOf("kind", "title", "text", "lines", "subText", "progress", "actions", "conversation", "turnId", "publicTitle", "count"),
            StatusContent::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet(),
        )
    }

    @Test
    fun `B-ANDR-047 equal inputs give equal statuses`() {
        val input = StatusInput(working = listOf(tests, shop))
        assertEquals(build(input), build(input))
        assertEquals(build(input).hashCode(), build(input).hashCode())
        assertNotEquals(build(input), build(StatusInput(working = listOf(tests))))
    }

    @Test
    fun `B-ANDR-047 Arabic copy is used when the language is Arabic`() {
        val s = build(StatusInput(pause = PauseReason.PhoneHot), ar)
        assertNotEquals("Paused · phone is hot", s.title)
        assertTrue("ساخن" in s.title)
    }
}
