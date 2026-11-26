package io.github.eslamasabry.oc.android.terminal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.termux.view.TerminalView
import com.termux.view.textselection.TextSelectionCursorController
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import java.lang.reflect.Field

/** Translucent selection alpha (~0.28f), matching KitTerminalViewDefaults.SELECTION_ALPHA. */
internal const val SELECTION_HIGHLIGHT_ALPHA: Int = 71
private const val DEFAULT_SELECTION_ACCENT: Int = 0xFF3B82F6.toInt()
private const val ACTION_BAR_CLEARANCE_DP: Float = 8f
private const val SELECTOR_COUNT: Int = 4
private const val NO_SELECTION: Int = -1
private const val INDEX_Y1: Int = 0
private const val INDEX_Y2: Int = 1
private const val INDEX_X1: Int = 2
private const val INDEX_X2: Int = 3

/** Normalized inclusive cell range of an active terminal selection. */
internal data class SelectionRange(val startY: Int, val startX: Int, val endY: Int, val endX: Int)

/** Visible terminal viewport and cell metrics needed to render selection and position the action bar. */
internal data class SelectionViewport(
    val topRow: Int,
    val columns: Int,
    val fontWidth: Float,
    val lineSpacing: Int,
    val viewWidth: Int,
    val viewHeight: Int,
)

/**
 * Translucent accent highlight for selected terminal text. Always strictly translucent (alpha < 1f)
 * so foreground glyphs stay readable across Dark and Light themes in Solid, Glass, and Soft styles.
 */
internal fun terminalSelectionHighlight(palette: TerminalPalette?): Int {
    val base =
        palette?.cursor?.takeIf { Color.alpha(it) > 0 }
            ?: palette?.foreground?.takeIf { Color.alpha(it) > 0 }
            ?: DEFAULT_SELECTION_ACCENT
    return Color.argb(
        SELECTION_HIGHLIGHT_ALPHA,
        Color.red(base),
        Color.green(base),
        Color.blue(base),
    )
}

/** Converts Termux's `[y1, y2, x1, x2]` selector array into an ordered [SelectionRange], or null. */
internal fun normalizeSelectionRange(selectors: IntArray): SelectionRange? {
    if (selectors.size < SELECTOR_COUNT || (0 until SELECTOR_COUNT).any { selectors[it] == NO_SELECTION }) {
        return null
    }
    val y1 = selectors[INDEX_Y1]
    val y2 = selectors[INDEX_Y2]
    val x1 = selectors[INDEX_X1]
    val x2 = selectors[INDEX_X2]
    val forward = y1 < y2 || (y1 == y2 && x1 <= x2)
    return if (forward) SelectionRange(y1, x1, y2, x2) else SelectionRange(y2, x2, y1, x1)
}

/**
 * Computes the exclusion rect reported to Android's floating ActionMode so the Copy / Paste / More
 * bar is placed above or below the selected lines and never overlaps them.
 */
internal fun selectionActionContentRect(
    range: SelectionRange,
    viewport: SelectionViewport,
    clearancePx: Int,
): Rect {
    val selectedTop = ((range.startY - viewport.topRow) * viewport.lineSpacing).coerceIn(0, viewport.viewHeight)
    val selectedBottom =
        ((range.endY - viewport.topRow + 1) * viewport.lineSpacing).coerceIn(selectedTop, viewport.viewHeight)
    val singleLine = range.startY == range.endY
    val left = if (singleLine) (range.startX * viewport.fontWidth).toInt().coerceIn(0, viewport.viewWidth) else 0
    val rightColumns = if (singleLine) range.endX + 1 else viewport.columns
    val right = (rightColumns * viewport.fontWidth).toInt().coerceIn(left, viewport.viewWidth)
    val top = (selectedTop - clearancePx).coerceAtLeast(0)
    val bottom = (selectedBottom + clearancePx).coerceAtMost(viewport.viewHeight)
    return Rect(left, top, right, bottom)
}

/**
 * Coordinates translucent selection rendering and floating ActionMode content-rect clearance for a
 * hosted Termux [TerminalView] (which is `final` upstream and cannot be subclassed).
 */
internal class TerminalSelectionOverlay {
    private val selectionBounds = IntArray(SELECTOR_COUNT)
    private val selectionPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = terminalSelectionHighlight(null)
        }

    fun updateHighlight(color: Int) {
        selectionPaint.color = color
    }

    /**
     * Draws [terminal] with its real foreground/ANSI colors and a translucent selection highlight
     * instead of Termux's opaque reverse-video swap. Returns `true` if it handled drawing.
     */
    fun drawTranslucentSelection(terminal: TerminalView, canvas: Canvas): Boolean {
        val emulator = terminal.mEmulator
        val renderer = terminal.mRenderer
        val controller = if (terminal.isSelectingText) selectionController(terminal) else null
        if (emulator == null || renderer == null || controller == null) return false
        controller.getSelectors(selectionBounds)
        val range = normalizeSelectionRange(selectionBounds) ?: return false
        val viewport =
            SelectionViewport(
                topRow = terminal.topRow,
                columns = emulator.mColumns,
                fontWidth = renderer.fontWidth,
                lineSpacing = renderer.fontLineSpacing,
                viewWidth = terminal.width,
                viewHeight = terminal.height,
            )
        canvas.save()
        canvas.translate(terminal.left.toFloat() - terminal.scrollX, terminal.top.toFloat() - terminal.scrollY)
        renderer.render(emulator, canvas, terminal.topRow, NO_SELECTION, NO_SELECTION, NO_SELECTION, NO_SELECTION)
        drawHighlightRects(canvas, range, viewport, emulator.mRows)
        canvas.restore()
        controller.render()
        return true
    }

    fun wrapActionModeCallback(
        terminal: TerminalView,
        delegate: ActionMode.Callback2,
    ): ActionMode.Callback2 =
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
                delegate.onCreateActionMode(mode, menu)

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                delegate.onPrepareActionMode(mode, menu)

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                delegate.onActionItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) {
                delegate.onDestroyActionMode(mode)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                val rect = computeActionContentRect(terminal)
                if (rect != null) outRect.set(rect) else delegate.onGetContentRect(mode, view, outRect)
            }
        }

    private fun drawHighlightRects(
        canvas: Canvas,
        range: SelectionRange,
        viewport: SelectionViewport,
        rows: Int,
    ) {
        val hasGrid = viewport.columns > 0 && rows > 0
        val hasFont = viewport.fontWidth > 0f && viewport.lineSpacing > 0
        if (!hasGrid || !hasFont) return
        val visibleStart = maxOf(range.startY, viewport.topRow)
        val visibleEnd = minOf(range.endY, viewport.topRow + rows - 1)
        val lineHeight = viewport.lineSpacing.toFloat()
        for (row in visibleStart..visibleEnd) {
            val startCol = (if (row == range.startY) range.startX else 0).coerceIn(0, viewport.columns - 1)
            val endCol =
                (if (row == range.endY) range.endX else viewport.columns - 1).coerceIn(startCol, viewport.columns - 1)
            val left = startCol * viewport.fontWidth
            val right = (endCol + 1) * viewport.fontWidth
            val top = (row - viewport.topRow) * lineHeight
            canvas.drawRect(left, top, right, top + lineHeight, selectionPaint)
        }
    }

    private fun computeActionContentRect(terminal: TerminalView): Rect? {
        val emulator = terminal.mEmulator
        val renderer = terminal.mRenderer
        val controller = selectionController(terminal)?.takeIf { it.isActive }
        val range =
            if (emulator != null && renderer != null && controller != null) {
                controller.getSelectors(selectionBounds)
                normalizeSelectionRange(selectionBounds)
            } else {
                null
            }
        if (range == null || emulator == null || renderer == null) return null
        val viewport =
            SelectionViewport(
                topRow = terminal.topRow,
                columns = emulator.mColumns,
                fontWidth = renderer.fontWidth,
                lineSpacing = renderer.fontLineSpacing,
                viewWidth = terminal.width,
                viewHeight = terminal.height,
            )
        val clearance = (ACTION_BAR_CLEARANCE_DP * terminal.resources.displayMetrics.density).toInt()
        return selectionActionContentRect(range, viewport, clearance)
    }

    private fun selectionController(terminal: TerminalView): TextSelectionCursorController? =
        CONTROLLER_FIELD?.get(terminal) as? TextSelectionCursorController

    private companion object {
        // Matched by declared type rather than field name so R8 minification cannot break lookup.
        val CONTROLLER_FIELD: Field? =
            TerminalView::class.java.declaredFields
                .firstOrNull { it.type == TextSelectionCursorController::class.java }
                ?.apply { isAccessible = true }
    }
}
