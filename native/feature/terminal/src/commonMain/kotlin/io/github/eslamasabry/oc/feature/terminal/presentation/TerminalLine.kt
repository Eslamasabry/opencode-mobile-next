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
    val modifier = XTERM_PLAIN + (if (alt) XTERM_ALT else 0) + (if (ctrl) XTERM_CTRL else 0)
    val plain = modifier == XTERM_PLAIN
    val final = CSI_FINAL[key]
    val code = TILDE_CODE[key]
    val altPrefix = if (alt) ESC else ""
    // Arrows first: the kit's text path writes a plain arrow as ESC [ [ A, which no shell reads as an arrow.
    return when {
        key.isModifier -> null
        final != null -> if (plain) "$ESC[$final" else "$ESC[1;$modifier$final"
        code != null -> if (plain) "$ESC[$code~" else "$ESC[$code;$modifier~"
        key == KitTerminalBarKey.Esc -> altPrefix + ESC
        key == KitTerminalBarKey.Tab -> altPrefix + "\t"
        else -> terminalBarKeyInput(key, ctrl = ctrl, alt = alt)
    }
}

private const val ESC = "\u001b"

/** xterm's modifier parameter: 1, plus 2 for Alt and 4 for Ctrl. */
private const val XTERM_PLAIN = 1
private const val XTERM_ALT = 2
private const val XTERM_CTRL = 4

/** The CSI keys and the letter that ends each (ESC [ A, or ESC [ 1 ; m A with a modifier). */
private val CSI_FINAL =
    mapOf(
        KitTerminalBarKey.Up to 'A',
        KitTerminalBarKey.Down to 'B',
        KitTerminalBarKey.Right to 'C',
        KitTerminalBarKey.Left to 'D',
        KitTerminalBarKey.Home to 'H',
        KitTerminalBarKey.End to 'F',
    )

/** The keys xterm sends as ESC [ n ~ (Page Up 5, Page Down 6). */
private val TILDE_CODE = mapOf(KitTerminalBarKey.PageUp to XTERM_PAGE_UP, KitTerminalBarKey.PageDown to XTERM_PAGE_DOWN)

private const val XTERM_PAGE_UP = 5
private const val XTERM_PAGE_DOWN = 6
