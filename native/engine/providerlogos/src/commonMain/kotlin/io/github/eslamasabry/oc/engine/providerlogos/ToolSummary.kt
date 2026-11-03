package io.github.eslamasabry.oc.engine.providerlogos

/*
 * Port of `toolSummary` in lib/ui/screens/tools_screen.dart (B-NAV-076). Dart's RegExp `\s` is JavaScript
 * whitespace (Unicode spaces included), so the class is spelled out instead of relying on JVM `\s` (ASCII only).
 */

private const val WS = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"

/** A list marker at the start of a line: `-`, `*`, `•`, or `1.` / `1)`, then whitespace. */
private val listMarker = Regex("^([-*•]|[0-9]+[.)])$WS+")
private val whitespaceRun = Regex("$WS+")
private val sentenceEnd = Regex("[.!?](?=$WS|$)")

/** Dart String.trim(): strips leading and trailing whitespace (the same set as [WS] plus U+0085). */
private fun dartTrim(text: String): String = text.trim(::isDartWhitespace)

private fun isDartWhitespace(c: Char): Boolean =
    c in '\u0009'..'\u000D' || c == ' ' || c == '\u0085' || c == ' ' || c == ' ' ||
        c in ' '..' ' || c == ' ' || c == ' ' || c == ' ' || c == ' ' ||
        c == '　' || c == '﻿'

/**
 * The first sentence of a tool's description, as one plain line: the first paragraph or list item, its list marker
 * dropped, stopped at the first sentence end (`.`, `!` or `?` followed by whitespace or the end). Empty when the
 * server sent no description. A version number like "1.2" is not a sentence end.
 */
public fun toolSummary(description: String): String {
    val lines = dartTrim(description).split('\n').map(::dartTrim)
    val first = mutableListOf<String>()
    for (line in lines) {
        // A blank line or the next list item ends the first thought.
        if (line.isEmpty() || (first.isNotEmpty() && listMarker.containsMatchIn(line))) break
        first += listMarker.replaceFirst(line, "")
    }
    val text = dartTrim(first.joinToString(" ").replace(whitespaceRun, " "))
    val end = sentenceEnd.find(text) ?: return text
    return text.substring(0, end.range.last + 1)
}
