package android.content

import android.app.ActivityManager

class SharedPreferences {
    val values = mutableMapOf<String, Any>()
    var failCommit = false
    var commits = 0
    fun getString(key: String, fallback: String?): String? = values[key] as? String ?: fallback
    fun getLong(key: String, fallback: Long): Long = values[key] as? Long ?: fallback
    fun getBoolean(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback
    fun edit() = Editor(this)
    class Editor(private val preferences: SharedPreferences) {
        private val pending = mutableMapOf<String, Any>()
        fun putString(key: String, value: String): Editor { pending[key] = value; return this }
        fun putLong(key: String, value: Long): Editor { pending[key] = value; return this }
        fun putBoolean(key: String, value: Boolean): Editor { pending[key] = value; return this }
        fun commit(): Boolean {
            preferences.commits++
            if (preferences.failCommit) return false
            preferences.values.putAll(pending)
            return true
        }
    }
}

class Context(
    private val stores: MutableMap<String, SharedPreferences> = mutableMapOf(),
    val manager: ActivityManager = ActivityManager(),
) {
    val applicationContext get() = this
    val packageName = "io.github.eslamasabry.opencode_mobile"
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        stores.getOrPut(name) { SharedPreferences() }
    fun <T> getSystemService(type: Class<T>): T? =
        if (type == ActivityManager::class.java) type.cast(manager) else null
    fun reopen() = Context(stores, manager)
    companion object { const val MODE_PRIVATE = 0 }
}
