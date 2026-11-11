package io.github.eslamasabry.oc.core.model

import kotlinx.collections.immutable.ImmutableList

// Workspace files, search, VCS and code health (models_requests.dart, value_types_core.dart).

public data class FileNode(val name: String, val path: String, val isDir: Boolean)

/** [type] is `text` or `binary`; [encoding] is set for base64 content. */
public data class FileContent(val content: String, val type: String, val encoding: String?, val mimeType: String?)

public data class FindMatch(val path: String, val lineNumber: Int, val snippet: String)

/** Dart FileDiff. */
public data class FileDiff(
    val file: String,
    val before: String? = null,
    val after: String? = null,
    val patch: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val status: String? = null,
)

public enum class VcsDiffMode { WorkingTree, Branch }

public enum class VersionControlSetupState { Git, Absent, Unknown }

public data class VersionControlFile(val path: String, val status: String, val additions: Int, val deletions: Int)

public data class VersionControlHealth(
    val branch: String?,
    val defaultBranch: String?,
    val changes: ImmutableList<VersionControlFile>,
    val setupState: VersionControlSetupState,
)

/**
 * The one rule for "is this a Git project" that the chat header, the Changes page and the new-chat start share:
 * the server's own setup state first, then the project record's flag, then a branch name or the project root's
 * listing. null = not known (not loaded yet, or the server did not say): callers show nothing about Git then, never
 * "Git" and never "not a Git project".
 *
 * [rootHasDotGit] is whether a listing of the project's ROOT folder (the folder the server names as the project's
 * directory) holds `.git`; null when no such listing was read. Only a root listing may say "no": a subfolder of a
 * repository has no `.git` of its own and is still in Git (the Git page's own `git status` finds the repository above
 * it). An OpenCode 2.2.x server's `GET /vcs` answers "no provider" for a folder it has not looked at yet, so its
 * silence is Unknown, never Absent; a server whose answer is final (Paseo) says Absent itself.
 */
public fun gitProjectState(
    health: VersionControlHealth?,
    projectIsGit: Boolean? = null,
    rootHasDotGit: Boolean? = null,
): Boolean? =
    when (health?.setupState) {
        VersionControlSetupState.Git -> true
        VersionControlSetupState.Absent -> false
        else ->
            projectIsGit
                ?: when {
                    !health?.branch.isNullOrBlank() || rootHasDotGit == true -> true
                    rootHasDotGit == false -> false
                    else -> null
                }
    }

public data class LanguageServiceHealth(val id: String, val name: String, val root: String, val status: String)

public data class FormatterHealth(val name: String, val extensions: ImmutableList<String>, val enabled: Boolean)

public data class WorkspaceSymbol(val name: String, val kind: Int, val path: String, val line: Int, val column: Int)
