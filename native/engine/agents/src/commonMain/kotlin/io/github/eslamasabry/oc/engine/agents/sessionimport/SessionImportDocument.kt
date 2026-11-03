package io.github.eslamasabry.oc.engine.agents.sessionimport

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why an import file or destination was refused. Words live in the UI. */
public enum class SessionImportProblem {
    /** Not an OpenCode JSON export, or its identifying structure is malformed (Dart `SessionImportInvalid`). */
    Invalid,

    /** Larger than [SessionImportDocument.MAX_BYTES]; never read past the limit (Dart `SessionImportTooLarge`). */
    TooLarge,
}

/** The result of reading or building an import value. */
public sealed interface SessionImportResult<out T> {
    public data class Ok<out T>(val value: T) : SessionImportResult<T>

    public data class Refused(val problem: SessionImportProblem) : SessionImportResult<Nothing>
}

public fun <T> SessionImportResult<T>.valueOrNull(): T? = (this as? SessionImportResult.Ok)?.value

/** Where the import lands. Always explicit: never the source file's own directory (Dart `SessionImportDestination`). */
public data class SessionImportDestination(val directory: String, val workspaceId: String? = null) {
    /** The wire `location`, or null when the directory or workspace is unsafe. */
    public fun toJson(): JsonObject? {
        if (!ImportRules.safeText(directory)) return null
        if (workspaceId != null && !ImportRules.validId(workspaceId, "wrk")) return null
        return JsonObject(
            buildMap {
                put("directory", JsonPrimitive(directory))
                if (workspaceId != null) put("workspaceID", JsonPrimitive(workspaceId))
            },
        )
    }
}

/**
 * A projected OpenCode 2 transcript, never a reconstruction from product models (port of lib/domain/session_import.dart).
 * Only its identifying structure is validated here; the server validates nested protocol fields, which are kept intact,
 * including unknown message types. The reviewed snapshot is a deep copy: later changes to the input tree (a JsonObject
 * built over a mutable map) or to a request body never reach it.
 */
public class SessionImportDocument private constructor(
    private val info: JsonObject,
    private val messages: ImmutableList<JsonObject>,
    public val hasRedactions: Boolean,
) {
    public val id: String get() = (info["id"] as JsonPrimitive).content
    public val title: String? get() = (info["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    public val parentId: String? get() = (info["parentID"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    public val messageCount: Int get() = messages.size
    public val archived: Boolean
        get() = ((info["time"] as JsonObject)["archived"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { it > 0 } ?: false

    /**
     * A fresh JSON tree for `POST /session/import`: the source `info` and `messages` unchanged, and the explicit
     * [destination] as `location`. Null when the destination is unsafe.
     */
    public fun requestBody(destination: SessionImportDestination): SessionImportResult<JsonObject> {
        val location = destination.toJson() ?: return SessionImportResult.Refused(SessionImportProblem.Invalid)
        return SessionImportResult.Ok(
            JsonObject(
                linkedMapOf(
                    "info" to ImportRules.copy(info, 0)!!,
                    "messages" to JsonArray(messages.map { ImportRules.copy(it, 0)!! }),
                    "location" to location,
                ),
            ),
        )
    }

    override fun toString(): String = "SessionImportDocument($messageCount messages)" // no titles or text in logs

    public companion object {
        public const val MAX_BYTES: Long = 128L * 1024 * 1024
        private val parser = Json { isLenient = false }

        /** Refuses an oversize file before any byte is read (the picker reports the length first). */
        public fun admitLength(length: Long): SessionImportResult<Unit> =
            if (length > MAX_BYTES) SessionImportResult.Refused(SessionImportProblem.TooLarge) else SessionImportResult.Ok(Unit)

        /**
         * Reads a file in chunks (any split, even inside a UTF-8 sequence). Stops pulling chunks once the size passes
         * [maxBytes]. Malformed UTF-8 or JSON is [SessionImportProblem.Invalid].
         */
        public fun read(chunks: Sequence<ByteArray>, maxBytes: Long = MAX_BYTES): SessionImportResult<SessionImportDocument> {
            var size = 0L
            val parts = ArrayList<ByteArray>()
            for (chunk in chunks) {
                size += chunk.size
                if (size > maxBytes) return SessionImportResult.Refused(SessionImportProblem.TooLarge)
                parts += chunk
            }
            val bytes = ByteArray(size.toInt())
            var at = 0
            for (part in parts) {
                part.copyInto(bytes, at)
                at += part.size
            }
            val text = try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return SessionImportResult.Refused(SessionImportProblem.Invalid)
            }
            val json = try {
                parser.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return SessionImportResult.Refused(SessionImportProblem.Invalid)
            }
            return fromJson(json)
        }

        /** Accepts the exact export envelope `{"data": …}` and the bare `SessionTransfer.Data` form. */
        public fun fromJson(value: JsonElement?): SessionImportResult<SessionImportDocument> {
            val invalid = SessionImportResult.Refused(SessionImportProblem.Invalid)
            var root = value as? JsonObject ?: return invalid
            if (root.containsKey("data")) {
                if (root.size != 1) return invalid
                root = root["data"] as? JsonObject ?: return invalid
            }
            if (root.size != 2) return invalid
            val info = root["info"] as? JsonObject ?: return invalid
            val list = root["messages"] as? JsonArray ?: return invalid
            if (!ImportRules.validInfo(info)) return invalid
            val ids = HashSet<String>()
            val messages = ArrayList<JsonObject>(list.size)
            for (message in list) {
                if (message !is JsonObject) return invalid
                val id = ImportRules.string(message["id"])
                val time = message["time"] as? JsonObject
                if (id == null || !ImportRules.validId(id, "msg_") || !ids.add(id) ||
                    !ImportRules.safeText(ImportRules.string(message["type"])) ||
                    time == null || !ImportRules.number(time["created"])
                ) {
                    return invalid
                }
                messages += ImportRules.copy(message, 0) as? JsonObject ?: return invalid
            }
            val snapshot = ImportRules.copy(info, 0) as? JsonObject ?: return invalid
            val redacted = ImportRules.redacted(snapshot) || messages.any(ImportRules::redacted)
            return SessionImportResult.Ok(SessionImportDocument(snapshot, messages.toImmutableList(), redacted))
        }
    }
}

internal object ImportRules {
    private const val MAX_NESTING = 256

    fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun hasControl(value: String): Boolean = value.any { val c = it.code; c < 0x20 || c == 0x7f || c in 0x80..0x9f }

    fun validId(value: String?, prefix: String): Boolean =
        value != null && value.length > prefix.length && value.startsWith(prefix) && !hasControl(value) && ' ' !in value

    fun safeText(value: String?): Boolean = value != null && value.isNotBlank() && !hasControl(value)

    /** A finite JSON number (never a string, boolean or null). */
    fun number(value: JsonElement?): Boolean {
        val p = value as? JsonPrimitive ?: return false
        if (p is JsonNull || p.isString || p.content == "true" || p.content == "false") return false
        return p.content.toDoubleOrNull()?.isFinite() == true
    }

    fun validInfo(info: JsonObject): Boolean {
        val time = info["time"] as? JsonObject ?: return false
        val location = info["location"] as? JsonObject ?: return false
        return validId(string(info["id"]), "ses") &&
            safeText(string(info["projectID"])) &&
            number(info["cost"]) &&
            tokens(info["tokens"]) &&
            number(time["created"]) && number(time["updated"]) &&
            (!time.containsKey("archived") || number(time["archived"])) &&
            safeText(string(location["directory"])) &&
            (!location.containsKey("workspaceID") || validId(string(location["workspaceID"]), "wrk")) &&
            (!info.containsKey("title") || string(info["title"]) != null) &&
            (!info.containsKey("parentID") || validId(string(info["parentID"]), "ses"))
    }

    private fun tokens(value: JsonElement?): Boolean {
        val t = value as? JsonObject ?: return false
        val cache = t["cache"] as? JsonObject ?: return false
        return listOf("input", "output", "reasoning").all { number(t[it]) } && listOf("read", "write").all { number(cache[it]) }
    }

    fun redacted(value: JsonElement): Boolean = when (value) {
        is JsonObject -> value.values.any(::redacted)
        is JsonArray -> value.any(::redacted)
        is JsonPrimitive -> value.isString && value.content.contains("[redacted:")
    }

    /** A deep copy over fresh maps and lists; null for a non-finite number or nesting deeper than 256. */
    fun copy(value: JsonElement, depth: Int): JsonElement? {
        if (depth > MAX_NESTING) return null
        return when (value) {
            is JsonObject -> {
                val out = LinkedHashMap<String, JsonElement>(value.size)
                for ((k, v) in value) out[k] = copy(v, depth + 1) ?: return null
                JsonObject(out)
            }
            is JsonArray -> {
                val out = ArrayList<JsonElement>(value.size)
                for (v in value) out += copy(v, depth + 1) ?: return null
                JsonArray(out)
            }
            is JsonNull -> JsonNull
            is JsonPrimitive -> when {
                value.isString -> JsonPrimitive(value.content)
                value.content == "true" || value.content == "false" -> value
                value.content.toDoubleOrNull()?.isFinite() == true -> value
                else -> null
            }
        }
    }
}
