package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/*
 * Feature-local ports (S2-SERVERS). The profiles engine, the connection controller (S1-CONN) and `ServersRoute`
 * (:ui:routes) are not on the M0 branch yet, so this feature names the narrow surface it needs here and the app binds
 * it once those land (report: "catalog requests"). Nothing here is copy, Compose or a platform type (rule 3).
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

/** What the editor saves. Saving never connects without a successful test first (07 §5 E02). */
public data class ServerDraft(
    val editing: ProfileId?,
    val name: String,
    val backend: BackendKind,
    val origin: ServerOrigin,
    val username: String?,
    val password: SecretText?,
    val pinnedSha256: String?,
    val cleartextConsent: Boolean,
)

/** One certificate, read by a TLS handshake that writes no application bytes (plan 04 §4.5). */
public data class CertificateSummary(
    val sha256Hex: String,
    val subject: String,
    val issuer: String,
    val notBeforeMs: Long,
    val notAfterMs: Long,
)

public enum class ServerStatus { Connected, Connecting, NotConnected, Failed }

/** One saved server as the list, the switcher and the server page see it. */
public data class SavedServer(
    val profile: ServerProfile,
    val status: ServerStatus,
    val active: Boolean,
    val failure: io.github.eslamasabry.oc.core.common.Failure? = null,
    /** Failed attempts in a row against this origin (B-NAV-009). */
    val attempts: Int = 0,
    /** OpenCode inside the app (the built-in Linux), not a Termux server (B-NAV-009). */
    val inAppServer: Boolean = false,
)

/** The profiles and connection surface (S1-CONN + the profiles engine). Every command is a suspend Outcome (rule 4). */
public interface ServersPort {
    public val servers: StateFlow<ImmutableList<SavedServer>>

    public suspend fun probe(target: ProbeTarget): Outcome<ProbeAnswer>

    public suspend fun save(draft: ServerDraft): Outcome<ProfileId>

    public suspend fun connect(id: ProfileId): Outcome<Unit>

    public suspend fun disconnect(id: ProfileId)

    /** Forget = the profile sweep (`ProfileScope`), never only the row. */
    public suspend fun forget(id: ProfileId): Outcome<Unit>

    public suspend fun inspectCertificate(origin: ServerOrigin): Outcome<CertificateSummary>

    /** Replace the profile's pin after the person compared the fingerprint (B-NAV-008 "explicit review"). */
    public suspend fun trustCertificate(id: ProfileId, sha256Hex: String): Outcome<Unit>
}

/** How the editor opens (07 §2.2.2 row 31). */
public sealed interface EditorMode {
    public data object Add : EditorMode

    public data class Edit(val profileId: ProfileId) : EditorMode

    public data class Credentials(val profileId: ProfileId) : EditorMode
}

/**
 * The navigation this feature asks for. The Entries file adapts it to `AppNavigator` once `ServersRoute` exists in
 * `:ui:routes` (07 §2.4: connect or save success → `land()`; Disconnect → `resetTo(Servers)`).
 */
public interface ServersNavigator {
    public fun openEditor(mode: EditorMode, focusPassword: Boolean = false)

    public fun openServerPage(id: ProfileId)

    public fun land()

    public fun resetToServers()

    public fun back()
}
