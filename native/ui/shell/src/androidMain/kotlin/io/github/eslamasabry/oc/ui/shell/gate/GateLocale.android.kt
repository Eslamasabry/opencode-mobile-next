package io.github.eslamasabry.oc.ui.shell.gate

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
public actual fun ProvideGateLocale(localeTag: String?, content: @Composable () -> Unit) {
    if (localeTag == null) {
        content()
        return
    }
    val base = LocalConfiguration.current
    val overridden =
        remember(base, localeTag) {
            Configuration(base).apply { setLocales(LocaleList(Locale.forLanguageTag(localeTag))) }
        }
    CompositionLocalProvider(LocalConfiguration provides overridden, content = content)
}
