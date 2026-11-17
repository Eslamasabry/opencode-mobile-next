package io.github.eslamasabry.oc.engine.voice.dictation

/**
 * Whisper writes what it hears besides speech as a tag: "[BLANK_AUDIO]", "[no audio]", "(static)", "[Bell tolls]".
 * When a recognised segment is NOTHING but such tags it carries no words, so it is dropped here, where segments become
 * text. Brackets inside a sentence the person dictated are never touched: only a whole-segment match counts.
 */
internal object NonSpeechTags {
    private val onlyTags = Regex("""^(?:\s*(?:\[[^\[\]]{1,40}]|\([^()]{1,40}\)))+\s*$""")

    /** The segment's words: empty when the whole segment is non-speech tags, otherwise the segment unchanged. */
    fun words(segment: String): String = if (onlyTags.matches(segment)) "" else segment
}
