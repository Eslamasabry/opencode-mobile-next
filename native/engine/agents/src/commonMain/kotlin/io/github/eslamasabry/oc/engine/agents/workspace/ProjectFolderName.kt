package io.github.eslamasabry.oc.engine.agents.workspace

/** Why a new project folder name cannot be used (Flutter `projectFolderNameProblem`, B-STATE-158). */
public enum class ProjectFolderNameProblem {
    /** Nothing but spaces. */
    Empty,

    /** `.`, `..` or a name with a slash or backslash: a path, not one folder. */
    NotSingleSegment,

    /** Not letters, digits, dot, dash or underscore starting with a letter or digit, up to 64 characters. */
    InvalidCharacters,
}

public object ProjectFolderName {
    private val SAFE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    /** The problem with [name] after trimming, or null when it is one safe path segment. */
    public fun problem(name: String): ProjectFolderNameProblem? {
        val value = name.trim()
        if (value.isEmpty()) return ProjectFolderNameProblem.Empty
        if (value == "." || value == ".." || '/' in value || '\\' in value) return ProjectFolderNameProblem.NotSingleSegment
        if (!SAFE.matches(value)) return ProjectFolderNameProblem.InvalidCharacters
        return null
    }

    /** The folder name to create: [name] trimmed, or null when it has a [problem]. */
    public fun validated(name: String): String? = if (problem(name) == null) name.trim() else null
}

/**
 * EN wording for [ProjectFolderNameProblem], copied from `lib/domain/workspace_paths.dart` (the Flutter app has these
 * only in English, not in the ARB files). Kept here for the parity port; the UI words module owns copy.
 */
public object ProjectFolderNameWords {
    public fun en(problem: ProjectFolderNameProblem): String = when (problem) {
        ProjectFolderNameProblem.Empty -> "Enter a folder name."
        ProjectFolderNameProblem.NotSingleSegment -> "Use a single folder name without slashes."
        ProjectFolderNameProblem.InvalidCharacters ->
            "Use letters, digits, dots, dashes or underscores, starting with a letter or digit (up to 64 characters)."
    }
}
