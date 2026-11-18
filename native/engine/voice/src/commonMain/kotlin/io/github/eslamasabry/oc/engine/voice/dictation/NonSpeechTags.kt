package io.github.eslamasabry.oc.engine.voice.dictation

private val tagOnly = Regex("""^(?:\s*(?:\[[^\[\]()]*]|\([^\[\]()]*\)))+\s*$""")

/**
 * Whisper writes what it hears besides speech as a tag: "[no audio]", "[BLANK_AUDIO]", "[Bell tolls]", "(static)".
 * A line that is only such tags is not words and is dropped, so silence yields "" (the same as nothing heard).
 * Brackets inside a real sentence ("see [1] for details") are kept: only whole lines are judged.
 */
internal fun dropNonSpeechTags(text: String): String =
    text.lineSequence().filterNot { tagOnly.matches(it) }.joinToString("\n").trim()
