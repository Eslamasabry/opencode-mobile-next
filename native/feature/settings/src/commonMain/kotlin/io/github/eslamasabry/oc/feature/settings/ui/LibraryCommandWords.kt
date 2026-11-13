package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.core.model.CommandInfo

/** What a command row shows: its words as the title, its slash word and argument hint under it. */
internal data class LibraryCommandWords(val title: String, val slash: String, val hint: String?)

/**
 * A server writes a command's argument template inside its description ("review changes
 * [commit|branch|pr], defaults to ..."). The title keeps the sentence before the template; the
 * template goes beside the slash word, so no row shows a title cut in the middle of raw syntax.
 */
internal fun libraryCommandWords(command: CommandInfo): LibraryCommandWords {
    val slash = "/${command.name.removePrefix("/")}"
    val text = command.description?.trim().orEmpty()
    if (text.isEmpty()) return LibraryCommandWords(slash, slash, null)
    val open = argumentTemplateStart(text)
    if (open <= 0) return LibraryCommandWords(text, slash, null)
    val before = text.substring(0, open).trim()
    if (before.isEmpty()) return LibraryCommandWords(text, slash, null)
    return LibraryCommandWords(before, slash, text.substring(open).trim())
}

/**
 * Where an argument template starts: the first bracket group closed by ']' whose words hold no space
 * ("[commit|branch|pr]"). A bracket inside a sentence ("open the [docs] page", "[1] first") is prose and stays.
 */
private fun argumentTemplateStart(text: String): Int {
    var from = 0
    while (true) {
        val open = text.indexOf('[', from)
        if (open < 0) return -1
        val close = text.indexOf(']', open)
        if (close < 0) return -1
        val inside = text.substring(open + 1, close)
        if (inside.isNotEmpty() && inside.none { it.isWhitespace() } && inside.any { it == '|' || it.isLetter() } &&
            !inside.all { it.isDigit() }
        ) {
            return open
        }
        from = close + 1
    }
}
