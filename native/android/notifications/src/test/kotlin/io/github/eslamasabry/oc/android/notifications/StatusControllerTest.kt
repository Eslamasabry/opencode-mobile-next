package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import io.github.eslamasabry.oc.android.notifications.StatusController.Run
import io.github.eslamasabry.oc.android.notifications.StatusController.Session
import io.github.eslamasabry.oc.android.notifications.StatusController.Todo
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every case of the Flutter `test/notification_status_controller_test.dart`, against [StatusController]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusControllerTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private var language = "en"

    private class TestClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private val clock = TestClock(Instant.parse("2026-10-14T13:05:00Z"))

    private val controller =
        StatusController(AndroidStatusWords(NotifyWords(app) { language }), clock) { "13:12" }.apply {
            profileId = "p1"
            setSessions(
                listOf(
                    Session("a", "UI review", "/home/me/IPTV_King", updatedAt = 20),
                    Session("b", "shopfront", "/home/me/shop", updatedAt = 10),
                    Session("child", "sub", parentId = "a"),
                ),
            )
        }

    @BeforeTest fun latin() { Numbers.pinned = NumberSymbols.Latin }

    @AfterTest fun unpin() { Numbers.pinned = null }

    @Test
    fun `B-ANDR-041 one busy chat with tasks reads as working, 5 of 6`() {
        controller.setBusy(setOf("a"))
        controller.onPlan("a", List(5) { Todo("done $it", "completed") } + Todo("Running the tests", "in_progress"))
        val s = controller.status()
        assertEquals(StatusKind.Working, s.kind)
        assertEquals("Running the tests", s.title)
        assertEquals("UI review · 5 of 6 tasks", s.text)
        assertEquals(83, s.progress)
        assertEquals("IPTV_King", s.subText)
        assertEquals("a", s.conversation?.sessionId?.raw)
        assertEquals("p1", s.conversation?.profileId?.raw)
    }

    @Test
    fun `B-ANDR-041 a plan that arrives before the polled busy state is kept`() {
        controller.onPlan("a", listOf(Todo("one", "completed"), Todo("Running the tests", "in_progress")))
        // A publish happens while the session is not yet known busy.
        controller.status()
        controller.setBusy(setOf("a"))
        val s = controller.status()
        assertEquals("Running the tests", s.title)
        assertEquals("UI review · 1 of 2 tasks", s.text)
    }

    @Test
    fun `B-ANDR-041 a plan update moves the count while the chat runs`() {
        controller.setBusy(setOf("a"))
        controller.onPlan("a", listOf(Todo("one", "completed"), Todo("two", "pending")))
        assertEquals("UI review · 1 of 2 tasks", controller.status().text)
        controller.onPlan("a", listOf(Todo("one", "completed"), Todo("two", "completed"), Todo("three", "cancelled")))
        assertEquals("UI review · 2 of 2 tasks", controller.status().text)
    }

    @Test
    fun `B-ANDR-041 sub-agent sessions are not chats`() {
        controller.setBusy(setOf("a", "child"))
        val s = controller.status()
        assertEquals(StatusKind.Working, s.kind)
        assertEquals("a", s.conversation?.sessionId?.raw)
    }

    @Test
    fun `B-ANDR-041 a chat waiting on the person is counted as needing, not working`() {
        controller.setBusy(setOf("a", "b"))
        controller.setPending(permissions = listOf("a"), questions = emptyList())
        val s = controller.status()
        assertEquals(StatusKind.NeedsYou, s.kind)
        assertEquals("1 needs you", s.title)
        assertEquals("UI review · IPTV_King · 1 still working", s.text)
        assertTrue(s.lines.single().startsWith("shopfront"))
    }

    @Test
    fun `B-ANDR-041 retry is waiting, never plain busy`() {
        controller.onRun("a", Run.Retry(nextTry = Instant.parse("2099-01-01T13:12:00Z")))
        val s = controller.status()
        assertEquals(StatusKind.Waiting, s.kind)
        assertTrue(s.title.startsWith("Waiting"))
        assertTrue("Tries again at" in s.text)
        assertTrue("UI review" in s.text)
        // The retry carries no server message at all, so raw provider text cannot show.
        assertFalse("raw provider" in s.text)
        assertTrue(StatusAction.StopReply in s.actions)
        assertEquals("a", s.conversation?.sessionId?.raw)

        controller.onRun("a", Run.Busy)
        assertEquals(StatusKind.Working, controller.status().kind)
    }

    @Test
    fun `B-ANDR-041 a finished reply becomes Last reply on the idle status`() {
        controller.onRun("a", Run.Busy)
        assertEquals(StatusKind.Working, controller.status().kind)
        controller.onRun("a", Run.Idle)
        val s = controller.status()
        assertEquals(StatusKind.Idle, s.kind)
        assertEquals("Last reply just now · UI review", s.text)
        clock.now += 12.minutes
        assertEquals("Last reply 12 min ago · UI review", controller.status().text)
    }

    @Test
    fun `B-ANDR-041 a failed run or a sub-agent ending never becomes Last reply`() {
        controller.onRun("a", Run.Busy)
        controller.onError("a")
        assertEquals("Ready when you are", controller.status().text)
        controller.onRun("child", Run.Busy)
        controller.onRun("child", Run.Idle)
        assertEquals("Ready when you are", controller.status().text)
    }

    @Test
    fun `B-ANDR-041 reconnecting says so, with what it was doing`() {
        controller.setBusy(setOf("a"))
        controller.status()
        controller.reconnecting = true
        val s = controller.status()
        assertEquals(StatusKind.Reconnecting, s.kind)
        assertEquals("UI review", s.text)
    }

    @Test
    fun `B-ANDR-041 the heat guard folds into the status, no second notification`() {
        controller.thermalPaused = true
        assertEquals(StatusKind.PausedHot, controller.status().kind)
        controller.thermalPaused = false
        assertEquals(StatusKind.Idle, controller.status().kind)
    }

    @Test
    fun `B-ANDR-041 Android's background limit is its own status`() {
        controller.androidLimit = true
        assertEquals(StatusKind.PausedAndroid, controller.status().kind)
    }

    @Test
    fun `B-ANDR-041 the status follows the app language`() {
        assertEquals("Ready · nothing running", controller.status().title)
        language = "ar"
        assertNotEquals("Ready · nothing running", controller.status().title)
    }

    @Test
    fun `B-ANDR-041 the tool line and elapsed time describe a run without a plan`() {
        controller.onRun("a", Run.Busy)
        controller.onToolLine("a", "Editing files")
        clock.now += 4.minutes
        val s = controller.status()
        assertEquals("Editing files", s.title)
        assertEquals("IPTV_King · 4 min", s.subText)
        // An invalid plan drops the count instead of showing a wrong one.
        controller.onPlan("a", listOf(Todo("x", "bogus")))
        assertEquals("UI review", controller.status().text)
    }
}
