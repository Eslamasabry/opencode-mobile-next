package io.github.eslamasabry.oc.migration.readers.secure

import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.migration.readers.xml.SharedPrefsFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * On-disk names of `flutter_secure_storage` 11.2.0 on Android with the options this app uses
 * (`const FlutterSecureStorage()` at all 8 call sites: no `AndroidOptions`, so no namespace, no key prefix,
 * `RSA_ECB_OAEPwithSHA_256andMGF1Padding` + `AES_GCM_NoPadding`). Read in the package archive whose SHA-256
 * matches `pubspec.lock` (`d4e1fb6b…95bb8`): `FlutterSecureStorageConfig.java`, `StorageCipherFactory.java`,
 * `StorageCipherImplementationGCM.java`, `KeyCipherImplementationRSAOAEP.java`, `NamespacedConfigSource.java`,
 * `UpgradeInspector.java`, `FlutterSecureStorage.java`.
 */
public object FssFormat {
    /** `DEFAULT_PREF_NAME`: `shared_prefs/FlutterSecureStorage.xml` holds the encrypted values. */
    public const val DATA_FILE: String = "FlutterSecureStorage"

    /** `getEffectiveKeyStoragePrefsName()` without a namespace: holds the RSA-wrapped AES key. */
    public const val KEY_FILE: String = "FlutterSecureKeyStorage"

    /** `NamespacedConfigSource`: `"FlutterSecureStorageConfiguration:" + dataPrefsName`, read first. */
    public const val CONFIG_FILE_NAMESPACED: String = "FlutterSecureStorageConfiguration:FlutterSecureStorage"

    /** `LEGACY_GLOBAL_CONFIG_NAME`, the fallback for each marker. */
    public const val CONFIG_FILE_LEGACY: String = "FlutterSecureStorageConfiguration"

    /** `DEFAULT_KEY_PREFIX`; entry names are `prefix + "_" + name`. Base64 of "This is the prefix for a secure storage\n". */
    public const val ENTRY_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"

    /** `StorageCipherImplementationGCM.SHARED_PREFERENCES_KEY`. */
    public const val WRAPPED_KEY_ENTRY: String = "AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"

    public const val MARKER_KEY_ALGORITHM: String = "FlutterSecureSAlgorithmKey"
    public const val MARKER_STORAGE_ALGORITHM: String = "FlutterSecureSAlgorithmStorage"
    public const val KEY_ALGORITHM_RSA_OAEP: String = "RSA_ECB_OAEPwithSHA_256andMGF1Padding"
    public const val STORAGE_ALGORITHM_GCM: String = "AES_GCM_NoPadding"

    /** v9-era markers that v11 maps to the new names but can no longer decrypt (`isRemoved`). */
    public const val REMOVED_KEY_ALGORITHM: String = "RSA_ECB_PKCS1Padding"
    public const val REMOVED_STORAGE_ALGORITHM: String = "AES_CBC_PKCS7Padding"

    /** The Keystore-AES key cipher (biometric family; this app never selects it). */
    public val KEYSTORE_AES_KEY_ALGORITHMS: Set<String> = setOf("AES_GCM_NoPadding", "AES_GCM_NoPadding_BIOMETRIC")

    /** Suffix of entries left by the plugin's own "migrate with backup" path. Never data. */
    public const val BACKUP_SUFFIX: String = "_BACKUP"

    /** Jetpack Security EncryptedSharedPreferences (Tink) keysets; v11 cannot read that store. */
    public val TINK_KEYSET_KEYS: Set<String> = setOf(
        "__androidx_security_crypto_encrypted_prefs_key_keyset__",
        "__androidx_security_crypto_encrypted_prefs_value_keyset__",
    )

    public const val IV_BYTES: Int = 12
    public const val TAG_BITS: Int = 128
    public const val AES_KEY_BYTES: Int = 16

    /** `KeyCipherImplementationRSAOAEP`: `packageName + ".FlutterSecureStoragePluginKeyOAEP" + ""`. */
    public fun keystoreAlias(packageName: String): String = "$packageName.FlutterSecureStoragePluginKeyOAEP"

    public fun entryName(secretName: String): String = ENTRY_PREFIX + "_" + secretName
}

/** A decrypted secret. `toString()` never shows it; call [reveal] at the single place that stores it. */
public class SecretText(private val value: String) {
    public fun reveal(): String = value
    public val length: Int get() = value.length
    override fun toString(): String = "SecretText(***)"
    override fun equals(other: Any?): Boolean = other is SecretText && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/** Why a name could not be read. Only these names reach journals and reports, never exception text. */
public enum class SecretFailure {
    /** Markers name a cipher v11 removed (v9 data that skipped 10.x). The Flutter app could not read it either. */
    RemovedCipher,
    /** Markers name the Keystore-AES (biometric) key cipher; this app never used it and it needs a prompt. */
    KeystoreAesKeyCipher,
    /** Markers hold a value no plugin version writes. */
    UnknownMarkers,
    /** Data present but no wrapped key in `FlutterSecureKeyStorage.xml`. */
    NoWrappedKey,
    WrappedKeyBadBase64,
    /** The Keystore has no RSA private key under the alias (app data restored onto another device, or wiped). */
    KeystoreAliasMissing,
    /** The Keystore refused the unwrap (key invalidated, wrong key, OEM failure). */
    UnwrapFailed,
    /** Unwrapped key is not 16 bytes. */
    WrongKeyLength,
    /** The stored value is not a string. */
    NotAString,
    BadBase64,
    /** Shorter than IV + tag. */
    TooShort,
    /** GCM tag check failed (wrong key or tampered/corrupt ciphertext). */
    AuthFailed,
    NotUtf8,
}

public sealed interface FlutterSecret {
    /** The name Dart used (`pw.<id>`, `oc.codexToken.<id>`, …), i.e. the entry name without the prefix. */
    public val name: String

    public data class Plain(override val name: String, val value: SecretText) : FlutterSecret
    public data class Failed(override val name: String, val reason: SecretFailure) : FlutterSecret
}

/** What the marker files say. */
public enum class MarkerState { Default, Missing, Removed, KeystoreAes, Unknown }

/** Everything about the store except the secrets themselves. Safe to journal. */
public data class FssCensus(
    val dataFileEntries: Int,
    val secretEntries: Int,
    val backupEntries: Int,
    /** Keys without the entry prefix (Tink keysets, plugin bookkeeping): counted, ignored. */
    val foreignEntries: Int,
    val tinkKeysetPresent: Boolean,
    val wrappedKeyPresent: Boolean,
    val markers: MarkerState,
    /** Which config file supplied the markers: "namespaced", "legacy", "mixed" or null. */
    val markerSource: String?,
)

public data class FssReadResult(val census: FssCensus, val secrets: List<FlutterSecret>) {
    val plain: List<FlutterSecret.Plain> get() = secrets.filterIsInstance<FlutterSecret.Plain>()
    val failed: List<FlutterSecret.Failed> get() = secrets.filterIsInstance<FlutterSecret.Failed>()
}

/**
 * The Android Keystore step, kept behind an interface so JVM tests use a software RSA key.
 *
 * Production (`:android:migration`): load `AndroidKeyStore`, take the PrivateKey under
 * [FssFormat.keystoreAlias] (never create it), then
 * `Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")`, `init(UNWRAP_MODE, key,
 * OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))`,
 * `unwrap(wrapped, "AES", Cipher.SECRET_KEY)`. See docs/native/slice0/P5-readers.md §4.
 */
public fun interface FssKeyUnwrapper {
    /** Throw [KeystoreAliasMissingException] when the alias is absent; any other exception means "unwrap failed". */
    public fun unwrap(wrappedKey: ByteArray): SecretKey
}

public class KeystoreAliasMissingException : Exception("keystore alias missing")

/** The four preference stores the plugin reads, as raw maps (from file copies or `getSharedPreferences().all`). */
public class FssStores(
    public val data: Map<String, RawPref>,
    public val keyStorage: Map<String, RawPref>,
    public val configNamespaced: Map<String, RawPref>,
    public val configLegacy: Map<String, RawPref>,
) {
    public companion object {
        /** Read the authoritative files (`.bak` first) from a `shared_prefs` directory or a byte backup of it. */
        public fun fromDirectory(sharedPrefsDir: File): FssStores {
            fun read(name: String) = SharedPrefsFile(sharedPrefsDir, name).read()?.androidMap ?: emptyMap()
            return FssStores(
                data = read(FssFormat.DATA_FILE),
                keyStorage = read(FssFormat.KEY_FILE),
                configNamespaced = read(FssFormat.CONFIG_FILE_NAMESPACED),
                configLegacy = read(FssFormat.CONFIG_FILE_LEGACY),
            )
        }
    }
}

/**
 * Reads and decrypts every secret. Never throws, never writes, never deletes (the plugin's `resetOnError`
 * behaviour is deliberately absent): every name gets its own result.
 *
 * Behaviour that matches the v11 plugin (and so the Flutter app on the phone today):
 * - entry names are those starting with `ENTRY_PREFIX + "_"`; the plugin's `readAll` uses `contains` +
 *   `replaceFirst`, which gives the same names for real data; `_BACKUP` copies are skipped;
 * - markers are read from the namespaced config file first, then the legacy one, per key;
 * - missing markers: `StorageCipherFactory` falls back to RSA-OAEP + GCM and the plugin *tries* to decrypt, so
 *   this reader tries too (the GCM tag makes a wrong guess fail safely as [SecretFailure.AuthFailed]);
 * - removed (v9) markers: v11 cannot read the data, so neither do we: every name is [SecretFailure.RemovedCipher].
 */
public class FlutterSecureStorageReader(private val unwrapper: FssKeyUnwrapper) {

    public fun read(stores: FssStores): FssReadResult {
        val prefixed = FssFormat.ENTRY_PREFIX + "_"
        val dataKeys = stores.data.keys
        val backupCount = dataKeys.count { it.startsWith(prefixed) && it.endsWith(FssFormat.BACKUP_SUFFIX) }
        val names = dataKeys
            .filter { it.startsWith(prefixed) && !it.endsWith(FssFormat.BACKUP_SUFFIX) }
            .map { it.substring(prefixed.length) }
            .sorted()
        val foreign = dataKeys.count { !it.startsWith(prefixed) }
        val tink = dataKeys.any { it in FssFormat.TINK_KEYSET_KEYS }

        val (keyMarker, keySrc) = marker(stores, FssFormat.MARKER_KEY_ALGORITHM)
        val (storageMarker, storageSrc) = marker(stores, FssFormat.MARKER_STORAGE_ALGORITHM)
        val markerState = markerState(keyMarker, storageMarker)
        val markerSource = listOfNotNull(keySrc, storageSrc).distinct().let {
            when (it.size) { 0 -> null; 1 -> it[0]; else -> "mixed" }
        }
        val wrappedRaw = stores.keyStorage[FssFormat.WRAPPED_KEY_ENTRY]
        val census = FssCensus(
            dataFileEntries = dataKeys.size,
            secretEntries = names.size,
            backupEntries = backupCount,
            foreignEntries = foreign,
            tinkKeysetPresent = tink,
            wrappedKeyPresent = wrappedRaw is RawPref.Str,
            markers = markerState,
            markerSource = markerSource,
        )
        if (names.isEmpty()) return FssReadResult(census, emptyList())

        fun all(reason: SecretFailure) = FssReadResult(census, names.map { FlutterSecret.Failed(it, reason) })
        when (markerState) {
            MarkerState.Removed -> return all(SecretFailure.RemovedCipher)
            MarkerState.KeystoreAes -> return all(SecretFailure.KeystoreAesKeyCipher)
            MarkerState.Unknown -> return all(SecretFailure.UnknownMarkers)
            MarkerState.Default, MarkerState.Missing -> Unit
        }
        val wrappedText = (wrappedRaw as? RawPref.Str)?.value ?: return all(SecretFailure.NoWrappedKey)
        val wrapped = try {
            FlutterPrefsCodec.decodeAndroidBase64(wrappedText)
        } catch (e: IllegalArgumentException) {
            return all(SecretFailure.WrappedKeyBadBase64)
        }
        val key = try {
            unwrapper.unwrap(wrapped)
        } catch (e: KeystoreAliasMissingException) {
            return all(SecretFailure.KeystoreAliasMissing)
        } catch (e: Exception) {
            return all(SecretFailure.UnwrapFailed)
        }
        if (key.encoded == null || key.encoded.size != FssFormat.AES_KEY_BYTES) return all(SecretFailure.WrongKeyLength)
        return FssReadResult(census, names.map { decryptOne(it, stores.data[prefixed + it], key) })
    }

    private fun marker(stores: FssStores, key: String): Pair<String?, String?> {
        (stores.configNamespaced[key] as? RawPref.Str)?.let { return it.value to "namespaced" }
        (stores.configLegacy[key] as? RawPref.Str)?.let { return it.value to "legacy" }
        return null to null
    }

    private fun markerState(keyAlg: String?, storageAlg: String?): MarkerState {
        // StorageCipherFactory: if either marker is missing, both fall back to the defaults.
        if (keyAlg == null || storageAlg == null) return MarkerState.Missing
        if (keyAlg == FssFormat.REMOVED_KEY_ALGORITHM || storageAlg == FssFormat.REMOVED_STORAGE_ALGORITHM) return MarkerState.Removed
        if (keyAlg in FssFormat.KEYSTORE_AES_KEY_ALGORITHMS) return MarkerState.KeystoreAes
        val storageOk = storageAlg == FssFormat.STORAGE_ALGORITHM_GCM || storageAlg == "AES_GCM_NoPadding_BIOMETRIC"
        if (keyAlg == FssFormat.KEY_ALGORITHM_RSA_OAEP && storageOk) return MarkerState.Default
        return MarkerState.Unknown
    }

    private fun decryptOne(name: String, raw: RawPref?, key: SecretKey): FlutterSecret {
        val text = (raw as? RawPref.Str)?.value ?: return FlutterSecret.Failed(name, SecretFailure.NotAString)
        val bytes = try {
            FlutterPrefsCodec.decodeAndroidBase64(text)
        } catch (e: IllegalArgumentException) {
            return FlutterSecret.Failed(name, SecretFailure.BadBase64)
        }
        if (bytes.size < FssFormat.IV_BYTES + FssFormat.TAG_BITS / 8) return FlutterSecret.Failed(name, SecretFailure.TooShort)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(FssFormat.TAG_BITS, bytes, 0, FssFormat.IV_BYTES))
                doFinal(bytes, FssFormat.IV_BYTES, bytes.size - FssFormat.IV_BYTES)
            }
        } catch (e: Exception) {
            return FlutterSecret.Failed(name, SecretFailure.AuthFailed)
        }
        val value = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plain)).toString()
        } catch (e: CharacterCodingException) {
            return FlutterSecret.Failed(name, SecretFailure.NotUtf8)
        } finally {
            plain.fill(0)
        }
        return FlutterSecret.Plain(name, SecretText(value))
    }
}
