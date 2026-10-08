package android.content

import android.app.NotificationManager
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

class Preferences {
    var choice: String? = null
    fun getString(key: String, fallback: String?): String? {
        check(key == "flutter.oc.appLocale")
        return choice ?: fallback
    }
}

open class Context(
    language: String = "en",
    val preferences: Preferences = Preferences(),
    val notifications: NotificationManager = NotificationManager(),
) {
    val resources = Resources(Configuration(Locale(language)))
    var serviceStarts = 0
    fun getSharedPreferences(name: String, mode: Int): Preferences {
        check(name == "FlutterSharedPreferences" && mode == MODE_PRIVATE)
        return preferences
    }
    fun <T> getSystemService(type: Class<T>): T? =
        if (type == NotificationManager::class.java) type.cast(notifications) else null
    fun createConfigurationContext(configuration: Configuration): Context =
        Context(configuration.locales[0].language, preferences, notifications)
    fun getString(id: Int, vararg args: Any): String = resources.getString(id, *args)
    fun startService(intent: Intent) { serviceStarts++ }
    fun startForegroundService(intent: Intent) { serviceStarts++ }
    companion object { const val MODE_PRIVATE = 0 }
}

class Intent
