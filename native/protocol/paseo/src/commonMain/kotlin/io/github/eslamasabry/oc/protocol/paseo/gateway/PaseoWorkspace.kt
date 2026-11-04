package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/*
 * Files, Changes and Worktrees of the folder a Paseo gateway is scoped to (ports of lib/paseo/gateway/workspace_base,
 * files, changes and worktrees; B-AGENT-156, 157, 158). Every request checks the folder did not change while it ran,
 * and a refusal is plain: the daemon's words never reach a Failure (not even Details).
 */

/**
 * Biggest file the viewer asks for; the daemon refuses larger ones and a bigger frame would drop
 * the connection.
 */
internal const val PASEO_MAX_FILE_BYTES: Int = 2 * 1024 * 1024

/** Runs [block] on the gateway's lane and turns module failures into a Failure. */
internal suspend fun <T> PaseoGateway.laneCall(block: suspend () -> T): Outcome<T> =
    withContext(lane) { paseoAttempt { block() } }

/**
 * One daemon request for the open folder; a folder change while it ran fails ScopeMismatch.
 * Refusals carry no text.
 */
internal suspend fun PaseoGateway.workspaceRequest(
    type: String,
    body: JsonObject,
    mutation: Boolean = false,
    timeoutMs: Long? = null,
): JsonObject {
    val scope = scopeDir
    val epoch = locationEpoch
    val result =
        try {
            transport.request(
                type,
                body,
                mutation = mutation,
                timeoutMs = timeoutMs,
                beforeSend = { checkLocation(scope, epoch) },
            )
        } catch (e: PaseoException) {
            throw PaseoException(e.kind)
        }
    checkLocation(scope, epoch)
    return result
}

/** Checkout answers carry their failure as `{code, message}`: a plain Unavailable. */
private fun requireCheckoutOk(payload: JsonObject) {
    val error = payload["error"]
    if (error != null && error !is kotlinx.serialization.json.JsonNull)
        paseoFail(PaseoFailureKind.Unavailable)
}

internal fun folderName(directory: String): String =
    directory.split('/').lastOrNull { it.isNotEmpty() } ?: directory

/** A path inside the open folder, relative, with `.` for the folder itself. */
internal fun PaseoGateway.relativeToScope(path: String): String {
    var value = path.trim()
    val root = scopeDir
    if (value == root || value == "$root/") return "."
    if (value.startsWith("$root/")) value = value.substring(root.length + 1)
    while (value.startsWith("./")) value = value.substring(2)
    return value.ifEmpty { "." }
}

/**
 * Where the daemon reads [path]: inside the folder relative to it, an absolute path outside it from
 * `/`.
 */
private fun PaseoGateway.readTarget(path: String): Pair<String, String> {
    val value = path.trim()
    val root = scopeDir
    val outside = value.startsWith("/") && value != root && !value.startsWith("$root/")
    return if (outside) "/" to value else root to relativeToScope(value)
}

// ---- Files (file_explorer_request, directory_suggestions_request)

internal suspend fun PaseoGateway.readFileList(path: String): ImmutableList<FileNode> {
    val (cwd, target) = readTarget(path)
    val payload =
        workspaceRequest(
            "file_explorer_request",
            jsonOf("cwd" to cwd, "path" to target, "mode" to "list"),
        )
    val directory =
        payload["directory"].objectOrNull() ?: paseoFail(PaseoFailureKind.InvalidResponse)
    val nodes =
        paseoList(directory["entries"], max = 50_000).map {
            val entry = paseoObject(it)
            FileNode(
                name = paseoString(entry["name"], max = 1024),
                path = paseoString(entry["path"]),
                isDir = entry.str("kind") == "directory",
            )
        }
    return nodes
        .sortedWith(compareBy<FileNode> { !it.isDir }.thenBy { it.name.lowercase() })
        .toImmutableList()
}

internal suspend fun PaseoGateway.readFileContent(path: String): FileContent {
    val (cwd, target) = readTarget(path)
    val payload =
        workspaceRequest(
            "file_explorer_request",
            jsonOf(
                "cwd" to cwd,
                "path" to target,
                "mode" to "file",
                "maxBytes" to PASEO_MAX_FILE_BYTES,
            ),
        )
    val file = paseoObject(payload["file"])
    val kind = file.str("kind")
    return FileContent(
        content = file.str("content") ?: "",
        type =
            when (kind) {
                "binary" -> "binary"
                "image" -> "image"
                else -> "text"
            },
        encoding = if (file.str("encoding") == "base64") "base64" else null,
        mimeType = file.str("mimeType"),
    )
}

internal suspend fun PaseoGateway.findFileByName(query: String): ImmutableList<String> {
    val text = query.trim()
    if (text.isEmpty()) return persistentListOf()
    val payload =
        workspaceRequest(
            "directory_suggestions_request",
            jsonOf(
                "query" to text,
                "cwd" to scopeDir,
                "includeFiles" to true,
                "includeDirectories" to false,
                "limit" to 100,
            ),
        )
    return paseoList(payload["entries"], max = 1000)
        .mapNotNull {
            it.objectOrNull()
                ?.takeIf { e -> e.str("kind") == "file" }
                ?.let { e -> paseoString(e["path"]) }
        }
        .toImmutableList()
}

// ---- Changes (checkout.diff.get, checkout_status_request)

private suspend fun PaseoGateway.checkoutDiff(cwd: String, mode: VcsDiffMode): List<JsonObject> {
    val payload =
        workspaceRequest(
            "checkout.diff.get.request",
            jsonOf(
                "cwd" to cwd,
                "compare" to
                    mapOf(
                        "mode" to (if (mode == VcsDiffMode.WorkingTree) "uncommitted" else "base"),
                        "ignoreWhitespace" to false,
                    ),
            ),
        )
    requireCheckoutOk(payload)
    return paseoList(payload["files"], max = 5000).map(::paseoObject)
}

private fun count(file: JsonObject, key: String): Int =
    file[key].longOrNull()?.takeIf { it >= 0 && it <= Int.MAX_VALUE }?.toInt() ?: 0

private fun fileStatus(file: JsonObject): String =
    when {
        file["isNew"].boolOrNull() == true -> "added"
        file["isDeleted"].boolOrNull() == true -> "deleted"
        else -> "modified"
    }

/** A daemon file diff as the unified patch the review screens read. */
internal fun paseoFileDiff(file: JsonObject): FileDiff {
    val path = paseoString(file["path"])
    val isNew = file["isNew"].boolOrNull() == true
    val isDeleted = file["isDeleted"].boolOrNull() == true
    val patch = StringBuilder()
    if (file.str("status") == "binary") {
        patch.append("Binary files a/$path and b/$path differ")
    } else {
        val hunks = file["hunks"].arrayOrNull()
        if (hunks != null && hunks.isNotEmpty()) {
            patch.append("--- ").append(if (isNew) "/dev/null" else "a/$path").append('\n')
            patch.append("+++ ").append(if (isDeleted) "/dev/null" else "b/$path").append('\n')
            for (hunk in hunks) {
                val lines = hunk.objectOrNull()?.get("lines").arrayOrNull() ?: continue
                for (raw in lines) {
                    val line = raw.objectOrNull() ?: continue
                    val text = line["content"].stringOrNull() ?: ""
                    patch
                        .append(
                            when (line.str("type")) {
                                "header" -> text
                                "add" -> "+$text"
                                "remove" -> "-$text"
                                else -> " $text"
                            }
                        )
                        .append('\n')
                }
            }
        }
    }
    return FileDiff(
        file = path,
        patch = patch.takeIf { it.isNotEmpty() }?.toString(),
        additions = count(file, "additions"),
        deletions = count(file, "deletions"),
        status = fileStatus(file),
    )
}

private fun fileStatuses(files: List<JsonObject>): ImmutableList<VersionControlFile> =
    files
        .map {
            VersionControlFile(
                paseoString(it["path"]),
                fileStatus(it),
                count(it, "additions"),
                count(it, "deletions"),
            )
        }
        .toImmutableList()

internal suspend fun PaseoGateway.readVcsDiffs(mode: VcsDiffMode): ImmutableList<FileDiff> =
    checkoutDiff(scopeDir, mode).map(::paseoFileDiff).toImmutableList()

/** What a conversation's agent changed: the uncommitted work in the folder it runs in. */
internal suspend fun PaseoGateway.readSessionDiff(id: String): ImmutableList<FileDiff> {
    val cwd = agents[id]?.str("cwd")?.takeIf { it.isNotEmpty() } ?: scopeDir
    return checkoutDiff(cwd, VcsDiffMode.WorkingTree).map(::paseoFileDiff).toImmutableList()
}

internal suspend fun PaseoGateway.readFileStatuses(
    directory: String? = null
): ImmutableList<VersionControlFile> =
    fileStatuses(
        checkoutDiff(
            directory?.let { paseoString(it, max = 4096) } ?: scopeDir,
            VcsDiffMode.WorkingTree,
        )
    )

internal suspend fun PaseoGateway.readVersionControlHealth(): VersionControlHealth {
    val payload = workspaceRequest("checkout_status_request", jsonOf("cwd" to scopeDir))
    requireCheckoutOk(payload)
    if (payload["isGit"].boolOrNull() != true)
        return VersionControlHealth(null, null, persistentListOf(), VersionControlSetupState.Absent)
    val changes =
        if (payload["isDirty"].boolOrNull() == true) readFileStatuses() else persistentListOf()
    return VersionControlHealth(
        payload.str("currentBranch"),
        payload.str("baseRef"),
        changes,
        VersionControlSetupState.Git,
    )
}

// ---- Worktrees (paseo_worktree_list / create_paseo_worktree / paseo_worktree_archive). No reset:
// the daemon has none.

internal suspend fun PaseoGateway.readWorktrees(
    projectDirectory: String
): ImmutableList<WorktreeInfo> {
    val payload = workspaceRequest("paseo_worktree_list_request", jsonOf("cwd" to projectDirectory))
    requireCheckoutOk(payload)
    return paseoList(payload["worktrees"], max = 5000)
        .map {
            val entry = paseoObject(it)
            val directory = paseoString(entry["worktreePath"])
            WorktreeInfo(
                folderName(directory),
                directory,
                entry.str("branchName")?.takeIf { b -> b.isNotEmpty() },
                ready = true,
            )
        }
        .toImmutableList()
}

internal suspend fun PaseoGateway.makeWorktree(
    projectDirectory: String,
    name: String?,
): WorktreeInfo {
    val slug = name?.trim().orEmpty()
    val payload =
        workspaceRequest(
            "create_paseo_worktree_request",
            jsonOf("cwd" to projectDirectory, "worktreeSlug" to slug.ifEmpty { null })
                .withoutNulls(),
            mutation = true,
        )
    requireCheckoutOk(payload)
    val workspace = paseoObject(payload["workspace"])
    val directory = paseoString(workspace["workspaceDirectory"] ?: workspace["projectRootPath"])
    val branch =
        workspace["gitRuntime"].objectOrNull()?.str("currentBranch")?.takeIf { it.isNotEmpty() }
    // The daemon answers once the folder exists: no readiness event follows.
    return WorktreeInfo(folderName(directory), directory, branch, ready = true)
}

internal suspend fun PaseoGateway.archiveWorktree(projectDirectory: String, directory: String) {
    val payload =
        workspaceRequest(
            "paseo_worktree_archive_request",
            jsonOf(
                "worktreePath" to paseoString(directory),
                "repoRoot" to projectDirectory,
                "scope" to "worktree",
            ),
            mutation = true,
        )
    requireCheckoutOk(payload)
    if (payload["success"].boolOrNull() != true) paseoFail(PaseoFailureKind.Unavailable)
}
