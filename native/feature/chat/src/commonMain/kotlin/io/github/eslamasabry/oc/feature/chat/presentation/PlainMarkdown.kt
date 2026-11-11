package io.github.eslamasabry.oc.feature.chat.presentation

/**
 * A reply's words without the marks it is written with, for one-line previews (the timeline). The same rule as the home
 * list's line (`HomePreviewRules.plain` in `:feature:chats`; features never import each other, so it is repeated, as
 * [ReplyModelNames] repeats the composer's): headings, quotes, bullets and code fences lose their lead, a link keeps
 * its label, emphasis and code keep their words.
 */
internal object PlainMarkdown {
    private val lineLead = Regex("^\\s{0,3}(?:#{1,6}\\s+|>\\s?|[-*+]\\s+(?:\\[[ xX]]\\s+)?|```\\S*)")
    private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val strong = Regex("(\\*\\*|__|~~)(?=\\S)(.+?)(?<=\\S)\\1")
    private val code = Regex("`+([^`]*)`+")
    private val spaces = Regex("\\s+")

    fun plain(text: String): String =
        text.lineSequence()
            .joinToString("\n") { it.replace(lineLead, "") }
            .replace(link, "$1")
            .replace(strong, "$2")
            .replace(code, "$1")
            .replace("**", "")

    /** [text] on one line: marks removed, whitespace folded. */
    fun oneLine(text: String): String = plain(text).replace(spaces, " ").trim()
}
