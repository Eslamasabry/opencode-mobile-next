package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.io.encoding.Base64

// Port of lib/domain/byo_host.dart: backend-only Ubuntu host adoption. The UI uses the controller, never shell text.

/** The capability name used when the whole feature is off (Dart `byoHostBuildEnabled`, default false). */
public val ByoHostFeature: FeatureId = FeatureId("byoHost")

private val SafeId = Regex("[a-zA-Z0-9_-]{1,64}")
private val SemVer = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")

/** Dart `byoHostSafeId`: profile ids and host ids are 1–64 of `[a-zA-Z0-9_-]`. */
public fun byoHostSafeId(value: String): Boolean = SafeId.matches(value)

internal fun isSemVer(value: String): Boolean = SemVer.matches(value)

/** Dart `ByoHostPhase`, same names and order. */
public enum class ByoHostPhase {
    Idle, Checking, NeedsTrust, Installing, Connecting, Ready, Disconnected, Removing, Failed, Removed,
}

/**
 * Dart `ByoHostFailureCode`, the plain-word outcome of every BYO-host command. Each value has one sentence in
 * :ui:words (English copy: `ByoHostFailure.message` in lib/domain/byo_host.dart). [asFailure] is the core family the
 * command's [io.github.eslamasabry.oc.core.common.Outcome] carries; the exact code is in [ByoHostSnapshot.failure].
 */
public enum class ByoHostFailureCode {
    Disabled, InvalidTarget, TailnetRequired, Unavailable, NeedsTrust, HostKeyChanged, Authentication,
    BundleUnavailable, Checksum, LingerRequired, SshPolicyRequired, UnsupportedHost, Transport, Protocol,
    IdentityChanged, Storage, Busy, RevocationFailed, Uncertain,
    ;

    public fun asFailure(): Failure =
        when (this) {
            Disabled, Unavailable, BundleUnavailable, UnsupportedHost, TailnetRequired, LingerRequired,
            SshPolicyRequired,
            -> Failure.Unavailable(ByoHostFeature)
            InvalidTarget -> Failure.Refused(RefusalReason.InvalidAddress)
            NeedsTrust -> Failure.Certificate(CertificateWhy.Untrusted)
            HostKeyChanged -> Failure.Certificate(CertificateWhy.Changed)
            Authentication -> Failure.SignIn(SignInWhy.PasswordRejected)
            Checksum -> Failure.UnreadableReply()
            Protocol -> Failure.UnreadableReply()
            IdentityChanged -> Failure.Incompatible(IncompatibleKind.ProtocolMismatch)
            Storage -> Failure.StorageRefused()
            Busy -> Failure.Busy(BusyKind.SessionBusy)
            RevocationFailed -> Failure.Unknown()
            Uncertain -> Failure.Refused(RefusalReason.DispatchUncertain)
            Transport -> Failure.Network()
        }
}

/**
 * The only exception a [ByoHostSshRunner] or [ByoHostStore] adapter should throw for an expected problem. It carries a
 * code, never remote stderr or a response body. Any other throwable from a port is treated as
 * [ByoHostFailureCode.Transport].
 */
public class ByoHostError(public val code: ByoHostFailureCode) : Exception(code.name)

/** `user@host[:port]` SSH target (Dart `ByoHostTarget`); option-injection and shell metacharacters never parse. */
public class ByoHostTarget private constructor(
    public val user: String,
    public val host: String,
    public val port: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is ByoHostTarget && other.user == user && other.host == host && other.port == port

    override fun hashCode(): Int = (user.hashCode() * 31 + host.hashCode()) * 31 + port

    override fun toString(): String = "ByoHostTarget($user@$host:$port)"

    public companion object {
        private val User = Regex("[a-zA-Z_][a-zA-Z0-9_.-]{0,63}")
        private val Host = Regex("[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}")

        /** Null means [ByoHostFailureCode.InvalidTarget]. */
        public fun of(user: String, host: String, port: Int = 22): ByoHostTarget? =
            if (!User.matches(user) || !Host.matches(host) || ".." in host || port !in 1..65535) {
                null
            } else {
                ByoHostTarget(user, host, port)
            }

        /** Dart `ByoHostTarget.parse`: trims the form, then exactly one `@`. Null means invalid target. */
        public fun parse(input: String, port: Int = 22): ByoHostTarget? {
            val parts = input.trim().split('@')
            return if (parts.size != 2) null else of(parts[0], parts[1], port)
        }
    }
}

/** A verified ssh-ed25519 host key and its OpenSSH SHA256 fingerprint (Dart `ByoHostKey`). */
public class ByoHostKey private constructor(public val publicKey: String, public val fingerprint: String) {
    override fun equals(other: Any?): Boolean =
        other is ByoHostKey && other.publicKey == publicKey && other.fingerprint == fingerprint

    override fun hashCode(): Int = publicKey.hashCode() * 31 + fingerprint.hashCode()

    override fun toString(): String = "ByoHostKey($fingerprint)"

    public companion object {
        private val PublicKey = Regex("ssh-ed25519 [A-Za-z0-9+/]+={0,2}")
        private val Fingerprint = Regex("SHA256:[A-Za-z0-9+/]{43}")
        private val Prefix = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".encodeToByteArray() + byteArrayOf(0, 0, 0, 32)

        /** OpenSSH `SHA256:` fingerprint of a raw key blob (unpadded standard base64 of the digest). */
        public fun fingerprintOf(blob: ByteArray): String =
            "SHA256:" + Base64.encode(hexBytes(DartJson.sha256Hex(blob))).trimEnd('=')

        /** Null means [ByoHostFailureCode.NeedsTrust]: bad shape or key type, or a fingerprint that does not match. */
        public fun of(publicKey: String, fingerprint: String): ByoHostKey? {
            if (!PublicKey.matches(publicKey) || !Fingerprint.matches(fingerprint)) return null
            val blob =
                try {
                    Base64.decode(publicKey.substringAfter(' '))
                } catch (_: IllegalArgumentException) {
                    return null
                }
            if (blob.size != 51 || !Prefix.indices.all { blob[it] == Prefix[it] }) return null
            if (fingerprintOf(blob) != fingerprint) return null
            return ByoHostKey(publicKey, fingerprint)
        }
    }
}

internal fun hexBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** Initial admin login only. The password and passphrase are never serialized or stored, and are wiped after use. */
public class ByoHostLogin(
    privateKey: String? = null,
    password: String? = null,
    passphrase: String? = null,
) {
    public var privateKey: String? = privateKey
        private set
    public var password: String? = password
        private set
    public var passphrase: String? = passphrase
        private set

    public fun consume() {
        privateKey = null
        password = null
        passphrase = null
    }

    override fun toString(): String = "ByoHostLogin(<redacted>)"
}

/** The phone's restricted SSH identity: a Keystore alias and its public key (Dart `ByoHostIdentity`). */
public data class ByoHostIdentity(val keyAlias: String, val publicKey: String) {
    override fun toString(): String = "ByoHostIdentity(<redacted>)"
}

/** One reviewed full-bundle digest per architecture; no mutable sidecar trust (Dart `ByoHostArtifact`). */
public class ByoHostArtifact private constructor(public val url: String, public val sha256: String) {
    override fun equals(other: Any?): Boolean = other is ByoHostArtifact && other.url == url && other.sha256 == sha256

    override fun hashCode(): Int = url.hashCode() * 31 + sha256.hashCode()

    override fun toString(): String = "ByoHostArtifact($sha256)"

    public companion object {
        private val Digest = Regex("[a-f0-9]{64}")
        private val Https = Regex("https://([^/?#@]+)(/[^?#]*)?")

        /** Null means [ByoHostFailureCode.BundleUnavailable]: https only, a host, no user info, query or fragment. */
        public fun of(url: String, sha256: String): ByoHostArtifact? {
            val match = Https.matchEntire(url) ?: return null
            if (match.groupValues[1].isEmpty() || !Digest.matches(sha256)) return null
            return ByoHostArtifact(url, sha256)
        }
    }
}

/** A reviewed setup bundle (Dart `ByoHostBundle`); artifacts are keyed `x64` / `arm64` only. */
public class ByoHostBundle private constructor(
    public val version: String,
    public val openCodeVersion: String,
    public val artifacts: Map<String, ByoHostArtifact>,
) {
    public companion object {
        public val Architectures: Set<String> = setOf("x64", "arm64")

        /** Null means [ByoHostFailureCode.BundleUnavailable]. */
        public fun of(
            version: String,
            openCodeVersion: String,
            artifacts: Map<String, ByoHostArtifact>,
        ): ByoHostBundle? =
            if (!isSemVer(version) || !isSemVer(openCodeVersion) || artifacts.isEmpty() ||
                artifacts.keys.any { it !in Architectures }
            ) {
                null
            } else {
                ByoHostBundle(version, openCodeVersion, artifacts.toMap())
            }
    }
}

/** What the installed supervisor says about itself (Dart `ByoHostDescriptor`). */
public data class ByoHostDescriptor(
    val hostId: String,
    val bundleVersion: String,
    val openCodeVersion: String,
    val port: Int,
)

/** Single secure envelope; record metadata never contains login or device secrets (Dart `ByoHostSecrets`). */
public data class ByoHostSecrets(val identity: ByoHostIdentity, val deviceToken: String) {
    override fun toString(): String = "ByoHostSecrets(<redacted>)"
}

/** The durable, non-secret adoption journal for one phone profile (Dart `ByoHostRecord`). */
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

/** What the controller shows; [failure] is the exact code of the last failed command, if it changed the phase. */
public data class ByoHostSnapshot(
    val phase: ByoHostPhase,
    val record: ByoHostRecord? = null,
    val hostKey: ByoHostKey? = null,
    val failure: ByoHostFailureCode? = null,
)
