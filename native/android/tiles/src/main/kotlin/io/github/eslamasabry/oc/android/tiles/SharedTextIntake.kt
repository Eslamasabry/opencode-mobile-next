package io.github.eslamasabry.oc.android.tiles

import android.content.Intent

/**
 * Text shared into the app. [text] is what the composer receives: the body, with the subject as a first line when
 * the body does not already start with it (the Flutter `LaunchShareChannel.sharedText` rule), capped at
 * [SharedTextIntake.MAX_CHARS].
 */
data class SharedText(val body: String, val subject: String?, val text: String)

/** Ports the Flutter `LaunchShareChannel.capture` (B-ANDR-074, B-ANDR-075). */
object SharedTextIntake {
    const val MAX_CHARS: Int = 100_000

    /**
     * For an ACTION_SEND of a text type with a non-empty body or subject: returns the trimmed text and consumes the
     * share (action becomes ACTION_MAIN, EXTRA_TEXT and EXTRA_SUBJECT are removed) so a recreated activity does not
     * deliver it again. Anything else returns null and leaves the intent untouched.
     */
    fun consume(intent: Intent?): SharedText? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        // A CharSequence extra (a styled share) reads as null through getStringExtra.
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty().take(MAX_CHARS)
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim().orEmpty().take(MAX_CHARS)
        if (body.isEmpty() && subject.isEmpty()) return null
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_SUBJECT)
        return SharedText(body = body, subject = subject.ifEmpty { null }, text = combine(body, subject))
    }

    /** Pure: the Flutter combination rule, capped at [MAX_CHARS]. */
    fun combine(body: String, subject: String): String {
        val joined =
            when {
                subject.isEmpty() -> body
                body.isEmpty() -> subject
                body.startsWith(subject) -> body
                else -> "$subject\n$body"
            }
        return joined.take(MAX_CHARS)
    }
}
