package io.github.eslamasabry.oc.engine.integrations.host

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/** Flutter `byoHostSafeId`: profile and device ids that may name keys, aliases and private folders. */
public fun byoHostSafeId(value: String): Boolean = SAFE_ID.matches(value)

private val SAFE_ID = Regex("^[a-zA-Z0-9_-]{1,64}$")

/** Flutter `ByoHostPhase`. */
public enum class ByoHostPhase { Idle, Checking, NeedsTrust, Installing, Connecting, Ready, Disconnected, Removing, Failed, Removed }

/** Flutter `ByoHostFailureCode`: the only thing a host failure carries. No shell, DNS, plugin or server text. */
public enum class ByoHostFailureCode {
    Disabled, InvalidTarget, TailnetRequired, Unavailable, NeedsTrust, HostKeyChanged, Authentication,
    BundleUnavailable, Checksum, LingerRequired, SshPolicyRequired, UnsupportedHost, Transport, Protocol,
    IdentityChanged, Storage, Busy, RevocationFailed, Uncertain,
    ;

    /** The flat core failure the UI words (one sentence per subtype in :ui:words). */
    public fun toFailure(): Failure =
        when (this) {
            Disabled, Unavailable, BundleUnavailable -> Failure.Unavailable(FeatureId("byoHost"))
            InvalidTarget -> Failure.Refused(RefusalReason.InvalidAddress)
            TailnetRequired -> Failure.Refused(RefusalReason.HostNotAllowedForBackend)
            Storage -> Failure.StorageRefused()
            Busy -> Failure.Busy()
            Transport -> Failure.Network(NetworkWhy.Unknown)
            else -> Failure.Refused(RefusalReason.ConsentMissing)
        }
}

/** The result of every public host operation. [Failed] carries a fixed code only, so its text can never leak. */
public sealed interface HostResult<out T> {
    public data class Ok<out T>(val value: T) : HostResult<T>

    public data class Failed(val code: ByoHostFailureCode) : HostResult<Nothing>
}

public fun <T> HostResult<T>.valueOrNull(): T? = (this as? HostResult.Ok)?.value

public fun HostResult<*>.codeOrNull(): ByoHostFailureCode? = (this as? HostResult.Failed)?.code

/** Internal abort: no message, no cause, so nothing a lower layer said can ride along. */
internal class HostAbort(val code: ByoHostFailureCode) : Exception() {
    override val message: String get() = "HostAbort(${code.name})"
}

internal fun abort(code: ByoHostFailureCode): Nothing = throw HostAbort(code)

/** Runs [block]; a [HostAbort] keeps its code, anything else becomes [fallback]. Cancellation is rethrown. */
internal suspend inline fun <T> hostResult(fallback: ByoHostFailureCode, block: () -> T): HostResult<T> =
    try {
        HostResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HostAbort) {
        HostResult.Failed(e.code)
    } catch (_: Throwable) {
        HostResult.Failed(fallback)
    }

/** Unwraps inside the engine: a failed result aborts with its code. */
internal fun <T> HostResult<T>.orAbort(): T =
    when (this) {
        is HostResult.Ok -> value
        is HostResult.Failed -> abort(code)
    }

/** Flutter `ByoHostTarget`: a validated user, host and port. No shell or SSH option can pass these rules. */
public class ByoHostTarget private constructor(public val user: String, public val host: String, public val port: Int) {
    override fun equals(other: Any?): Boolean =
        other is ByoHostTarget && other.user == user && other.host == host && other.port == port

    override fun hashCode(): Int = (user.hashCode() * 31 + host.hashCode()) * 31 + port

    override fun toString(): String = "ByoHostTarget(<redacted>)"

    public companion object {
        private val USER = Regex("^[a-zA-Z_][a-zA-Z0-9_.-]{0,63}$")
        private val HOST = Regex("^[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}$")

        public fun of(user: String, host: String, port: Int = 22): ByoHostTarget? =
            if (!USER.matches(user) || !HOST.matches(host) || ".." in host || port !in 1..65535) {
                null
            } else {
                ByoHostTarget(user, host, port)
            }

        /** `user@host`. */
        public fun parse(input: String, port: Int = 22): ByoHostTarget? {
            val parts = input.trim().split('@')
            return if (parts.size != 2) null else of(parts[0], parts[1], port)
        }
    }
}

/**
 * Flutter `ByoHostKey`: an Ed25519 SSH host key whose SHA256 fingerprint is computed from its wire encoding. A key is
 * never trusted because keyscan returned it; only an instance built by [parse] or [fromPublicKey] exists.
 */
public class ByoHostKey private constructor(public val publicKey: String, public val fingerprint: String) {
    override fun equals(other: Any?): Boolean =
        other is ByoHostKey && other.publicKey == publicKey && other.fingerprint == fingerprint

    override fun hashCode(): Int = publicKey.hashCode() * 31 + fingerprint.hashCode()

    override fun toString(): String = "ByoHostKey($fingerprint)"

    public companion object {
        private val PUBLIC = Regex("^ssh-ed25519 [A-Za-z0-9+/]+={0,2}$")
        private val FINGERPRINT = Regex("^SHA256:[A-Za-z0-9+/]{43}$")
        private val PREFIX = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".encodeToByteArray() + byteArrayOf(0, 0, 0, 32)

        /** A stored or confirmed pin: the fingerprint must be exactly the one computed from the key. */
        public fun parse(publicKey: String, fingerprint: String): ByoHostKey? {
            if (!PUBLIC.matches(publicKey) || !FINGERPRINT.matches(fingerprint)) return null
            val bytes = wire(publicKey.split(' ')[1]) ?: return null
            return if (fingerprintOf(bytes) == fingerprint) ByoHostKey(publicKey, fingerprint) else null
        }

        /** Flutter `keyFromPublicKey`: checks the wire representation and computes the actual fingerprint. */
        public fun fromPublicKey(publicKey: String): ByoHostKey? {
            val parts = publicKey.trim().split(Regex("\\s+"))
            if (parts.size < 2 || parts[0] != "ssh-ed25519") return null
            val bytes = wire(parts[1]) ?: return null
            return ByoHostKey("ssh-ed25519 ${Base64.encode(bytes)}", fingerprintOf(bytes))
        }

        private fun wire(base64: String): ByteArray? {
            val bytes =
                try {
                    Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(base64)
                } catch (_: IllegalArgumentException) {
                    return null
                }
            if (bytes.size != 51) return null
            for (i in PREFIX.indices) if (bytes[i] != PREFIX[i]) return null
            return bytes
        }

        internal fun fingerprintOf(bytes: ByteArray): String =
            "SHA256:" + Base64.encode(hexBytes(DartJson.sha256Hex(bytes))).replace("=", "")
    }
}

internal fun hexBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** Initial login only (Flutter `ByoHostLogin`). Never serialized or stored; [consume] clears every field. */
public class ByoHostLogin(
    public var privateKey: String? = null,
    public var password: String? = null,
    public var passphrase: String? = null,
) {
    public fun consume() {
        privateKey = null
        password = null
        passphrase = null
    }

    override fun toString(): String = "ByoHostLogin(<redacted>)"
}

/** Public metadata of the Keystore identity: its alias and public key. There is no private key here, ever. */
public data class ByoHostIdentity(val keyAlias: String, val publicKey: String) {
    override fun toString(): String = "ByoHostIdentity(<redacted>)"
}

/** One reviewed artifact of the setup bundle: a plain https URL and its full-archive SHA-256. */
public class ByoHostArtifact private constructor(public val url: String, public val sha256: String) {
    public companion object {
        private val SHA = Regex("^[a-f0-9]{64}$")
        private val HTTPS = Regex("^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/%-]*)?$")

        public fun of(url: String, sha256: String): ByoHostArtifact? =
            if (HTTPS.matches(url) && SHA.matches(sha256)) ByoHostArtifact(url, sha256) else null
    }
}

/** Every architecture has a reviewed full-bundle digest; no mutable sidecar trust. */
public class ByoHostBundle private constructor(
    public val version: String,
    public val openCodeVersion: String,
    public val artifacts: Map<String, ByoHostArtifact>,
) {
    public companion object {
        public fun of(version: String, openCodeVersion: String, artifacts: Map<String, ByoHostArtifact>): ByoHostBundle? =
            if (!SEMVER.matches(version) || !SEMVER.matches(openCodeVersion) || artifacts.isEmpty() ||
                artifacts.keys.any { it != "x64" && it != "arm64" }
            ) {
                null
            } else {
                ByoHostBundle(version, openCodeVersion, artifacts.toMap())
            }
    }
}

internal val SEMVER = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")

/** What the installed supervisor says about itself. */
public data class ByoHostDescriptor(val hostId: String, val bundleVersion: String, val openCodeVersion: String, val port: Int)

/** A local forwarding lease. */
public interface ByoHostTunnel {
    public val localPort: Int

    public suspend fun close()
}

/** Single secure envelope; metadata never contains login or device secrets. */
public class ByoHostSecrets(public val identity: ByoHostIdentity, public val deviceToken: String) {
    override fun toString(): String = "ByoHostSecrets(<redacted>)"
}

/** The durable, non-secret adoption journal of one profile. */
public data class ByoHostRecord(
    val profileId: String,
    val target: ByoHostTarget,
    val hostKey: ByoHostKey,
    val phase: ByoHostPhase,
    val hostId: String? = null,
    val bundleVersion: String? = null,
    val openCodeVersion: String? = null,
    val remotePort: Int? = null,
    val revoked: Boolean = false,
)

/** What the controller shows. */
public data class ByoHostSnapshot(
    val phase: ByoHostPhase,
    val record: ByoHostRecord? = null,
    val hostKey: ByoHostKey? = null,
    val failure: ByoHostFailureCode? = null,
)
