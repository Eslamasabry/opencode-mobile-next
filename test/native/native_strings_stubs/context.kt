package android.content

import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

class Preferences {
    var choice: Any? = null
    var failRead = false
    val reads = mutableListOf<String>()
    fun getString(key: String, fallback: String?): String? {
        reads.add(key)
        if (failRead) throw IllegalStateException("read refused")
        return choice as String? ?: fallback
    }
}

open class Context(
    systemLanguage: String = "en",
    val preferences: Preferences = Preferences(),
    systemLanguages: List<String>? = null,
) {
    val resources = Resources(Configuration((systemLanguages ?: listOf(systemLanguage)).map { Locale(it) }))
    val preferenceFiles = mutableListOf<String>()
    val applicationContext: Context get() = this
    fun getSharedPreferences(name: String, mode: Int): Preferences {
        check(mode == MODE_PRIVATE)
        preferenceFiles.add(name)
        return preferences
    }
    fun createConfigurationContext(configuration: Configuration): Context =
        Context(configuration.locales[0].language, preferences)
    fun getString(id: Int, vararg args: Any): String = resources.getString(id, *args)
    companion object { const val MODE_PRIVATE = 0 }
}
