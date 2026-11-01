package io.github.eslamasabry.oc.core.markdown

/** The text handed to [proseForSpeech] (over 131,072 chars) or its result (over 12,000) is too long to speak. */
public class SpeechTooLongException : IllegalArgumentException("Speech text is too long")

private val speechFence = Regex("""^\s*(`{3,}|~{3,})""")
private val speechRule = Regex("""\s*(-{3,}|\*{3,}|_{3,})\s*""")
private val speechMarks = Regex("""^\s*(?:#{1,6}\s+|>\s?|[-*+]\s+|\d+\.\s+)""")

/** The longest source [proseForSpeech] reads. */
public const val SPEECH_SOURCE_LIMIT: Int = 131_072

/**
 * The prose a reader would speak (B-CHAT-060; Flutter `KitMarkdown.proseForSpeech`): fences, tables, rules and link
 * targets dropped; heading, quote and list marks dropped; pictures read as their description; inline code becomes a
 * pause. No URL is ever spoken from a link. Throws [SpeechTooLongException] over [SPEECH_SOURCE_LIMIT] chars of
 * source or 12,000 chars of speech.
 */
public fun proseForSpeech(text: String): String {
    if (text.length > SPEECH_SOURCE_LIMIT) throw SpeechTooLongException()
    val output = StringBuilder()
    var fence: String? = null
    for (line in text.replace("\r\n", "\n").split('\n')) {
        val marker = speechFence.find(line)
        if (fence != null) {
            val open = fence
            if (marker != null) {
                val m = marker.groupValues[1]
                val rest = line.substring(marker.range.last + 1)
                if (m.startsWith(open[0]) && m.length >= open.length && rest.isBlank()) {
                    fence = null
                }
            }
            continue
        }
        if (marker != null) {
            fence = marker.groupValues[1]
            continue
        }
        if (speechRule.matchEntire(line) != null || TableScanner.isDelimiter(line)) continue
        val prose = PictureScanner.withAltText(speechMarks.replaceFirst(line, ""))
        output.append(
            InlineParser.speechPattern.replace(prose) { m ->
                val g = m.groups
                if (g[6] != null) {
                    " "
                } else {
                    // Link destinations live in group 8 and are never spoken.
                    (g[7] ?: g[1] ?: g[2] ?: g[3] ?: g[4] ?: g[5])?.value ?: ""
                }
            },
        )
        output.append('\n')
        if (output.length > 12_000) throw SpeechTooLongException()
    }
    return output.toString().trim()
}
