package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a server configuration page may show (Flutter `setupRedact`, B-SEC-040). A server's configuration holds
 * provider keys, tool-server environments and headers, commands and agent prompts; none of them may reach the screen,
 * whatever shape the server nests them in.
 *
 * - A value under a secret-looking name (key, token, secret, password, credential, environment, headers, options,
 *   request, command, template, prompt, system, content) is replaced by [MASK] whole, lists and objects included.
 * - Other text goes through the shared [Redactor] (known secrets, bearer values, keys, JWTs); names do too.
 * - Numbers, flags and null stay readable.
 *
 * The AI setup page (not yet native: OpenCode 2 reports `setupConfigRead = false`) and any proposed change
 * ([redactChanged]) must show only what passes through here.
 */
public class ServerConfigRedaction(private val redactor: Redactor = Redactor()) {
    /** The configuration as it may be shown. [name] is the name the value sits under ("" at the top). */
    public fun redact(value: JsonElement, name: String = ""): JsonElement {
        if (SECRET_NAME.containsMatchIn(name)) return JsonPrimitive(MASK)
        return when (value) {
            is JsonObject -> JsonObject(value.entries.associate { (k, v) -> redactor.redact(k) to redact(v, k) })
            // A list keeps the name it sits under: `secretList: ["…"]` is masked item by item.
            is JsonArray -> JsonArray(value.map { redact(it, name) })
            JsonNull -> JsonNull
            is JsonPrimitive -> if (value.isString) JsonPrimitive(redactor.redact(value.content)) else value
        }
    }

    /** One changed value of a proposed change, shown as text: masked by the name it sits under. */
    public fun redactChanged(value: String, name: String): String =
        if (SECRET_NAME.containsMatchIn(name)) MASK else redactor.redact(value)

    public companion object {
        public const val MASK: String = Redactor.MASK

        private val SECRET_NAME =
            Regex(
                "key|token|secret|password|credential|environment|headers|options|request|command|template|prompt|system|content",
                RegexOption.IGNORE_CASE,
            )
    }
}
