package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a file change does to one file. */
public enum class ChangeKind { Create, Edit, Delete }

/**
 * One file a change touches: its project-relative [path], what happens to it, and the unified diff the server sent for
 * it ([patch]; null when the request carries none). Server data: the screen masks it before drawing or copying it.
 */
public data class FileChange(val path: String, val kind: ChangeKind, val patch: String?)

/**
 * The change a file-change request asks to make, as the request itself states it (review 2026-11-13 #1: a person
 * approves what they can read). [files] in the server's order; [combined] is one diff covering several files when the
 * server sent only that. [missing]: the request names a file to create or change but carries no content at all, and
 * the card says so instead of looking complete.
 */
public data class PermissionChangeUi(
    val files: List<FileChange>,
    val combined: String? = null,
) {
    /** The diff the card previews: the first file's own, else the combined one. */
    val first: String?
        get() = files.firstNotNullOfOrNull { it.patch?.takeIf(String::isNotBlank) } ?: combined?.takeIf(String::isNotBlank)

    val missing: Boolean
        get() = first == null && files.any { it.kind != ChangeKind.Delete }
}

/**
 * Reads the change from what each server sends with the ask (no extra call):
 * - OpenCode 2 write, edit and patch: `metadata.files[]{file, status: added|modified|deleted, patch}` (the patch tool
 *   adds a combined `metadata.diff`);
 * - OpenCode 1 edit and write: `metadata{filepath, diff}`; apply_patch: `metadata.files[]{relativePath, type, diff}`
 *   and a combined `metadata.diff`;
 * - Paseo write and edit: `metadata{filePath, diff}` (the daemon's unified diff, or one built from the new content).
 */
public object PermissionChange {
    private val changeActions = setOf("edit", "write", "multiedit", "patch", "apply_patch")
    private val pathKeys = listOf("file", "relativePath", "filePath", "filepath", "file_path", "path")
    private val diffKeys = listOf("diff", "patch", "preview")

    /** Null for any ask that is not a file change. [directory] makes an absolute path inside it project-relative. */
    public fun of(request: PermissionRequest, directory: String? = null): PermissionChangeUi? {
        if (request.permission.trim().lowercase() !in changeActions) return null
        val combined = text(request.metadata, diffKeys)
        val listed =
            (request.metadata["files"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { file ->
                val path = text(file, pathKeys) ?: return@mapNotNull null
                val patch = text(file, listOf("patch", "diff"))
                FileChange(relative(path, directory), kind(file, patch), patch)
            }
        if (listed.isNotEmpty()) return PermissionChangeUi(listed, combined.takeIf { listed.all { it.patch == null } })
        val paths =
            request.patterns.map(String::trim).filter { it.isNotEmpty() && '*' !in it }
                .ifEmpty { listOfNotNull(text(request.metadata, pathKeys)) }
                .distinct()
        if (paths.size == 1) {
            val path = paths.single()
            val kind = if (creates(combined)) ChangeKind.Create else ChangeKind.Edit
            return PermissionChangeUi(listOf(FileChange(relative(path, directory), kind, combined)))
        }
        return PermissionChangeUi(paths.map { FileChange(relative(it, directory), ChangeKind.Edit, null) }, combined)
    }

    private fun kind(file: JsonObject, patch: String?): ChangeKind =
        when (text(file, listOf("type", "status"))) {
            "add", "added" -> ChangeKind.Create
            "delete", "deleted" -> ChangeKind.Delete
            null -> if (creates(patch)) ChangeKind.Create else ChangeKind.Edit
            else -> ChangeKind.Edit
        }

    /** A diff whose one hunk starts from nothing (`@@ -0,0 +1,n @@`, `--- /dev/null`): a new file. */
    internal fun creates(diff: String?): Boolean {
        if (diff.isNullOrBlank()) return false
        val hunks = diff.lineSequence().filter { it.startsWith("@@") }.toList()
        return "\n--- /dev/null" in "\n$diff" || (hunks.size == 1 && hunks.single().startsWith("@@ -0,0 "))
    }

    private fun relative(path: String, directory: String?): String {
        val root = directory?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return path
        return if (path.startsWith("$root/")) path.substring(root.length + 1) else path
    }

    private fun text(obj: JsonObject, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        }
}
