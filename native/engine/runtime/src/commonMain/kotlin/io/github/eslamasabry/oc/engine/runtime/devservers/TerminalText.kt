package io.github.eslamasabry.oc.engine.runtime.devservers

/**
 * Plain text from terminal output (port of lib/domain/ansi_text.dart): colour and cursor codes removed, line endings
 * made `\n`, other control characters dropped. A lone carriage return (a progress bar redrawing itself) becomes a new
 * line so each state stays readable.
 */
public object TerminalText {
    private val osc = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)?")
    private val csi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val other = Regex("\u001B[@-Z\\\\-_()#][0-9A-Za-z]?")
    private val control = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001A\u001C-\u001F\u007F]")

    public fun plain(input: String): String {
        if (input.isEmpty()) return input
        return input
            .replace(osc, "")
            .replace(csi, "")
            .replace(other, "")
            .replace("\u001B", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(control, "")
    }
}
