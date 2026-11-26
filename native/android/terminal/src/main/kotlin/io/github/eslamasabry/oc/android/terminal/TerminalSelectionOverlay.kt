package io.github.eslamasabry.oc.android.terminal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.termux.view.TerminalRenderer
import com.termux.view.TerminalView
import com.termux.view.textselection.TextSelectionCursorController
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import java.lang.reflect.Field
import kotlin.math.ceil

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

/** Font cell metrics used to align selection highlights and the floating action bar. */
internal data class SelectionCellMetrics(
    val fontWidth: Float,
    val lineSpacing: Int,
    val rowTopOffset: Int = 0,
)

/** Visible terminal viewport and cell metrics needed to render selection and position the action bar. */
internal data class SelectionViewport(
    val topRow: Int,
    val columns: Int,
    val viewWidth: Int,
    val viewHeight: Int,
    val cell: SelectionCellMetrics,
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
    if (selectors.size < SELECTOR_COUNT || selectors[INDEX_X1] < 0 || selectors[INDEX_X2] < 0) {
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
    val cell = viewport.cell
    val selectedTop =
        ((range.startY - viewport.topRow) * cell.lineSpacing + cell.rowTopOffset)
            .coerceIn(0, viewport.viewHeight)
    val selectedBottom =
        ((range.endY - viewport.topRow + 1) * cell.lineSpacing + cell.rowTopOffset)
            .coerceIn(selectedTop, viewport.viewHeight)
    val singleLine = range.startY == range.endY
    val left = if (singleLine) (range.startX * cell.fontWidth).toInt().coerceIn(0, viewport.viewWidth) else 0
    val rightColumns = if (singleLine) range.endX + 1 else viewport.columns
    val right = (rightColumns * cell.fontWidth).toInt().coerceIn(left, viewport.viewWidth)
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
        var handled = false
        if (emulator != null && renderer != null && controller != null) {
            controller.getSelectors(selectionBounds)
            val range = normalizeSelectionRange(selectionBounds)
            if (range != null) {
                val viewport = viewportFor(terminal, emulator.mColumns, renderer)
                canvas.save()
                canvas.translate(terminal.left.toFloat() - terminal.scrollX, terminal.top.toFloat() - terminal.scrollY)
                renderer.render(
                    emulator,
                    canvas,
                    terminal.topRow,
                    NO_SELECTION,
                    NO_SELECTION,
                    NO_SELECTION,
                    NO_SELECTION,
                )
                drawHighlightRects(canvas, range, viewport, emulator.mRows)
                canvas.restore()
                handled = true
            }
        }
        return handled
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
                val delegateRect = Rect()
                delegate.onGetContentRect(mode, view, delegateRect)
                val rect = computeActionContentRect(terminal)
                if (rect != null) {
                    rect.bottom = maxOf(rect.bottom, delegateRect.bottom)
                    outRect.set(rect)
                } else {
                    outRect.set(delegateRect)
                }
            }
        }

    private fun drawHighlightRects(
        canvas: Canvas,
        range: SelectionRange,
        viewport: SelectionViewport,
        rows: Int,
    ) {
        val cell = viewport.cell
        val hasGrid = viewport.columns > 0 && rows > 0
        val hasFont = cell.fontWidth > 0f && cell.lineSpacing > 0
        if (!hasGrid || !hasFont) return
        val visibleStart = maxOf(range.startY, viewport.topRow)
        val visibleEnd = minOf(range.endY, viewport.topRow + rows - 1)
        val lineHeight = cell.lineSpacing.toFloat()
        for (row in visibleStart..visibleEnd) {
            val startCol = (if (row == range.startY) range.startX else 0).coerceIn(0, viewport.columns - 1)
            val endCol =
                (if (row == range.endY) range.endX else viewport.columns - 1).coerceIn(startCol, viewport.columns - 1)
            val left = startCol * cell.fontWidth
            val right = (endCol + 1) * cell.fontWidth
            val top = (row - viewport.topRow) * lineHeight + cell.rowTopOffset
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
        val viewport = viewportFor(terminal, emulator.mColumns, renderer)
        val clearance = (ACTION_BAR_CLEARANCE_DP * terminal.resources.displayMetrics.density).toInt()
        return selectionActionContentRect(range, viewport, clearance)
    }

    private fun viewportFor(terminal: TerminalView, columns: Int, renderer: TerminalRenderer): SelectionViewport =
        SelectionViewport(
            topRow = terminal.topRow,
            columns = columns,
            viewWidth = terminal.width,
            viewHeight = terminal.height,
            cell =
                SelectionCellMetrics(
                    fontWidth = renderer.fontWidth,
                    lineSpacing = renderer.fontLineSpacing,
                    rowTopOffset = rowTopOffset(renderer),
                ),
        )

    private fun rowTopOffset(renderer: TerminalRenderer): Int {
        val paint = RENDERER_PAINT_FIELD?.get(renderer) as? Paint ?: return 0
        return renderer.fontLineSpacing + ceil(paint.ascent().toDouble()).toInt()
    }

    private fun selectionController(terminal: TerminalView): TextSelectionCursorController? =
        CONTROLLER_FIELD?.get(terminal) as? TextSelectionCursorController

    private companion object {
        // Matched by declared type rather than field name so R8 minification cannot break lookup.
        val CONTROLLER_FIELD: Field? =
            TerminalView::class.java.declaredFields
                .firstOrNull { it.type == TextSelectionCursorController::class.java }
                ?.apply { isAccessible = true }

        val RENDERER_PAINT_FIELD: Field? =
            TerminalRenderer::class.java.declaredFields
                .firstOrNull { it.type == Paint::class.java }
                ?.apply { isAccessible = true }
    }
}
