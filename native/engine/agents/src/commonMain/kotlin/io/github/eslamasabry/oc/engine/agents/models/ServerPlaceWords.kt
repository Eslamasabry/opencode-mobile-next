package io.github.eslamasabry.oc.engine.agents.models

/**
 * EN + AR wording for [ServerPlaceText], copied from `lib/l10n/app_en.arb` / `app_ar.arb` (`settingsV3ModelNewOn*`,
 * `settingsV3SectionOn`, `settingsV3SavedOn*`, `settingsV3UseMyDefaultDone*`, `phoneServerCardTitle`). Kept here only
 * for this parity port; the UI words module owns copy and should take this table over.
 */
public enum class ServerPlaceLanguage { En, Ar }

public object ServerPlaceWords {
    /** The phone server card's title ("On this phone"). */
    public fun phoneCardTitle(language: ServerPlaceLanguage): String = when (language) {
        ServerPlaceLanguage.En -> "On this phone"
        ServerPlaceLanguage.Ar -> "على هذا الهاتف"
    }

    public fun render(text: ServerPlaceText, language: ServerPlaceLanguage): String {
        val en = language == ServerPlaceLanguage.En
        return when (text) {
            is ServerPlaceText.NewConversations -> when (val place = text.place) {
                ServerPlace.ThisPhone -> if (en) "New conversations on this phone" else "المحادثات الجديدة على هذا الهاتف"
                is ServerPlace.Named -> if (en) "New conversations on ${place.name}" else "المحادثات الجديدة على ${place.name}"
            }
            is ServerPlaceText.SectionOn -> when (val place = text.place) {
                ServerPlace.ThisPhone -> phoneCardTitle(language)
                is ServerPlace.Named -> if (en) "On ${place.name}" else "على ${place.name}"
            }
            is ServerPlaceText.SavedOn -> when (val place = text.place) {
                ServerPlace.ThisPhone ->
                    if (en) {
                        "Saved on this phone. Other apps using this phone's server get this too."
                    } else {
                        "يُحفظ على هذا الهاتف. التطبيقات الأخرى التي تستخدم خادم هذا الهاتف تحصل على هذا أيضًا."
                    }
                is ServerPlace.Named ->
                    if (en) {
                        "Saved on ${place.name}. Other apps using ${place.name} get this too."
                    } else {
                        "يُحفظ على ${place.name}. التطبيقات الأخرى التي تستخدم ${place.name} تحصل على هذا أيضًا."
                    }
            }
            is ServerPlaceText.UseMyDefaultDone -> when (val place = text.place) {
                ServerPlace.ThisPhone ->
                    if (en) "Reset on this phone to your own choices: ${text.what}." else "أُعيد على هذا الهاتف إلى اختياراتك: ${text.what}."
                is ServerPlace.Named ->
                    if (en) {
                        "Reset on ${place.name} to your own choices: ${text.what}."
                    } else {
                        "أُعيد على ${place.name} إلى اختياراتك: ${text.what}."
                    }
            }
        }
    }
}
