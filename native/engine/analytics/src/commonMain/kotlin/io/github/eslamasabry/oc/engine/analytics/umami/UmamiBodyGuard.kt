package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The allow-list guard on the REAL request body. It reads the JSON that is about to leave the phone, not the typed
 * event that made it, and lets through only what the lists name:
 * - the event's own keys: `type`, and in `payload` the fixed set below, each with the one form it may take (a listed
 *   page address and title, the fixed host name, a language tag, a build tag, a whole-number time, an event name);
 * - `data`: a key of the event's own words ([productSchema], with a listed, safe value) or an envelope field
 *   ([UmamiField], with a listed word or a matching number).
 * A key or value that is not named is DROPPED from the body (an event whose required parts are not valid is withheld
 * whole); the drop is reported by NAME only, never by value. Nothing is ever repaired or guessed.
 */
public object UmamiBodyGuard {
    /** What the guard decided: the body to send, which of the given events it holds and what it dropped. */
    public class Result(
        /** The JSON to post, or null when no event is left. */
        public val body: String?,
        /** Indexes (into the given list) of the events in [body], in order. */
        public val kept: List<Int>,
        /** Names of dropped keys, as `payload.<key>` or `data.<key>` (or `event` for a withheld event). */
        public val dropped: Set<String>,
    )

    private val website = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    /** The phone's language tag as it is (`en`, `ar-EG`): a bounded shape, so nothing else rides in the field. */
    private val language = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")
    private val tag = Regex("(?:test-)?[0-9]{1,10}")
    private val keyName = Regex("[a-z][a-z0-9_]{0,39}")
    private val payloadKeys =
        setOf("website", "hostname", "language", "url", "title", "tag", "timestamp", "data", "name", "salt")
    private val required = setOf("website", "language", "url", "title", "tag", "timestamp", "data")
    private val addresses = umamiPages.values.map { it.url }.toSet()
    private val titles = umamiPages.values.map { it.title }.toSet()

    public fun check(events: List<UmamiEvent>): Result {
        val dropped = linkedSetOf<String>()
        val kept = ArrayList<Int>()
        val bodies = ArrayList<JsonElement>()
        events.forEachIndexed { index, event ->
            val encoded = UmamiCodec.json.encodeToJsonElement(UmamiEvent.serializer(), event)
            val cleaned = (encoded as? JsonObject)?.let { clean(it, dropped) }
            if (cleaned == null) {
                dropped += "event"
            } else {
                kept += index
                bodies += cleaned
            }
        }
        val body =
            when (bodies.size) {
                0 -> null
                1 -> bodies.single().toString()
                else -> JsonArray(bodies).toString()
            }
        return Result(body, kept, dropped)
    }

    private fun clean(event: JsonObject, dropped: MutableSet<String>): JsonObject? {
        event.keys.filter { it != "type" && it != "payload" }.forEach { dropped += "event.${safe(it)}" }
        val payload = event["payload"] as? JsonObject
        val name = text(payload?.get("name"))
        val known = name == null || ProductEvent.entries.any { it.wire == name }
        val cleaned =
            if (text(event["type"]) == "event" && payload != null && known) {
                cleanPayload(payload, name ?: ProductEvent.ScreenView.wire, dropped)
            } else {
                null
            }
        return cleaned?.let { JsonObject(mapOf("type" to JsonPrimitive("event"), "payload" to JsonObject(it))) }
    }

    /** The payload's listed keys in their valid form, or null when a required part is missing or invalid. */
    private fun cleanPayload(
        payload: JsonObject,
        event: String,
        dropped: MutableSet<String>,
    ): Map<String, JsonElement>? {
        val out = LinkedHashMap<String, JsonElement>()
        var whole = true
        for ((key, value) in payload) {
            when {
                key !in payloadKeys -> dropped += "payload.${safe(key)}"
                key == "data" -> cleanData(value, event, dropped)?.let { out[key] = it } ?: run { whole = false }
                valid(key, value) -> out[key] = value
                key in required -> whole = false
                else -> dropped += "payload.$key"
            }
        }
        return out.takeIf { whole && it.keys.containsAll(required) }
    }

    /** One check per payload key; each says what a value of that key may be. */
    private val validWords: Map<String, (String) -> Boolean> =
        mapOf(
            "website" to { word -> website.matches(word) },
            "hostname" to { word -> word == "opencode-mobile.app" },
            "language" to { word -> word.length <= LANGUAGE_MAX && language.matches(word) },
            "url" to { word -> word in addresses },
            "title" to { word -> word in titles },
            "tag" to { word -> tag.matches(word) },
            "name" to { word -> ProductEvent.entries.any { it.wire == word } },
            "salt" to { word -> word == HASH_PERIOD },
        )

    private fun valid(key: String, value: JsonElement): Boolean =
        if (key == "timestamp") {
            (value as? JsonPrimitive)?.let { !it.isString && it.longOrNull != null } == true
        } else {
            text(value)?.let { validWords[key]?.invoke(it) } == true
        }

    private fun cleanData(value: JsonElement, event: String, dropped: MutableSet<String>): JsonObject? {
        val data = value as? JsonObject ?: return null
        val own = productSchema[event].orEmpty()
        val out = LinkedHashMap<String, JsonElement>()
        for ((key, raw) in data) {
            val word = text(raw)
            val field = UmamiField.ofWire(key)
            val allowed =
                word != null &&
                    if (field != null) field.accepts(word)
                    else own[key]?.contains(word) == true && productValueIsSafe(word)
            if (allowed) out[key] = raw else dropped += "data.${safe(key)}"
        }
        return JsonObject(out)
    }

    /** A string value, or null for a number, a flag, a null or anything nested. */
    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A key name for the log: only a plain lower-case word is ever repeated. */
    private fun safe(key: String): String = if (keyName.matches(key)) key else "?"


    private const val LANGUAGE_MAX = 35
}
