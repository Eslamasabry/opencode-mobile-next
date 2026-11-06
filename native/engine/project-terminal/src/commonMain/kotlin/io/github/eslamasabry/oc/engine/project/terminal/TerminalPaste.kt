package io.github.eslamasabry.oc.engine.project.terminal

/**
 * What a terminal paste may send (E11-S01; Flutter `local_terminal_screen.dart` `_paste`). The clipboard's text goes
 * to the shell whole or not at all: an empty clipboard sends nothing, and a paste over [MAX_BYTES] is refused rather
 * than cut, because a cut-off command line could still run. Escapes, line ends and bracketed paste (DECSET 2004, when
 * the shell asked for it) are the emulator's job (Termux `TerminalEmulator.paste`), so the shell cannot be tricked
 * into ending a bracketed paste early.
 */
public object TerminalPaste {
    /** The same bound as queued typing and OSC 52 copies. */
    public const val MAX_BYTES: Int = 65_536

    /** The text to paste, or `null` when there is nothing to paste or it is too long to paste whole. */
    public fun accept(clip: CharSequence?): String? {
        if (clip.isNullOrEmpty()) return null
        val text = clip.toString()
        // Each UTF-16 unit is at most 3 UTF-8 bytes; only count exactly near the bound.
        if (text.length > MAX_BYTES) return null
        if (text.length * 3 > MAX_BYTES && text.encodeToByteArray().size > MAX_BYTES) return null
        return text
    }
}
