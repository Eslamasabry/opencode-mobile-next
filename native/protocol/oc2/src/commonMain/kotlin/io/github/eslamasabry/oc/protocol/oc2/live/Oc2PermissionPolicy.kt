package io.github.eslamasabry.oc.protocol.oc2.live

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The session permission layer the app installs before a prompt, command or shell runs (Codex chat-header BLOCKER 1;
 * frozen Flutter `lib/api2/session_permission_policy.dart`). OC2 evaluates agent rules, then session rules, last
 * match winning; "Ask first" turns every implicit or explicit allow into an ask, so no tool runs without a card.
 * Strict parsing: anything malformed is `null`, and the caller refuses to send (a malformed policy must never lose a
 * denial silently).
 */
internal class Oc2PermissionPolicy private constructor(
    val rules: List<Rule>,
    val reverted: Boolean,
    val agent: String?,
    private val metadata: JsonObject,
) {
    data class Rule(val action: String, val resource: String, val effect: String) {
        fun json(): JsonObject = buildJsonObject {
            put("action", action)
            put("resource", resource)
            put("effect", effect)
        }
    }

    /** The original session layer, kept apart from the app's last installed one (a plan agent keeps its denials). */
    private fun original(): List<Rule>? {
        val saved = metadata[METADATA_KEY] ?: return rules
        if (saved !is JsonObject || (saved["version"] as? JsonPrimitive)?.contentOrNull != "1") return null
        val applied = rules(saved["applied"]) ?: return null
        val original = rules(saved["original"]) ?: return null
        // Another client changed this session's rules: its new truth stays.
        return if (applied == rules) original else rules
    }

    /**
     * The PATCH body for [agentData] (the agent object). With [serverRuns] (approved automatically) the session gets
     * its original rules back; otherwise a leading `* * ask` and every allow turned into an ask.
     */
    fun updateForAgent(agentData: JsonObject, serverRuns: Boolean): JsonObject? {
        val id = (agentData["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id.isNullOrEmpty() || (agent != null && id != agent)) return null
        val original = original() ?: return null
        val required =
            if (serverRuns) original
            else {
                val agentRules = rules(agentData["permissions"]) ?: return null
                listOf(Rule("*", "*", "ask")) +
                    (agentRules + original).map { if (it.effect == "allow") it.copy(effect = "ask") else it }
            }
        if (required.size > MAX_RULES) return null
        return buildJsonObject {
            put("permissions", JsonArray(required.map { it.json() }))
            put(
                "metadata",
                JsonObject(
                    metadata +
                        (METADATA_KEY to buildJsonObject {
                            put("version", 1)
                            put("original", JsonArray(original.map { it.json() }))
                            put("applied", JsonArray(required.map { it.json() }))
                        }),
                ),
            )
        }
    }

    /** This (read-back) session already carries exactly [update]. */
    fun confirms(update: JsonObject): Boolean {
        val wanted = rules(update["permissions"]) ?: return false
        val saved = metadata[METADATA_KEY] as? JsonObject ?: return false
        val expected = (update["metadata"] as? JsonObject)?.get(METADATA_KEY) as? JsonObject ?: return false
        return wanted == rules &&
            (saved["version"] as? JsonPrimitive)?.contentOrNull == (expected["version"] as? JsonPrimitive)?.contentOrNull &&
            rules(saved["original"]) != null && rules(saved["original"]) == rules(expected["original"]) &&
            rules(saved["applied"]) != null && rules(saved["applied"]) == rules(expected["applied"])
    }

    companion object {
        const val METADATA_KEY: String = "oc.mobile.permissionRequests"
        private const val MAX_RULES = 1000
        private val effects = setOf("allow", "ask", "deny")

        fun rules(raw: JsonElement?): List<Rule>? {
            if (raw !is JsonArray || raw.size > MAX_RULES) return null
            return raw.map { rule ->
                val o = rule as? JsonObject ?: return null
                val action = o.text("action") ?: return null
                val resource = o.text("resource") ?: return null
                val effect = o.text("effect")?.takeIf { it in effects } ?: return null
                Rule(action, resource, effect)
            }
        }

        /** `GET /session/{id}` → the policy, or `null` when it can't be verified. */
        fun fromSession(response: JsonElement, sessionId: String): Oc2PermissionPolicy? {
            val data = (response as? JsonObject)?.get("data") as? JsonObject ?: return null
            if (data.text("id") != sessionId) return null
            val agent = data["agent"]
            if (agent != null && agent !is JsonNull && (agent as? JsonPrimitive)?.takeIf { it.isString }?.content.isNullOrEmpty())
                return null
            val metadata = data["metadata"]
            if (metadata != null && metadata !is JsonNull && metadata !is JsonObject) return null
            val permissions = data["permissions"]
            val rules = if (permissions == null) emptyList() else rules(permissions) ?: return null
            return Oc2PermissionPolicy(
                rules,
                reverted = data["revert"].let { it != null && it !is JsonNull },
                agent = (agent as? JsonPrimitive)?.takeIf { it.isString }?.content,
                metadata = metadata as? JsonObject ?: JsonObject(emptyMap()),
            )
        }

        /** `GET /agent`: OC2 puts its resolved default first; it must be a visible primary agent. */
        fun defaultAgent(response: JsonElement): JsonObject? {
            val entries = (response as? JsonObject)?.get("data") as? JsonArray ?: return null
            val first = entries.firstOrNull() as? JsonObject ?: return null
            if (first.text("id").isNullOrEmpty()) return null
            if ((first["hidden"] as? JsonPrimitive)?.contentOrNull != "false") return null
            if (first.text("mode") !in setOf("primary", "all")) return null
            return first
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    }
}
