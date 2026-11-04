package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.SetupInspection
import io.github.eslamasabry.oc.core.domain.SetupMcpState
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flutter setup_config_adapter.dart: GET /config is a list of layered sources, never an effective
 * merge.
 */
internal object Oc2SetupInspection {
    suspend fun read(gateway: Oc2Gateway, redactor: Redactor): Outcome<SetupInspection> {
        val location = gateway.location.value
        val query =
            mapOf(
                "location[directory]" to location.directory,
                "location[workspace]" to location.workspace,
            )
        val config =
            when (val read = gateway.json("/config", query = query)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        redactor.registerCredentialValues(config)
        val sources =
            ((config as? JsonObject)?.get("data") ?: config) as? JsonArray
                ?: return Outcome.Failed(Failure.UnreadableReply())
        val kinds = sources.map { source ->
            val kind =
                ((source as? JsonObject)?.get("type") as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content ?: return Outcome.Failed(Failure.UnreadableReply())
            redactor.redact(kind).take(80)
        }
        val mcp =
            when (val read = gateway.json("/mcp", query = query)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        val rows =
            ((mcp as? JsonObject)?.get("data") ?: mcp) as? JsonArray
                ?: return Outcome.Failed(Failure.UnreadableReply())
        val known = setOf("connected", "pending", "disabled", "failed", "needs_auth")
        val servers = rows.map { raw ->
            val row = raw as? JsonObject ?: return Outcome.Failed(Failure.UnreadableReply())
            val name =
                (row["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return Outcome.Failed(Failure.UnreadableReply())
            val status = ((row["status"] as? JsonObject)?.get("status") as? JsonPrimitive)?.content
            SetupMcpState(
                redactor.redact(name).take(160),
                status?.takeIf { it in known } ?: "unknown",
            )
        }
        return Outcome.Ok(SetupInspection(kinds.toImmutableList(), servers.toImmutableList()))
    }
}
