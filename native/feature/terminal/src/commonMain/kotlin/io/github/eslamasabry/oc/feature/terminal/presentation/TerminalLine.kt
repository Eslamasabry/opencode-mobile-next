package io.github.eslamasabry.oc.feature.terminal.presentation

import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.terminalBarKeyInput

/**
 * The one line under a server terminal's bar (Flutter terminal_surface.dart `_status`): nothing while the shell is
 * connected, otherwise why it is not and what to do. Decided here once, so the screen never says "disconnected" while
 * the shell is live.
 */
public sealed interface TerminalLine {
    /** Connected, or no terminal is open: no line at all. */
    public data object None : TerminalLine

    /** Opening the shell's stream. */
    public data object Connecting : TerminalLine

    /** The program ended; [exitCode] is the server's code when it gave one. */
    public data class Ended(val command: String, val exitCode: Int?) : TerminalLine

    /** The stream closed while the program still runs: Reconnect. */
    public data object Closed : TerminalLine
}

public fun terminalLine(state: RemoteTerminalState): TerminalLine {
    val terminal = state.selected ?: return TerminalLine.None
    return when {
        state.connected -> TerminalLine.None
        state.working -> TerminalLine.Connecting
        // A failure has its own notice with Try again; a second line would say the same thing twice.
        state.failure != null -> TerminalLine.None
        !terminal.running ->
            TerminalLine.Ended(
                (listOf(terminal.command) + terminal.arguments).joinToString(" ").trim(),
                terminal.exitCode,
            )
        else -> TerminalLine.Closed
    }
}

/**
 * What a key of the bar types into a server terminal (Flutter `sendTerminalBarKey` through xterm's key input): the
 * kit's text path for symbols and Ctrl-C/Ctrl-D, and the xterm sequences for arrows and the named keys, with the
 * xterm modifier parameter (1 + Alt 2 + Ctrl 4) when Ctrl or Alt is latched. Null only for the modifiers themselves,
 * which the bar latches instead of sending.
 */
public fun terminalKeyText(key: KitTerminalBarKey, ctrl: Boolean, alt: Boolean): String? {
    if (key.isModifier) return null
    val modifier = 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
    val esc = "\u001b"
    fun csi(final: Char): String = if (modifier == 1) "$esc[$final" else "$esc[1;$modifier$final"
    fun tilde(code: Int): String = if (modifier == 1) "$esc[$code~" else "$esc[$code;$modifier~"
    val altPrefix = if (alt) esc else ""
    // Arrows first: the kit's text path writes a plain arrow as ESC [ [ A, which no shell reads as an arrow.
    when (key) {
        KitTerminalBarKey.Up -> return csi('A')
        KitTerminalBarKey.Down -> return csi('B')
        KitTerminalBarKey.Right -> return csi('C')
        KitTerminalBarKey.Left -> return csi('D')
        else -> Unit
    }
    terminalBarKeyInput(key, ctrl = ctrl, alt = alt)?.let { return it }
    return when (key) {
        KitTerminalBarKey.Esc -> altPrefix + esc
        KitTerminalBarKey.Tab -> altPrefix + "\t"
        KitTerminalBarKey.Home -> csi('H')
        KitTerminalBarKey.End -> csi('F')
        KitTerminalBarKey.PageUp -> tilde(5)
        KitTerminalBarKey.PageDown -> tilde(6)
        else -> null
    }
}
