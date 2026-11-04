package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Handwritten normalized extension data (Flutter `providerQuotaFixture`), as mutable maps so tests can edit it. */
internal fun quotaFixture(fetchedAtMs: Long = 1_000_000, provider: QuotaProvider = QuotaProvider.Codex): MutableMap<String, Any?> =
    linkedMapOf(
        "schemaVersion" to 1,
        "provider" to provider.wire,
        "source" to
            when (provider) {
                QuotaProvider.Codex -> "codex.wham"
                QuotaProvider.Claude -> "claude.oauth"
                QuotaProvider.Minimax -> "minimax.tokenPlan"
                QuotaProvider.Glm -> "glm.codingPlan"
            },
        "status" to "ok",
        "freshness" to "fresh",
        "fetchedAtMs" to fetchedAtMs,
        "expiresAtMs" to fetchedAtMs + 60_000,
        "account" to
            linkedMapOf<String, Any?>(
                "ref" to "a".repeat(64),
                "status" to if (provider == QuotaProvider.Codex) "matched" else "sourceBound",
            ).also { if (provider == QuotaProvider.Codex) it["plan"] = "plus" },
        "ordinaryUsageAllowed" to if (provider == QuotaProvider.Codex) true else null,
        "windows" to
            mutableListOf<Any?>(
                linkedMapOf<String, Any?>(
                    "id" to "primary",
                    "status" to "reported",
                    "usedPercent" to 25.5,
                    "durationSeconds" to 18000,
                    "resetsAtMs" to fetchedAtMs + 300_000,
                ),
                linkedMapOf<String, Any?>("id" to "secondary", "status" to "missing"),
            ),
    )

@Suppress("UNCHECKED_CAST")
internal fun MutableMap<String, Any?>.account(): MutableMap<String, Any?> = this["account"] as MutableMap<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun MutableMap<String, Any?>.windowList(): MutableList<Any?> = this["windows"] as MutableList<Any?>

@Suppress("UNCHECKED_CAST")
internal fun MutableMap<String, Any?>.firstWindow(): MutableMap<String, Any?> = windowList().first() as MutableMap<String, Any?>

internal fun toJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        else -> error("unsupported fixture value")
    }

internal fun parse(value: Any?): ProviderQuotaSnapshot? = ProviderQuotaSnapshot.fromJson(toJson(value))

internal fun parseOk(value: Any?): ProviderQuotaSnapshot = assertNotNullSnapshot(parse(value))

private fun assertNotNullSnapshot(s: ProviderQuotaSnapshot?): ProviderQuotaSnapshot = s ?: error("expected a valid snapshot")

internal fun encode(value: Any?): String = toJson(value).toString()
