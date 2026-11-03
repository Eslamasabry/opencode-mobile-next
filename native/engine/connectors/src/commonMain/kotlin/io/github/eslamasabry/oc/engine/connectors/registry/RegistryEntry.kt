package io.github.eslamasabry.oc.engine.connectors.registry

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.text.WebAddress
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One public MCP registry listing (Dart `RegistryEntry`, lib/domain/setup_registry.dart). Metadata only: registry
 * content is untrusted and never executed. [title] and [description] are plain, redacted text.
 */
public class RegistryEntry internal constructor(
    public val name: String,
    public val version: String,
    public val description: String,
    public val title: String,
    public val remotes: List<RegistryRemote>,
    public val packages: List<RegistryPackage>,
) {
    public val id: String get() = "$name@$version"

    /** The first remote that needs no connection details. The UI still asks before using it. */
    public val connectableRemote: RegistryRemote? get() = remotes.firstOrNull { !it.requiresConfiguration }

    public val unsupportedReason: RegistryUnsupported?
        get() =
            when {
                connectableRemote != null -> null
                remotes.isNotEmpty() -> RegistryUnsupported.NeedsConnectionDetails
                packages.isNotEmpty() -> RegistryUnsupported.NeedsReviewedCommand
                else -> RegistryUnsupported.NoHttpsEndpoint
            }

    /** The cache form (Dart `toJson`): redacted again on the way out. */
    public fun toJson(redactor: Redactor): JsonObject =
        buildJsonObject {
            put("name", redactor.redact(name))
            put("version", redactor.redact(version))
            put("description", redactor.redact(description))
            if (title.isNotEmpty()) put("title", redactor.redact(title))
            put("remotes", JsonArray(remotes.map { it.toJson(redactor) }))
            put("packages", JsonArray(packages.map { it.toJson(redactor) }))
        }

    public companion object {
        /** Null when [value] is not a listing with a safe name and version (Dart `fromJson`). */
        public fun fromJson(value: JsonElement?, redactor: Redactor): RegistryEntry? {
            if (value !is JsonObject) return null
            val name = identifier(value["name"], redactor) ?: return null
            val version = identifier(value["version"], redactor) ?: return null
            return RegistryEntry(
                name = name,
                version = version,
                description = safeText(value["description"], 1000, redactor),
                title = safeText(value["title"], 120, redactor),
                remotes = items(value["remotes"]).mapNotNull { RegistryRemote.fromJson(it, redactor) },
                packages = items(value["packages"]).mapNotNull { RegistryPackage.fromJson(it, redactor) },
            )
        }
    }
}

/** Why a listing cannot be added from the catalogue (Dart `unsupportedReason`, fixed copy in the UI). */
public enum class RegistryUnsupported { NeedsConnectionDetails, NeedsReviewedCommand, NoHttpsEndpoint }

public class RegistryRemote internal constructor(
    public val type: String,
    public val url: String,
    /** Not evidence that OAuth is supported or that sign-in is required. */
    public val requiresConfiguration: Boolean,
    /** Header names the listing declares (never a value). */
    public val headers: List<RegistryVariable>,
) {
    internal fun toJson(redactor: Redactor): JsonObject =
        buildJsonObject {
            put("type", redactor.redact(type))
            put("url", redactor.redact(url))
            put("requiresConfiguration", requiresConfiguration)
            if (headers.isNotEmpty()) put("headers", JsonArray(headers.map { it.toJson() }))
        }

    internal companion object {
        private val UNSAFE_CHARS = Regex("""[{}\s]""")

        fun fromJson(value: JsonElement, redactor: Redactor): RegistryRemote? {
            if (value !is JsonObject) return null
            val type = value.string("type")
            if (type != "streamable-http" && type != "sse") return null
            val raw = value.string("url") ?: return null
            if (raw.length > 2048) return null
            val url = WebAddress.parse(raw) ?: return null
            if (url.scheme != "https" || !url.hasAuthority || url.host.isEmpty() || url.userInfo.isNotEmpty() ||
                url.hasQuery || url.hasFragment || UNSAFE_CHARS.containsMatchIn(raw) || unsafeEncoded(raw, redactor)
            ) {
                return null
            }
            val headers = value["headers"]
            val variables = value["variables"]
            return RegistryRemote(
                type = type,
                url = redactor.redact(raw),
                requiresConfiguration =
                    value.bool("requiresConfiguration") ||
                        (headers is JsonArray && headers.isNotEmpty()) ||
                        (variables is JsonObject && variables.isNotEmpty()),
                headers = items(headers).mapNotNull { RegistryVariable.fromJson(it, header = true, redactor) },
            )
        }

        private fun unsafeEncoded(raw: String, redactor: Redactor): Boolean {
            val decoded = WebAddress.decode(raw) ?: return true
            return redactor.containsSecret(decoded)
        }
    }
}

/**
 * A header or environment variable a listing declares: its name and whether it is required or secret. Values,
 * defaults, descriptions and formats are never retained.
 */
public data class RegistryVariable(val name: String, val required: Boolean = false, val secret: Boolean = false) {
    internal fun toJson(): JsonObject =
        buildJsonObject {
            put("name", name)
            if (required) put("isRequired", true)
            if (secret) put("isSecret", true)
        }

    internal companion object {
        private val ENV_NAME = Regex("""^[A-Za-z_][A-Za-z0-9_]{0,127}$""")
        private val HEADER_NAME = Regex("""^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$""")

        fun fromJson(value: JsonElement, header: Boolean, redactor: Redactor): RegistryVariable? {
            if (value !is JsonObject) return null
            val name = value.string("name") ?: return null
            if (!(if (header) HEADER_NAME else ENV_NAME).matches(name) || redactor.containsSecret(name)) return null
            return RegistryVariable(name, required = value.bool("isRequired"), secret = value.bool("isSecret"))
        }
    }
}

public class RegistryPackage internal constructor(
    public val registryType: String,
    public val identifier: String,
    public val version: String,
    /** `stdio`, `streamable-http` or `sse`; null when the listing names none. */
    public val transport: String?,
    public val runtimeHint: String?,
    public val environment: List<RegistryVariable>,
    /** Required runtime or package arguments exist; their values (a command line) are never retained. */
    public val requiresArguments: Boolean,
) {
    internal fun toJson(redactor: Redactor): JsonObject =
        buildJsonObject {
            put("registryType", redactor.redact(registryType))
            put("identifier", redactor.redact(identifier))
            put("version", redactor.redact(version))
            transport?.let { put("transport", it) }
            runtimeHint?.let { put("runtimeHint", it) }
            if (environment.isNotEmpty()) {
                put("environmentVariables", buildJsonArray { environment.forEach { add(it.toJson()) } })
            }
            if (requiresArguments) put("requiresArguments", true)
        }

    internal companion object {
        private val TRANSPORTS = setOf("stdio", "streamable-http", "sse")

        fun fromJson(value: JsonElement, redactor: Redactor): RegistryPackage? {
            if (value !is JsonObject) return null
            val type = identifier(value["registryType"], redactor) ?: return null
            val identifier = identifier(value["identifier"], redactor) ?: return null
            val version = identifier(value["version"], redactor) ?: return null
            val transport = value["transport"]
            val transportType = if (transport is JsonObject) transport.string("type") else value.string("transport")
            fun required(list: JsonElement?): Boolean =
                list is JsonArray && list.any { it is JsonObject && it.bool("isRequired") }
            return RegistryPackage(
                registryType = type,
                identifier = identifier,
                version = version,
                transport = transportType?.takeIf { it in TRANSPORTS },
                runtimeHint = identifier(value["runtimeHint"], redactor),
                environment =
                    items(value["environmentVariables"]).mapNotNull {
                        RegistryVariable.fromJson(it, header = false, redactor)
                    },
                requiresArguments =
                    value.bool("requiresArguments") ||
                        required(value["runtimeArguments"]) ||
                        required(value["packageArguments"]),
            )
        }
    }
}

// ---- Dart helpers (_items, _safeText, _identifier)

private val IDENTIFIER = Regex("""^[A-Za-z0-9@._/+-]+$""")
private val CONTROL = Regex("""[\x00-\x1f]""")

internal fun items(value: JsonElement?): List<JsonElement> = if (value is JsonArray) value.take(10) else emptyList()

internal fun safeText(value: JsonElement?, limit: Int, redactor: Redactor): String {
    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return ""
    val safe = CONTROL.replace(redactor.redact(text), " ")
    return if (safe.length > limit) safe.substring(0, limit) else safe
}

internal fun identifier(value: JsonElement?, redactor: Redactor): String? {
    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    if (text.isEmpty() || text.length > 256) return null
    if (!IDENTIFIER.matches(text) || redactor.containsSecret(text)) return null
    return redactor.redact(text)
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Dart `value[key] == true`: only a JSON boolean true. */
internal fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true
