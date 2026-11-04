package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import kotlinx.serialization.json.*

/**
 * Email remains protocol-private and is erased into an opaque hash before a usage reading is made.
 */
public class CodexAccount(
    public val requiresSignIn: Boolean,
    public val type: String?,
    public val email: String?,
    public val plan: String?,
) {
    override fun toString(): String = "CodexAccount(signedIn=${type != null})"
}

public data class CodexLimitWindow(
    val usedPercent: Long,
    val durationMinutes: Long?,
    val resetsAtSeconds: Long?,
)

public data class CodexLimitBucket(val primary: CodexLimitWindow?, val secondary: CodexLimitWindow?)

private class Invalid : Exception()

private fun JsonObject.text(key: String, max: Int): String? {
    val value = this[key]
    if (value == null || value == JsonNull) return null
    val primitive = value as? JsonPrimitive ?: throw Invalid()
    if (!primitive.isString || primitive.content.length > max) throw Invalid()
    return primitive.content
}

private fun JsonObject.integer(key: String, required: Boolean = false): Long? {
    val value = this[key]
    if (value == null || value == JsonNull) {
        if (required) throw Invalid()
        return null
    }
    val primitive = value as? JsonPrimitive ?: throw Invalid()
    if (primitive.isString) throw Invalid()
    return primitive.longOrNull?.takeIf { it >= 0 } ?: throw Invalid()
}

internal fun parseAccount(root: JsonObject): Outcome<CodexAccount> =
    try {
        val required =
            (root["requiresOpenaiAuth"] as? JsonPrimitive)
                ?.takeUnless { it.isString }
                ?.booleanOrNull ?: throw Invalid()
        if (!root.containsKey("account")) throw Invalid()
        val raw = root["account"]
        if (raw == JsonNull) CodexAccount(required, null, null, null).ok()
        else {
            val account = raw as? JsonObject ?: throw Invalid()
            CodexAccount(
                    required,
                    account.text("type", 100) ?: throw Invalid(),
                    account.text("email", 320),
                    account.text("planType", 100),
                )
                .ok()
        }
    } catch (_: Invalid) {
        Failure.UnreadableReply().failed()
    }

internal fun parseLimits(root: JsonObject): Outcome<List<CodexLimitBucket>> =
    try {
        val byId = root["rateLimitsByLimitId"] as? JsonObject
        val buckets =
            if (byId != null && byId.isNotEmpty()) {
                if (byId.size > 32) throw Invalid()
                byId.values.toList()
            } else listOf(root["rateLimits"] ?: throw Invalid())
        buckets
            .map { raw ->
                val bucket = raw as? JsonObject ?: throw Invalid()
                bucket.text("limitName", 100)
                bucket.text("limitId", 100)
                fun window(key: String): CodexLimitWindow? {
                    val rawWindow = bucket[key]
                    if (rawWindow == null || rawWindow == JsonNull) return null
                    val value = rawWindow as? JsonObject ?: throw Invalid()
                    val reset = value.integer("resetsAt")
                    if (reset != null && reset > 8640000000000L) throw Invalid()
                    return CodexLimitWindow(
                        value.integer("usedPercent", true)!!,
                        value.integer("windowDurationMins"),
                        reset,
                    )
                }
                CodexLimitBucket(window("primary"), window("secondary"))
            }
            .ok()
    } catch (_: Invalid) {
        Failure.UnreadableReply().failed()
    }
