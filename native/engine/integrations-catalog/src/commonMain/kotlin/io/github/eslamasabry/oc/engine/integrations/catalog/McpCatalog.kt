package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.*

/** App binding: public registry requests never use the selected server's authenticated client. */
public interface McpCatalogHost {
    public suspend fun loadCache(profile: String): Outcome<JsonObject?>

    public suspend fun saveCache(profile: String, value: JsonObject): Outcome<Unit>

    public suspend fun saveRefreshedCache(profile: String, value: JsonObject): Outcome<Unit>

    public suspend fun fetchRegistry(query: String?): Outcome<JsonObject>

    public fun sourceIdentity(profile: String): Any?

    public fun current(profile: String, directory: String?, workspace: String?): Boolean

    public fun capabilities(profile: String): ServerCapabilities

    public suspend fun inventory(
        profile: String,
        directory: String?,
        workspace: String?,
    ): Outcome<List<McpServerInfo>>

    public suspend fun add(
        profile: String,
        directory: String?,
        workspace: String?,
        draft: McpServerDraft,
    ): Outcome<Unit>
}

public enum class CatalogRuntime {
    Hosted,
    Node,
    Python,
    Docker,
    Unavailable,
}

/**
 * Only metadata and variable names survive parsing. Registry defaults and credential values are
 * discarded.
 */
public data class McpCatalogEntry(
    val id: String,
    val version: String,
    val title: String,
    val description: String,
    val runtime: CatalogRuntime,
    val draft: McpServerDraft?,
    val requiresArguments: Boolean,
) {
    public val serverName: String
        get() = catalogServerName(id)
}

public fun catalogServerName(id: String): String {
    fun clean(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '_')
    val parts = id.split('/').filter { it.isNotEmpty() }
    var name = clean(parts.lastOrNull().orEmpty())
    if (parts.size > 1 && name in setOf("mcp", "mcp-server", "mcpserver", "server", "remote")) {
        val prefixes =
            setOf(
                "io",
                "com",
                "ai",
                "ac",
                "dev",
                "app",
                "org",
                "net",
                "co",
                "github",
                "gitlab",
                "www",
            )
        clean(parts.first().split('.').filter { it.lowercase() !in prefixes }.joinToString("-"))
            .takeIf { it.isNotEmpty() }
            ?.let { name = it }
    }
    return name.take(64).ifEmpty { "mcp-server" }
}

private val CATALOG_IDENTIFIER = Regex("^[A-Za-z0-9@._/+\\-]+$")
private val CATALOG_URL =
    Regex("\\b(?:[a-z][a-z0-9+.-]*:(?://)?|www\\.)[^\\s<>]+", RegexOption.IGNORE_CASE)
private val CATALOG_SPACING =
    Regex("[\\s\\x00-\\x1f\\x7f-\\x9f\\u061c\\u200b-\\u200f\\u202a-\\u202e\\u2060-\\u206f]+")

private fun JsonObject.flag(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.rows(key: String): List<JsonObject> =
    (this[key] as? JsonArray).orEmpty().take(10).mapNotNull { it as? JsonObject }

private fun identifier(value: String?, redactor: Redactor): String? = value?.takeIf {
    it.length in 1..256 && CATALOG_IDENTIFIER.matches(it) && !redactor.containsSecret(it)
}

private fun prose(value: String, limit: Int, redactor: Redactor): String =
    redactor
        .redact(redactor.redact(value).replace(CATALOG_URL, " ").replace(CATALOG_SPACING, " "))
        .trim()
        .takeCodePoints(limit)

public fun parseMcpCatalogEntry(value: JsonObject, redactor: Redactor): McpCatalogEntry? {
    val id = identifier(value.string("name"), redactor) ?: return null
    val version = identifier(value.string("version"), redactor) ?: return null
    val name = catalogServerName(id)
    val title = prose(value.string("title").orEmpty(), 120, redactor)
    val description = prose(value.string("description").orEmpty(), 1000, redactor)
    fun variables(rows: List<JsonObject>, headers: Boolean): Map<String, String> =
        rows
            .filter { it.flag("isRequired") }
            .mapNotNull { row ->
                row.string("name")?.takeIf {
                    Regex(
                            if (headers) "^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$"
                            else "^[A-Za-z_][A-Za-z0-9_]{0,127}$"
                        )
                        .matches(it) && !redactor.containsSecret(it)
                }
            }
            .associateWith { "" }
    fun draft(
        kind: McpServerKind,
        url: String? = null,
        command: List<String> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
    ) =
        McpServerDraft(
            name,
            kind,
            url,
            command.toImmutableList(),
            null,
            headers.toImmutableMap(),
            environment.toImmutableMap(),
            false,
            null,
        )
    val remotes =
        value.rows("remotes").filter { remote ->
            val raw = remote.string("url").orEmpty()
            val url = CatalogUrl.parse(raw)
            remote.string("type") in setOf("streamable-http", "sse") &&
                raw.length <= 2048 &&
                url != null &&
                url.scheme == "https" &&
                url.host.isNotEmpty() &&
                url.userInfo.isEmpty() &&
                url.rawQuery == null &&
                '#' !in raw &&
                !Regex("[{}\\s]").containsMatchIn(raw) &&
                !redactor.containsSecret(raw) &&
                !redactor.containsSecret(CatalogUrl.decode(raw))
        }
    val remote =
        remotes.firstOrNull {
            it.rows("headers").isEmpty() &&
                !it.flag("requiresConfiguration") &&
                (it["variables"] as? JsonObject).isNullOrEmpty()
        } ?: remotes.firstOrNull()
    if (remote != null) {
        val headers = variables(remote.rows("headers"), true)
        val unfillable =
            remote.flag("requiresConfiguration") && headers.isEmpty() ||
                !(remote["variables"] as? JsonObject).isNullOrEmpty()
        return McpCatalogEntry(
            id,
            version,
            title,
            description,
            CatalogRuntime.Hosted,
            draft(McpServerKind.Remote, remote.string("url"), headers = headers),
            unfillable,
        )
    }
    val packages = value.rows("packages")
    val pkg = packages.firstOrNull {
        it.string("registryType") in setOf("npm", "pypi") &&
            ((it["transport"] as? JsonObject)?.string("type") ?: it.string("transport")) in
                setOf(null, "stdio") &&
            identifier(it.string("identifier"), redactor) != null &&
            identifier(it.string("version"), redactor) != null
    }
    if (pkg == null)
        return McpCatalogEntry(
            id,
            version,
            title,
            description,
            if (packages.any { it.string("registryType") == "oci" }) CatalogRuntime.Docker
            else CatalogRuntime.Unavailable,
            null,
            false,
        )
    val node = pkg.string("registryType") == "npm"
    val command =
        if (node) listOf("npx", "-y", "${pkg.string("identifier")}@${pkg.string("version")}")
        else listOf("uvx", "${pkg.string("identifier")}==${pkg.string("version")}")
    val arguments = pkg.rows("runtimeArguments") + pkg.rows("packageArguments")
    return McpCatalogEntry(
        id,
        version,
        title,
        description,
        if (node) CatalogRuntime.Node else CatalogRuntime.Python,
        draft(
            McpServerKind.Local,
            command = command,
            environment = variables(pkg.rows("environmentVariables"), false),
        ),
        pkg.flag("requiresArguments") || arguments.any { it.flag("isRequired") },
    )
}

/** Both cache and wire formats are accepted; no more than 100 metadata records are examined. */
public fun parseMcpCatalog(payload: JsonObject, redactor: Redactor): List<McpCatalogEntry> {
    val raw = (payload["servers"] ?: payload["entries"]) as? JsonArray ?: return emptyList()
    return raw.take(100).mapNotNull { row ->
        val item = row as? JsonObject ?: return@mapNotNull null
        val status =
            (((item["_meta"] as? JsonObject)?.get("io.modelcontextprotocol.registry/official"))
                    as? JsonObject)
                ?.string("status")
        if (status in setOf("deleted", "deprecated")) null
        else parseMcpCatalogEntry((item["server"] as? JsonObject) ?: item, redactor)
    }
}

/**
 * A bounded cache containing sanitized metadata; never header/environment values or registry
 * defaults.
 */
public fun encodeMcpCatalog(entries: List<McpCatalogEntry>): JsonArray =
    JsonArray(
        entries.take(100).map { entry ->
            buildJsonObject {
                put("name", entry.id)
                put("version", entry.version)
                put("title", entry.title)
                put("description", entry.description)
                val draft = entry.draft
                if (draft?.kind == McpServerKind.Remote)
                    put(
                        "remotes",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "streamable-http")
                                    put("url", draft.url.orEmpty())
                                    put("requiresConfiguration", entry.requiresArguments)
                                    put(
                                        "headers",
                                        buildJsonArray {
                                            draft.headers.keys.forEach { name ->
                                                add(
                                                    buildJsonObject {
                                                        put("name", name)
                                                        put("isRequired", true)
                                                        put("isSecret", true)
                                                    }
                                                )
                                            }
                                        },
                                    )
                                }
                            )
                        },
                    )
                if (draft?.kind == McpServerKind.Local)
                    put(
                        "packages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    val node = entry.runtime == CatalogRuntime.Node
                                    val spec = draft.command.last()
                                    put("registryType", if (node) "npm" else "pypi")
                                    put(
                                        "identifier",
                                        if (node) spec.substringBeforeLast('@')
                                        else spec.substringBefore("=="),
                                    )
                                    put(
                                        "version",
                                        if (node) spec.substringAfterLast('@')
                                        else spec.substringAfter("=="),
                                    )
                                    put(
                                        "environmentVariables",
                                        buildJsonArray {
                                            draft.environment.keys.forEach { name ->
                                                add(
                                                    buildJsonObject {
                                                        put("name", name)
                                                        put("isRequired", true)
                                                        put("isSecret", true)
                                                    }
                                                )
                                            }
                                        },
                                    )
                                    if (entry.requiresArguments)
                                        put(
                                            "runtimeArguments",
                                            buildJsonArray {
                                                add(buildJsonObject { put("isRequired", true) })
                                            },
                                        )
                                }
                            )
                        },
                    )
                if (entry.runtime == CatalogRuntime.Docker)
                    put(
                        "packages",
                        buildJsonArray { add(buildJsonObject { put("registryType", "oci") }) },
                    )
            }
        }
    )

public fun orderMcpCatalog(
    entries: List<McpCatalogEntry>,
    installed: Set<String>,
): List<McpCatalogEntry> = entries.sortedBy {
    if (it.serverName in installed) 0 else if (it.draft != null) 1 else 2
}

/**
 * Protocol result only: no prose from the registry, URLs, commands, keys or inferred OAuth
 * capability.
 */
public fun searchMcpCatalog(
    arguments: JsonObject,
    entries: List<McpCatalogEntry>?,
    connected: Map<String, Boolean>?,
    redactor: Redactor,
): JsonObject? {
    if (arguments.keys.any { it !in setOf("query", "limit") }) return null
    val query = arguments.string("query")?.trim() ?: return null
    val limitValue = arguments["limit"]
    val limit =
        if (limitValue == null) 5
        else (limitValue as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
    if (
        query.isBlank() ||
            query.takeCodePoints(101).length > query.takeCodePoints(100).length ||
            redactor.containsSecret(query) ||
            CATALOG_URL.containsMatchIn(query) ||
            limit !in 1..10
    )
        return null
    if (entries == null)
        return buildJsonObject {
            put("status", "catalogue_not_loaded")
            put(
                "message",
                "The connector catalogue could not be loaded right now. Try again in a moment.",
            )
            put("matches", JsonArray(emptyList()))
        }
    val terms = prose(query, 100, redactor).lowercase().split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return null
    val matches =
        entries
            .take(100)
            .filter { entry ->
                Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$").matches(entry.id) &&
                    terms.all { term ->
                        "${entry.id} ${entry.title} ${entry.description}".lowercase().contains(term)
                    }
            }
            .sortedWith(
                compareBy<McpCatalogEntry> { it.id.lowercase() }
                    .thenBy { it.version }
                    .thenBy { it.title }
                    .thenBy { it.description }
            )
            .distinctBy { it.id }
            .take(limit)
    return buildJsonObject {
        put("status", "ok")
        put(
            "matches",
            buildJsonArray {
                matches.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("catalogId", entry.id)
                            put("name", entry.title.ifEmpty { entry.serverName })
                            put("description", entry.description.takeCodePoints(300))
                            put(
                                "runtime",
                                when (entry.runtime) {
                                    CatalogRuntime.Hosted -> "hosted"
                                    CatalogRuntime.Node -> "npx"
                                    CatalogRuntime.Python -> "uvx"
                                    CatalogRuntime.Docker -> "docker"
                                    CatalogRuntime.Unavailable -> "unavailable"
                                },
                            )
                            put("needsSignIn", "unknown")
                            put(
                                "connected",
                                connected?.let { JsonPrimitive(it[entry.serverName] ?: false) }
                                    ?: JsonNull,
                            )
                        }
                    )
                }
            },
        )
    }
}
