package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin

/*
 * Pairing ports (S2-SERVERS, trimmed at the P12 integration: the editor, server page and switcher ports were not taken
 * because the current Servers screen stays). Nothing here is copy, Compose or a platform type (rule 3).
 */

/**
 * The trust and address rules the screens need. Production binds it to `:core:security` `AddressRules` (a feature may
 * not import `:core:security`, K1); tests bind the same object, so the rows run against the real rules.
 */
public interface AddressPolicy {
    /** What the person typed → origin. A bare authority gains https (loopback: http). Refusals are Failure values. */
    public fun normalize(typed: String): Outcome<ServerOrigin>

    /** An absolute URL → its trust origin. Refuses credentials, a query, a fragment and a non-HTTP scheme. */
    public fun origin(url: String): Outcome<ServerOrigin>

    /** Plain HTTP anywhere but this device needs the person's explicit, per-origin choice (plan 04 §4.3). */
    public fun needsCleartextConsent(origin: ServerOrigin): Boolean

    public fun isLoopback(host: String): Boolean

    public fun isPrivateNetwork(host: String): Boolean

    public fun isTailnet(host: String): Boolean

    /** A pin needs HTTPS and 64 hex digits (colons allowed); returns the lower-case hex. */
    public fun checkPin(origin: ServerOrigin, fingerprint: String): Outcome<String>
}

/** A password or token held by the editor. It never prints (B-SEC-107, plan 16). */
public class SecretText(private val value: String) {
    public fun reveal(): String = value

    public val isEmpty: Boolean get() = value.isEmpty()

    override fun toString(): String = "SecretText(<redacted>)"

    override fun equals(other: Any?): Boolean = other is SecretText && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/** Where a test or a connect goes. [consentedOrigin] is non-null only after the person chose plain HTTP for it. */
public data class ProbeTarget(
    val origin: ServerOrigin,
    val backend: BackendKind,
    val username: String?,
    val password: SecretText?,
    val pinnedSha256: String?,
    val consentedOrigin: ServerOrigin?,
)

/** The server answered as a server this app can use. */
public data class ProbeAnswer(val serverVersion: String?)
