package io.github.eslamasabry.oc.core.data.android

import android.content.SharedPreferences
import io.github.eslamasabry.oc.core.data.secret.SecretEntries

/** Test access to the internal entry file adapter. */
internal fun SharedPreferencesSecretEntriesForTest(prefs: SharedPreferences): SecretEntries = SharedPreferencesSecretEntries(prefs)
