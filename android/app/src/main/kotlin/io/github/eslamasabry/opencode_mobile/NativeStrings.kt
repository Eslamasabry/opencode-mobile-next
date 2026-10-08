package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Resource copy for detached services as well as activities.
 * Reads the acknowledged legacy Flutter preference each time, so a changed
 * choice or a restarted process needs no channel handshake. Never mutates
 * the application's configuration or reads any other saved preference.
 */
object NativeStrings {
    private const val PREFERENCES = "FlutterSharedPreferences"
    private const val LANGUAGE_KEY = "flutter.oc.appLocale"
    private val supported = setOf("en", "ar")
    // Keep in step with AppLocaleStore: untranslated app choices fall back to
    // English rather than accidentally reverting to a different system language.
    private val appLanguages = setOf("en", "ar", "es", "ja", "pt", "ru", "zh")

    fun get(context: Context, id: Int, vararg args: Any): String =
        localized(context).getString(id, *args)

    fun quantity(context: Context, id: Int, count: Int, vararg args: Any): String =
        localized(context).resources.getQuantityString(id, count, *args)

    internal fun matches(context: Context, id: Int, value: CharSequence?): Boolean =
        supported.any { language ->
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language.takeIf { it in supported } ?: "en"))
            context.createConfigurationContext(configuration).getString(id) == value?.toString()
        }

    private fun localized(context: Context): Context {
        val choice = try {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, null)
        } catch (_: Exception) {
            null
        }
        val configuration = Configuration(context.resources.configuration)
        val systemLanguage = (0 until configuration.locales.size())
            .map { configuration.locales[it].language }
            .firstOrNull { it in appLanguages }
        val language = when {
            choice in appLanguages -> choice!!
            systemLanguage != null -> systemLanguage
            else -> "en"
        }
        configuration.setLocale(Locale.forLanguageTag(language.takeIf { it in supported } ?: "en"))
        return context.createConfigurationContext(configuration)
    }
}
