package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Port of lib/domain/setup_registry.dart: metadata only. Registry content is untrusted and is never executed.

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.isTrue(): Boolean = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

private fun items(value: JsonElement?): List<JsonElement> = (value as? JsonArray)?.take(10) ?: emptyList()

private val IDENTIFIER = Regex("^[A-Za-z0-9@._/+-]+$")
private val CONTROL = Regex("[\\x00-\\x1f]")

/** Identifiers are metadata, never URLs, command lines or credential values. */
internal fun registryIdentifier(redactor: Redactor, value: JsonElement?): String? {
    val text = value.str() ?: return null
    if (text.isEmpty() || text.length > 256 || !IDENTIFIER.matches(text) || redactor.containsSecret(text)) return null
    return redactor.redact(text)
}

private fun safeText(redactor: Redactor, value: JsonElement?, limit: Int): String {
    val text = value.str() ?: return ""
    val safe = redactor.redact(text).replace(CONTROL, " ")
    return if (safe.length > limit) safe.substring(0, limit) else safe
}

public data class RegistryEntry(
    val name: String,
    val version: String,
    val description: String,
    /** The listing's display name (`title`), plain text; empty when absent. */
    val title: String,
    val remotes: List<RegistryRemote>,
    val packages: List<RegistryPackage>,
) {
    val id: String get() = "$name@$version"

    /** The UI must still ask the person to select the endpoint and review a diff. */
    val connectableRemote: RegistryRemote? get() = remotes.firstOrNull { !it.requiresConfiguration }

    val unsupportedReason: String?
        get() = when {
            connectableRemote != null -> null
            remotes.isNotEmpty() -> NEEDS_DETAILS
            packages.isNotEmpty() -> "Package installation needs a reviewed command on your server. Set it up by hand."
            else -> "This listing has no supported HTTPS endpoint. Set it up by hand."
        }

    public fun toJson(redactor: Redactor): JsonObject = buildJsonObject {
        put("name", redactor.redact(name))
        put("version", redactor.redact(version))
        put("description", redactor.redact(description))
        if (title.isNotEmpty()) put("title", redactor.redact(title))
        put("remotes", JsonArray(remotes.map { it.toJson(redactor) }))
        put("packages", JsonArray(packages.map { it.toJson(redactor) }))
    }

    public companion object {
        internal const val NEEDS_DETAILS: String = "This server needs connection details. Set it up by hand."

        public fun fromJson(redactor: Redactor, value: JsonElement?): RegistryEntry? {
            if (value !is JsonObject) return null
            val name = registryIdentifier(redactor, value["name"]) ?: return null
            val version = registryIdentifier(redactor, value["version"]) ?: return null
            return RegistryEntry(
                name = name,
                version = version,
                description = safeText(redactor, value["description"], 1000),
                title = safeText(redactor, value["title"], 120),
                remotes = items(value["remotes"]).mapNotNull { RegistryRemote.fromJson(redactor, it) },
                packages = items(value["packages"]).mapNotNull { RegistryPackage.fromJson(redactor, it) },
            )
        }
    }
}

/** A refused registry action; [message] is fixed application copy, never an HTTP body or raw exception. */
public data class SetupRegistryFailure(val message: String)

public data class RegistryRemote(
    val type: String,
    val url: String,
    /** Header values, defaults and descriptions are deliberately not retained. Not evidence of OAuth support. */
    val requiresConfiguration: Boolean,
    /** Header names only, so a form can ask for each value as a secret. */
    val headers: List<RegistryVariable> = emptyList(),
) {
    /** A disabled candidate only; applying it still needs the controller's validation and explicit confirmation. */
    public fun toMcpConfig(): SetupOutcome<JsonObject> =
        if (requiresConfiguration) {
            SetupOutcome.Failed(SetupFailure(SetupFailureCode.Unsupported, RegistryEntry.NEEDS_DETAILS))
        } else {
            SetupOutcome.Ok(
                buildJsonObject {
                    put("type", "remote")
                    put("url", url)
                    put("enabled", false)
                },
            )
        }

    public fun toJson(redactor: Redactor): JsonObject = buildJsonObject {
        put("type", redactor.redact(type))
        put("url", redactor.redact(url))
        put("requiresConfiguration", requiresConfiguration)
        if (headers.isNotEmpty()) put("headers", JsonArray(headers.map { it.toJson() }))
    }

    public companion object {
        private val UNSAFE_CHARS = Regex("[{}\\s]")

        public fun fromJson(redactor: Redactor, value: JsonElement?): RegistryRemote? {
            if (value !is JsonObject) return null
            val type = value["type"].str()
            val raw = value["url"].str()
            if (type != "streamable-http" && type != "sse") return null
            if (raw == null || raw.length > 2048) return null
            val url = SetupUri.parse(raw)
            if (url == null || url.scheme != "https" || url.host.isEmpty() || url.userInfo.isNotEmpty() ||
                url.hasQuery || url.hasFragment || UNSAFE_CHARS.containsMatchIn(raw) || unsafeEncodedUrl(redactor, raw)
            ) {
                return null
            }
            val headers = value["headers"]
            val variables = value["variables"]
            return RegistryRemote(
                type = type,
                url = redactor.redact(raw),
                requiresConfiguration = value["requiresConfiguration"].isTrue() ||
                    (headers is JsonArray && headers.isNotEmpty()) ||
                    (variables is JsonObject && variables.isNotEmpty()),
                headers = items(headers).mapNotNull { RegistryVariable.fromJson(redactor, it, header = true) },
            )
        }

        private fun unsafeEncodedUrl(redactor: Redactor, raw: String): Boolean {
            val decoded = SetupUri.decodeFull(raw) ?: return true
            return redactor.containsSecret(decoded)
        }
    }
}

/**
 * A header or environment variable a listing declares: name and whether it is required or secret. Value, default,
 * description and format are never retained (a "value" or "default" can carry a credential).
 */
public data class RegistryVariable(val name: String, val required: Boolean = false, val secret: Boolean = false) {
    public fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        if (required) put("isRequired", true)
        if (secret) put("isSecret", true)
    }

    public companion object {
        private val ENV_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$")
        private val HEADER_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")

        public fun fromJson(redactor: Redactor, value: JsonElement?, header: Boolean = false): RegistryVariable? {
            if (value !is JsonObject) return null
            val name = value["name"].str() ?: return null
            if (!(if (header) HEADER_NAME else ENV_NAME).matches(name) || redactor.containsSecret(name)) return null
            return RegistryVariable(name, required = value["isRequired"].isTrue(), secret = value["isSecret"].isTrue())
        }
    }
}

public data class RegistryPackage(
    val registryType: String,
    val identifier: String,
    val version: String,
    /** `stdio`, `streamable-http` or `sse`; null when the listing names none. */
    val transport: String? = null,
    /** The runner the listing suggests (`npx`, `uvx`, `docker`), an identifier only. */
    val runtimeHint: String? = null,
    /** Environment variable names the package declares (never values). */
    val environment: List<RegistryVariable> = emptyList(),
    /** Required runtime or package arguments exist. Their values are never retained: an argument list is a command line. */
    val requiresArguments: Boolean = false,
) {
    public fun toJson(redactor: Redactor): JsonObject = buildJsonObject {
        put("registryType", redactor.redact(registryType))
        put("identifier", redactor.redact(identifier))
        put("version", redactor.redact(version))
        transport?.let { put("transport", it) }
        runtimeHint?.let { put("runtimeHint", it) }
        if (environment.isNotEmpty()) put("environmentVariables", JsonArray(environment.map { it.toJson() }))
        if (requiresArguments) put("requiresArguments", true)
    }

    public companion object {
        private val TRANSPORTS = setOf("stdio", "streamable-http", "sse")

        public fun fromJson(redactor: Redactor, value: JsonElement?): RegistryPackage? {
            if (value !is JsonObject) return null
            val type = registryIdentifier(redactor, value["registryType"]) ?: return null
            val identifier = registryIdentifier(redactor, value["identifier"]) ?: return null
            val version = registryIdentifier(redactor, value["version"]) ?: return null
            val transport = value["transport"]
            val transportType = if (transport is JsonObject) transport["type"].str() else transport.str()
            fun required(list: JsonElement?): Boolean =
                list is JsonArray && list.any { it is JsonObject && it["isRequired"].isTrue() }
            return RegistryPackage(
                registryType = type,
                identifier = identifier,
                version = version,
                transport = transportType?.takeIf { it in TRANSPORTS },
                runtimeHint = registryIdentifier(redactor, value["runtimeHint"]),
                environment = items(value["environmentVariables"]).mapNotNull { RegistryVariable.fromJson(redactor, it) },
                requiresArguments = value["requiresArguments"].isTrue() ||
                    required(value["runtimeArguments"]) || required(value["packageArguments"]),
            )
        }
    }
}

/** A request the registry client makes: fixed origin, no credentials, no redirects. */
public data class RegistryRequest(
    val url: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val followRedirects: Boolean,
    val maxRedirects: Int,
    /** The transport stops reading (and answers [RegistryHttpReply.TooLarge]) past this many bytes. */
    val maxBytes: Int,
)

/** A cancel handle the store trips when the person goes offline, opts out or leaves. */
public class RegistryCancellation {
    public var isCancelled: Boolean = false
        private set

    public fun cancel() {
        isCancelled = true
    }
}

public sealed interface RegistryHttpReply {
    public class Body(public val status: Int, public val bytes: ByteArray) : RegistryHttpReply

    public data object TooLarge : RegistryHttpReply

    /** Network, timeout, TLS or cancellation: never carries the raw error. */
    public data object Failed : RegistryHttpReply
}

/** The unauthenticated public-catalog transport. Never hand it a server's authenticated client. */
public fun interface RegistryHttp {
    public suspend fun get(request: RegistryRequest, cancellation: RegistryCancellation): RegistryHttpReply
}

/** Explicitly invoked, unauthenticated, single-page public catalog request (port of `SetupRegistryClient`). */
public class SetupRegistryClient(private val http: RegistryHttp, private val redactor: Redactor) {
    /**
     * [search] narrows the list on the registry's side (its `search` parameter). It is the person's own words; a term
     * that looks like a credential is dropped and never sent.
     */
    public suspend fun fetch(search: String? = null, cancellation: RegistryCancellation = RegistryCancellation()): SetupOutcome<List<RegistryEntry>> {
        val term = searchTerm(search)
        val request = RegistryRequest(
            url = ENDPOINT,
            query = buildMap {
                put("limit", MAX_ENTRIES.toString())
                put("version", "latest")
                term?.let { put("search", it) }
            },
            headers = mapOf("Accept" to "application/json"),
            followRedirects = false,
            maxRedirects = 0,
            maxBytes = MAX_RESPONSE_BYTES,
        )
        val reply = http.get(request, cancellation)
        if (reply !is RegistryHttpReply.Body || reply.status != 200 || reply.bytes.size > MAX_RESPONSE_BYTES) return failed()
        val decoded = try {
            Json.parseToJsonElement(reply.bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (_: SerializationException) {
            return failed()
        } catch (_: CharacterCodingException) {
            return failed()
        } catch (_: IllegalArgumentException) {
            return failed()
        }
        val servers = (decoded as? JsonObject)?.get("servers") as? JsonArray ?: return failed()
        val result = ArrayList<RegistryEntry>()
        for (item in servers.take(MAX_ENTRIES)) {
            if (item !is JsonObject) continue
            val official = ((item["_meta"] as? JsonObject)?.get("io.modelcontextprotocol.registry/official") as? JsonObject)
            val status = official?.get("status").str()
            if (status == "deleted" || status == "deprecated") continue
            RegistryEntry.fromJson(redactor, item["server"])?.let(result::add)
        }
        return SetupOutcome.Ok(result.toList())
    }

    private fun searchTerm(value: String?): String? {
        val term = value?.trim() ?: ""
        if (term.isEmpty() || term.length > 100 || redactor.containsSecret(term)) return null
        return term
    }

    public companion object {
        public const val ENDPOINT: String = "https://registry.modelcontextprotocol.io/v0.1/servers"
        public const val MAX_RESPONSE_BYTES: Int = 512 * 1024
        public const val MAX_ENTRIES: Int = 100
        public const val LOAD_FAILED: String = "The public registry could not be loaded. Try again or set up by hand."

        private fun failed(): SetupOutcome.Failed = SetupOutcome.Failed(SetupFailure(SetupFailureCode.Transport, LOAD_FAILED))
    }
}

internal fun registryEntriesJson(redactor: Redactor, entries: List<RegistryEntry>): JsonArray =
    buildJsonArray { entries.forEach { add(it.toJson(redactor)) } }
