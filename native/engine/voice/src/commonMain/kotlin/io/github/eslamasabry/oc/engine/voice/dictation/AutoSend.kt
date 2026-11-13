package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.engine.voice.state.VoiceMode

private val modelNotes = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|[♪♫]""")
private val notLetterOrDigit = Regex("""[^\p{L}\p{N}]+""")

/**
 * Port of Dart `isSendableTranscript` (B-MEDIA-016): at least two letters or digits (code points, any script) once
 * the speech model's notes about the sound ("[BLANK_AUDIO]", "(music)", "*sigh*", "♪") are taken out.
 */
public fun isSendableTranscript(transcript: String): Boolean {
    val words = transcript.replace(modelNotes, " ").replace(notLetterOrDigit, "")
    return codePointCount(words) >= 2
}

/** Port of Dart `mayAutoSend`: sendable, and never something the chat would run as a command ("/…" or "!…"). */
public fun mayAutoSend(transcript: String): Boolean {
    val text = transcript.trimStart()
    if (text.startsWith('/') || text.startsWith('!')) return false
    return isSendableTranscript(text)
}

internal fun codePointCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        i += if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

/** Plan 10 §6.6: `stopOnSilence = mode == Tap && settings.autoSend`. */
public fun stopsOnSilence(mode: VoiceMode, autoSend: Boolean): Boolean = mode == VoiceMode.Tap && autoSend
