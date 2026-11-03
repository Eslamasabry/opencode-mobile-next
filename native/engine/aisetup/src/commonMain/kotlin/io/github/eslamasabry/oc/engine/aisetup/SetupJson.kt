package io.github.eslamasabry.oc.engine.aisetup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val STRUCTURAL_SECRET_KEY = Regex(
    "key|token|secret|password|credential|environment|headers|options|request|command|template|prompt|system|content",
    RegexOption.IGNORE_CASE,
)

/**
 * Structural redaction (Dart `setupRedact`): arbitrary env/header values, provider credentials and free-form
 * executable/prompt bodies are masked even when they do not look like a known credential.
 */
public fun setupRedact(value: JsonElement?, redaction: SetupRedaction, key: String = ""): JsonElement? {
    if (STRUCTURAL_SECRET_KEY.containsMatchIn(key)) return JsonPrimitive(redaction.mask)
    return when (value) {
        null -> null
        is JsonObject ->
            JsonObject(
                LinkedHashMap<String, JsonElement>().apply {
                    for ((k, v) in value) put(redaction.text(k), setupRedact(v, redaction, k) ?: JsonNull)
                },
            )
        is JsonArray -> JsonArray(value.map { setupRedact(it, redaction, key) ?: JsonNull })
        is JsonNull -> JsonNull
        is JsonPrimitive -> if (value.isString) JsonPrimitive(redaction.text(value.content)) else value
    }
}

internal fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.isAbsentOrNull(): Boolean = this == null || this is JsonNull

internal fun at(root: JsonObject, path: List<String>): JsonElement? {
    var cursor: JsonElement? = root
    for (part in path) cursor = (cursor as? JsonObject)?.get(part) ?: return null
    return cursor
}

internal fun has(root: JsonObject, path: List<String>): Boolean {
    var cursor: JsonElement = root
    for (part in path) {
        val map = cursor as? JsonObject ?: return false
        cursor = map[part] ?: return false
    }
    return true
}

/** Dart `_put`: replaces any non-map ancestor by a new map (callers forbid that case before dispatch). */
internal fun put(root: JsonObject, path: List<String>, value: JsonElement?): JsonObject {
    val copy = LinkedHashMap<String, JsonElement>(root)
    if (path.size == 1) {
        copy[path[0]] = value ?: JsonNull
    } else {
        val child = root[path[0]] as? JsonObject ?: JsonObject(emptyMap())
        copy[path[0]] = put(child, path.drop(1), value)
    }
    return JsonObject(copy)
}

/** Dart `_remove`: no-op when an ancestor is absent or not a map. */
internal fun remove(root: JsonObject, path: List<String>): JsonObject {
    val copy = LinkedHashMap<String, JsonElement>(root)
    if (path.size == 1) {
        copy.remove(path[0])
        return JsonObject(copy)
    }
    val child = root[path[0]] as? JsonObject ?: return root
    copy[path[0]] = remove(child, path.drop(1))
    return JsonObject(copy)
}

/** The parts of a URL the setup rules look at (Dart `Uri.tryParse` subset). */
internal class SetupUrl(
    val scheme: String,
    val userInfo: String,
    val host: String,
    val hasQuery: Boolean,
    val hasFragment: Boolean,
) {
    companion object {
        private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

        fun parse(value: String): SetupUrl? {
            if (value.any { it.isWhitespace() }) return null
            val scheme = SCHEME.find(value)?.groupValues?.get(1)
                ?: return SetupUrl("", "", "", '?' in value, '#' in value)
            var rest = value.substring(scheme.length + 1)
            val fragment = rest.indexOf('#')
            val hasFragment = fragment >= 0
            if (hasFragment) rest = rest.substring(0, fragment)
            val query = rest.indexOf('?')
            val hasQuery = query >= 0
            if (hasQuery) rest = rest.substring(0, query)
            if (!rest.startsWith("//")) return SetupUrl(scheme.lowercase(), "", "", hasQuery, hasFragment)
            val authority = rest.substring(2).substringBefore('/')
            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else ""
            val hostPort = if (at >= 0) authority.substring(at + 1) else authority
            val host =
                if (hostPort.startsWith("[")) {
                    val end = hostPort.indexOf(']')
                    if (end < 0) return null
                    hostPort.substring(1, end)
                } else {
                    hostPort.substringBefore(':')
                }
            return SetupUrl(scheme.lowercase(), userInfo, host.lowercase(), hasQuery, hasFragment)
        }
    }
}

private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1")

/** HTTPS, or HTTP to a loopback host; never user info, a query or a fragment. */
internal fun isAllowedMcpUrl(value: String): Boolean {
    val uri = SetupUrl.parse(value) ?: return false
    if (uri.host.isEmpty() || uri.userInfo.isNotEmpty() || uri.hasQuery || uri.hasFragment) return false
    return uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOOPBACK)
}
