package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.IntegrationAuthMode
import io.github.eslamasabry.oc.core.model.IntegrationAuthState
import io.github.eslamasabry.oc.core.security.AddressRules
import kotlinx.serialization.json.*

/**
 * Exact Flutter oc.pendingAuth metadata schema, shared by the session and the per-profile atomic
 * merge writer.
 */
public object LibraryRecoveryCodec {
    public fun parse(raw: String, profile: String, nowMs: Long): List<LibraryAttempt>? {
        if (profile.isEmpty() || profile.length > 256 || raw.encodeToByteArray().size > 524_288)
            return null
        val rows =
            try {
                Json.parseToJsonElement(raw) as? JsonArray
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
        if (rows.size > 16) return null
        val results = mutableListOf<LibraryAttempt>()
        for (value in rows) {
            val obj = value as? JsonObject ?: return null
            if (
                obj.keys !=
                    setOf(
                        "attemptID",
                        "integrationID",
                        "kind",
                        "mode",
                        "profileID",
                        "origin",
                        "directory",
                        "workspace",
                        "expiresAt",
                    )
            )
                return null
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val id = text("attemptID")?.takeIf(::validId) ?: return null
            val integration = text("integrationID")?.takeIf(::validId) ?: return null
            if (
                text("profileID") != profile ||
                    text("kind") !in listOf("oauth", "command") ||
                    text("mode") !in listOf("auto", "code")
            )
                return null
            val origin =
                text("origin")?.takeIf {
                    it.length <= 2048 && AddressRules.origin(it) is Outcome.Ok
                } ?: return null
            val expiry =
                (obj["expiresAt"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                    ?: return null
            if (expiry <= 0 || expiry > nowMs + RETENTION) return null
            fun place(key: String): String? = if (obj[key] == JsonNull) null else text(key)
            for (key in listOf("directory", "workspace")) if (
                obj[key] != JsonNull &&
                    (place(key) == null ||
                        place(key)!!.length > 2048 ||
                        LOCATION_CONTROLS.containsMatchIn(place(key)!!))
            )
                return null
            if (nowMs - expiry > RETENTION) continue
            results +=
                LibraryAttempt(
                    integration,
                    id,
                    text("kind") == "command",
                    if (text("mode") == "code") IntegrationAuthMode.Code
                    else IntegrationAuthMode.Auto,
                    expiry,
                    origin,
                    place("directory"),
                    place("workspace"),
                    if (expiry <= nowMs) IntegrationAuthState.Expired
                    else IntegrationAuthState.Pending,
                )
        }
        return results.takeIf {
            it.map { e ->
                    listOf(e.origin, e.directory, e.workspace, e.integration, e.attempt, e.command)
                }
                .distinct()
                .size == it.size
        }
    }

    public fun encode(entries: List<LibraryAttempt>, profile: String): String =
        JsonArray(
                entries.map { entry ->
                    buildJsonObject {
                        put("attemptID", entry.attempt)
                        put("integrationID", entry.integration)
                        put("kind", if (entry.command) "command" else "oauth")
                        put("mode", if (entry.mode == IntegrationAuthMode.Code) "code" else "auto")
                        put("profileID", profile)
                        put("origin", entry.origin)
                        put("directory", entry.directory?.let(::JsonPrimitive) ?: JsonNull)
                        put("workspace", entry.workspace?.let(::JsonPrimitive) ?: JsonNull)
                        put("expiresAt", entry.expires)
                    }
                }
            )
            .toString()

    private fun validId(id: String): Boolean =
        id.length <= 256 && id != "." && id != ".." && Regex("^[A-Za-z0-9_.:-]+$").matches(id)

    private const val RETENTION: Long = 24 * 60 * 60 * 1000L
    private val LOCATION_CONTROLS = Regex("[\\x00-\\x1f\\x7f-\\x9f]")
}
