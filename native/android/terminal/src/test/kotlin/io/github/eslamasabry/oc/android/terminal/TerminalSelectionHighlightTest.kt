package io.github.eslamasabry.oc.android.terminal

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import com.termux.view.TerminalView
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderSession
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class TerminalSelectionHighlightTest {
    private data class RecordedRect(val rect: RectF, val color: Int)

    private data class RecordedText(val text: String, val x: Float, val y: Float, val color: Int)

    private data class ThemeSample(
        val name: String,
        val ground: Int,
        val palette: TerminalPalette,
    )

    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val rects = mutableListOf<RecordedRect>()
        val texts = mutableListOf<RecordedText>()

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            rects += RecordedRect(RectF(left, top, right, bottom), paint.color)
            super.drawRect(left, top, right, bottom, paint)
        }

        override fun drawRect(rect: RectF, paint: Paint) {
            rects += RecordedRect(RectF(rect), paint.color)
            super.drawRect(rect, paint)
        }

        override fun drawRect(r: Rect, paint: Paint) {
            rects += RecordedRect(RectF(r), paint.color)
            super.drawRect(r, paint)
        }

        override fun drawTextRun(
            text: CharArray,
            index: Int,
            count: Int,
            contextIndex: Int,
            contextCount: Int,
            x: Float,
            y: Float,
            isRtl: Boolean,
            paint: Paint,
        ) {
            texts += RecordedText(String(text, index, count), x, y, paint.color)
            super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint)
        }

        override fun drawTextRun(
            text: CharSequence,
            start: Int,
            end: Int,
            contextStart: Int,
            contextEnd: Int,
            x: Float,
            y: Float,
            isRtl: Boolean,
            paint: Paint,
        ) {
            texts += RecordedText(text.subSequence(start, end).toString(), x, y, paint.color)
            super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            texts += RecordedText(text, x, y, paint.color)
            super.drawText(text, x, y, paint)
        }
    }

    private class ActionModeCaptureFrame(activity: Activity) : FrameLayout(activity) {
        var lastCallback: ActionMode.Callback2? = null
        var lastType: Int = -1
        var lastMode: ActionMode? = null

        override fun startActionModeForChild(
            originalView: View?,
            callback: ActionMode.Callback?,
            type: Int,
        ): ActionMode? {
            lastType = type
            lastCallback = callback as? ActionMode.Callback2
            val mode = FakeActionMode(context, originalView ?: this, callback)
            lastMode = mode
            return mode
        }
    }

    private class FakeActionMode(
        private val ctx: android.content.Context,
        private val anchor: View,
        private val callback: ActionMode.Callback?,
    ) : ActionMode() {
        private val popupMenu = PopupMenu(ctx, anchor)
        private var finished = false

        override fun setTitle(title: CharSequence?) = Unit

        override fun setTitle(resId: Int) = Unit

        override fun setSubtitle(subtitle: CharSequence?) = Unit

        override fun setSubtitle(resId: Int) = Unit

        override fun setCustomView(view: View?) = Unit

        override fun invalidate() = Unit

        override fun finish() {
            if (!finished) {
                finished = true
                callback?.onDestroyActionMode(this)
            }
        }

        override fun getMenu(): Menu = popupMenu.menu

        override fun getTitle(): CharSequence = ""

        override fun getSubtitle(): CharSequence = ""

        override fun getCustomView(): View? = null

        override fun getMenuInflater(): MenuInflater = MenuInflater(ctx)
    }

    private class Harness(
        val activity: Activity,
        val container: ActionModeCaptureFrame,
        val host: TerminalSurfaceHost,
        val terminal: TerminalView,
        val session: RemoteTerminalRenderSession,
        val copied: MutableList<String>,
    )

    private val immediateDispatcher =
        object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                block.run()
            }
        }

    private val dispatchers =
        object : AppDispatchers {
            override val main: CoroutineDispatcher = immediateDispatcher
            override val default: CoroutineDispatcher = immediateDispatcher
            override val io: CoroutineDispatcher = immediateDispatcher

            override fun serial(name: String): CoroutineDispatcher = immediateDispatcher
        }

    private fun <T> runSync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context: CoroutineContext = EmptyCoroutineContext

                override fun resumeWith(res: Result<T>) {
                    result = res
                }
            }
        )
        return requireNotNull(result) { "Suspend block did not complete synchronously" }.getOrThrow()
    }

    private fun setupHarness(widthPx: Int = 1080, heightPx: Int = 1920): Harness {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val copied = mutableListOf<String>()
        val renderer =
            AndroidRemoteTerminalRenderer(
                app = RuntimeEnvironment.getApplication(),
                dispatchers = dispatchers,
                redactor = Redactor(),
                clipboard = TerminalTextClipboard { copied += it },
            )
        val outcome = runSync { renderer.open(onInput = {}, onResize = { _, _ -> }) }
        val session = (outcome as Outcome.Ok).value
        val host = session.view.native as TerminalSurfaceHost
        val terminal = host.getChildAt(0) as TerminalView
        val container = ActionModeCaptureFrame(activity)
        activity.setContentView(container)
        container.addView(host, FrameLayout.LayoutParams(widthPx, heightPx))
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        container.measure(wSpec, hSpec)
        container.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()
        return Harness(activity, container, host, terminal, session, copied)
    }

    private fun longPressCell(terminal: TerminalView, col: Int, row: Int): MotionEvent {
        val fontWidth = terminal.mRenderer.fontWidth
        val lineSpacing = terminal.mRenderer.fontLineSpacing
        val x = (col + 0.5f) * fontWidth
        val y = (row - terminal.topRow + 0.5f) * lineSpacing
        return MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, x, y, 0)
    }

    private fun palette(foreground: Int, accent: Int): TerminalPalette =
        TerminalPalette(
            ansi = List(16) { foreground },
            foreground = foreground,
            background = Color.TRANSPARENT,
            cursor = accent,
        )

    private fun themeSamples(): List<ThemeSample> =
        listOf(
            ThemeSample("Dark Solid", 0xFF0E1116.toInt(), palette(0xFFF0F4F8.toInt(), 0xFF4F8CFF.toInt())),
            ThemeSample("Dark Glass", 0xFF131926.toInt(), palette(0xFFF3F6FA.toInt(), 0xFF60A5FA.toInt())),
            ThemeSample("Dark Soft", 0xFF1A1D24.toInt(), palette(0xFFEBEFF5.toInt(), 0xFF7C9CFF.toInt())),
            ThemeSample("Light Solid", 0xFFF7F8FA.toInt(), palette(0xFF14171F.toInt(), 0xFF2563EB.toInt())),
            ThemeSample("Light Glass", 0xFFEEF2F8.toInt(), palette(0xFF111827.toInt(), 0xFF1D4ED8.toInt())),
            ThemeSample("Light Soft", 0xFFF4F1EC.toInt(), palette(0xFF1C1917.toInt(), 0xFF3B82F6.toInt())),
        )

    private fun blendOver(fg: Int, bg: Int): Int {
        val alpha = Color.alpha(fg) / 255.0
        val inv = 1.0 - alpha
        val r = (Color.red(fg) * alpha + Color.red(bg) * inv).toInt().coerceIn(0, 255)
        val g = (Color.green(fg) * alpha + Color.green(bg) * inv).toInt().coerceIn(0, 255)
        val b = (Color.blue(fg) * alpha + Color.blue(bg) * inv).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun contrastRatio(c1: Int, c2: Int): Double {
        val l1 = luminance(c1)
        val l2 = luminance(c2)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun assertThemeSelection(
        h: Harness,
        sample: ThemeSample,
        rowTop: Float,
        rowBottom: Float,
        lineSpacing: Float,
    ) {
        h.host.appearance(sample.palette)
        val event = longPressCell(h.terminal, col = 8, row = 1)
        h.terminal.startTextSelectionMode(event)
        event.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(h.terminal.isSelectingText, "${sample.name}: selection should be active")

        val bitmap = Bitmap.createBitmap(h.terminal.width, h.terminal.height, Bitmap.Config.ARGB_8888)
        val canvas = RecordingCanvas(bitmap)
        h.host.draw(canvas)

        val rowRects =
            canvas.rects.filter { r ->
                r.rect.width() > 0f &&
                    r.rect.height() > 0f &&
                    r.rect.bottom > rowTop &&
                    r.rect.top < rowBottom &&
                    r.rect.width() < h.terminal.width.toFloat()
            }
        assertTrue(rowRects.isNotEmpty(), "${sample.name}: expected a selection highlight rect")
        for (r in rowRects) {
            val alpha = Color.alpha(r.color)
            val hex = Integer.toHexString(r.color)
            assertTrue(alpha in 1..254, "${sample.name}: alpha must be translucent (1..254), got $alpha (0x$hex)")
            val fgBlend = blendOver(r.color, sample.palette.foreground)
            val bgBlend = blendOver(r.color, sample.ground)
            val contrast = contrastRatio(fgBlend, bgBlend)
            assertTrue(contrast >= 4.5, "${sample.name}: contrast too low ($contrast) with 0x$hex")
        }

        val selectedTexts =
            canvas.texts.filter { t -> t.text.contains("target") && t.y >= rowTop && t.y <= rowBottom + lineSpacing }
        assertTrue(selectedTexts.isNotEmpty(), "${sample.name}: selected characters must be drawn")
        for (t in selectedTexts) {
            val hex = Integer.toHexString(t.color)
            assertEquals(255, Color.alpha(t.color), "${sample.name}: selected text must stay opaque, got 0x$hex")
        }

        h.terminal.stopTextSelectionMode()
        bitmap.recycle()
    }

    @Test
    fun `B-481 selection highlight is translucent and keeps selected text readable in dark and light themes`() {
        val h = setupHarness()
        runSync {
            h.session.append("first line alpha\r\nsecond target beta\r\nthird line gamma\r\n")
        }
        shadowOf(Looper.getMainLooper()).idle()
        val lineSpacing = h.terminal.mRenderer.fontLineSpacing.toFloat()
        val rowTop = 1f * lineSpacing
        val rowBottom = 2f * lineSpacing

        for (sample in themeSamples()) {
            assertThemeSelection(h, sample, rowTop, rowBottom, lineSpacing)
        }
    }

    @Test
    fun `B-481 Copy Paste More action bar rect does not overlap selected lines`() {
        val h = setupHarness()
        val lines = (0..14).joinToString("\r\n") { i -> "line_$i selectable_word_$i end" } + "\r\n"
        runSync { h.session.append(lines) }
        shadowOf(Looper.getMainLooper()).idle()

        val lineSpacing = h.terminal.mRenderer.fontLineSpacing
        val toolbarHeight = 132
        for (row in listOf(0, 5, 10)) {
            val event = longPressCell(h.terminal, col = 10, row = row)
            h.terminal.startTextSelectionMode(event)
            event.recycle()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(ActionMode.TYPE_FLOATING, h.container.lastType, "row $row: expected floating ActionMode")
            val callback = assertNotNull(h.container.lastCallback, "row $row: expected ActionMode.Callback2")
            val mode = assertNotNull(h.container.lastMode, "row $row: expected ActionMode")

            val outRect = Rect()
            callback.onGetContentRect(mode, h.terminal, outRect)

            val selectedTop = (row - h.terminal.topRow) * lineSpacing
            val selectedBottom = (row - h.terminal.topRow + 1) * lineSpacing

            assertTrue(
                outRect.top <= selectedTop,
                "row $row: outRect.top (${outRect.top}) must be <= selectedTop ($selectedTop)",
            )
            assertTrue(
                outRect.bottom >= selectedBottom,
                "row $row: outRect.bottom (${outRect.bottom}) must be >= selectedBottom ($selectedBottom)",
            )

            // Simulate Android FloatingToolbar: above outRect.top if room fits, else below outRect.bottom.
            val toolbarTop =
                if (outRect.top >= toolbarHeight) outRect.top - toolbarHeight else outRect.bottom
            val toolbarBottom = toolbarTop + toolbarHeight
            val overlapsSelected = toolbarBottom > selectedTop && toolbarTop < selectedBottom
            assertTrue(
                !overlapsSelected,
                "row $row: toolbar [$toolbarTop,$toolbarBottom] overlaps [$selectedTop,$selectedBottom] ($outRect)",
            )

            h.terminal.stopTextSelectionMode()
        }
    }

    @Test
    fun `B-481 Copy action copies exact selected text with no trailing junk`() {
        val h = setupHarness()
        runSync { h.session.append("prefix token_alpha   \r\nsecond line\r\n") }
        shadowOf(Looper.getMainLooper()).idle()

        val event = longPressCell(h.terminal, col = 9, row = 0)
        h.terminal.startTextSelectionMode(event)
        event.recycle()
        shadowOf(Looper.getMainLooper()).idle()

        val callback = assertNotNull(h.container.lastCallback, "expected ActionMode.Callback2")
        val mode = assertNotNull(h.container.lastMode, "expected ActionMode")
        val menu = mode.menu
        assertTrue(callback.onCreateActionMode(mode, menu), "onCreateActionMode should succeed")
        val copyItem =
            (0 until menu.size()).map { menu.getItem(it) }.firstOrNull {
                it.title?.toString()?.contains("copy", ignoreCase = true) == true
            }
        assertNotNull(copyItem, "Copy action must be present in selection menu")
        assertEquals("token_alpha", h.host.selectedText(), "selectedText before Copy must match")

        assertTrue(callback.onActionItemClicked(mode, copyItem), "Copy action click must be handled")
        assertEquals(listOf("token_alpha"), h.copied, "Copy must write exact selected text with no trailing junk")
        assertTrue(!h.terminal.isSelectingText, "selection mode should end after Copy")
    }

    @Test
    fun `B-481 multi-line selection content rect encloses all selected rows cleanly`() {
        val range = normalizeSelectionRange(intArrayOf(6, 2, 12, 4))
        assertNotNull(range)
        val viewport =
            SelectionViewport(
                topRow = 0,
                columns = 40,
                fontWidth = 24f,
                lineSpacing = 48,
                viewWidth = 1080,
                viewHeight = 1920,
            )
        val rect = selectionActionContentRect(range, viewport, clearancePx = 24)
        assertEquals(0, rect.left)
        assertEquals(40 * 24, rect.right)
        assertTrue(rect.top <= 2 * 48, "top (${rect.top}) must be <= first selected row (${2 * 48})")
        assertTrue(rect.bottom >= 7 * 48, "bottom (${rect.bottom}) must be >= last selected row (${7 * 48})")
    }
}
