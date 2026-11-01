package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** How long the words wait, visibly, before they are sent (Dart `voiceAutoSendCountdown`, plan 10 §6.7). */
public val VOICE_AUTO_SEND_COUNTDOWN: Duration = 3.seconds

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

/**
 * "Send when I stop talking" (plan 10 §6.7, B-MEDIA-017). The setting is off by default; only a tap dictation with the
 * setting on stops by silence (a hold or a conversation never does). Then the words wait [VOICE_AUTO_SEND_COUNTDOWN]
 * in the pill. The pill ticks [secondsLeft]; the ViewModel asks [decide] at zero or on "Send now".
 */
public class AutoSendCountdown private constructor(public val text: String, public val startedAt: Instant) {
    public val sendAt: Instant get() = startedAt + VOICE_AUTO_SEND_COUNTDOWN

    /** 3, 2, 1, then 0 ("Sending in 3…"). */
    public fun secondsLeft(now: Instant): Int {
        val left = sendAt - now
        if (left <= Duration.ZERO) return 0
        return ((left.inWholeMilliseconds + 999) / 1000).toInt()
    }

    public sealed interface Decision {
        /** Send [text] through the one send path "as if typed". */
        public data class Send(val text: String) : Decision

        /** Not yet zero. */
        public data object Wait : Decision

        /** The countdown ends; the words stay in the field, unsent. */
        public data object Cancel : Decision
    }

    /**
     * Sends only if the field still holds the captured text, [mayAutoSend] says yes, the app is in the foreground and
     * no send is in flight; anything else cancels. [sendNow] is the "Send now" button (no wait).
     */
    public fun decide(
        now: Instant,
        fieldText: String,
        foreground: Boolean,
        sendInFlight: Boolean,
        sendNow: Boolean = false,
    ): Decision {
        if (fieldText != text || !foreground || sendInFlight || !mayAutoSend(text)) return Decision.Cancel
        if (!sendNow && now < sendAt) return Decision.Wait
        return Decision.Send(text)
    }

    public companion object {
        /**
         * Starts a countdown only when the recording ended by silence and the words may go out by themselves;
         * null otherwise (the words simply stay in the box).
         */
        public fun startIfAllowed(words: String, stoppedBySilence: Boolean, now: Instant): AutoSendCountdown? =
            if (stoppedBySilence && mayAutoSend(words)) AutoSendCountdown(words, now) else null
    }
}

/** Plan 10 §6.6: `stopOnSilence = mode == Tap && settings.autoSend`. */
public fun stopsOnSilence(mode: VoiceMode, autoSend: Boolean): Boolean = mode == VoiceMode.Tap && autoSend
