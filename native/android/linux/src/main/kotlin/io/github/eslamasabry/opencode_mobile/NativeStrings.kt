package io.github.eslamasabry.opencode_mobile

import android.app.LocaleConfig
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import io.github.eslamasabry.oc.core.common.text.AppLanguageResolver
import java.util.Locale

/** Resource copy for detached services as well as activities.
 * Reads the acknowledged legacy Flutter preference each time, so a changed
 * choice or a restarted process needs no channel handshake. Never mutates
 * the application's configuration or reads any other saved preference.
 */
object NativeStrings {
    private const val PREFERENCES = "FlutterSharedPreferences"
    private const val LANGUAGE_KEY = "flutter.oc.appLocale"
    @Volatile private var cachedSupported: Set<String>? = null

    fun get(context: Context, id: Int, vararg args: Any): String =
        localized(context).getString(id, *args)

    fun quantity(context: Context, id: Int, count: Int, vararg args: Any): String =
        localized(context).resources.getQuantityString(id, count, *args)

    internal fun matches(context: Context, id: Int, value: CharSequence?): Boolean =
        !value.isNullOrEmpty() &&
            supported(context).any { language ->
                atLocale(context, language).getString(id) == value.toString()
            }

    private fun supported(context: Context): Set<String> {
        cachedSupported?.let { return it }
        val tags = mutableSetOf(AppLanguageResolver.FALLBACK)
        addLocaleConfigTags(context, tags)
        if (tags.size <= 1) addTranslatedResourceTags(context, tags)
        if ("id" in tags || "in" in tags) {
            tags += "id"
            tags += "in"
        }
        val built = tags.toSet()
        if (built.size > 1) cachedSupported = built
        return built
    }

    private fun addLocaleConfigTags(context: Context, tags: MutableSet<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val list = LocaleConfig(context).supportedLocales ?: return
        for (i in 0 until list.size()) {
            tags += list[i].language.lowercase(Locale.ROOT)
            tags += list[i].toLanguageTag().substringBefore('-').lowercase(Locale.ROOT)
        }
    }

    private fun addTranslatedResourceTags(context: Context, tags: MutableSet<String>) {
        val english =
            atLocale(context, AppLanguageResolver.FALLBACK)
                .getString(LinuxR.string.native_phone_server_body)
        for (raw in context.resources.assets.locales) {
            val code = raw.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            if (
                code.isNotEmpty() &&
                    code != AppLanguageResolver.FALLBACK &&
                    atLocale(context, code).getString(LinuxR.string.native_phone_server_body) !=
                        english
            ) {
                tags += code
            }
        }
    }

    private fun atLocale(context: Context, language: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    private fun localized(context: Context): Context {
        val shipped = supported(context)
        val choice = try {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, null)
        } catch (_: Exception) {
            null
        }
        val configuration = Configuration(context.resources.configuration)
        val systemTags =
            (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
        val language = when {
            !choice.isNullOrBlank() -> AppLanguageResolver.resolve(listOf(choice), shipped)
            else -> AppLanguageResolver.resolve(systemTags, shipped)
        }
        return atLocale(context, language)
    }
}
