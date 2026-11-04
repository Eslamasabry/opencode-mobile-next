package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.core.model.HostUpdateResult
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * Updating the computer's agent helper (`daemon.update.*`), a port of lib/paseo/gateway/host_update.dart and
 * lib/domain/host_daemon_update.dart (B-AGENT-167). Progress phases arrive as `daemon.update.progress` pushes that
 * repeat the request id without settling it; the answer is a result, never a thrown failure, when the daemon said
 * why it could not update. The daemon restarts itself right after it answers; the gateway's own reconnect brings
 * the connection back.
 */

// HostUpdatePhase and HostUpdateResult are the shared core.model types (AgentToolsGateway).

private val REASONS = Redactor()

private fun phaseOf(name: String?): HostUpdatePhase? =
    when (name) {
        "starting" -> HostUpdatePhase.Starting
        "downloading" -> HostUpdatePhase.Downloading
        "installing" -> HostUpdatePhase.Installing
        "complete" -> HostUpdatePhase.Complete
        else -> null
    }

private fun cleanVersion(value: JsonElement?): String? = value.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }

private val UNPRINTABLE = Regex("[\\x00-\\x08\\x0b-\\x1f\\x7f]")

private fun clipReason(error: String): String {
    val clean = error.replace(UNPRINTABLE, " ").trim()
    return if (clean.length > 600) clean.substring(0, 600) else clean
}

/** Installing downloads a package on the computer. */
public const val HOST_UPDATE_TIMEOUT_MS: Long = 10 * 60 * 1000L

/**
 * Asks the helper to update itself. [onProgress] gets each phase in order (on the gateway's lane); a lost link or a
 * folder change is a Failure, a refusal the daemon explained is `HostUpdateResult(success = false, reason)`.
 */
public suspend fun PaseoGateway.updateHostDaemon(onProgress: ((HostUpdatePhase) -> Unit)? = null): Outcome<HostUpdateResult> = laneCall {
    val before = transport.serverVersion
    val scope = scopeDir
    val epoch = locationEpoch
    val result: JsonObject = transport.request(
        "daemon.update.request",
        JsonObject(emptyMap()),
        mutation = true,
        timeoutMs = HOST_UPDATE_TIMEOUT_MS,
        answerErrors = true,
        beforeSend = { checkLocation(scope, epoch) },
        onProgress = { _, payload -> phaseOf(payload.str("phase"))?.let { onProgress?.invoke(it) } },
    )
    val success = result["success"].boolOrNull() == true
    HostUpdateResult(
        success = success,
        previousVersion = cleanVersion(result["previousVersion"]) ?: before,
        newVersion = cleanVersion(result["newVersion"]),
        reason = if (success) null else result.str("error")?.let { REASONS.details(clipReason(it)) },
    )
}
