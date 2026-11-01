package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OpenCode 1 is recognition-only (ADR §5.2 :protocol:oc2). Serial names are the Dart profile JSON values. */
@Serializable
public enum class BackendKind {
    @SerialName("openCode") OpenCode2,
    @SerialName("paseo") Paseo,
    @SerialName("codex") Codex,
}

/** Canonical origin: scheme + lower-case host + effective port. Never a path (B-SEC-153). */
@Serializable
public data class ServerOrigin(val scheme: String, val host: String, val port: Int) {
    /** `http://[::1]:4096` form, identical to Dart `serverOrigin()`. */
    public override fun toString(): String = "$scheme://${if (':' in host) "[$host]" else host}:$port"
}

@Serializable public data class ProfileTrust(val pinnedLeafSha256: String?, val cleartextConsentAtMs: Long?)

/**
 * Connection-facing profile shape (plan 03 §10.3). Audit 03 MAJOR 5: this is NOT the persisted profile DTO. The
 * complete persisted DTO (codexDirectory, requiresOpenCode2Migration, openCode2HistoryPending, serverVersion,
 * orchestration metadata, field order for fingerprints) belongs to S1-DATA / the profiles engine and is frozen in
 * contracts/s1.md §Profiles by its owner.
 */
@Serializable
public data class ServerProfile(
    val id: ProfileId,
    val name: String,
    val backend: BackendKind,
    val origin: ServerOrigin,
    val trust: ProfileTrust,
    val managedOnPhone: Boolean,
    val username: String?, // the password lives in SecretStore only
)

/** The directory + workspace a gateway is pinned to (Dart `setLocation`). */
public data class ServerLocation(val directory: String? = null, val workspace: String? = null) {
    public companion object {
        public val Default: ServerLocation = ServerLocation()
    }
}

/** Where an event came from (Dart EventEnvelope directory/project/workspace). */
public data class EventLocation(val directory: String?, val project: String?, val workspace: String?)
