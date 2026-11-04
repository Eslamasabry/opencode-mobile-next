package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** The daemon's actual checkout/worktree RPCs, sharing the live gateway's lifetime and scope fence. */
internal class PaseoWorkspaceOperations(private val gateway: PaseoGateway) : ServerOperationsGateway {
    private suspend inline fun <T> call(crossinline block: suspend () -> T): Outcome<T> =
        withContext(gateway.lane) { paseoAttempt { block() } }

    private suspend fun request(type: String, body: JsonObject, mutation: Boolean = false): JsonObject {
        val scope = gateway.scopeDir
        val epoch = gateway.locationEpoch
        if (gateway.closed) paseoFail(PaseoFailureKind.Disconnected)
        val response = gateway.transport.request(type, body, mutation = mutation)
        gateway.checkLocation(scope, epoch)
        if (response["error"] != null && response["error"] != JsonNull) paseoFail(PaseoFailureKind.Unavailable)
        return response
    }

    private fun directory(value: String): String {
        paseoString(value)
        if (!value.startsWith('/') || value.any { it.code < 32 || it.code == 127 }) paseoFail(PaseoFailureKind.ScopeMismatch)
        return value
    }

    internal suspend fun changesAt(location: ServerLocation, mode: VcsDiffMode = VcsDiffMode.WorkingTree): Outcome<ImmutableList<FileDiff>> = call {
        if (!location.workspace.isNullOrEmpty()) paseoFail(PaseoFailureKind.ScopeMismatch)
        val cwd = directory(location.directory ?: paseoFail(PaseoFailureKind.ScopeMismatch))
        diffs(cwd, mode).map(::fileDiff).toImmutableList()
    }

    private suspend fun diffs(cwd: String, mode: VcsDiffMode): List<JsonObject> {
        val response = request("checkout.diff.get.request", jsonOf("cwd" to directory(cwd), "compare" to mapOf("mode" to if (mode == VcsDiffMode.WorkingTree) "uncommitted" else "base", "ignoreWhitespace" to false)))
        return paseoList(response["files"], max = 5000).map(::paseoObject)
    }

    override suspend fun listVcsDiffs(mode: VcsDiffMode): Outcome<ImmutableList<FileDiff>> = changesAt(gateway.location.value, mode)

    private fun count(file: JsonObject, key: String): Int = file[key].longOrNull()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0

    private fun status(file: JsonObject): String = when {
        file["isNew"].boolOrNull() == true -> "added"
        file["isDeleted"].boolOrNull() == true -> "deleted"
        else -> "modified"
    }

    private fun fileStatus(file: JsonObject): VersionControlFile = VersionControlFile(paseoString(file["path"]), status(file), count(file, "additions"), count(file, "deletions"))

    override suspend fun listFileStatuses(): Outcome<ImmutableList<VersionControlFile>> = call {
        diffs(gateway.scopeDir, VcsDiffMode.WorkingTree).map(::fileStatus).toImmutableList()
    }

    override suspend fun listWorktreeFileStatuses(directory: String): Outcome<ImmutableList<VersionControlFile>> = call {
        diffs(directory(directory), VcsDiffMode.WorkingTree).map(::fileStatus).toImmutableList()
    }

    override suspend fun versionControlHealth(): Outcome<VersionControlHealth> = call {
        val response = request("checkout_status_request", jsonOf("cwd" to gateway.scopeDir))
        if (response["isGit"].boolOrNull() != true) {
            VersionControlHealth(null, null, persistentListOf(), VersionControlSetupState.Absent)
        } else {
            val marks = if (response["isDirty"].boolOrNull() == true) diffs(gateway.scopeDir, VcsDiffMode.WorkingTree).map(::fileStatus).toImmutableList() else persistentListOf()
            VersionControlHealth(response.str("currentBranch"), response.str("baseRef"), marks, VersionControlSetupState.Git)
        }
    }

    private fun fileDiff(file: JsonObject): FileDiff {
        val path = paseoString(file["path"])
        val patch = buildString {
            if (file.str("status") == "binary") append("Binary files a/$path and b/$path differ")
            else {
                val hunks = file.arr("hunks").orEmpty()
                if (hunks.isNotEmpty()) {
                    appendLine("--- ${if (file["isNew"].boolOrNull() == true) "/dev/null" else "a/$path"}")
                    appendLine("+++ ${if (file["isDeleted"].boolOrNull() == true) "/dev/null" else "b/$path"}")
                    for (hunk in hunks) for (raw in hunk.objectOrNull()?.arr("lines").orEmpty()) {
                        val line = raw.objectOrNull() ?: continue
                        append(when (line.str("type")) { "header" -> ""; "add" -> "+"; "remove" -> "-"; else -> " " })
                        appendLine(line.str("content").orEmpty())
                    }
                }
            }
        }
        return FileDiff(path, patch = patch.takeIf { it.isNotEmpty() }, additions = count(file, "additions"), deletions = count(file, "deletions"), status = status(file))
    }

    override suspend fun listWorktrees(projectDirectory: String, projectId: String?): Outcome<ImmutableList<WorktreeInfo>> = call {
        val response = request("paseo_worktree_list_request", jsonOf("cwd" to directory(projectDirectory)))
        paseoList(response["worktrees"], max = 5000).map { raw ->
            val entry = paseoObject(raw)
            val path = paseoString(entry["worktreePath"])
            WorktreeInfo(folderName(path), path, entry.str("branchName")?.takeIf { it.isNotEmpty() }, false)
        }.toImmutableList()
    }

    override suspend fun createWorktree(projectDirectory: String, name: String?): Outcome<WorktreeInfo> = call {
        val body = linkedMapOf<String, Any?>("cwd" to directory(projectDirectory))
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { body["worktreeSlug"] = paseoString(it, max = 256) }
        val response = request("create_paseo_worktree_request", jsonOf(*body.toList().toTypedArray()), mutation = true)
        val workspace = paseoObject(response["workspace"])
        val path = paseoString(workspace["workspaceDirectory"] ?: workspace["projectRootPath"])
        WorktreeInfo(folderName(path), path, workspace.obj("gitRuntime")?.str("currentBranch")?.takeIf { it.isNotEmpty() }, true)
    }

    override suspend fun removeWorktree(projectDirectory: String, directory: String): Outcome<Unit> = call {
        val response = request("paseo_worktree_archive_request", jsonOf("worktreePath" to directory(directory), "repoRoot" to directory(projectDirectory), "scope" to "worktree"), mutation = true)
        if (response["success"].boolOrNull() != true) paseoFail(PaseoFailureKind.Unavailable)
    }

    private fun folderName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
}
