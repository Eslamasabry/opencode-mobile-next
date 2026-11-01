package io.github.eslamasabry.oc.core.data.android

import android.content.Context
import android.content.SharedPreferences
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.secret.AeadSecretStore
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Android stores (plan 05 §2.2, §4.2). With `:android:migration`, the only `getSharedPreferences(` call sites
 * (Konsist K7). Build each store once per process (the app graph holds them as singletons).
 */
public object AndroidDataStores {

    /** The ADOPTED Flutter file: `shared_prefs/FlutterSharedPreferences.xml`, `flutter.` prefix and encodings kept. */
    public fun flutterPrefs(context: Context, gate: MigrationGate, io: CoroutineDispatcher): PrefsKeyValueStore =
        PrefsKeyValueStore(
            backend = SharedPreferencesBackend(open(context, FlutterPrefsCodec.FILE_NAME)),
            codec = FlutterPrefsCodec(ObjectStreamJavaListDecoder),
            gate = gate,
            io = io,
        )

    /** `shared_prefs/oc_secrets_v1.xml` + Keystore alias `oc.secrets.aesgcm.v1`. */
    public fun secrets(context: Context, redactor: Redactor, io: CoroutineDispatcher): SecretStore =
        AeadSecretStore(
            entries = SharedPreferencesSecretEntries(open(context, AeadSecretStore.FILE_NAME)),
            aead = KeystoreAead(AeadSecretStore.KEY_ALIAS),
            redactor = redactor,
            io = io,
        )

    /** The codec with the JVM Java-list reader, for the migrator's census over raw prefs maps. */
    public fun flutterCodec(): FlutterPrefsCodec = FlutterPrefsCodec(ObjectStreamJavaListDecoder)

    /**
     * Kotlin-owned named prefs files moved from the Flutter app (plan 05 Appendix D; plan 03 K7 `AndroidNamedPrefs`).
     * Their per-profile entries are cleaned by the owning module's `forgetProfile` hook.
     */
    public fun namedPrefs(context: Context, fileName: String): SharedPreferences = open(context, fileName)

    private fun open(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}
