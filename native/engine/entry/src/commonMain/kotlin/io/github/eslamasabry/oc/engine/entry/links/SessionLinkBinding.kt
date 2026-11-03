package io.github.eslamasabry.oc.engine.entry.links

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Verified installation metadata for a session link (Flutter `SessionLinkBinding`): which Tailscale origin and which
 * server installation a profile's links belong to. It grants no authority to a session and holds no credential.
 */
public data class SessionLinkBinding(
    val origin: String,
    val instanceId: String,
    /** UTC epoch milliseconds. */
    val verifiedAtMillis: Long,
)

/** Safe categories only (Flutter `SessionAddressFailureCode`): no link data is kept in a failure. */
public enum class SessionLinkFailure { InvalidLink, TooLarge, Credentials, InstanceMismatch, ProfileMissing, Storage }

/** The binding store's answers: a value, or one [SessionLinkFailure]. */
public sealed interface SessionLinkResult<out T> {
    public data class Ok<out T>(val value: T) : SessionLinkResult<T>

    public data class Failed(val failure: SessionLinkFailure) : SessionLinkResult<Nothing>
}

/** Thrown inside this package only, to leave validation early; never escapes a public call. */
internal class SessionLinkRefusal(val failure: SessionLinkFailure) : Exception() {
    override val message: String get() = failure.name
}

/** The canonical form and the stored shape (Flutter `SessionAddressLink.normalizeOrigin` / `validInstance`). */
internal object SessionLinkCodec {
    const val MAX_LENGTH: Int = 1024
    const val MAX_STORED: Int = 2048
    private val origin = Regex("""^https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?$""", RegexOption.IGNORE_CASE)
    private val label = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
    private val uuid = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")
    private val json = Json

    private fun fail(failure: SessionLinkFailure): Nothing = throw SessionLinkRefusal(failure)

    private fun boundedAscii(value: String) {
        if (value.length > MAX_LENGTH) fail(SessionLinkFailure.TooLarge)
        if (value.isEmpty() || value.any { it.code < 0x21 || it.code > 0x7e }) fail(SessionLinkFailure.InvalidLink)
    }

    fun normalizeOrigin(value: String, secrets: SecretRecognizer): String {
        boundedAscii(value)
        if (secrets.containsSecret(value) || '@' in value) fail(SessionLinkFailure.Credentials)
        val match = origin.matchEntire(value) ?: fail(SessionLinkFailure.InvalidLink)
        val host = match.groupValues[1].lowercase()
        val labels = host.split('.')
        if (host.length > 253 || labels.size != 4 || labels[2] != "ts" || labels[3] != "net" || labels.any { !label.matches(it) }) {
            fail(SessionLinkFailure.InvalidLink)
        }
        val port = match.groupValues[2].ifEmpty { "443" }.toInt()
        if (port !in 1..65535) fail(SessionLinkFailure.InvalidLink)
        val normalized = "https://$host" + if (port == 443) "" else ":$port"
        if (secrets.containsSecret(normalized)) fail(SessionLinkFailure.Credentials)
        return normalized
    }

    /** The canonical binding, or a refusal. Checks the secrets every time it is called. */
    fun validated(binding: SessionLinkBinding, secrets: SecretRecognizer): SessionLinkBinding {
        val origin = normalizeOrigin(binding.origin, secrets)
        val instance = binding.instanceId.lowercase()
        if (!uuid.matches(binding.instanceId) ||
            secrets.containsSecret(binding.instanceId) ||
            secrets.containsSecret(instance) ||
            binding.verifiedAtMillis < 0
        ) {
            fail(SessionLinkFailure.InvalidLink)
        }
        return SessionLinkBinding(origin, instance, binding.verifiedAtMillis)
    }

    fun encode(binding: SessionLinkBinding): String =
        buildJsonObject {
            put("schemaVersion", 1)
            put("origin", binding.origin)
            put("instanceId", binding.instanceId)
            put("verifiedAt", binding.verifiedAtMillis)
        }.toString()

    /** Exactly the four known fields, schema 1; anything else is [SessionLinkFailure.Storage]. */
    fun decode(raw: String, secrets: SecretRecognizer): SessionLinkBinding {
        if (raw.length > MAX_STORED) fail(SessionLinkFailure.Storage)
        val element =
            try {
                json.parseToJsonElement(raw)
            } catch (_: IllegalArgumentException) {
                fail(SessionLinkFailure.Storage)
            }
        val obj = element as? JsonObject ?: fail(SessionLinkFailure.Storage)
        if (obj.size != 4) fail(SessionLinkFailure.Storage)
        val version = obj["schemaVersion"] as? JsonPrimitive
        val origin = obj["origin"] as? JsonPrimitive
        val instance = obj["instanceId"] as? JsonPrimitive
        val at = obj["verifiedAt"] as? JsonPrimitive
        if (version == null || version.isString || version.longOrNull != 1L) fail(SessionLinkFailure.Storage)
        if (origin == null || !origin.isString || instance == null || !instance.isString) fail(SessionLinkFailure.Storage)
        if (at == null || at.isString) fail(SessionLinkFailure.Storage)
        val millis = at.longOrNull ?: fail(SessionLinkFailure.Storage)
        return try {
            validated(SessionLinkBinding(origin.content, instance.content, millis), secrets)
        } catch (_: SessionLinkRefusal) {
            fail(SessionLinkFailure.Storage)
        }
    }
}
