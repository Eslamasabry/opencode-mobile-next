package io.github.eslamasabry.oc.android.terminal

/** The application supplies its kit-owned sensitive writer; the terminal module owns no platform clipboard sink. */
fun interface TerminalTextClipboard {
    fun copySensitive(text: String)
}
