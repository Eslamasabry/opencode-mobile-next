package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Registers the credentials in a `/integration` or `/provider` answer with the redactor, without the parts of those
 * answers that only look like credentials to [Redactor.registerCredentialValues] (it registers every string under a
 * credential-named member, `key` included). The redactor then masks a registered value everywhere, even inside longer
 * words, so a registered ordinary word corrupts every preview, tool row and log line that contains it.
 *
 * Both exceptions are tied to their place in the answer, never to a member name alone, so a credential the server or
 * the person put anywhere else is walked exactly as before:
 * - A sign-in method's `form` (`data[].methods[].form`) lists the fields the sign-in asks for: each entry's `key` is
 *   the field's NAME ("project", "location", "profile", "account", "server", "accountId" on a 2.2.6 server) and a
 *   `when` condition inside it names another field. Registered, "/root/projects/bg5.txt" was shown as
 *   "/root/•••s/bg5.txt" and "Check the server" lost its noun. In the 2.2.6 schema a form field has no other
 *   credential-named member (`additionalProperties: false`), and a hidden field's `default` (a preset the person never
 *   sees) was never a credential-named member: the readers register it themselves.
 * - The built-in OpenCode provider (a `data[]` row with `id` "opencode") carries `settings.apiKey = "public"`: OpenCode's
 *   published anonymous key for its free models, which guards nothing. Registered, every "public" folder was masked.
 *   Exact match only; any other value, and the same value under any other provider, stays registered.
 */
internal fun Redactor.registerAnswerCredentials(answer: JsonElement) {
    registerCredentialValues(withoutDescriptors(answer, member = null))
}

/** [member] is the name of the member that holds [value] (for an array element: the member that holds the array). */
private fun withoutDescriptors(value: JsonElement, member: String?): JsonElement =
    when (value) {
        is JsonArray -> JsonArray(value.map { withoutDescriptors(it, member) })
        is JsonObject -> {
            val method = member == "methods" && (value["type"] as? JsonPrimitive)?.isString == true
            // A provider row: an element of the answer's `data` list (or of a bare top-level list).
            val freeProvider =
                (member == "data" || member == null) && (value["id"] as? JsonPrimitive)?.content == FREE_PROVIDER_ID
            JsonObject(
                buildMap {
                    for ((name, field) in value) {
                        if (method && name == "form" && field is JsonArray) continue
                        put(
                            name,
                            if (freeProvider && name == "settings" && field is JsonObject) withoutPublicKey(field)
                            else withoutDescriptors(field, name),
                        )
                    }
                }
            )
        }
        else -> value
    }

private fun withoutPublicKey(settings: JsonObject): JsonElement {
    val key = settings["apiKey"] as? JsonPrimitive
    val anonymous = key != null && key.isString && key.content == PUBLIC_ANONYMOUS_KEY
    return withoutDescriptors(if (anonymous) JsonObject(settings - "apiKey") else settings, "settings")
}

private const val FREE_PROVIDER_ID = "opencode"
private const val PUBLIC_ANONYMOUS_KEY = "public"
