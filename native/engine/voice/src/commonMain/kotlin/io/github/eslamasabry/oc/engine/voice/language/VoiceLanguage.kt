package io.github.eslamasabry.oc.engine.voice.language

import io.github.eslamasabry.oc.engine.voice.state.PackId

/**
 * What the speech model is told the person speaks (Dart `VoiceLanguage`, `lib/voice/model_manifest.dart`; plan 10 §6.1,
 * B-MEDIA-028). Stored as the Dart enum name ([storedId]), so `auto`, `detect`, `english`… keep their meaning; an
 * unknown stored value reads as [Auto]. [label] is the language's own name (never translated); [Auto] and [Detect]
 * are worded by the app's strings.
 */
public enum class VoiceLanguage(public val storedId: String, public val whisperCode: String, public val label: String) {
    Auto("auto", "", "Auto"),
    Detect("detect", "", "Detect from speech"),
    English("english", "en", "English"),
    Arabic("arabic", "ar", "العربية"),
    Spanish("spanish", "es", "Español"),
    French("french", "fr", "Français"),
    German("german", "de", "Deutsch"),
    Italian("italian", "it", "Italiano"),
    Portuguese("portuguese", "pt", "Português"),
    Dutch("dutch", "nl", "Nederlands"),
    Russian("russian", "ru", "Русский"),
    Ukrainian("ukrainian", "uk", "Українська"),
    Polish("polish", "pl", "Polski"),
    Czech("czech", "cs", "Čeština"),
    Romanian("romanian", "ro", "Română"),
    Greek("greek", "el", "Ελληνικά"),
    Turkish("turkish", "tr", "Türkçe"),
    Hebrew("hebrew", "he", "עברית"),
    Persian("persian", "fa", "فارسی"),
    Urdu("urdu", "ur", "اردو"),
    Hindi("hindi", "hi", "हिन्दी"),
    Bengali("bengali", "bn", "বাংলা"),
    Tamil("tamil", "ta", "தமிழ்"),
    Chinese("chinese", "zh", "中文"),
    Japanese("japanese", "ja", "日本語"),
    Korean("korean", "ko", "한국어"),
    Vietnamese("vietnamese", "vi", "Tiếng Việt"),
    Thai("thai", "th", "ไทย"),
    Indonesian("indonesian", "id", "Bahasa Indonesia"),
    Malay("malay", "ms", "Bahasa Melayu"),
    Swedish("swedish", "sv", "Svenska"),
    Norwegian("norwegian", "no", "Norsk"),
    Danish("danish", "da", "Dansk"),
    Finnish("finnish", "fi", "Suomi"),
    Hungarian("hungarian", "hu", "Magyar"),
    Swahili("swahili", "sw", "Kiswahili"),
    ;

    /** A language the person names, not one of the two automatic choices. */
    public val isNamed: Boolean get() = whisperCode.isNotEmpty()

    public companion object {
        public fun fromStoredId(id: String?): VoiceLanguage = entries.firstOrNull { it.storedId == id } ?: Auto

        /** The named language for an app-locale language code, or null when Whisper has none (`iw`, `nb`/`nn`, `in` fold). */
        public fun forLanguageCode(code: String?): VoiceLanguage? {
            val lower = (code ?: "").lowercase()
            val folded =
                when (lower) {
                    "iw" -> "he"
                    "nb", "nn" -> "no"
                    "in" -> "id"
                    else -> lower
                }
            if (folded.isEmpty()) return null
            return entries.firstOrNull { it.isNamed && it.whisperCode == folded }
        }

        /** [Auto] follows the app's language when Whisper knows it, else listens for it ([Detect]). */
        public fun resolve(chosen: VoiceLanguage, appLanguageCode: String?): VoiceLanguage =
            if (chosen != Auto) chosen else forLanguageCode(appLanguageCode) ?: Detect
    }
}

/** The recognizer's `language` field (plan 10 §6.1, B-MEDIA-033): `Detect` → "" (automatic detection). */
public fun VoiceLanguage.recognizerCode(appLanguageCode: String?): String =
    VoiceLanguage.resolve(this, appLanguageCode).whisperCode

/** The plain line under the language choice (Dart `voiceLanguageNote`, B-MEDIA-029); null when nothing needs saying. */
public sealed interface VoiceLanguageNote {
    /** "Same as the app" cannot follow [appLanguageCode] offline, so the model listens for the language. */
    public data class UnavailableOffline(val appLanguageCode: String) : VoiceLanguageNote

    /** The Compact (tiny) model is rough outside English. */
    public data class CompactRough(val language: VoiceLanguage) : VoiceLanguageNote

    public companion object {
        public fun of(chosen: VoiceLanguage, pack: PackId, appLanguageCode: String): VoiceLanguageNote? {
            val effective = VoiceLanguage.resolve(chosen, appLanguageCode)
            if (chosen == VoiceLanguage.Auto && effective == VoiceLanguage.Detect && appLanguageCode.isNotEmpty()) {
                return UnavailableOffline(appLanguageCode)
            }
            if (pack.raw == "tiny" && effective.isNamed && effective.whisperCode != "en") return CompactRough(effective)
            return null
        }
    }
}
