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
    /** Opaque, memory-only identity of the exact stored edit baseline; contains no credential. */
    val editToken: Any? = null,
    val reconnectOnSave: Boolean = false,
    /** This phone's own server (the OpenCode the app runs here): its address, sign-in and HTTPS advice are the app's. */
    val ownPhone: Boolean = false,
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
    /** Opaque, memory-only identity of the exact stored edit baseline; contains no credential. */
    val editToken: Any? = null,
) {
    override fun toString(): String =
        "ServerEditorInput(id=${id?.raw}, backend=$backend, keepSecret=$keepSecret)"
}

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
public enum class EditorTailscaleState {
    Installed,
    Missing,
    Unavailable,
}

/**
 * W4 (owner rule: no developer text): the phone's own server is edited by its name only. Its address
 * (`http://127.0.0.1:…`), the HTTPS advice, the password the app set when it started it and the `opencode` user name are
 * the app's own, so the page leaves them out; the password field comes back only when the server asks for it again.
 * NV: Flutter's editor shows every field for this server too.
 */
public object ServerEditorRules {
    public fun showsAddress(record: ServerEditorRecord?): Boolean = record == null || !record.ownPhone

    public fun showsPassword(record: ServerEditorRecord?): Boolean = record == null || !record.ownPhone || record.needsSecret

    public fun showsUsername(record: ServerEditorRecord?): Boolean = record == null || !record.ownPhone
}

public interface ServerEditorPort {
    public fun tailscaleState(): EditorTailscaleState

    public fun openTailscale(): Outcome<Unit>

    public fun installTailscale(): Outcome<Unit>

    public suspend fun read(id: ProfileId): Outcome<ServerEditorRecord>

    public suspend fun check(input: ServerEditorInput): Outcome<ServerEditorProbe>

    public suspend fun save(input: ServerEditorInput, version: String?): Outcome<ProfileId>

    public suspend fun inspect(address: String): Outcome<ServerCertificate>
}
