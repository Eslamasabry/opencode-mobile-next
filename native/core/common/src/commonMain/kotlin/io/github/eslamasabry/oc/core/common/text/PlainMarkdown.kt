package io.github.eslamasabry.oc.core.common.text

/**
 * A reply's words without the marks it is written with, for one-line previews: the home list, the dock's peek and the
 * conversation's timeline say "Folder: second/", never "**Folder:** `second/`" (seen raw on the owner's phone). One rule
 * for every preview, so they cannot drift apart. Headings, quotes, bullets and code fences lose their lead, a link keeps
 * its label, emphasis and code keep their words; emoji and everything else stay.
 */
public object PlainMarkdown {
    private val lineLead = Regex("^\\s{0,3}(?:#{1,6}\\s+|>\\s?|[-*+]\\s+(?:\\[[ xX]]\\s+)?|```\\S*)")
    private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val strong = Regex("(\\*\\*|__|~~)(?=\\S)(.+?)(?<=\\S)\\1")
    private val code = Regex("`+([^`]*)`+")
    private val spaces = Regex("\\s+")

    /** [text] without its marks, its lines kept. */
    public fun plain(text: String): String =
        text.lineSequence()
            .joinToString("\n") { it.replace(lineLead, "") }
            .replace(link, "$1")
            .replace(strong, "$2")
            .replace(code, "$1")
            .replace("**", "")

    /** [text] on one line: marks removed, whitespace folded, trimmed (each caller cuts it to its own length). */
    public fun oneLine(text: String): String = plain(text).replace(spaces, " ").trim()
}
