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
 * - A sign-in method's `form` lists the fields the sign-in asks for: each entry's `key` is the field's NAME
 *   ("project", "location", "profile", "account", "server", "accountId" on a 2.2.6 server) and a `when` condition's
 *   `key` names another field. Registered, "/root/projects/bg5.txt" was shown as "/root/•••s/bg5.txt" and "Check the
 *   server" lost its noun. The form is left out of the walk; the readers still register a hidden field's default
 *   themselves (a preset the person never sees), and a visible default is shown to the person in the clear.
 * - The built-in OpenCode provider carries `settings.apiKey = "public"`: OpenCode's published anonymous key for its
 *   free models, which guards nothing. Registered, every "public" folder was masked. Any other value stays.
 *
 * Every other member is walked exactly as before: a provider's or connection's real `key`, `apiKey`, `token`,
 * `password`, `access`, `refresh` and the rest are still registered.
 */
internal fun Redactor.registerAnswerCredentials(answer: JsonElement) {
    registerCredentialValues(withoutDescriptors(answer))
}

private fun withoutDescriptors(value: JsonElement): JsonElement =
    when (value) {
        is JsonArray -> JsonArray(value.map(::withoutDescriptors))
        is JsonObject ->
            JsonObject(
                value
                    .filterNot { (name, field) -> isDescriptor(name, field) || isPublicKey(name, field) }
                    .mapValues { (_, field) -> withoutDescriptors(field) }
            )
        else -> value
    }

private fun isDescriptor(name: String, field: JsonElement): Boolean =
    (name == "form" || name == "when") && field is JsonArray

private fun isPublicKey(name: String, field: JsonElement): Boolean =
    name.lowercase().replace("-", "").replace("_", "") == "apikey" &&
        field is JsonPrimitive &&
        field.isString &&
        field.content == PUBLIC_ANONYMOUS_KEY

private const val PUBLIC_ANONYMOUS_KEY = "public"
