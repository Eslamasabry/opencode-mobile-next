package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.TransientLabel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The probe's narrow allowlist projection (port of `AgentAuthProbeResult.fromJson`, lib/domain/agent_auth_probe.dart).
 * Only `state`, `accountDisplayName` and `error` cross the bridge; unknown, malformed or contradictory replies are
 * errors (`InvalidResponse`), never a guessed signed-out state. `:android:agenthost` decodes its reply with this.
 */
public object AuthProjectionJson {
    private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
    private const val ACCOUNT_LIMIT = 160

    public fun decode(json: JsonObject): AuthProjection {
        val state = when (json.text("state")) {
            "signedIn" -> AuthState.SignedIn
            "signedOut" -> AuthState.SignedOut
            "error" -> AuthState.Error
            else -> return failed(AuthProbeError.InvalidResponse)
        }
        if (state == AuthState.Error) {
            return failed(ERRORS[json.text("error")] ?: AuthProbeError.InvalidResponse)
        }
        val account = json["accountDisplayName"].orNull()
        if (json["error"].orNull() != null || (state == AuthState.SignedOut && account != null)) {
            return failed(AuthProbeError.InvalidResponse)
        }
        if (account != null) {
            val text = (account as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (text == null || text.isBlank() || text.length > ACCOUNT_LIMIT || CONTROL.containsMatchIn(text)) {
                return failed(AuthProbeError.InvalidResponse)
            }
            return AuthProjection(state, TransientLabel(text))
        }
        return AuthProjection(state)
    }

    public fun failed(error: AuthProbeError): AuthProjection = AuthProjection(AuthState.Error, null, error)

    private val ERRORS: Map<String, AuthProbeError> = mapOf(
        "probeUnsupported" to AuthProbeError.ProbeUnsupported,
        "invalidResponse" to AuthProbeError.InvalidResponse,
        "timedOut" to AuthProbeError.TimedOut,
        "hostUnavailable" to AuthProbeError.HostUnavailable,
        "notInstalled" to AuthProbeError.NotInstalled,
        "invalidContext" to AuthProbeError.InvalidContext,
        "signInExpired" to AuthProbeError.SignInExpired,
        "signOutFailed" to AuthProbeError.SignOutFailed,
    )

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this

    private fun JsonObject.text(key: String): String? =
        (get(key).orNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
}
