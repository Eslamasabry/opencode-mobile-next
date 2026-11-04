package io.github.eslamasabry.oc.android.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import io.github.eslamasabry.oc.core.common.terminal.TerminalLinks
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import io.github.eslamasabry.oc.core.common.terminal.TerminalRow
import io.github.eslamasabry.oc.core.common.terminal.TerminalSurface

/** The same view seam for local PTYs, sign-in sessions and external daemon streams. */
internal open class TerminalSurfaceHost(context: Context, protected val terminal: TerminalView) :
    FrameLayout(context), TerminalSurface {
    private var onOpen: ((String) -> Unit)? = null
    private var palette: TerminalPalette? = null

    init {
        addView(
            terminal,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override fun links(onOpen: ((String) -> Unit)?) {
        this.onOpen = onOpen
    }

    override val columns: Int
        get() = terminal.mEmulator?.mColumns ?: 0

    override fun selectedText(): String =
        if (terminal.isSelectingText) terminal.selectedText.orEmpty() else ""

    override fun appearance(palette: TerminalPalette) {
        this.palette = palette
        applyPalette()
        terminal.invalidate()
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        applyPalette()
        super.dispatchDraw(canvas)
    }

    fun applyPalette() {
        val colors = terminal.mEmulator?.mColors?.mCurrentColors ?: return
        val theme = palette ?: return
        theme.ansi.take(16).forEachIndexed { index, color -> colors[index] = color }
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
        colors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
    }

    /**
     * Coordinates are the actual Termux cell positions, including the current scrollback offset.
     */
    fun openLink(event: MotionEvent): Boolean {
        if (terminal.isSelectingText) return false
        val open = onOpen ?: return false
        val emulator = terminal.mEmulator ?: return false
        val screen = emulator.screen
        val point = terminal.getColumnAndRow(event, true)
        val first = maxOf(-screen.activeTranscriptRows, point[1] - 60)
        val last = minOf(emulator.mRows - 1, point[1] + 60)
        if (first > last) return false
        val rows =
            (first..last).map { row ->
                TerminalRow(
                    screen.getSelectedText(0, row, emulator.mColumns - 1, row),
                    row > -screen.activeTranscriptRows && screen.getLineWrap(row - 1),
                )
            }
        val url =
            TerminalLinks.urlAt(rows, emulator.mColumns, point[0], point[1] - first) ?: return false
        open(url)
        return true
    }
}

/** The stream parser admits bounded UTF-8 writes only; this sink never reads the clipboard. */
internal fun copyTerminalText(session: TerminalSession, text: String, context: Context) {
    if (text.isEmpty() || text.toByteArray(Charsets.UTF_8).size > 65_536) return
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
        ClipData.newPlainText("", text)
    )
}
