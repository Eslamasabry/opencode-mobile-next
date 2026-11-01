package io.github.eslamasabry.oc.core.data.android

import android.content.SharedPreferences
import io.github.eslamasabry.oc.core.data.kv.PrefsBackend
import io.github.eslamasabry.oc.core.data.kv.RawPref

/**
 * [PrefsBackend] over a real `SharedPreferences` file (plan 05 §2.2). Physical keys; `commit()`, never `apply()`.
 * Called only by `PrefsKeyValueStore`, on its IO dispatcher and under its file-wide lock.
 */
internal class SharedPreferencesBackend(private val prefs: SharedPreferences) : PrefsBackend {

    override fun load(): Map<String, RawPref> {
        val out = LinkedHashMap<String, RawPref>()
        for ((key, value) in prefs.all) {
            // SharedPreferences can only hold the types fromPlatformValue knows; anything else is kept as unknown.
            out[key] = RawPref.fromPlatformValue(value) ?: RawPref.Other(tag = "unknown", rawXml = "")
        }
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
                // Only reached when compensating a refused write over a value a Kotlin writer once stored this way.
                is RawPref.Int32 -> editor.putInt(key, value.value)
                is RawPref.F32 -> editor.putFloat(key, value.value)
                is RawPref.StrSet -> editor.putStringSet(key, value.value)
                // The Editor cannot write these tags; a compensation leaves the platform's copy as it is.
                is RawPref.F64, is RawPref.Other -> Unit
            }
        }
        return editor.commit()
    }
}
