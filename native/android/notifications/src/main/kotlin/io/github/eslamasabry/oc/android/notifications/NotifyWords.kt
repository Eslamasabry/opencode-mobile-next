package io.github.eslamasabry.oc.android.notifications

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Notification copy in the app's language (plan 11 §10.7, D11-4): every render resolves its words through a context
 * configured with the stored app locale (`oc.appLocale`), not the system one, so a language chosen in the app reaches
 * the shade without a restart. All seven app languages ship natively (fixes map T16: the Flutter app had only en/ar).
 *
 * [localeTag] is read on every call (null or an unsupported tag = the system language).
 */
public class NotifyWords(private val base: Context, private val localeTag: () -> String?) {
    public fun text(id: Int, vararg args: Any): String = localized().getString(id, *args)

    public fun plural(id: Int, count: Int, vararg args: Any): String =
        localized().resources.getQuantityString(id, count, *args)

    /** The context to build notifications with, so system-drawn parts follow the same language. */
    public fun localized(): Context {
        val tag = localeTag()?.takeIf { it.isNotBlank() } ?: return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language !in SUPPORTED) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    public companion object {
        /** The seven app languages (plan 06 §13). */
        public val SUPPORTED: Set<String> = setOf("en", "ar", "es", "ja", "pt", "ru", "zh")
    }
}
