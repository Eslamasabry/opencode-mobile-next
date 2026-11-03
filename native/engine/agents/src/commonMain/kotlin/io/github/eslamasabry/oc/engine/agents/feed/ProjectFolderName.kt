package io.github.eslamasabry.oc.engine.agents.feed

/** Why a new project folder name cannot be used; the screen says it in plain words (B-STATE-158). */
public enum class FolderNameProblem {
    /** Nothing typed ("Enter a folder name."). */
    Empty,

    /** `.`, `..` or a path with slashes ("Use a single folder name without slashes."). */
    NotOneFolder,

    /** Hidden, too long or odd characters ("Use letters, digits, dots, dashes or underscores…"). */
    BadCharacters,
}

/** Port of Dart `projectFolderNameProblem` (lib/domain/workspace_paths.dart). Pure. */
public object ProjectFolderName {
    public const val MAX_LENGTH: Int = 64
    private val safe = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    /** The trimmed name that would be created. */
    public fun clean(name: String): String = name.trim()

    /** Null when [name], trimmed, is one safe path segment. */
    public fun problem(name: String): FolderNameProblem? {
        val value = clean(name)
        return when {
            value.isEmpty() -> FolderNameProblem.Empty
            value == "." || value == ".." || '/' in value || '\\' in value -> FolderNameProblem.NotOneFolder
            !safe.matches(value) -> FolderNameProblem.BadCharacters
            else -> null
        }
    }
}
