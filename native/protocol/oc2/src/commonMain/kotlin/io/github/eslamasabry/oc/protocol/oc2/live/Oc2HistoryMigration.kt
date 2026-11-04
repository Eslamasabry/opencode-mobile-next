package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Public inspection seam; the concrete authenticated gateway remains internal to the protocol. */
public suspend fun io.github.eslamasabry.oc.core.domain.ServerGateway.readHistoryMigrationStatus():
    Outcome<HistoryMigrationStatus?> =
    (this as? Oc2Gateway)?.historyMigrationStatus()
        ?: Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("historyMigration")
            )
        )

/** Flutter `HistoryMigrationPhase` (lib/domain/history_migration.dart). */
public enum class HistoryMigrationPhase {
    Required,
    Running,
    Completed,
    Error,
}

/**
 * Flutter `HistoryMigrationStatus` (B-PROTO-063): the OpenCode 2 answer to "is the OpenCode 1
 * history copied yet". [label] and [error] are server text: technical, Details only, never the page
 * line.
 */
public data class HistoryMigrationStatus(
    val phase: HistoryMigrationPhase,
    val label: String? = null,
    val done: Int? = null,
    val total: Int? = null,
    val error: String? = null,
) {
    /** True while there is something to tell the person: not finished. */
    val pending: Boolean
        get() = phase == HistoryMigrationPhase.Required || phase == HistoryMigrationPhase.Running

    /**
     * The plain line the page shows; carries counts only, never [label] or [error]. Null shows
     * nothing.
     */
    val line: HistoryMigrationLine?
        get() =
            when (phase) {
                HistoryMigrationPhase.Completed -> null
                HistoryMigrationPhase.Required -> HistoryMigrationLine.Copying(null, null)
                HistoryMigrationPhase.Running ->
                    if (done != null && total != null) HistoryMigrationLine.Copying(done, total)
                    else HistoryMigrationLine.Copying(null, null)
                HistoryMigrationPhase.Error -> HistoryMigrationLine.Failed
            }

    public companion object {
        private fun number(value: JsonElement?): Int? =
            (value as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.doubleOrNull
                ?.takeIf { it.isFinite() }
                ?.toInt()

        /**
         * Reads the server's union, bare or in `{data}`. Anything unrecognised is null (says
         * nothing, not wrong).
         */
        public fun fromJson(json: JsonElement?): HistoryMigrationStatus? {
            val outer = json as? JsonObject ?: return null
            val map = outer["data"] as? JsonObject ?: outer
            val status = (map["status"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return when (status) {
                "required" -> HistoryMigrationStatus(HistoryMigrationPhase.Required)
                "completed" -> HistoryMigrationStatus(HistoryMigrationPhase.Completed)
                "running" -> {
                    val progress = map["progress"] as? JsonObject
                    HistoryMigrationStatus(
                        HistoryMigrationPhase.Running,
                        label = (progress?.get("label") as? JsonPrimitive)?.content,
                        done = number(progress?.get("numerator")),
                        total = number(progress?.get("denominator")),
                    )
                }
                "error" ->
                    HistoryMigrationStatus(
                        HistoryMigrationPhase.Error,
                        error =
                            when (val e = map["error"]) {
                                null,
                                kotlinx.serialization.json.JsonNull -> null
                                is JsonPrimitive -> e.content
                                else -> e.toString()
                            },
                    )
                else -> null
            }
        }
    }
}

/**
 * Which plain sentence the history page shows (Flutter `historyLine`). :ui:words owns the words:
 * Copying(null,null) = "Bringing your earlier conversations across…", Copying(3,12) = "… (3 of
 * 12)…", Failed = "… original data is still on this phone, untouched" (never the server's error
 * text).
 */
public sealed interface HistoryMigrationLine {
    public data class Copying(val done: Int?, val total: Int?) : HistoryMigrationLine

    public data object Failed : HistoryMigrationLine
}

/**
 * `GET /experimental/migration/v1` (Flutter `historyMigrationStatus`). A server without the route
 * (the beta) or with any HTTP failure has nothing to report: null.
 */
internal suspend fun Oc2Gateway.historyMigrationStatusRead(): Outcome<HistoryMigrationStatus?> =
    when (val reply = json("/experimental/migration/v1")) {
        is Outcome.Failed -> null.ok()
        is Outcome.Ok -> HistoryMigrationStatus.fromJson(reply.value).ok()
    }
