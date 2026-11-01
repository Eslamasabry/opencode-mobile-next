package io.github.eslamasabry.oc.engine.voice.readaloud

/**
 * The language a reply is written in (Dart `detectReadLanguage`, plan 10 §8.5, B-MEDIA-005): a lowercase language
 * code, or `other` when its script has no code here. Arabic script is `ar`; other non-Latin scripts are `other`; Latin
 * script follows [appLanguage] when that language is written in Latin letters, else `en`.
 */
public object ReadLanguage {
    private val nonLatinApp = setOf("ar", "fa", "ur", "he", "iw", "ru", "uk", "el", "hi", "bn", "ta", "zh", "ja", "ko", "th")

    public fun detect(text: String, appLanguage: String?): String {
        var arabic = 0
        var latin = 0
        var other = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val rune =
                if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                    (((c.code - 0xD800) shl 10) or (text[i + 1].code - 0xDC00)) + 0x10000
                } else {
                    c.code
                }
            i += if (rune > 0xFFFF) 2 else 1
            when {
                rune in 0x0600..0x06FF || rune in 0x0750..0x077F || rune in 0xFB50..0xFDFF || rune in 0xFE70..0xFEFF -> arabic++
                rune in 0x41..0x5A || rune in 0x61..0x7A || rune in 0xC0..0x24F -> latin++
                rune in 0x0370..0x05FF || rune in 0x0700..0x074F || rune in 0x0780..0x1FFF || rune in 0x3040..0xD7FF ||
                    rune in 0xFF00..0xFFEF -> other++
            }
        }
        if (arabic == 0 && latin == 0 && other == 0) return languageOf(appLanguage)
        if (arabic >= latin && arabic >= other) return "ar"
        if (other > latin) return "other"
        val app = languageOf(appLanguage)
        return if (app.isEmpty() || app in nonLatinApp) "en" else app
    }

    public fun languageOf(tag: String?): String = (tag ?: "").lowercase().split('-', '_').first()
}

/** Dart `splitForSynthesis`: sentence ends first, then the nearest space once a piece passes [limit] characters. */
public object SentenceSplitter {
    private val sentence = Regex("""[^.!?\n]+[.!?]*\s*|\n+""")

    public fun split(text: String, limit: Int = 280): List<String> {
        val pieces = mutableListOf<String>()
        for (match in sentence.findAll(text)) {
            var part = match.value.trim()
            while (part.length > limit) {
                var cut = part.lastIndexOf(' ', limit)
                if (cut <= 0) cut = limit
                pieces += part.substring(0, cut).trim()
                part = part.substring(cut).trim()
            }
            if (part.isNotEmpty()) pieces += part
        }
        return pieces
    }
}

/** A phone voice (`Voice.name` is both id and label, parity). */
public data class TtsVoice(val id: String, val label: String, val localeTag: String)

/** Plan 10 §8.2 (B-MEDIA-018, B-MEDIA-002): language and region first, then language, else none (engine default). */
public object VoicePick {
    public fun forLocale(voices: List<TtsVoice>, localeTag: String): TtsVoice? {
        val wanted = parts(localeTag) ?: return null
        voices.firstOrNull { parts(it.localeTag) == wanted && wanted.second != null }?.let { return it }
        return voices.firstOrNull { parts(it.localeTag)?.first == wanted.first }
    }

    /** An explicit choice is kept while that voice still exists (Dart `InteractionDefaults.voice`). */
    public fun choose(voices: List<TtsVoice>, localeTag: String, explicitId: String? = null): TtsVoice? =
        voices.firstOrNull { it.id == explicitId } ?: forLocale(voices, localeTag)

    /** Tags accept `_` or `-` in any case: "en_us", "AR-ae". */
    private fun parts(tag: String): Pair<String, String?>? {
        val bits = tag.lowercase().split('-', '_').filter { it.isNotEmpty() }
        if (bits.isEmpty()) return null
        return bits[0] to bits.getOrNull(1)
    }
}
