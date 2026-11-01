package io.github.eslamasabry.oc.android.migration.android

import android.content.Context
import android.content.SharedPreferences
import io.github.eslamasabry.oc.android.migration.FlutterPrefsFile
import io.github.eslamasabry.oc.android.migration.KotlinPrefsSource
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref

/**
 * The adopted Flutter file through Android's own `SharedPreferencesImpl` (plan 05 §2.2): `commit()`, never `apply()`.
 * Opened lazily, on first use, so the first `getSharedPreferences` of the file comes after S0 (plan 05 §7.1: loading
 * a file with a `.bak` twin renames it). Konsist K7 allows `getSharedPreferences(` in `:android:migration`.
 */
internal class SharedPreferencesFlutterPrefs(context: Context) : FlutterPrefsFile {
    private val app = context.applicationContext
    private val prefs: SharedPreferences by lazy { app.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override fun live(): Map<String, RawPref> {
        val out = LinkedHashMap<String, RawPref>()
        for ((k, v) in prefs.all) out[k] = RawPref.fromAndroidValue(v) ?: RawPref.Other("unknown", "")
        return out
    }

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        val editor = prefs.edit()
        for ((key, value) in changes) {
            when (value) {
                null, RawPref.Null -> editor.remove(key)
                is RawPref.Str -> editor.putString(key, value.value)
                is RawPref.Bool -> editor.putBoolean(key, value.value)
                is RawPref.Int64 -> editor.putLong(key, value.value)
                // The migrator never writes the types the Flutter reader would mis-handle (plan 05 §2.3 rule 2).
                else -> return false
            }
        }
        return editor.commit()
    }

    companion object {
        const val FILE_NAME = "FlutterSharedPreferences"
    }
}

/** Kotlin-owned prefs files (plan 05 Appendix D), read-only. A file that does not exist reads as null. */
internal class AndroidKotlinPrefs(context: Context) : KotlinPrefsSource {
    private val app = context.applicationContext

    override fun read(fileName: String): Map<String, Any?>? {
        val dir = app.filesDir.parentFile?.let { java.io.File(it, "shared_prefs") } ?: return null
        if (!java.io.File(dir, "$fileName.xml").exists() && !java.io.File(dir, "$fileName.xml.bak").exists()) return null
        return app.getSharedPreferences(fileName, Context.MODE_PRIVATE).all
    }
}
