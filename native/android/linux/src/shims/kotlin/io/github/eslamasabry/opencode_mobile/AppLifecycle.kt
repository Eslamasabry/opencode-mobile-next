// S1-LINUX shim (native-authored, not a twin). TODO(:android:platform): replaced by the copied AppLifecycle (minus its
// MethodChannel glue) when that module lands. The Flutter file imports io.flutter, so it cannot be copied here.
package io.github.eslamasabry.opencode_mobile

import android.content.Context

/**
 * The two members BuiltinLinux reaches, with the Flutter app's body verbatim: same prefs file `oc_lifecycle`, same key
 * `running_services`, `commit()` not `apply()`, and the read-once-before-first-write rule.
 */
object AppLifecycle {
    private const val PREFS = "oc_lifecycle"
    private const val KEY_SERVICES = "running_services"

    private var previousServices: List<String>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun previousServices(context: Context): List<String> {
        previousServices?.let { return it }
        val saved = (prefs(context).getString(KEY_SERVICES, "") ?: "")
            .split(',')
            .filter { it.isNotEmpty() }
        previousServices = saved
        return saved
    }

    @Synchronized
    fun recordServices(context: Context, names: List<String>) {
        previousServices(context)
        // commit, not apply: a force stop may come a moment later and runs nothing of ours.
        prefs(context).edit().putString(KEY_SERVICES, names.joinToString(",")).commit()
    }
}
