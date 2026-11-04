package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.engine.setupassist.RegistryEntry
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Registry metadata is only a starting point for the manual form; selecting a listing never
 * executes it.
 */
public data class LibraryRegistryItem(
    val entry: RegistryEntry,
    val name: String,
    val draft: McpServerDraft?,
    val node: Boolean,
    val needsSettings: Boolean,
)

public fun libraryRegistryItem(entry: RegistryEntry): LibraryRegistryItem {
    fun clean(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '_')
    val parts = entry.name.split('/').filter(String::isNotEmpty)
    var name = clean(parts.lastOrNull() ?: entry.name)
    if (parts.size > 1 && name in setOf("mcp", "mcp-server", "mcpserver", "server", "remote")) {
        val namespace =
            clean(
                parts
                    .first()
                    .split('.')
                    .filter {
                        it.lowercase() !in
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
                    }
                    .joinToString("-")
            )
        if (namespace.isNotEmpty()) name = namespace
    }
    name = name.take(64).ifEmpty { "mcp-server" }
    val remote = entry.connectableRemote ?: entry.remotes.firstOrNull()
    if (remote != null)
        return LibraryRegistryItem(
            entry,
            name,
            McpServerDraft(
                name,
                McpServerKind.Remote,
                remote.url,
                persistentListOf(),
                null,
                remote.headers.filter { it.required }.associate { it.name to "" }.toImmutableMap(),
                persistentMapOf(),
                true,
                null,
            ),
            false,
            remote.requiresConfiguration,
        )
    val pkg =
        entry.packages.firstOrNull {
            it.registryType in setOf("npm", "pypi") &&
                (it.transport == null || it.transport == "stdio")
        } ?: return LibraryRegistryItem(entry, name, null, false, false)
    val node = pkg.registryType == "npm"
    return LibraryRegistryItem(
        entry,
        name,
        McpServerDraft(
            name,
            McpServerKind.Local,
            null,
            (if (node) listOf("npx", "-y", "${pkg.identifier}@${pkg.version}")
                else listOf("uvx", "${pkg.identifier}==${pkg.version}"))
                .toImmutableList(),
            null,
            persistentMapOf(),
            pkg.environment.filter { it.required }.associate { it.name to "" }.toImmutableMap(),
            true,
            null,
        ),
        node,
        pkg.requiresArguments || pkg.environment.any { it.required },
    )
}
