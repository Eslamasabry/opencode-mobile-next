package io.github.eslamasabry.oc.android.migration.readers.secure

import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** The exact OAEP parameters of `KeyCipherImplementationRSAOAEP.getAlgorithmParameterSpec()` (MGF1 uses SHA-1). */
internal fun fssOaepSpec(): OAEPParameterSpec =
    OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

/**
 * JVM stand-in for the Android Keystore unwrap: the same transformation and parameters, with a software
 * RSA private key. The instrumented test on an emulator swaps this for the real `AndroidKeyStore` key.
 */
internal class SoftwareRsaOaepUnwrapper(private val privateKey: PrivateKey?) : FssKeyUnwrapper {
    override fun unwrap(wrappedKey: ByteArray): SecretKey {
        val key = privateKey ?: throw KeystoreAliasMissingException()
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.UNWRAP_MODE, key, fssOaepSpec())
        return cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) as SecretKey
    }
}

/**
 * A JVM port of the plugin's write path (`StorageCipherImplementationGCM` constructor + `encrypt`,
 * `FlutterSecureStorage.writeUnsafe`, `StorageCipherFactory.storeCurrentAlgorithms`), used to build fixtures:
 *
 * 1. a random 16-byte AES key, wrapped with the RSA public key (OAEP SHA-256 / MGF1 SHA-1), stored as
 *    `Base64.DEFAULT` under [FssFormat.WRAPPED_KEY_ENTRY] in `FlutterSecureKeyStorage.xml`;
 * 2. each value: `IV(12 random) ‖ AES-GCM(UTF-8 value) ‖ tag(16)`, `Base64.DEFAULT`, under
 *    `ENTRY_PREFIX + "_" + name` in `FlutterSecureStorage.xml` (no AAD);
 * 3. markers in `FlutterSecureStorageConfiguration:FlutterSecureStorage.xml`.
 */
internal class FssFixtureWriter(
    private val publicKey: PublicKey,
    private val random: SecureRandom,
    aesKey: ByteArray? = null,
) {
    val aesKey: ByteArray = aesKey ?: ByteArray(FssFormat.AES_KEY_BYTES).also { random.nextBytes(it) }
    private val secretKey: Key = SecretKeySpec(this.aesKey, "AES")

    fun wrappedKeyBase64(): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.WRAP_MODE, publicKey, fssOaepSpec(), random)
        return FlutterPrefsCodec.encodeAndroidBase64Default(cipher.wrap(secretKey))
    }

    /** `encrypt(value.getBytes(UTF_8))` then `Base64.encodeToString(result, 0)`. */
    fun encryptValue(value: String): String = encryptBytes(value.toByteArray(Charsets.UTF_8))

    fun encryptBytes(plain: ByteArray): String {
        val iv = ByteArray(FssFormat.IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(FssFormat.TAG_BITS, iv))
        return FlutterPrefsCodec.encodeAndroidBase64Default(iv + cipher.doFinal(plain))
    }

    enum class Markers { Default, None, LegacyFileOnly, Removed }

    class Files(
        val data: LinkedHashMap<String, RawPref>,
        val keyStorage: LinkedHashMap<String, RawPref>,
        val configNamespaced: LinkedHashMap<String, RawPref>,
        val configLegacy: LinkedHashMap<String, RawPref>,
    ) {
        fun stores(): FssStores = FssStores(data, keyStorage, configNamespaced, configLegacy)

        /** File name (without `.xml`) → XML bytes, for writing into a fixture `shared_prefs/` dir. Empty stores are omitted. */
        fun xmlFiles(): Map<String, ByteArray> = buildMap {
            if (data.isNotEmpty()) put(FssFormat.DATA_FILE, AndroidPrefsXmlWriter.write(data))
            if (keyStorage.isNotEmpty()) put(FssFormat.KEY_FILE, AndroidPrefsXmlWriter.write(keyStorage))
            if (configNamespaced.isNotEmpty()) put(FssFormat.CONFIG_FILE_NAMESPACED, AndroidPrefsXmlWriter.write(configNamespaced))
            if (configLegacy.isNotEmpty()) put(FssFormat.CONFIG_FILE_LEGACY, AndroidPrefsXmlWriter.write(configLegacy))
        }
    }

    fun write(secrets: Map<String, String>, markers: Markers = Markers.Default, backupCopies: Boolean = false): Files {
        val data = LinkedHashMap<String, RawPref>()
        for ((name, value) in secrets) {
            data[FssFormat.entryName(name)] = RawPref.Str(encryptValue(value))
            if (backupCopies) data[FssFormat.entryName(name) + FssFormat.BACKUP_SUFFIX] = RawPref.Str(encryptValue("backup-$value"))
        }
        val keyStorage = linkedMapOf<String, RawPref>(FssFormat.WRAPPED_KEY_ENTRY to RawPref.Str(wrappedKeyBase64()))
        val ns = LinkedHashMap<String, RawPref>()
        val legacy = LinkedHashMap<String, RawPref>()
        when (markers) {
            Markers.Default -> {
                ns[FssFormat.MARKER_KEY_ALGORITHM] = RawPref.Str(FssFormat.KEY_ALGORITHM_RSA_OAEP)
                ns[FssFormat.MARKER_STORAGE_ALGORITHM] = RawPref.Str(FssFormat.STORAGE_ALGORITHM_GCM)
            }
            Markers.LegacyFileOnly -> {
                legacy[FssFormat.MARKER_KEY_ALGORITHM] = RawPref.Str(FssFormat.KEY_ALGORITHM_RSA_OAEP)
                legacy[FssFormat.MARKER_STORAGE_ALGORITHM] = RawPref.Str(FssFormat.STORAGE_ALGORITHM_GCM)
            }
            Markers.Removed -> {
                legacy[FssFormat.MARKER_KEY_ALGORITHM] = RawPref.Str(FssFormat.REMOVED_KEY_ALGORITHM)
                legacy[FssFormat.MARKER_STORAGE_ALGORITHM] = RawPref.Str(FssFormat.REMOVED_STORAGE_ALGORITHM)
            }
            Markers.None -> Unit
        }
        return Files(data, keyStorage, ns, legacy)
    }
}
