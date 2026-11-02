package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Conversation identity and titles across resumed copies (B-AGENT-180, 181), ports of `PaseoGateway.sessionKey`,
 * `hasOwnTitle`, `_rememberedTitle` and the title part of `_remember` (lib/paseo/gateway.dart, gateway/copies.dart,
 * gateway/sessions.dart). Pure; the gateway keeps the maps.
 */
public object PaseoTitles {
    /** `<persistence.provider>:<persistence.sessionId>` — two records with the same key are one conversation. */
    public fun sessionKey(agent: JsonObject?): String? {
        val handle = agent?.obj("persistence") ?: return null
        val id = handle.str("sessionId")?.takeIf { it.isNotEmpty() } ?: return null
        return "${handle["provider"]?.let { (it as? JsonPrimitive)?.content }}:$id"
    }

    /** The record has a title of its own (not the "<Runtime> conversation" stand-in). */
    public fun hasOwnTitle(agent: JsonObject?): Boolean = !agent?.str("title").isNullOrBlank()

    /**
     * The snapshot the gateway should remember for [agent] (Dart `_remember`): a record reloaded without its title keeps
     * the one already [known] (the previous record or a `keepTitle` hint); a record that never had one takes the title
     * saved for its conversation ([savedTitle] by [sessionKey]); otherwise it is unchanged and the mapper's stand-in
     * applies.
     */
    public fun withTitle(agent: JsonObject, known: String?, savedTitle: ((String) -> String?)?): JsonObject {
        if (!agent.str("title").isNullOrBlank()) return agent
        val title = known?.takeIf { it.isNotBlank() } ?: remembered(agent, savedTitle) ?: return agent
        return JsonObject(LinkedHashMap(agent).apply { put("title", JsonPrimitive(title)) })
    }

    /** The title saved for [agent]'s conversation, if any (Dart `_rememberedTitle`). */
    public fun remembered(agent: JsonObject, savedTitle: ((String) -> String?)?): String? {
        val lookup = savedTitle ?: return null
        val key = sessionKey(agent) ?: return null
        return lookup(key)?.takeIf { it.isNotBlank() }
    }
}
