package io.github.eslamasabry.oc.ui.kit.content.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalRenderer
import kotlin.math.floor

/**
 * Remote PTY grid using the pinned Termux emulator and renderer. No local process or fake PTY is
 * created. IME commits and hardware keys have separate paths, so an edit is sent once. Selection is
 * available by visible Select text mode as well as long-press; Copy is performed only by the
 * screen's explicit action.
 */
public class AndroidRemoteTerminalView(
    context: Context,
    private val send: (String) -> Unit,
    private val resize: (Int, Int) -> Unit,
) : View(context) {
    private val renderer =
        TerminalRenderer(
            (14 * resources.displayMetrics.scaledDensity).toInt().coerceAtLeast(12),
            Typeface.MONOSPACE,
        )
    private var inputOn = false
    private var top = 0
    private var selection = false
    private var startX = -1
    private var startY = -1
    private var endX = -1
    private var endY = -1
    private var composed = ""
    private val emulator =
        TerminalEmulator(
            object : TerminalOutput() {
                override fun write(data: ByteArray, offset: Int, count: Int) {
                    if (inputOn) send(data.decodeToString(offset, offset + count))
                }

                override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

                // OSC clipboard commands are untrusted server output, never clipboard authority.
                override fun onCopyTextToClipboard(text: String?) = Unit

                override fun onPasteTextFromClipboard() = Unit

                override fun onBell() = Unit

                override fun onColorsChanged() {
                    invalidate()
                }
            },
            80,
            24,
            1,
            1,
            5000,
            QuietTerminalClient,
        )
    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    requestFocus()
                    if (inputOn && !selection)
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as InputMethodManager)
                            .showSoftInput(
                                this@AndroidRemoteTerminalView,
                                InputMethodManager.SHOW_IMPLICIT,
                            )
                    return true
                }

                override fun onLongPress(event: MotionEvent) {
                    selectAt(event)
                }

                override fun onScroll(
                    first: MotionEvent?,
                    current: MotionEvent,
                    dx: Float,
                    dy: Float,
                ): Boolean {
                    if (selection) {
                        endX = column(current.x)
                        endY = row(current.y)
                    } else {
                        top =
                            (top + (dy / renderer.fontLineSpacing).toInt()).coerceIn(
                                -emulator.screen.activeTranscriptRows,
                                0,
                            )
                    }
                    invalidate()
                    return true
                }
            },
        )

    init {
        isFocusableInTouchMode = true
        isFocusable = true
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    public fun append(text: String) {
        val bytes = text.encodeToByteArray()
        emulator.append(bytes, bytes.size)
        if (!selection) top = 0
        invalidate()
    }

    public fun clear() {
        emulator.reset()
        emulator.screen.clearTranscript()
        top = 0
        clearSelection()
        invalidate()
    }

    public fun enableInput(on: Boolean) {
        inputOn = on
    }

    public fun selectText(on: Boolean) {
        selection = on
        if (!on) clearSelection()
    }

    public fun selectedText(): String =
        if (startX < 0) "" else emulator.getSelectedText(startX, startY, endX, endY)

    public fun paste(text: String) {
        if (inputOn) emulator.paste(text)
    }

    private fun clearSelection() {
        selection = false
        startX = -1
        startY = -1
        endX = -1
        endY = -1
        invalidate()
    }

    private fun column(x: Float): Int =
        floor(x / renderer.fontWidth).toInt().coerceIn(0, emulator.mColumns - 1)

    private fun row(y: Float): Int =
        (top + floor(y / renderer.fontLineSpacing).toInt()).coerceIn(
            -emulator.screen.activeTranscriptRows,
            emulator.mRows - 1,
        )

    private fun selectAt(event: MotionEvent) {
        selection = true
        startX = column(event.x)
        startY = row(event.y)
        endX = startX
        endY = startY
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selection && event.actionMasked == MotionEvent.ACTION_DOWN) selectAt(event)
        if (selection && event.actionMasked == MotionEvent.ACTION_MOVE) {
            endX = column(event.x)
            endY = row(event.y)
            invalidate()
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cols = floor(w / renderer.fontWidth).toInt().coerceIn(2, 2000)
        val rows = (h / renderer.fontLineSpacing).coerceIn(2, 1000)
        emulator.resize(cols, rows, 1, 1)
        resize(rows, cols)
    }

    override fun onDraw(canvas: Canvas) {
        renderer.render(emulator, canvas, top, startY, endY, startX, endX)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(info: EditorInfo): InputConnection {
        info.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        composed = ""
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (inputOn && text != null) send(text.toString())
                composed = ""
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composed = text?.toString().orEmpty()
                return true
            }

            override fun finishComposingText(): Boolean {
                composed = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (inputOn && beforeLength > 0)
                    send("\u007f".repeat(beforeLength.coerceAtMost(1024)))
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean =
                if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputOn || keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val modifiers =
            (if (event.isCtrlPressed) KeyHandler.KEYMOD_CTRL else 0) or
                (if (event.isAltPressed) KeyHandler.KEYMOD_ALT else 0) or
                (if (event.isShiftPressed) KeyHandler.KEYMOD_SHIFT else 0)
        val code =
            KeyHandler.getCode(
                keyCode,
                modifiers,
                emulator.isCursorKeysApplicationMode,
                emulator.isKeypadApplicationMode,
            )
        if (code != null) {
            send(code)
            return true
        }
        val point =
            event.getUnicodeChar(
                event.metaState and KeyEvent.META_CTRL_MASK.inv() and KeyEvent.META_ALT_MASK.inv()
            )
        if (point == 0) return super.onKeyDown(keyCode, event)
        val text =
            String(
                Character.toChars(
                    if (event.isCtrlPressed && point in 0x40..0x7f) point and 0x1f else point
                )
            )
        send(if (event.isAltPressed) "\u001b$text" else text)
        return true
    }
}

/**
 * The emulator's session callbacks do not own a local session. Library logs are deliberately
 * discarded.
 */
private object QuietTerminalClient : TerminalSessionClient {
    override fun onTextChanged(session: TerminalSession?) = Unit

    override fun onTitleChanged(session: TerminalSession?) = Unit

    override fun onSessionFinished(session: TerminalSession?) = Unit

    override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) = Unit

    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit

    override fun onBell(session: TerminalSession?) = Unit

    override fun onColorsChanged(session: TerminalSession?) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

    override fun logError(tag: String?, message: String?) = Unit

    override fun logWarn(tag: String?, message: String?) = Unit

    override fun logInfo(tag: String?, message: String?) = Unit

    override fun logDebug(tag: String?, message: String?) = Unit

    override fun logVerbose(tag: String?, message: String?) = Unit

    override fun logStackTraceWithMessage(tag: String?, message: String?, error: Exception?) = Unit

    override fun logStackTrace(tag: String?, error: Exception?) = Unit
}
