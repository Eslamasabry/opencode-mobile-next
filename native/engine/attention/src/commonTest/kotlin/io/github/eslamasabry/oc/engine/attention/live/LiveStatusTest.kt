package io.github.eslamasabry.oc.engine.attention.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The Flutter app's English `liveTool*` / `notifStatus*` copy, as `:ui:words` supplies it. */
internal object EnglishLiveWords : LiveWords {
    override val toolCommand = "Running a command…"
    override val toolEdit = "Editing files…"
    override val toolRead = "Reading files…"
    override val toolSearch = "Searching files…"
    override val toolWeb = "Browsing the web…"
    override val toolSubagent = "Running a subagent…"
    override val toolPlan = "Planning…"
    override val toolWorking = "Working…"
    override val toolCard = "Showing a card…"
    override val toolBackgroundTask = "Checking a background task…"

    override fun toolOther(tool: String) = "Running $tool…"

    override fun needsYou(count: Int) = if (count == 1) "1 needs you" else "$count need you"

    override fun chatsWorking(count: Int) = if (count == 1) "1 chat working" else "$count chats working"

    override val readyTitle = "Ready"
}

/** The Flutter app's Arabic copy (app_ar.arb). */
internal object ArabicLiveWords : LiveWords {
    override val toolCommand = "جارٍ تشغيل أمر…"
    override val toolEdit = "جارٍ تعديل الملفات…"
    override val toolRead = "جارٍ قراءة الملفات…"
    override val toolSearch = "جارٍ البحث في الملفات…"
    override val toolWeb = "جارٍ تصفح الويب…"
    override val toolSubagent = "جارٍ تشغيل وكيل فرعي…"
    override val toolPlan = "جارٍ التخطيط…"
    override val toolWorking = "جارٍ العمل…"
    override val toolCard = "جارٍ عرض بطاقة…"
    override val toolBackgroundTask = "جارٍ فحص مهمة في الخلفية…"

    override fun toolOther(tool: String) = "جارٍ تشغيل $tool…"

    override fun needsYou(count: Int) = if (count == 2) "طلبان ينتظرانك" else "$count طلب ينتظرك"

    override fun chatsWorking(count: Int) = "$count محادثة تعمل"

    override val readyTitle = "جاهز"
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveStatusTest {
    private val debounce = 120.milliseconds

    private class Recorder {
        val calls = mutableListOf<Map<String, Any?>>()
        var fail: Throwable? = null
        val platform = LivePlatform { payload ->
            calls += payload
            fail?.let { throw it }
        }
    }

    private fun TestScope.publisher(r: Recorder, enabled: Boolean = true, active: Boolean = true, persisted: MutableList<Boolean>? = null) =
        LiveStatusPublisher(r.platform, backgroundScope, testTimeSource, debounce, persistDisabled = { persisted?.add(false) })
            .also { it.setServiceState(enabled, active) }

    @Test
    fun `B-ANDR-032 the live status carries exactly the four fields Android reads`() {
        assertEquals(
            mapOf("runningCount" to 2, "pendingCount" to 1, "title" to "Fix login", "detail" to "Editing files…"),
            LiveStatus(runningCount = 2, pendingCount = 1, title = "Fix login", detail = "Editing files…").toPayload(),
        )
        assertEquals(mapOf("runningCount" to 0, "pendingCount" to 0, "title" to null, "detail" to null), LiveStatus.Idle.toPayload())
    }

    @Test
    fun `B-ANDR-032 the live status compares by value`() {
        assertEquals(LiveStatus(runningCount = 1, title = "a"), LiveStatus(runningCount = 1, title = "a"))
        assertNotEquals(LiveStatus(runningCount = 1, title = "a"), LiveStatus(runningCount = 1, title = "b"))
        assertEquals(LiveStatus(runningCount = 1).hashCode(), LiveStatus(runningCount = 1).hashCode())
    }

    @Test
    fun `B-ANDR-032 nothing is sent while live mode is off or the service is down`() =
        runTest {
            val r = Recorder()
            publisher(r, enabled = false, active = false).publish(LiveStatus(runningCount = 1))
            assertTrue(r.calls.isEmpty())
            publisher(r, enabled = true, active = false).publish(LiveStatus(runningCount = 1))
            assertTrue(r.calls.isEmpty())
        }

    @Test
    fun `B-ANDR-032 the first status goes at once and unchanged ones are skipped`() =
        runTest {
            val r = Recorder()
            val live = publisher(r)
            val status = LiveStatus(runningCount = 1, title = "Build feature")
            live.publish(status)
            assertEquals(listOf(status.toPayload()), r.calls)
            assertEquals(status, live.lastPublished)
            delay(debounce * 2)
            live.publish(LiveStatus(runningCount = 1, title = "Build feature"))
            assertEquals(1, r.calls.size)
        }

    @Test
    fun `B-ANDR-032 a burst coalesces into one trailing push with the newest`() =
        runTest {
            val r = Recorder()
            val live = publisher(r)
            live.publish(LiveStatus(runningCount = 1))
            live.publish(LiveStatus(runningCount = 2))
            live.publish(LiveStatus(runningCount = 3))
            live.publish(LiveStatus(runningCount = 3, detail = "Editing files…"))
            assertEquals(1, r.calls.size, "leading edge only")
            delay(debounce * 2)
            assertEquals(2, r.calls.size)
            assertEquals(mapOf("runningCount" to 3, "pendingCount" to 0, "title" to null, "detail" to "Editing files…"), r.calls.last())
        }

    @Test
    fun `B-ANDR-032 a burst that returns to the shown status sends nothing`() =
        runTest {
            val r = Recorder()
            val live = publisher(r)
            live.publish(LiveStatus(runningCount = 1))
            live.publish(LiveStatus(runningCount = 2))
            live.publish(LiveStatus(runningCount = 1))
            delay(debounce * 2)
            assertEquals(1, r.calls.size)
        }

    @Test
    fun `B-ANDR-032 a missing platform channel is survived without an error`() =
        runTest {
            val r = Recorder().apply { fail = IllegalStateException("no runner") }
            val live = publisher(r)
            live.publish(LiveStatus(runningCount = 1))
            assertNull(live.lastError)
            assertEquals(1, r.calls.size)
        }

    @Test
    fun `B-ANDR-032 a pause from the notification is not an Android timeout`() =
        runTest {
            val persisted = mutableListOf<Boolean>()
            val live = publisher(Recorder(), persisted = persisted)
            live.handleNativeTimeout(LiveStatusPublisher.PAUSE_REASON)
            assertFalse(live.enabled)
            assertFalse(live.active)
            assertFalse(live.stoppedByAndroidTimeout)
            assertEquals(listOf(false), persisted, "live mode is saved off")
            live.handleNativeTimeout("systemTimeout")
            assertTrue(live.stoppedByAndroidTimeout)
        }

    private val input =
        LiveInput(
            sessions =
                mapOf(
                    "old" to LiveSession("old", "Older run", 10),
                    "new" to LiveSession("new", "  Fix login  ", 20),
                    "idle" to LiveSession("idle", "Idle one", 30),
                ),
            busy = setOf("old", "new"),
            pendingPermissions = 1,
            pendingQuestions = 1,
        )

    @Test
    fun `B-ANDR-033 the live status counts runs and requests and names the freshest busy session`() =
        runTest {
            val composer = LiveStatusComposer(EnglishLiveWords) { }
            composer.update(input)
            val status = composer.status()
            assertEquals(2, status.runningCount)
            assertEquals(2, status.pendingCount)
            assertEquals("Fix login", status.title)
            assertEquals(LiveStatusKind.NeedsYou, status.status?.kind)
            assertEquals("2 need you", status.status?.title)
        }

    @Test
    fun `B-ANDR-033 it publishes on state changes and tool events`() =
        runTest {
            val r = Recorder()
            val live = publisher(r)
            val composer = LiveStatusComposer(EnglishLiveWords, live::publish)
            composer.update(input)
            val first = r.calls.single()
            assertEquals(2, first["runningCount"])
            assertEquals(2, first["pendingCount"])
            assertEquals("Fix login", first["title"])
            assertNull(first["detail"])
            assertEquals("needs_you", (first["status"] as Map<*, *>)["kind"])

            composer.onToolPart("new", "edit", "running")
            assertEquals("Editing files…", composer.status().detail)
            delay(debounce * 2)
            assertEquals("Editing files…", r.calls.last()["detail"])

            composer.onToolPart("new", "edit", "completed")
            assertNull(composer.status().detail)
        }

    @Test
    fun `B-ANDR-033 the running sentence follows the app language`() =
        runTest {
            val composer = LiveStatusComposer(EnglishLiveWords) { }
            composer.update(input)
            composer.setWords(ArabicLiveWords)
            composer.onToolPart("new", "edit", "running")
            assertEquals("جارٍ تعديل الملفات…", composer.status().detail)
            for (tool in listOf("bash", "read", "grep", "webfetch", "task", "")) {
                assertTrue(Regex("[؀-ۿ]").containsMatchIn(LiveStatusComposer.toolSentence(tool, ArabicLiveWords)), tool)
            }
            assertEquals("جارٍ تشغيل mcp thing…", LiveStatusComposer.toolSentence("mcp_thing", ArabicLiveWords))
        }

    @Test
    fun `B-ANDR-033 tool sentences never repeat the tool input`() {
        assertEquals("Running a command…", LiveStatusComposer.toolSentence("bash", EnglishLiveWords))
        assertEquals("Editing files…", LiveStatusComposer.toolSentence("Edit", EnglishLiveWords))
        assertEquals("Searching files…", LiveStatusComposer.toolSentence("grep", EnglishLiveWords))
        assertEquals("Running mcp thing…", LiveStatusComposer.toolSentence("mcp_thing", EnglishLiveWords))
        assertEquals("Working…", LiveStatusComposer.toolSentence("", EnglishLiveWords))
    }
}
