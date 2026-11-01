package io.github.eslamasabry.oc.core.data.android

import android.content.SharedPreferences
import io.github.eslamasabry.oc.core.data.secret.SecretEntries

/** `shared_prefs/oc_secrets_v1.xml`: encrypted entry text only. Excluded from backup by the app manifest. */
internal class SharedPreferencesSecretEntries(private val prefs: SharedPreferences) : SecretEntries {
    override fun get(name: String): String? = prefs.getString(name, null)

    override fun names(): Set<String> = prefs.all.keys.toSet()

    override fun put(name: String, value: String): Boolean = prefs.edit().putString(name, value).commit()

    override fun remove(name: String): Boolean = prefs.edit().remove(name).commit()
}
