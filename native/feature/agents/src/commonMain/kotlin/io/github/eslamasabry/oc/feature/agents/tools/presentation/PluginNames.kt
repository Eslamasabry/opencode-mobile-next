package io.github.eslamasabry.oc.feature.agents.tools.presentation

import io.github.eslamasabry.oc.core.domain.LibraryPlugin
import io.github.eslamasabry.oc.core.domain.LibraryPluginSource
import io.github.eslamasabry.oc.core.domain.LibraryPluginStatus

/**
 * A plugin's name as a person reads it (Flutter `readablePluginName`, server_plugins_section.dart:408-478): the scope,
 * version, path, the first dotted part and category words are dropped, camelCase and dashes become words, known
 * acronyms keep their capitals and the first word is sentence case. `opencode.tool.input.repair` reads "Input
 * repair", `opencode.vcs.git` "Vcs GIT", `@acme/opencode-plugin-lint@1.2` "Lint". The raw id stays under Details.
 */
public fun readablePluginName(id: String): String {
    var name = id.trim()
    if (name.startsWith("@")) {
        val slash = name.indexOf('/')
        if (slash > 0) name = name.substring(slash + 1)
    }
    val version = name.indexOf('@')
    if (version > 0) name = name.substring(0, version)
    if (name.contains('/')) name = name.split('/').last()
    var parts = name.split('.').filter { it.isNotEmpty() }
    if (parts.size > 1) parts = parts.drop(1)
    val categories = setOf("tool", "tools", "config", "plugin", "plugins", "provider", "providers", "builtin", "core")
    val rest = parts.toMutableList()
    while (rest.size > 1 && rest.first().lowercase() in categories) rest.removeAt(0)
    val words =
        rest
            .flatMap { part ->
                part.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").split(Regex("[-_\\s]+")).filter { it.isNotEmpty() }
            }
            .toMutableList()
    while (words.size > 1 && words.first().lowercase() in setOf("opencode", "plugin")) words.removeAt(0)
    if (words.isEmpty()) return id
    return words
        .mapIndexed { i, raw ->
            val word = raw.lowercase()
            ACRONYMS[word] ?: if (i == 0) word.replaceFirstChar { it.uppercaseChar() } else word
        }
        .joinToString(" ")
}

private val ACRONYMS =
    listOf("ai", "api", "cli", "git", "http", "https", "id", "json", "llm", "lsp", "mcp", "pr", "sdk", "ssh", "tui", "ui", "url")
        .associateWith { it.uppercase() }

/**
 * A6 (sweep-b 2026-11-11), as Flutter's plugins section does: what the person added is listed openly; the server's
 * own plugins fold into one row ("Built in · N active"), open from the start when one of them failed.
 */
public data class PluginGroups(val added: List<LibraryPlugin>, val builtIn: List<LibraryPlugin>) {
    val builtInActive: Int
        get() = builtIn.count { it.status == LibraryPluginStatus.Active }

    val builtInFailed: Int
        get() = builtIn.count { it.status == LibraryPluginStatus.Failed }

    public companion object {
        public fun of(plugins: List<LibraryPlugin>): PluginGroups =
            plugins.partition { it.source != LibraryPluginSource.Builtin }.let { (added, builtIn) ->
                PluginGroups(added, builtIn)
            }
    }
}
