package io.github.eslamasabry.oc.android.migration.android

import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.FssKeyUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.secure.KeystoreAliasMissingException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Unwraps `flutter_secure_storage` 11.2.0's AES key with the plugin's own RSA key (plan 05 §7.5, P5 §4): the same
 * transformation, provider and OAEP parameters as `KeyCipherImplementationRSAOAEP`. NEVER creates the alias (a new key
 * could never decrypt the old data) and never instantiates the plugin classes.
 */
internal class AndroidKeystoreUnwrapper(private val packageName: String) : FssKeyUnwrapper {
    override fun unwrap(wrappedKey: ByteArray): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val priv = ks.getKey(FssFormat.keystoreAlias(packageName), null) as? PrivateKey ?: throw KeystoreAliasMissingException()
        return Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround").run {
            init(Cipher.UNWRAP_MODE, priv, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
            unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) as SecretKey
        }
    }
}
