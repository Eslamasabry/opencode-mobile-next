package io.github.eslamasabry.oc.engine.project.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The keys a phone keyboard lacks, for a terminal (Flutter `TerminalBarKey`). [face] is the key cap, the same in every
 * language.
 */
public enum class TerminalBarKey(public val face: String) {
    Esc("Esc"),
    Slash("/"),
    Dash("-"),
    Pipe("|"),
    Home("Home"),
    Up("↑"),
    End("End"),
    PageUp("PgUp"),
    Tab("Tab"),
    Ctrl("Ctrl"),
    Alt("Alt"),
    Tilde("~"),
    Left("←"),
    Down("↓"),
    Right("→"),
    PageDown("PgDn"),

    /** Ctrl-C in one tap (a server terminal's first key). */
    Interrupt("^C"),

    /** Ctrl-D in one tap. */
    EndOfInput("^D"),
    ;

    public val isModifier: Boolean get() = this == Ctrl || this == Alt

    /** The character a symbol key types, or null for a named key. */
    public val text: String?
        get() =
            when (this) {
                Slash -> "/"
                Dash -> "-"
                Pipe -> "|"
                Tilde -> "~"
                else -> null
            }

    public companion object {
        /** The two rows, as Termux users know them, plus `| ~`. */
        public val rows: List<List<TerminalBarKey>> =
            listOf(
                listOf(Esc, Slash, Dash, Pipe, Home, Up, End, PageUp),
                listOf(Tab, Ctrl, Alt, Tilde, Left, Down, Right, PageDown),
            )

        /** The keys a bar with interrupt keys leads with, on a row of their own above [rows]. */
        public val extraRow: List<TerminalBarKey> = listOf(Interrupt, EndOfInput)

        /** The one-row bar: the keys a phone keyboard lacks most, in the order a hand reaches for them. */
        public val singleRowKeys: List<TerminalBarKey> =
            listOf(Esc, Tab, Ctrl, Alt, Left, Up, Down, Right, Pipe, Tilde, Slash, Dash, Home, End, PageUp, PageDown)

        /** How many of [singleRowKeys] share the width of a phone. */
        public const val SINGLE_ROW_SHOWN: Int = 11
    }
}

/**
 * [text] as typed with Ctrl and/or Alt held: Ctrl turns a letter or one of `@[\]^_`, space or `?` into its control
 * byte (Ctrl-C is 0x03), Alt puts Esc before it. Only the first character takes the modifiers; text with no control
 * form (a digit under Ctrl) passes unchanged.
 */
public fun terminalModifiedText(text: String, ctrl: Boolean = false, alt: Boolean = false): String {
    if (text.isEmpty() || (!ctrl && !alt)) return text
    val firstLength = if (text[0].isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) 2 else 1
    var first = text.substring(0, firstLength)
    if (ctrl && firstLength == 1) {
        val control = controlByte(text[0].code)
        if (control != null) first = control.toChar().toString()
    }
    if (alt) first = "\u001b$first"
    return first + text.substring(firstLength)
}

private fun controlByte(rune: Int): Int? =
    when (rune) {
        in 0x61..0x7a -> rune - 0x60 // a-z
        in 0x40..0x5f -> rune - 0x40 // @ A-Z [ \ ] ^ _
        0x20 -> 0 // space: NUL, as in Termux and xterm
        0x3f -> 0x7f // ?: DEL
        else -> null
    }

/**
 * What a key-bar key sends, as a real keyboard would. Arrows follow [applicationCursorKeys] (DECCKM, see
 * [TerminalScreen.cursorKeysMode]) when no modifier is held; a modified arrow or Home/End sends the xterm modified form
 * (`ESC [ 1 ; 5 D` for Ctrl-Left). Modifier keys themselves send nothing (empty).
 */
public fun terminalBarKeyText(
    key: TerminalBarKey,
    ctrl: Boolean = false,
    alt: Boolean = false,
    applicationCursorKeys: Boolean = false,
): String {
    key.text?.let { return terminalModifiedText(it, ctrl = ctrl, alt = alt) }
    when (key) {
        TerminalBarKey.Interrupt -> return terminalModifiedText("c", ctrl = true, alt = alt)
        TerminalBarKey.EndOfInput -> return terminalModifiedText("d", ctrl = true, alt = alt)
        TerminalBarKey.Ctrl, TerminalBarKey.Alt -> return ""
        TerminalBarKey.Esc -> return if (alt) "\u001b\u001b" else "\u001b"
        TerminalBarKey.Tab -> return if (alt) "\u001b\t" else "\t"
        TerminalBarKey.PageUp -> return tilde(5, ctrl, alt)
        TerminalBarKey.PageDown -> return tilde(6, ctrl, alt)
        else -> Unit
    }
    val final =
        when (key) {
            TerminalBarKey.Up -> 'A'
            TerminalBarKey.Down -> 'B'
            TerminalBarKey.Right -> 'C'
            TerminalBarKey.Left -> 'D'
            TerminalBarKey.Home -> 'H'
            TerminalBarKey.End -> 'F'
            else -> return ""
        }
    val modifier = modifierParam(ctrl, alt)
    if (modifier != null) return "\u001b[1;$modifier$final"
    return "\u001b${if (applicationCursorKeys) 'O' else '['}$final"
}

private fun tilde(code: Int, ctrl: Boolean, alt: Boolean): String {
    val modifier = modifierParam(ctrl, alt)
    return if (modifier == null) "\u001b[$code~" else "\u001b[$code;$modifier~"
}

/** xterm's modifier parameter: 1 + Alt(2) + Ctrl(4); null when neither is held. */
private fun modifierParam(ctrl: Boolean, alt: Boolean): Int? {
    if (!ctrl && !alt) return null
    return 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
}

/** Which modifiers are latched. */
public data class TerminalModifiers(val ctrl: Boolean = false, val alt: Boolean = false)

/**
 * The sticky Ctrl and Alt of a key bar. A tap latches a modifier for the next key, from the bar or the phone's
 * keyboard; that key releases it. A second tap releases the latch.
 */
public class TerminalKeyBarController {
    private val latched = MutableStateFlow(TerminalModifiers())
    public val modifiers: StateFlow<TerminalModifiers> = latched.asStateFlow()

    public val ctrl: Boolean get() = latched.value.ctrl
    public val alt: Boolean get() = latched.value.alt

    public fun toggle(key: TerminalBarKey) {
        when (key) {
            TerminalBarKey.Ctrl -> latched.value = latched.value.copy(ctrl = !ctrl)
            TerminalBarKey.Alt -> latched.value = latched.value.copy(alt = !alt)
            else -> Unit
        }
    }

    /** The latched modifiers, released. */
    public fun take(): TerminalModifiers {
        val held = latched.value
        if (held.ctrl || held.alt) latched.value = TerminalModifiers()
        return held
    }

    /** Text from the phone's keyboard with the latched modifiers applied (and released). */
    public fun apply(text: String): String {
        if (!ctrl && !alt) return text
        val held = take()
        return terminalModifiedText(text, ctrl = held.ctrl, alt = held.alt)
    }

    /** What a bar key sends with the latched modifiers, which it releases. A modifier key toggles its latch. */
    public fun press(key: TerminalBarKey, applicationCursorKeys: Boolean = false): String {
        if (key.isModifier) {
            toggle(key)
            return ""
        }
        val held = take()
        return terminalBarKeyText(key, ctrl = held.ctrl, alt = held.alt, applicationCursorKeys = applicationCursorKeys)
    }
}
