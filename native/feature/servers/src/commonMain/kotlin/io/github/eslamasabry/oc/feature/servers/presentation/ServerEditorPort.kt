package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind

/** The editor only reads metadata; a saved password never crosses this boundary. */
public data class ServerEditorRecord(
    val id: ProfileId,
    val name: String,
    val address: String,
    val username: String,
    val backend: BackendKind,
    val directory: String,
    val hasSecret: Boolean,
    val needsSecret: Boolean,
    val tailscale: Boolean,
    val cleartextOrigin: String?,
    val certificatePin: String?,
    val certificateOrigin: String?,
)

/**
 * Ephemeral command input. SecretText is redacted and this value never enters saved/observable
 * state.
 */
public data class ServerEditorInput(
    val id: ProfileId?,
    val name: String,
    val address: String,
    val username: String,
    val backend: BackendKind,
    val directory: String,
    val secret: SecretText?,
    val keepSecret: Boolean,
    val tailscale: Boolean,
    val cleartextOrigin: String?,
    val certificatePin: String?,
    val certificateOrigin: String?,
)

public data class ServerEditorProbe(val version: String?, val runtimes: List<String> = emptyList())

public data class ServerCertificate(
    val origin: String,
    val fingerprint: String,
    val validFrom: String,
    val validUntil: String,
)

/**
 * App-bound profile store and disposable authenticated probes; a probe never saves or starts a
 * conversation.
 */
public interface ServerEditorPort {
    public suspend fun read(id: ProfileId): Outcome<ServerEditorRecord>

    public suspend fun check(input: ServerEditorInput): Outcome<ServerEditorProbe>

    public suspend fun save(input: ServerEditorInput, version: String?): Outcome<ProfileId>

    public suspend fun inspect(address: String): Outcome<ServerCertificate>
}
