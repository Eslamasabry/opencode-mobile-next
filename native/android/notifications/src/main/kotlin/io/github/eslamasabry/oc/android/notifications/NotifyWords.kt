package io.github.eslamasabry.oc.android.notifications

import android.content.Context
import android.content.res.Configuration
import io.github.eslamasabry.oc.core.common.text.Numbers
import java.util.Locale

/**
 * Notification copy in the app's language (plan 11 §10.7, D11-4): every render resolves its words through a context
 * configured with the stored app locale (`oc.appLocale`), not the system one, so a language chosen in the app reaches
 * the shade without a restart. All seven app languages ship natively (fixes map T16: the Flutter app had only en/ar).
 *
 * [localeTag] is read on every call (null or an unsupported tag = the system language).
 */
public class NotifyWords(private val base: Context, private val localeTag: () -> String?) {
    public fun text(id: Int, vararg args: Any): String = localized().getString(id, *numbered(args))

    public fun plural(id: Int, count: Int, vararg args: Any): String =
        localized().resources.getQuantityString(id, count, *numbered(args))

    /** The context to build notifications with, so system-drawn parts follow the same language. */
    public fun localized(): Context {
        val tag = localeTag()?.takeIf { it.isNotBlank() } ?: return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language !in SUPPORTED) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    public companion object {
        /**
         * Whole numbers become text through the shared [Numbers] formatter (the phone's number setting, owner decision
         * 2026-11-03), so the shade shows the digits the screens show; the templates take `%s`, never `%d`.
         */
        public fun numbered(args: Array<out Any>): Array<Any> =
            Array(args.size) { i ->
                when (val a = args[i]) {
                    is Int -> Numbers.format(a)
                    is Long -> Numbers.format(a)
                    else -> a
                }
            }

        /** The seven app languages (plan 06 §13). */
        public val SUPPORTED: Set<String> = setOf("en", "ar", "es", "ja", "pt", "ru", "zh")
    }
}
