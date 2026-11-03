package io.github.eslamasabry.oc.engine.integrations.host

import kotlin.coroutines.cancellation.CancellationException

/**
 * The Keystore signer (Flutter `ByoHostSigner`; Android half `oc/byo_host_signer`). Public metadata only: the private
 * key stays in the Android Keystore, and there is intentionally no export or raw-sign method. Implementations may
 * throw; callers treat any throw as a failure and keep none of its text.
 */
public interface ByoHostSigner {
    public suspend fun ensureIdentity(profileId: String): ByoHostIdentity

    public suspend fun openAgent(profileId: String, socketPath: String, user: String)

    public suspend fun closeAgent(profileId: String)

    public suspend fun deleteIdentity(profileId: String)

    public suspend fun deleteAllIdentities()
}

/** The raw platform channel: method name + string arguments → an optional string map. May throw anything. */
public fun interface SignerChannel {
    public suspend fun invoke(method: String, arguments: Map<String, String>): Map<String, String>?
}

/** The only failure the signer bridge reports: a fixed code, never platform detail. */
public class ByoHostSignerException(public val code: String) : Exception() {
    override val message: String get() = toString()

    override fun toString(): String = "ByoHostSignerException($code)"
}

/**
 * Owns the Dart side of `oc/byo_host_signer` (Flutter `AndroidByoHostSigner`). Unsafe aliases, users and socket paths
 * never reach native; platform errors become `unavailable`; an unexpected alias or key shape is `protocol`.
 */
public class ChannelByoHostSigner(private val channel: SignerChannel) : ByoHostSigner {
    private fun validateId(id: String) {
        if (!SAFE.matches(id)) throw ByoHostSignerException("invalid")
    }

    private suspend fun invoke(method: String, arguments: Map<String, String>): Map<String, String>? =
        try {
            channel.invoke(method, arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            throw ByoHostSignerException("unavailable")
        }

    override suspend fun ensureIdentity(profileId: String): ByoHostIdentity {
        validateId(profileId)
        val result = invoke("ensureIdentity", mapOf("profileId" to profileId))
        val alias = result?.get("keyAlias")
        val publicKey = result?.get("publicKey")
        if (alias != "oc.byoHostSsh.$profileId" || publicKey == null || !ECDSA.matches(publicKey)) {
            throw ByoHostSignerException("protocol")
        }
        return ByoHostIdentity(alias, publicKey)
    }

    override suspend fun openAgent(profileId: String, socketPath: String, user: String) {
        validateId(profileId)
        if (!USER.matches(user) || !SOCKET.matches(socketPath) || "/../" in socketPath || "/./" in socketPath) {
            throw ByoHostSignerException("invalid")
        }
        invoke("openAgent", mapOf("profileId" to profileId, "socketPath" to socketPath, "user" to user))
    }

    override suspend fun closeAgent(profileId: String) {
        validateId(profileId)
        invoke("closeAgent", mapOf("profileId" to profileId))
    }

    override suspend fun deleteAllIdentities() {
        invoke("deleteAllIdentities", emptyMap())
    }

    override suspend fun deleteIdentity(profileId: String) {
        validateId(profileId)
        invoke("deleteIdentity", mapOf("profileId" to profileId))
    }

    public companion object {
        private val SAFE = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val USER = Regex("^[A-Za-z_][A-Za-z0-9_.-]{0,63}$")
        private val SOCKET = Regex("^/.*/linux/ubuntu/tmp/\\.oa-[a-f0-9]{16}/s$")
        internal val ECDSA = Regex("^ecdsa-sha2-nistp256 [A-Za-z0-9+/]+={0,2}$")
    }
}
