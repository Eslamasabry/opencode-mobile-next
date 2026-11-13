package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.model.PermissionRequest

/**
 * What an Always allow answer would cover, as a choice the screen words (review 2026-11-13 #3: a raw `*` is not a
 * scope a person can read). The raw patterns stay available as the technical detail.
 */
public sealed interface AlwaysScope {
    /** `*` on a file change: "any file change". */
    public data object AnyFileChange : AlwaysScope

    /** File patterns: "file changes matching" a pattern. */
    public data class FileChangesMatching(val patterns: String) : AlwaysScope

    /** `*` on a command: "every command". */
    public data object AnyCommand : AlwaysScope

    /** `npm install *`: "npm install with any arguments". */
    public data class CommandFamily(val command: String) : AlwaysScope

    /** One exact command or value, as it is. */
    public data class Exactly(val text: String) : AlwaysScope

    /** `*` (or the tool's own name) on any other ask: "every request like this". */
    public data object Everything : AlwaysScope

    /** Anything else: "requests matching …". */
    public data class Matching(val patterns: String) : AlwaysScope
}

public object AlwaysScopes {
    private val fileActions = setOf("edit", "write", "multiedit", "patch", "apply_patch")
    private val shellActions = setOf("bash", "shell")

    /** Null when the request saves nothing. */
    public fun of(request: PermissionRequest): AlwaysScope? {
        val saved = request.always.map(String::trim).filter(String::isNotEmpty).distinct()
        if (saved.isEmpty()) return null
        val action = request.permission.trim().lowercase()
        val joined = saved.joinToString(", ")
        val all = saved.all { it == "*" }
        return when {
            action in fileActions -> if (all) AlwaysScope.AnyFileChange else AlwaysScope.FileChangesMatching(joined)
            action in shellActions -> {
                val one = saved.singleOrNull()
                when {
                    all -> AlwaysScope.AnyCommand
                    one != null && one.endsWith(" *") && '*' !in one.dropLast(2) -> AlwaysScope.CommandFamily(one.dropLast(2).trim())
                    one != null && '*' !in one -> AlwaysScope.Exactly(one)
                    else -> AlwaysScope.Matching(joined)
                }
            }
            all || saved == listOf(request.permission) -> AlwaysScope.Everything
            else -> AlwaysScope.Matching(joined)
        }
    }
}
