package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
 * Applies the widget's real RemoteViews (the layouts and binders the launcher gets) and draws
 * them, so the card is checked as a picture of what the launcher shows, not only as XML.
 *
 * With `WIDGET_RENDER_OUT=<dir>` every case is also written there as a PNG (the QA renders);
 * with `WIDGET_PREVIEW_OUT=<res dir>` the picker preview images are written into
 * `drawable-nodpi` and `drawable-night-nodpi` (launchers that ignore previewLayout use them).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetCardRenderTest {
    @org.junit.Before fun clock() {
        // Robolectric's boot clock starts at zero; a turn that began minutes ago needs a phone's uptime.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(3))
    }

    @Test fun aRequestLeadsWithItsQuestionAndAllowOnceInMint() {
        val v = render(WidgetFixtures.needs(), WIDE_W, WIDE_H, "needs-4x2")
        assertEquals("Run npm install?", text(v, R.id.wc_title))
        assertEquals("Needs you", text(v, R.id.wc_status))
        assertTrue(shown(v, R.id.wc_allow))
        assertEquals("Allow once", text(v, R.id.wc_allow))
        assertTrue(shown(v, R.id.wc_open))
        // The command is in the question, so no second line repeats it; no field under the buttons.
        assertTrue(!shown(v, R.id.wc_cmd))
        assertTrue(!shown(v, R.id.wc_field))
        assertEquals("This phone · neon-breaker", text(v, R.id.wc_meta))
    }

    @Test fun aLongCommandIsShownWholeOrNotAllowedFromTheWidget() {
        val v = render(WidgetFixtures.needs(command = WidgetFixtures.LONG_COMMAND), WIDE_W, WIDE_H, "needs-long-4x2")
        assertEquals("Run this command?", text(v, R.id.wc_title))
        assertTrue(shown(v, R.id.wc_cmd))
        assertTrue(shown(v, R.id.wc_allow))
        // On a 2x2 the same command does not fit whole: Open only.
        val long = WidgetFixtures.needs(command = WidgetFixtures.LONG_COMMAND)
        val small = render(long, CARD_W, CARD_H, "needs-long-2x2")
        assertTrue(!shown(small, R.id.wc_cmd))
        assertTrue(!shown(small, R.id.wc_allow))
        assertTrue(shown(small, R.id.wc_open))
    }

    @Test fun aRequestTheWidgetCannotAnswerOffersOpenAlone() {
        val v = render(WidgetFixtures.question(), WIDE_W, WIDE_H, "question-4x2")
        assertEquals("Needs you", text(v, R.id.wc_status))
        assertTrue(!shown(v, R.id.wc_allow))
        assertTrue(shown(v, R.id.wc_open))
    }

    @Test fun aWorkingTurnShowsItsTitleAndOneLiveLine() {
        val v = render(WidgetFixtures.working(), WIDE_W, WIDE_H, "working-4x2")
        assertEquals("Neon brick breaker", text(v, R.id.wc_title))
        assertTrue(shown(v, R.id.wc_live_row))
        assertTrue(clockText(v, R.id.wc_live).startsWith("Editing files · "))
        // No header status: the live line says it.
        assertTrue(!shown(v, R.id.wc_status))
        assertEquals("Steer while it works…", text(v, R.id.wc_field_text))
    }

    @Test fun aTurnTheWidgetCannotSeeLiveIsNotShownAsRunning() {
        val v = render(WidgetFixtures.all().getValue("offline"), WIDE_W, WIDE_H, null)
        assertTrue(clockText(v, R.id.wc_live).startsWith("Offline · last seen"))
        // No steering field: only the app can reach it now.
        assertTrue(!shown(v, R.id.wc_field))
        assertTrue(shown(v, R.id.wc_open))
        assertTrue(!shown(v, R.id.wc_allow))
    }

    @Test fun nothingWaitingShowsTheLastConversationAndAQuietNewConversationField() {
        val v = render(WidgetFixtures.idle(), WIDE_W, WIDE_H, "idle-4x2")
        assertEquals("Neon brick breaker", text(v, R.id.wc_title))
        assertTrue(text(v, R.id.wc_meta).startsWith("This phone · neon-breaker · "))
        assertEquals("New conversation in neon-breaker", text(v, R.id.wc_field_text))
        assertTrue(!shown(v, R.id.wc_buttons))
        assertTrue(!shown(v, R.id.wc_status))
    }

    @Test fun aTallCardAddsRowsAndNothingElse() {
        val v = render(WidgetFixtures.needs(withOthers = true), WIDE_W, LIST_H, "needs-4x4")
        assertTrue(shown(v, R.id.wc_rows))
        val rows = v.findViewById<android.view.ViewGroup>(R.id.wc_rows)
        assertTrue(rows.childCount in 2..5)
        // The same card at 4x2 has no rows.
        assertTrue(!shown(render(WidgetFixtures.needs(withOthers = true), WIDE_W, WIDE_H, null), R.id.wc_rows))
    }

    @Test fun everySizeRendersWithoutCuttingTheBottomAction() {
        for ((name, size) in SIZES) {
            for (fixture in listOf(WidgetFixtures.needs(), WidgetFixtures.working(), WidgetFixtures.idle())) {
                val v = render(fixture, size.first, size.second, null)
                val bottom = listOf(R.id.wc_buttons, R.id.wc_field, R.id.wc_allow, R.id.wc_mic)
                    .mapNotNull { v.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }
                for (b in bottom) {
                    assertTrue("$name ${b.id}", b.bottom <= (b.parent as View).height)
                }
            }
        }
    }

    @Test fun lightArabicAndLargeTextRender() {
        RuntimeEnvironment.setQualifiers("+ar")
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        val ar = render(WidgetFixtures.needs(server = "هذا الهاتف"), WIDE_W, WIDE_H, "needs-4x2-ar")
        assertEquals(View.LAYOUT_DIRECTION_RTL, ar.layoutDirection)
        render(WidgetFixtures.working(server = "هذا الهاتف"), WIDE_W, WIDE_H, "working-4x2-ar")
        java.util.Locale.setDefault(java.util.Locale.US)
        RuntimeEnvironment.setQualifiers("+en")
        RuntimeEnvironment.setFontScale(2f)
        render(WidgetFixtures.needs(), 320, WIDE_H, "needs-320-2x")
        render(WidgetFixtures.working(), 320, WIDE_H, "working-320-2x")
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test fun lightCards() {
        for ((name, fixture) in WidgetFixtures.all()) {
            val light = fixture.put("appTheme", JSONObject().put("background", 0xFFF3F3F1L))
            render(light, WIDE_W, WIDE_H, "$name-4x2-light")
        }
    }

    @Test fun everyStateAtEverySize() {
        for ((name, fixture) in WidgetFixtures.all()) {
            for ((size, wh) in SIZES) render(fixture, wh.first, wh.second, "$name-$size")
        }
        render(JSONObject(), WIDE_W, WIDE_H, "empty-4x2")
    }

    /**
     * TalkBack speaks a Chronometer's elapsed time whatever its contentDescription, so a stopped
     * clock ("3 min ago") was read as "211 hours". Every clock on screen must either be a counting
     * Chronometer or a plain TextView that reads exactly what it shows.
     */
    @Test fun aStoppedClockIsReadAloudAsWhatItShows() {
        val ids = listOf(R.id.wc_live, R.id.wc_row_state, R.id.wc_sub)
        val stillIds = listOf(R.id.wc_live_still, R.id.wc_row_state_still, R.id.wc_sub_still)
        val fixtures = WidgetFixtures.all() + ("others" to WidgetFixtures.needs(withOthers = true))
        val clocks = fixtures.flatMap { (name, fixture) ->
            SIZES.flatMap { (size, wh) ->
                val v = render(fixture, wh.first, wh.second, null)
                allViews(v).filter { it.id in ids + stillIds && onScreen(v, it) }
                    .map { "$name $size ${v.resources.getResourceEntryName(it.id)}" to it }
            }
        }
        val stopped = clocks.count { (tag, c) -> checkClockReadsAloud(c, tag) }
        assertTrue("some stopped clock was checked", stopped > 0)
    }

    /** True when [c] is a stopped clock (a plain TextView), after checking it reads what it shows. */
    private fun checkClockReadsAloud(c: View, tag: String): Boolean {
        if (c is android.widget.Chronometer) {
            assertTrue("$tag: a stopped Chronometer would be spoken as an elapsed time", c.format != null)
            return false
        }
        c as TextView
        assertTrue("$tag: has words", c.text.isNotBlank())
        assertEquals("$tag: reads what it shows", null, c.contentDescription)
        return true
    }

    private fun allViews(v: View): List<View> =
        listOf(v) + ((v as? android.view.ViewGroup)?.let { g ->
            (0 until g.childCount).flatMap { allViews(g.getChildAt(it)) }
        } ?: emptyList())

    private fun onScreen(root: View, v: View): Boolean =
        generateSequence(v) { c -> (c.parent as? View).takeIf { c !== root } }.all { it.visibility == View.VISIBLE }

    /** The words the live clock shows: the counting Chronometer, else its stopped twin. */
    private fun clockText(root: View, id: Int): String =
        text(root, if (shown(root, id)) id else R.id.wc_live_still)

    /** The picker images, at about 1x of a 420dpi phone (the size the launcher draws them). */
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun pickerPreviews() {
        val out = System.getenv("WIDGET_PREVIEW_OUT")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
            val dir = File(out, if (night) "drawable-night-nodpi" else "drawable-nodpi").apply { mkdirs() }
            val theme = JSONObject().put("background", if (night) 0xFF0B0C0EL else 0xFFF3F3F1L)
            for ((name, case) in PREVIEWS) {
                val snapshot = case.first().put("appTheme", theme)
                val v = render(snapshot, case.second.first, case.second.second, null)
                File(dir, "$name.png").outputStream().use { draw(v).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun render(snapshot: JSONObject, widthDp: Int, heightDp: Int, name: String?): View {
        val context = context()
        val views = SessionWidgetRenderer(context, 1, snapshot, snapshot.optBoolean("connected"))
            .render(widthDp, heightDp)
        val view = views.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt()
        val h = (heightDp * density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        // A launcher formats a counting Chronometer when it attaches; this view never attaches.
        fun tick(v: View) {
            if (v is android.widget.Chronometer && v.format != null) v.base = v.base
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) tick(v.getChildAt(i))
        }
        tick(view)
        val out = System.getenv("WIDGET_RENDER_OUT")?.takeIf { it.isNotBlank() }
        if (out != null && name != null) {
            File(out).mkdirs()
            File(out, "$name.png").outputStream().use { draw(view).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return view
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    /** Visible itself and in every parent up to the root. */
    private fun shown(root: View, id: Int): Boolean =
        generateSequence(root.findViewById<View>(id)) { v -> (v.parent as? View).takeIf { v !== root } }
            .let { chain -> chain.any() && chain.all { it.visibility == View.VISIBLE } }

    private fun text(root: View, id: Int): String = root.findViewById<TextView>(id).text.toString()

    private companion object {
        // Launcher3 portrait cells on a 412dp phone: 2x2, 4x2, 4x4, 4x1 and 2x1.
        const val CARD_W = 170
        const val CARD_H = 190
        const val WIDE_W = 360
        const val WIDE_H = 190
        const val LIST_H = 400
        val SIZES = linkedMapOf(
            "2x2" to (CARD_W to CARD_H),
            "4x2" to (WIDE_W to WIDE_H),
            "4x4" to (WIDE_W to LIST_H),
            "4x1" to (WIDE_W to 96),
            "2x1" to (CARD_W to 96),
        )
        // The old images' pixel sizes at 420dpi (2.625): 462x586, 966x272, 966x1214. The 4x2 entry's
        // image is the Conversations widget's (TwoWidgetsRenderTest).
        val PREVIEWS: Map<String, Pair<() -> JSONObject, Pair<Int, Int>>> = linkedMapOf(
            "widget_preview_sessions" to ({ WidgetFixtures.needs() } to (176 to 223)),
            "widget_preview_sessions_strip" to ({ WidgetFixtures.needs() } to (368 to 104)),
            "widget_preview_sessions_list" to ({ WidgetFixtures.needs(withOthers = true) } to (368 to 462)),
        )
    }
}

/** Sample snapshots in the published v2 shape. Sample words only: never anyone's data. */
internal object WidgetFixtures {
    const val LONG_COMMAND = "npm run build -- --mode production && npm test -- --watch=false"
    private const val MINUTE = 60_000L

    fun all(): Map<String, JSONObject> = linkedMapOf(
        "needs" to needs(),
        "working" to working(),
        "idle" to idle(),
        "question" to question(),
        // The app closed while a turn ran: the cached turn cannot be shown as running.
        "offline" to working().put("connected", false).put("pausedAt", System.currentTimeMillis() - 5 * MINUTE),
    )

    private fun base(rows: List<JSONObject>) = JSONObject()
        .put("version", 2).put("profileID", "p1").put("scopeToken", "s1").put("connected", true)
        .put("lastProjectID", "proj").put("onDemand", JSONObject().put("fingerprint", "f1"))
        .put("runningCount", rows.count { it.optBoolean("busy") })
        .put("pendingCount", rows.count { it.has("requestID") || it.optBoolean("needsYou") })
        .put("sessions", JSONArray(rows))
        .put("appTheme", JSONObject().put("background", 0xFF0B0C0EL))

    private fun row(id: String, title: String, project: String = "neon-breaker", server: String = "This phone") =
        JSONObject().put("id", id).put("title", title).put("project", project).put("server", server)
            .put("steerMode", "steer").put("onDemandActions", JSONArray())
            .put("updatedAt", System.currentTimeMillis() - 26 * MINUTE)

    private fun others() = listOf(
        row("s2", "Settings page dark mode", "settings-demo").put("busy", true)
            .put("startedAt", System.currentTimeMillis() - 3 * MINUTE).put("step", "Running a command…"),
        row("s3", "Fix login redirect", "api-server", "MacBook").put("needsYou", true),
        row("s4", "Write theme tests", "settings-demo").put("updatedAt", System.currentTimeMillis() - 11 * 60 * MINUTE),
        row("s5", "Landing page copy", "site"),
    )

    fun needs(command: String = "npm install", withOthers: Boolean = false, server: String = "This phone"): JSONObject =
        base(listOf(row("s1", "Neon brick breaker", server = server).put("busy", true).put("requestID", "r1")
            .put("startedAt", System.currentTimeMillis() - 2 * MINUTE)
            .put("onDemandActions", JSONArray(listOf("allowOnce", "reject")))
            .put("ask", JSONObject().put("kind", "run").put("q", "Run this command?").put("file", command)))
            + if (withOthers) others() else emptyList())

    fun question(): JSONObject = base(listOf(row("s1", "Neon brick breaker").put("needsYou", true)
        .put("ask", JSONObject().put("kind", "question").put("q", "Which colour should the paddle be?"))))

    fun working(server: String = "This phone"): JSONObject = base(listOf(
        row("s1", "Neon brick breaker", server = server).put("busy", true)
        .put("startedAt", System.currentTimeMillis() - 2 * MINUTE - 13_000).put("step", "Editing files…")))

    fun idle(): JSONObject = base(listOf(row("s1", "Neon brick breaker")))
}
