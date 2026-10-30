package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two picker widgets (owner, 2026-10-29), drawn from their real RemoteViews: Conversations
 * (4x2) and Status (2x2). With `WIDGET_RENDER_OUT=<dir>` every case is written there as a PNG;
 * with `WIDGET_PREVIEW_OUT=<res dir>` their picker images are written into `drawable-nodpi` and
 * `drawable-night-nodpi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TwoWidgetsRenderTest {
    @org.junit.Before fun clock() {
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(3))
    }

    @Test fun aWorkingTurnLeadsAndTheNextConversationsFollowAsRows() {
        val v = conversations(TwoFixtures.working(), W4, TALL_W4H, "conversations-working")
        assertEquals("Fix the TV focus ring", text(v, R.id.cv_title))
        val meta = text(v, R.id.cv_meta)
        assertTrue(meta, meta.startsWith("Working · IPTV_King · git add -A · 3 min"))
        assertTrue(!shown(v, R.id.cv_buttons))
        val rows = v.findViewById<ViewGroup>(R.id.cv_rows)
        assertTrue(shown(v, R.id.cv_rows))
        assertTrue("${rows.childCount} rows", rows.childCount >= 2)
        val first = rows.getChildAt(0)
        assertEquals("Say hi", first.findViewById<TextView>(R.id.cv_row_title).text.toString())
        assertEquals("settings-demo · 16 min", first.findViewById<TextView>(R.id.cv_row_meta).text.toString())
        assertEquals("Conversations", text(v, R.id.cv_label))
        assertTrue(shown(v, R.id.cv_new))
        // Nothing is cut at the bottom.
        assertTrue(rows.bottom <= (rows.parent as View).height)
    }

    @Test fun aRequestShowsItsAskWithAllowReplyAndOpen() {
        val v = conversations(TwoFixtures.needs(), W4, H2, "conversations-needs")
        assertTrue(!shown(v, R.id.cv_title))
        assertEquals("Needs you · IPTV_King · Claude Code wants to run", text(v, R.id.cv_meta))
        assertEquals("git push origin main", text(v, R.id.cv_ask))
        assertEquals("Allow", text(v, R.id.cv_allow))
        assertTrue(shown(v, R.id.cv_allow) && shown(v, R.id.cv_reply) && shown(v, R.id.cv_open))
        assertEquals("Reply", text(v, R.id.cv_reply))
        val buttons = v.findViewById<View>(R.id.cv_buttons)
        assertTrue(buttons.bottom <= (buttons.parent as View).height)
    }

    @Test fun everyConversationsTargetIsAtLeast48dpAndAnswersSayWhichConversation() {
        val v = conversations(TwoFixtures.needs(), W4, H2, null)
        val density = v.resources.displayMetrics.density
        for (id in listOf(R.id.cv_new, R.id.cv_allow_hit, R.id.cv_reply_hit, R.id.cv_open_hit)) {
            val hit = v.findViewById<View>(id)
            assertTrue("$id is ${hit.height / density}dp", hit.height >= 48 * density - 1)
            assertTrue("$id is ${hit.width / density}dp wide", hit.width >= 48 * density - 1)
            assertTrue(hit.contentDescription?.isNotBlank() == true)
        }
        val title = TwoFixtures.needs().getJSONArray("sessions").getJSONObject(0).optString("title")
        assertEquals("Allow once: $title", v.findViewById<View>(R.id.cv_allow_hit).contentDescription.toString())
        assertEquals("Reply to $title", v.findViewById<View>(R.id.cv_reply_hit).contentDescription.toString())
        // The pills keep their look and are not read twice.
        assertEquals(34 * density, v.findViewById<View>(R.id.cv_allow).height.toFloat(), 1f)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, v.findViewById<View>(R.id.cv_allow).importantForAccessibility)
        val wide = conversations(TwoFixtures.working(), W4, 400, null)
        val rows = wide.findViewById<ViewGroup>(R.id.cv_rows)
        assertTrue(rows.childCount >= 1)
        for (i in 0 until rows.childCount) assertTrue(rows.getChildAt(i).height >= 48 * density - 1)
    }

    @Test fun everyStatusLineIsAtLeast48dp() {
        val v = status(TwoFixtures.statusOk(), true, TwoFixtures.plans(62), null)
        val group = v.findViewById<ViewGroup>(R.id.st_lines)
        val density = v.resources.displayMetrics.density
        assertTrue(group.childCount >= 3)
        for (i in 0 until group.childCount) assertTrue(group.getChildAt(i).height >= 48 * density - 1)
    }

    @Test fun aCommandTooLongToReadWholeIsAnsweredInTheApp() {
        val v = conversations(TwoFixtures.needs(command = WidgetFixtures.LONG_COMMAND + " && " +
            WidgetFixtures.LONG_COMMAND), W4, H2, null)
        assertTrue(!shown(v, R.id.cv_allow))
        assertTrue(shown(v, R.id.cv_open))
    }

    @Test fun nothingWaitingShowsTheLastConversation() {
        val v = conversations(TwoFixtures.idle(), W4, H2, "conversations-idle")
        assertEquals("Say hi", text(v, R.id.cv_title))
        assertTrue(text(v, R.id.cv_meta).startsWith("settings-demo · 16 min ago"))
        assertEquals("Open the app to connect", text(conversations(JSONObject(), W4, H2, "conversations-empty"),
            R.id.cv_title))
    }

    @Test fun statusSaysEachThingAndThePlanClosestToRunningOut() {
        val v = status(TwoFixtures.statusOk(), true, TwoFixtures.plans(62), "status-ok")
        val lines = lines(v)
        assertEquals(listOf("Laptop|online", "This phone|online", "Linux|running", "Tasks|2 running"), lines)
        assertEquals("Claude · 5 h", text(v, R.id.st_plan_name))
        assertEquals("62% left", text(v, R.id.st_plan_left))
    }

    @Test fun problemsComeFirst() {
        val v = status(TwoFixtures.statusProblem(), true, TwoFixtures.plans(8), "status-problem")
        val lines = lines(v)
        assertEquals("Laptop|offline 12 min", lines.first())
        assertTrue(lines.contains("Tasks|none"))
        assertEquals("8% left", text(v, R.id.st_plan_left))
    }

    @Test fun fewLinesSitInTheMiddleAndAFullCellStartsAtTheTop() {
        val few = TwoFixtures.statusOk().put("servers", JSONArray(listOf(
            JSONObject().put("name", "This phone").put("state", "online").put("phone", true))))
        val v = statusAt(few, false, emptyList(), "status-few", W2 to TALL_H2)
        assertTrue(shown(v, R.id.st_gap_top))
        val lines = v.findViewById<View>(R.id.st_lines)
        val header = v.findViewById<View>(R.id.st_header)
        // The gap above the lines matches the gap under them, within a line.
        val above = lines.top - header.bottom
        val below = (lines.parent as View).height - lines.bottom
        val density = v.resources.displayMetrics.density
        assertTrue("$above vs $below", kotlin.math.abs(above - below) < 30 * density)
        val full = statusAt(TwoFixtures.statusOk(), true, TwoFixtures.plans(62), null, W2 to H2)
        assertTrue(!shown(full, R.id.st_gap_top))
    }

    @Test fun replyOpensTheConversationReadyToTypeAndOpenJustOpensIt() {
        val ctx = context()
        val snapshot = TwoFixtures.needs()
        val session = snapshot.getJSONArray("sessions").getJSONObject(0)
        val open = SessionWidgetIntents.chatIntent(ctx, 1, snapshot, session)
        val reply = SessionWidgetIntents.chatIntent(ctx, 1, snapshot, session, reply = true)
        assertTrue(open != reply)
        val shadowReply = org.robolectric.Shadows.shadowOf(reply).savedIntent
        val shadowOpen = org.robolectric.Shadows.shadowOf(open).savedIntent
        assertTrue(shadowReply.getBooleanExtra(SessionWidgetIntents.EXTRA_REPLY, false))
        assertTrue(!shadowOpen.getBooleanExtra(SessionWidgetIntents.EXTRA_REPLY, false))
    }

    @Test fun editScopesTheRowsToOneProject() {
        val prefs = context().getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        prefs.edit().putString("flutter.oc.widgetStyle.p1",
            JSONObject().put("1", JSONObject().put("project", "settings-demo")).toString()).commit()
        try {
            val v = conversations(TwoFixtures.working(), W4, H2, "conversations-scoped")
            assertEquals("Say hi", text(v, R.id.cv_title))
            assertEquals("Conversations · settings-demo", text(v, R.id.cv_label))
            assertTrue(!shown(v, R.id.cv_rows))
            val none = conversations(TwoFixtures.working().put("sessions", JSONArray()), W4, H2, null)
            assertEquals("No conversations in settings-demo yet", text(none, R.id.cv_title))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun whenTheAppIsNotWatchingTheHeaderSaysHowLongAgo() {
        val paused = TwoFixtures.statusOk().put("connected", false)
            .put("pausedAt", System.currentTimeMillis() - 5 * 60_000L)
        val v = status(paused, true, TwoFixtures.plans(62), "status-paused")
        assertTrue(shown(v, R.id.st_age))
        assertEquals("5 min ago", text(v, R.id.st_age))
        assertTrue(!shown(status(TwoFixtures.statusOk(), true, TwoFixtures.plans(62), null), R.id.st_age))
    }

    @Test fun lightArabicAndLargeText() {
        val light = JSONObject().put("background", 0xFFF3F3F1L)
        conversations(TwoFixtures.working().put("appTheme", light), W4, H2, "conversations-working-light")
        conversations(TwoFixtures.needs().put("appTheme", light), W4, H2, "conversations-needs-light")
        status(TwoFixtures.statusOk().put("appTheme", light), true, TwoFixtures.plans(62), "status-ok-light")
        status(TwoFixtures.statusProblem().put("appTheme", light), true, TwoFixtures.plans(8), "status-problem-light")
        RuntimeEnvironment.setQualifiers("+ar")
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        val ar = conversations(TwoFixtures.needs(), W4, H2, "conversations-needs-ar")
        assertEquals(View.LAYOUT_DIRECTION_RTL, ar.layoutDirection)
        status(TwoFixtures.statusProblem(), true, TwoFixtures.plans(8), "status-problem-ar")
        java.util.Locale.setDefault(java.util.Locale.US)
        RuntimeEnvironment.setQualifiers("+en")
        RuntimeEnvironment.setFontScale(1.5f)
        conversations(TwoFixtures.working(), W4, H2, "conversations-working-1.5x")
        status(TwoFixtures.statusOk(), true, TwoFixtures.plans(62), "status-ok-1.5x")
        RuntimeEnvironment.setFontScale(1f)
    }

    /** The picker images, at about 1x of a 420dpi phone (the size the launcher draws them). */
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun pickerPreviews() {
        val out = System.getenv("WIDGET_PREVIEW_OUT")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
            val dir = File(out, if (night) "drawable-night-nodpi" else "drawable-nodpi").apply { mkdirs() }
            val theme = JSONObject().put("background", if (night) 0xFF0B0C0EL else 0xFFF3F3F1L)
            val c = conversations(TwoFixtures.working().put("appTheme", theme), PREVIEW_W4, PREVIEW_H2, null)
            File(dir, "widget_preview_sessions_wide.png").outputStream()
                .use { draw(c).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val s = statusAt(TwoFixtures.statusOk().put("appTheme", theme), true, TwoFixtures.plans(62), null,
                PREVIEW_W2 to PREVIEW_H2)
            File(dir, "widget_preview_status.png").outputStream()
                .use { draw(s).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun conversations(snapshot: JSONObject, w: Int, h: Int, name: String?): View =
        lay(SessionWidgetRenderer(context(), 1, snapshot, snapshot.optBoolean("connected"))
            .renderConversations(w, h), w, h, name)

    private fun status(snapshot: JSONObject, linux: Boolean, plans: List<UsageWidgetRow>, name: String?): View =
        statusAt(snapshot, linux, plans, name, W2 to H4)

    private fun statusAt(snapshot: JSONObject, linux: Boolean, plans: List<UsageWidgetRow>, name: String?,
        size: Pair<Int, Int>): View = lay(StatusWidgetRenderer(context(), 1, snapshot,
            StatusWidgetRenderer.Linux(linux, linux), plans).render(size.second), size.first, size.second, name)

    private fun lay(views: android.widget.RemoteViews, widthDp: Int, heightDp: Int, name: String?): View {
        val context = context()
        val view = views.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt()
        val h = (heightDp * density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val out = System.getenv("WIDGET_RENDER_OUT")?.takeIf { it.isNotBlank() }
        if (out != null && name != null) {
            File(out).mkdirs()
            File(out, "$name.png").outputStream().use { draw(view).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return view
    }

    private fun lines(v: View): List<String> {
        val group = v.findViewById<ViewGroup>(R.id.st_lines)
        return (0 until group.childCount).map { group.getChildAt(it) }.map {
            it.findViewById<TextView>(R.id.st_line_name).text.toString() + "|" +
                it.findViewById<TextView>(R.id.st_line_value).text.toString()
        }
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun shown(root: View, id: Int): Boolean =
        generateSequence(root.findViewById<View>(id)) { v -> (v.parent as? View).takeIf { v !== root } }
            .let { chain -> chain.any() && chain.all { it.visibility == View.VISIBLE } }

    private fun text(root: View, id: Int): String = root.findViewById<TextView>(id).text.toString()

    private companion object {
        // Launcher3 portrait cells on a 412dp phone.
        const val W4 = 360
        const val W2 = 170
        const val H2 = 200
        const val TALL_H2 = 238
        const val H4 = 300
        const val TALL_W4H = 300
        const val PREVIEW_W4 = 368
        const val PREVIEW_W2 = 176
        const val PREVIEW_H2 = 223
    }
}

/** Sample snapshots for the two widgets, in the words of the approved mockups. Never anyone's data. */
internal object TwoFixtures {
    private const val MINUTE = 60_000L
    private val now get() = System.currentTimeMillis()

    private fun base(rows: List<JSONObject>) = JSONObject()
        .put("version", 2).put("profileID", "p1").put("scopeToken", "s1").put("connected", true)
        .put("lastProjectID", "proj").put("onDemand", JSONObject().put("fingerprint", "f1"))
        .put("runningCount", rows.count { it.optBoolean("busy") })
        .put("pendingCount", rows.count { it.has("requestID") || it.optBoolean("needsYou") })
        .put("sessions", JSONArray(rows))
        .put("appTheme", JSONObject().put("background", 0xFF0B0C0EL))

    private fun row(id: String, title: String, project: String, minutesAgo: Long) =
        JSONObject().put("id", id).put("title", title).put("project", project).put("server", "Laptop")
            .put("steerMode", "steer").put("onDemandActions", JSONArray())
            .put("updatedAt", now - minutesAgo * MINUTE)

    private fun others() = listOf(
        row("s2", "Say hi", "settings-demo", 16),
        row("s3", "Design review", "IPTV_King", 60),
        row("s4", "Widget sizes", "oc_app", 180),
    )

    fun working(): JSONObject = base(listOf(row("s1", "Fix the TV focus ring", "IPTV_King", 0).put("busy", true)
        .put("startedAt", now - 3 * MINUTE - 5_000).put("step", "git add -A")) + others())

    fun needs(command: String = "git push origin main"): JSONObject = base(listOf(
        row("s1", "Fix the TV focus ring", "IPTV_King", 0).put("busy", true).put("requestID", "r1")
            .put("startedAt", now - 2 * MINUTE)
            .put("onDemandActions", JSONArray(listOf("allowOnce", "reject")))
            .put("ask", JSONObject().put("kind", "run").put("q", "Claude Code wants to run").put("file", command)),
    ) + others())

    fun idle(): JSONObject = base(others())

    fun statusOk(): JSONObject = working().put("runningCount", 2).put("servers", JSONArray(listOf(
        JSONObject().put("name", "Laptop").put("state", "online"),
        JSONObject().put("name", "This phone").put("state", "online").put("phone", true),
    )))

    fun statusProblem(): JSONObject = idle().put("runningCount", 0).put("servers", JSONArray(listOf(
        JSONObject().put("name", "This phone").put("state", "online").put("phone", true),
        JSONObject().put("name", "Laptop").put("state", "offline").put("since", now - 12 * MINUTE - 5_000),
    )))

    fun plans(left: Int): List<UsageWidgetRow> = listOf(
        UsageWidgetRow("claude", "", "", 18_000L, "", left, now + 2 * 60 * MINUTE, now - 5 * MINUTE, false),
        UsageWidgetRow("claude", "", "", 604_800L, "", 90, now + 3 * 24 * 60 * MINUTE, now - 5 * MINUTE, false),
    )
}
