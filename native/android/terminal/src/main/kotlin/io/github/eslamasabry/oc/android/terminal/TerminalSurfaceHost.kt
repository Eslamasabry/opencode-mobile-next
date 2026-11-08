package io.github.eslamasabry.oc.android.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import io.github.eslamasabry.oc.core.common.terminal.TerminalLinks
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import io.github.eslamasabry.oc.core.common.terminal.TerminalRow
import io.github.eslamasabry.oc.core.common.terminal.TerminalSurface
import io.github.eslamasabry.oc.engine.project.terminal.TerminalPaste

/** The same view seam for local PTYs, sign-in sessions and external daemon streams. */
internal open class TerminalSurfaceHost(context: Context, protected val terminal: TerminalView) :
    FrameLayout(context), TerminalSurface {
    private var onOpen: ((String) -> Unit)? = null
    private var palette: TerminalPalette? = null

    init {
        // Termux's TerminalView leaves focus to its layout XML (the Termux app sets both flags there). Built in code
        // it takes neither focus nor the keyboard: a tap never connected the IME and keys never reached the process.
        terminal.isFocusable = true
        terminal.isFocusableInTouchMode = true
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

/**
 * A tap on the grid: the grid takes focus (so a hardware keyboard and the IME's input connection reach it) and the
 * on-screen keyboard comes up. An explicit tap asks for the keyboard outright, so it also shows when the phone has a
 * hardware keyboard attached.
 */
internal fun showTerminalKeyboard(view: android.view.View) {
    if (!view.isFocusable) return
    view.requestFocus()
    (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
        ?.showSoftInput(view, 0)
}

/**
 * The stream parser admits bounded UTF-8 writes only; this sink never reads the clipboard. As in
 * Dart (`_KitTerminalOscHost`), a write is honoured only while the terminal is on screen: a session
 * running unseen (a background sign-in, a closed screen) cannot change the clipboard.
 */
internal fun copyTerminalText(session: TerminalSession, text: String, shown: android.view.View?) {
    if (shown == null || !shown.isShown) return
    if (text.isEmpty() || text.toByteArray(Charsets.UTF_8).size > 65_536) return
    (shown.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.setPrimaryClip(ClipData.newPlainText("", text))
}

/** What [pasteTerminalText] did. */
internal enum class TerminalPasted {
    Sent,
    Nothing,
    TooLong,
}

/**
 * E11-S01: the clipboard's text into [session], through Termux's `TerminalEmulator.paste` (escape and C1 controls
 * removed, line ends sent as Return, wrapped in ESC[200~ … ESC[201~ when the shell turned on bracketed paste). Like
 * [copyTerminalText], only while the terminal is on screen: a session running unseen never reads the clipboard. The
 * text is never logged.
 */
internal fun pasteTerminalText(session: TerminalSession, shown: android.view.View?): TerminalPasted {
    if (shown == null || !shown.isShown || !session.isRunning) return TerminalPasted.Nothing
    val clipboard =
        shown.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return TerminalPasted.Nothing
    val clip =
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(shown.context)
    if (clip.isNullOrEmpty()) return TerminalPasted.Nothing
    val text = TerminalPaste.accept(clip) ?: return TerminalPasted.TooLong
    val emulator = session.emulator ?: return TerminalPasted.Nothing
    emulator.paste(text)
    return TerminalPasted.Sent
}
