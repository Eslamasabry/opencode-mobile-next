package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.json.TolerantJson
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `GET /api/experimental/session/stats` (`data`). Unlike the other DTOs this one is strict, as Flutter's
 * `UsageStatistics.fromJson` (lib/domain/usage_statistics.dart) is: a statistics page with a negative count, a
 * fractional count, an inverted range or an unknown tool mode is not shown at all, so [fromJson] returns null.
 * The usage operations themselves are S5 work (plan 15, S5-OC2MORE / S5-INTEG); this is the wire shape only.
 */
public data class Oc2UsageStatistics(
    public val from: Long,
    public val to: Long,
    public val sessions: Long,
    public val subagents: Long,
    public val prompts: Long,
    public val steps: Long,
    public val activeDays: Long,
    public val streak: Long,
    public val cost: Double,
    public val tokens: Tokens,
    /** Null when the server reports tool mode `none`. */
    public val tools: ToolTotals?,
    public val models: List<Model>,
    public val activity: List<Activity>,
) {
    public data class Tokens(
        public val input: Long,
        public val output: Long,
        public val reasoning: Long,
        public val cacheRead: Long,
        public val cacheWrite: Long,
    )

    public data class ToolTotals(
        public val calls: Long,
        public val succeeded: Long,
        public val failed: Long,
        public val unfinished: Long,
    )

    public data class Model(
        public val providerID: String,
        public val modelID: String,
        public val variant: String?,
        public val steps: Long,
        public val tokens: Tokens,
        public val cost: Double,
    )

    public data class Activity(public val date: String, public val steps: Long)

    public companion object {
        private class Invalid : Exception()

        public fun fromJson(obj: JsonObject): Oc2UsageStatistics? =
            try {
                read(obj)
            } catch (_: Invalid) {
                null
            }

        private fun read(json: JsonObject): Oc2UsageStatistics {
            val range = map(json["range"])
            val from = timestamp(range["from"])
            val to = timestamp(range["to"])
            if (from > to) throw Invalid()
            val tools = map(json["tools"])
            val mode = (tools["mode"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (mode !in setOf("none", "summary", "detail")) throw Invalid()
            return Oc2UsageStatistics(
                from = from,
                to = to,
                sessions = count(json["sessions"]),
                subagents = count(json["subagents"]),
                prompts = count(json["prompts"]),
                steps = count(json["steps"]),
                activeDays = count(json["activeDays"]),
                streak = count(json["streak"]),
                cost = cost(json["cost"]),
                tokens = tokens(map(json["tokens"])),
                tools =
                    if (mode == "none") {
                        null
                    } else {
                        map(tools["totals"]).let {
                            ToolTotals(count(it["calls"]), count(it["succeeded"]), count(it["failed"]), count(it["unfinished"]))
                        }
                    },
                models = list(json["models"]) { model(it) },
                activity = list(json["activity"]) { Activity(text(it["date"]), count(it["steps"])) },
            )
        }

        private fun model(json: JsonObject): Model {
            val model = map(json["model"])
            val variant = model["variant"]
            if (variant != null && variant != JsonNull && (variant !is JsonPrimitive || !variant.isString)) throw Invalid()
            return Model(
                providerID = text(model["providerID"]),
                modelID = text(model["id"]),
                variant = (variant as? JsonPrimitive)?.takeIf { it.isString }?.content,
                steps = count(json["steps"]),
                tokens = tokens(map(json["tokens"])),
                cost = cost(json["cost"]),
            )
        }

        private fun tokens(json: JsonObject): Tokens {
            val cache = map(json["cache"])
            return Tokens(count(json["input"]), count(json["output"]), count(json["reasoning"]), count(cache["read"]),
                count(cache["write"]))
        }

        private fun map(value: JsonElement?): JsonObject = value as? JsonObject ?: throw Invalid()

        private fun <T> list(value: JsonElement?, parse: (JsonObject) -> T): List<T> =
            (value as? JsonArray ?: throw Invalid()).map { parse(map(it)) }

        private fun count(value: JsonElement?): Long {
            val n = TolerantJson.number(value) ?: throw Invalid()
            if (!n.isFinite() || n < 0 || n != kotlin.math.truncate(n)) throw Invalid()
            return n.toLong()
        }

        private fun cost(value: JsonElement?): Double {
            val n = TolerantJson.number(value) ?: throw Invalid()
            if (!n.isFinite() || n < 0) throw Invalid()
            return n
        }

        private fun timestamp(value: JsonElement?): Long {
            val n = TolerantJson.number(value) ?: throw Invalid()
            if (!n.isFinite() || n != kotlin.math.truncate(n) || abs(n) > MAX_TIMESTAMP) throw Invalid()
            return n.toLong()
        }

        private fun text(value: JsonElement?): String =
            (value as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content ?: throw Invalid()

        private const val MAX_TIMESTAMP = 8.64e15
    }
}
