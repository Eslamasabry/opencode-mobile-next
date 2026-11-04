package io.github.eslamasabry.oc.engine.project.terminal

public enum class TerminalKey {
    Control,
    Alt,
    Escape,
    Tab,
    Up,
    Down,
    Left,
    Right,
    Home,
    End,
    PageUp,
    PageDown,
    Pipe,
    Slash,
    Backslash,
    Tilde,
    Minus,
    Underscore,
}

/** One-use modifiers. Only the first Unicode scalar is modified, never a whole paste. */
public object TerminalKeys {
    public fun text(value: String, control: Boolean, alt: Boolean): String {
        if (value.isEmpty()) return value
        val first = value.first()
        val mapped =
            if (!control) first
            else
                when {
                    first in 'a'..'z' -> (first.code - 96).toChar()
                    first in '@'..'_' -> (first.code and 31).toChar()
                    first == ' ' -> '\u0000'
                    first == '?' -> '\u007f'
                    else -> first
                }
        return (if (alt) "\u001b" else "") + mapped + value.drop(1)
    }

    public fun bytes(
        key: TerminalKey,
        control: Boolean,
        alt: Boolean,
        applicationCursor: Boolean,
    ): String {
        val final =
            when (key) {
                TerminalKey.Up -> 'A'
                TerminalKey.Down -> 'B'
                TerminalKey.Right -> 'C'
                TerminalKey.Left -> 'D'
                TerminalKey.Home -> 'H'
                TerminalKey.End -> 'F'
                else -> null
            }
        if (final != null) {
            val modifier = 1 + (if (alt) 2 else 0) + (if (control) 4 else 0)
            return if (modifier > 1) "\u001b[1;${modifier}$final"
            else "\u001b${if (applicationCursor) "O" else "["}$final"
        }
        val raw =
            when (key) {
                TerminalKey.Escape -> "\u001b"
                TerminalKey.Tab -> "\t"
                TerminalKey.PageUp ->
                    "\u001b[5${if (control || alt) ";${1 + (if (alt) 2 else 0) + (if (control) 4 else 0)}" else ""}~"
                TerminalKey.PageDown ->
                    "\u001b[6${if (control || alt) ";${1 + (if (alt) 2 else 0) + (if (control) 4 else 0)}" else ""}~"
                TerminalKey.Pipe -> "|"
                TerminalKey.Slash -> "/"
                TerminalKey.Backslash -> "\\"
                TerminalKey.Tilde -> "~"
                TerminalKey.Minus -> "-"
                TerminalKey.Underscore -> "_"
                else -> ""
            }
        return if (key == TerminalKey.PageUp || key == TerminalKey.PageDown) raw
        else text(raw, control, alt)
    }
}
