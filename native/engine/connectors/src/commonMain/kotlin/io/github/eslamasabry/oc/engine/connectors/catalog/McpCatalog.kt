package io.github.eslamasabry.oc.engine.connectors.catalog

import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryPackage
import io.github.eslamasabry.oc.engine.connectors.text.WebAddress
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** What a catalogue server needs on the machine that runs it (Dart `McpCatalogRuntime`). */
public enum class McpCatalogRuntime {
    /** Someone else hosts it; the server only connects to its address. */
    Hosted,

    /** An npm package started with `npx` (Node). */
    Node,

    /** A PyPI package started with `uvx` (Python with uv). */
    Python,

    /** A container image (Docker). The catalogue does not add these. */
    Docker,

    /** No address or package the app can use. */
    None,
}

/**
 * One registry listing as the MCP catalogue shows it (Dart `McpCatalogItem`, lib/domain/mcp_catalog.dart): what it is,
 * where it runs, what it needs, and the draft the manual form opens with when it is turned on. The draft holds only
 * validated identifiers, an HTTPS address without credentials, and variable NAMES with empty values.
 */
public class McpCatalogItem private constructor(
    public val entry: RegistryEntry,
    /** The server name the form suggests and the name looked for in the server's list to show the row as on. */
    public val serverName: String,
    public val runtime: McpCatalogRuntime,
    /** The form's starting point; null when the catalogue cannot add it (docker or none). */
    public val draft: McpServerDraft? = null,
    /** The host of a hosted server's address. */
    public val hostedBy: String? = null,
    /** The listing declares a required secret (an API key or token). */
    public val needsKey: Boolean = false,
    /** The listing declares other required settings or arguments. */
    public val needsSettings: Boolean = false,
) {
    public val title: String get() = entry.title.ifEmpty { serverName }
    public val description: String get() = entry.description
    public val addable: Boolean get() = draft != null

    public companion object {
        public fun from(entry: RegistryEntry): McpCatalogItem {
            val name = catalogServerName(entry.name)
            val remote = entry.connectableRemote ?: entry.remotes.firstOrNull()
            if (remote != null) {
                val declared = remote.headers
                // Only required names become rows the form insists on; optional headers are added by hand.
                val headers = declared.filter { it.required }
                return McpCatalogItem(
                    entry = entry,
                    serverName = name,
                    runtime = McpCatalogRuntime.Hosted,
                    hostedBy = WebAddress.parse(remote.url)?.host,
                    needsKey = declared.any { it.secret && it.required },
                    needsSettings =
                        declared.any { it.required && !it.secret } ||
                            (remote.requiresConfiguration && declared.isEmpty()),
                    draft =
                        draft(
                            name = name,
                            kind = McpServerKind.Remote,
                            url = remote.url,
                            headers = headers.associate { it.name to "" },
                        ),
                )
            }
            val pkg =
                runnable(entry.packages)
                    ?: return McpCatalogItem(
                        entry = entry,
                        serverName = name,
                        runtime =
                            if (entry.packages.any { it.registryType == "oci" }) {
                                McpCatalogRuntime.Docker
                            } else {
                                McpCatalogRuntime.None
                            },
                    )
            val node = pkg.registryType == "npm"
            val environment = pkg.environment.filter { it.required }
            return McpCatalogItem(
                entry = entry,
                serverName = name,
                runtime = if (node) McpCatalogRuntime.Node else McpCatalogRuntime.Python,
                needsKey = environment.any { it.secret },
                needsSettings = pkg.requiresArguments || environment.any { !it.secret },
                draft =
                    draft(
                        name = name,
                        kind = McpServerKind.Local,
                        command =
                            if (node) {
                                listOf("npx", "-y", "${pkg.identifier}@${pkg.version}")
                            } else {
                                listOf("uvx", "${pkg.identifier}==${pkg.version}")
                            },
                        environment = environment.associate { it.name to "" },
                    ),
            )
        }

        /** The first package the catalogue can start: npm or PyPI over stdio. */
        private fun runnable(packages: List<RegistryPackage>): RegistryPackage? =
            packages.firstOrNull {
                (it.registryType == "npm" || it.registryType == "pypi") &&
                    (it.transport == null || it.transport == "stdio")
            }

        private fun draft(
            name: String,
            kind: McpServerKind,
            url: String? = null,
            command: List<String> = emptyList(),
            headers: Map<String, String> = emptyMap(),
            environment: Map<String, String> = emptyMap(),
        ): McpServerDraft =
            McpServerDraft(
                name = name,
                kind = kind,
                url = url,
                command = if (command.isEmpty()) persistentListOf() else command.toImmutableList(),
                cwd = null,
                headers = headers.toImmutableMap(),
                environment = environment.toImmutableMap(),
                detectOAuth = true, // the Dart draft default
                timeoutMs = null,
            )
    }
}

private val GENERIC_NAMES = setOf("mcp", "mcp-server", "mcpserver", "server", "remote")
private val NAMESPACE_PREFIXES =
    setOf("io", "com", "ai", "ac", "dev", "app", "org", "net", "co", "github", "gitlab", "www")
private val NOT_NAME = Regex("[^a-z0-9_-]+")
private val DASHES = Regex("-{2,}")
private val EDGES = Regex("^[-_]+|[-_]+$")

/**
 * The MCP server name for registry listing [registryName] (`io.github.acme/weather-mcp` → `weather-mcp`): its last
 * path segment, or, when that is only a generic word (`mcp`, `mcp-server`), its namespace without the reversed-domain
 * prefix (`ac.inference.sh/mcp` → `inference-sh`). Lower case; anything but letters, digits, `-` and `_` becomes `-`;
 * at most 64 characters; `mcp-server` when nothing is left.
 */
public fun catalogServerName(registryName: String): String {
    fun clean(value: String): String =
        EDGES.replace(DASHES.replace(NOT_NAME.replace(value.lowercase(), "-"), "-"), "")
    val parts = registryName.split('/').filter { it.isNotEmpty() }
    var name = clean(parts.lastOrNull() ?: registryName)
    if (parts.size > 1 && name in GENERIC_NAMES) {
        val labels = parts.first().split('.').filter { it.lowercase() !in NAMESPACE_PREFIXES }
        val namespace = clean(labels.joinToString("-"))
        if (namespace.isNotEmpty()) name = namespace
    }
    if (name.length > 64) name = name.substring(0, 64)
    return name.ifEmpty { "mcp-server" }
}

/**
 * The catalogue's rows in the page's order: those already on this server first, then those it can add, then those
 * it cannot. Within each group the registry's own order holds.
 */
public fun orderCatalog(entries: Iterable<RegistryEntry>, installed: Set<String>): List<McpCatalogItem> =
    entries.map(McpCatalogItem::from).sortedBy {
        when {
            it.serverName in installed -> 0
            it.addable -> 1
            else -> 2
        }
    }
