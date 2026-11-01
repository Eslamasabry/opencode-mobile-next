package io.github.eslamasabry.oc.ui.shell.gate

import android.os.LocaleList
import java.util.Locale

public actual fun applyGateLocale(localeTag: String?): String {
    val previous = Locale.getDefault().toLanguageTag()
    if (localeTag != null) {
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)
        LocaleList.setDefault(LocaleList(locale))
    }
    return previous
}
