package io.github.eslamasabry.oc.engine.agents.diagnostics

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.agents.host.AgentHelperExit
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.HelperExitKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** The plain notice of a stopped helper, as a words key (English in the S3-AGENTSE report). */
public enum class HelperNotice {
    /** "The agent helper stopped unexpectedly. Restart it and try again." */
    StoppedUnexpectedly,

    /** "The agent helper stopped. Android may have stopped it to free memory or limit background processes. Restart it and try again." */
    PossiblyStoppedByAndroid,
}

/**
 * Narrow native observations of the helper (port of lib/domain/agent_helper_status.dart). No process arguments,
 * profile ids or log text. A SIGKILL is not proof of the cause, so the Android line says "may have".
 */
public data class AgentHelperStatus(
    val running: Boolean? = null,
    val lastExitAtEpochMillis: Long? = null,
    val lastExitCode: Long? = null,
    val lastStopRequested: Boolean? = null,
    val possibleResourceKill: Boolean = false,
) {
    public val unexpectedStop: Boolean
        get() = running == false && lastExitAtEpochMillis != null && lastStopRequested == false

    public val notice: HelperNotice?
        get() = when {
            !unexpectedStop -> null
            possibleResourceKill -> HelperNotice.PossiblyStoppedByAndroid
            else -> HelperNotice.StoppedUnexpectedly
        }

    public companion object {
        /** Dart's largest DateTime in milliseconds; a later or non-positive stamp cannot invent a dated kill. */
        public const val MAX_EPOCH_MILLIS: Long = 8_640_000_000_000_000L

        /** Reads the persisted native record; anything malformed is unknown, never guessed. */
        public fun fromRecord(record: JsonObject): AgentHelperStatus {
            fun prim(key: String): JsonPrimitive? = (record[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
            fun int(key: String): Long? = prim(key)?.takeUnless { it.isString }?.let { p ->
                p.longOrNull?.takeIf { p.content.none { c -> c == '.' || c == 'e' || c == 'E' } }
            }
            fun bool(key: String): Boolean? = prim(key)?.takeUnless { it.isString }?.booleanOrNull
            val at = int("lastExitAtMs")?.takeIf { it in 1..MAX_EPOCH_MILLIS }
            return AgentHelperStatus(
                running = bool("running"),
                lastExitAtEpochMillis = at,
                lastExitCode = int("lastExitCode"),
                lastStopRequested = bool("lastStopRequested"),
                possibleResourceKill = prim("exitReason")?.takeIf { it.isString }?.content == "memory_or_phantom_kill",
            )
        }
    }
}

/**
 * Recent helper exits for diagnostics (port of `withAgentHelperExits`, lib/builtin/agent_helper_diagnostics.dart;
 * B-AGENT-022). Only observed unexpected exits, newest first, never a service key (its suffix names a private
 * profile). [limit] is clamped to 0…100.
 */
public class HelperDiagnostics(private val host: AgentHost) {
    public suspend fun recentExits(limit: Int): Outcome<List<AgentHelperExit>> {
        val bounded = limit.coerceIn(0, MAX_EXITS)
        if (bounded == 0) return Outcome.Ok(emptyList())
        return host.helperExits(MAX_EXITS).map { exits ->
            exits.filter { it.kind != HelperExitKind.Requested }.sortedByDescending { it.atEpochMillis }.take(bounded)
        }
    }

    public companion object {
        public const val MAX_EXITS: Int = 100

        /**
         * The pure merge the Android side feeds (`performance.services`): keys `agent-host.*` whose last exit is dated
         * and was not requested. The returned exits carry no key.
         */
        public fun exitsFromServices(services: Map<String, JsonElement>, limit: Int): List<AgentHelperExit> =
            services.entries
                .filter { it.key.startsWith("agent-host.") }
                .mapNotNull { (_, value) ->
                    val status = AgentHelperStatus.fromRecord(value as? JsonObject ?: return@mapNotNull null)
                    val at = status.lastExitAtEpochMillis ?: return@mapNotNull null
                    if (status.lastStopRequested != false) return@mapNotNull null
                    AgentHelperExit(at, if (status.possibleResourceKill) HelperExitKind.LimitOrMemory else HelperExitKind.Unexpected)
                }
                .sortedByDescending { it.atEpochMillis }
                .take(limit.coerceIn(0, MAX_EXITS))
    }
}
