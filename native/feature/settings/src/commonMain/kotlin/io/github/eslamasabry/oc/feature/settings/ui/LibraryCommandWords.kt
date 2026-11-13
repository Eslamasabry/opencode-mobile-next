package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.core.model.CommandInfo

/** What a command row shows: its words as the title, its slash word and argument hint under it. */
internal data class LibraryCommandWords(val title: String, val slash: String, val hint: String?)

/**
 * A server writes a command's argument template inside its description ("review changes
 * [commit|branch|pr], defaults to ..."). The title keeps every word of the description and drops
 * only the template groups; the groups go beside the slash word. So no row shows a title cut in
 * the middle of raw syntax, and none loses the words after a bracket.
 */
internal fun libraryCommandWords(command: CommandInfo): LibraryCommandWords {
    val slash = "/${command.name.removePrefix("/")}"
    val text = command.description?.trim().orEmpty()
    if (text.isEmpty()) return LibraryCommandWords(slash, slash, null)
    val groups = TEMPLATE.findAll(text).toList()
    if (groups.isEmpty()) return LibraryCommandWords(text, slash, null)
    val title =
        TEMPLATE.replace(text, " ")
            .replace(SPACES, " ")
            .replace(SPACE_BEFORE_MARK, "$1")
            .trim()
            .trim(',', ';', ':')
            .trim()
    if (title.isEmpty() || title.none { it.isLetterOrDigit() }) return LibraryCommandWords(text, slash, null)
    return LibraryCommandWords(title, slash, groups.joinToString(" ") { it.value })
}

/**
 * A bracket group that ends a clause (before a comma, a stop, another group or the end): that is
 * where a server writes its template. One in the middle of a sentence, or a Markdown link's text,
 * stays in the words.
 */
private val TEMPLATE = Regex("""\[[^\[\]]*](?=\s*(?:[,;:.]|\[|$))""")
private val SPACES = Regex("""\s+""")
private val SPACE_BEFORE_MARK = Regex("""\s+([,.;:!?)])""")
