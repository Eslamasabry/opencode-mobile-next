package io.github.eslamasabry.oc.ui.shell.gate

import java.util.Locale

public actual fun applyGateLocale(localeTag: String?): String {
    val previous = Locale.getDefault().toLanguageTag()
    if (localeTag != null) Locale.setDefault(Locale.forLanguageTag(localeTag))
    return previous
}
