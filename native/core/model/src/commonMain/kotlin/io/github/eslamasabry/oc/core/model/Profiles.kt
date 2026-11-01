package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.serialization.Serializable

/** OpenCode 1 is recognition-only (ADR §5.2 :protocol:oc2). */
public enum class BackendKind { OpenCode2, Paseo, Codex }

@Serializable public data class ServerOrigin(val scheme: String, val host: String, val port: Int)

@Serializable public data class ProfileTrust(val pinnedLeafSha256: String?, val cleartextConsentAtMs: Long?)

/**
 * Slice-0 skeleton shape (plan 03 §10.3). Audit MAJOR 5: this is NOT the persisted profile DTO. The complete
 * persisted DTO (codexDirectory, requiresOpenCode2Migration, openCode2HistoryPending, serverVersion, orchestration
 * metadata, field order for fingerprints) is frozen separately in contracts/slice-1.md before :engine:profiles starts.
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

public enum class StreamStatus { Connecting, Connected, Reconnecting, Disconnected }

@Serializable public data class Session(val id: SessionId, val title: String, val updatedAtMs: Long)
