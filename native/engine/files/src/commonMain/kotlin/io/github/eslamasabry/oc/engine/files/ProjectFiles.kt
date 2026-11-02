package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.FileNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One opened file: its exact bytes and what the viewer draws. */
public class OpenedFile(public val data: FileData, public val body: FileBody)

/**
 * Reads one project's tree through the conversation's own gateway (the server's pinned location). Stateless: the
 * folder and file screens own their state; each call is one request and never retries.
 */
public object ProjectFiles {
    /** The entries of [folder] ("" = the project root), in the server's order (folders first on OpenCode 2). */
    public suspend fun list(gateway: FileGateway, folder: String): Outcome<ImmutableList<FileNode>> =
        when (val listed = gateway.listFiles(FilePaths.normalize(folder))) {
            is Outcome.Failed -> listed
            is Outcome.Ok ->
                Outcome.Ok(
                    listed.value
                        .map { it.copy(path = FilePaths.normalize(it.path)) }
                        .filter { it.path.isNotEmpty() && it.name.isNotEmpty() }
                        .toImmutableList()
                )
        }

    public suspend fun open(gateway: FileGateway, path: String, line: Int? = null): Outcome<OpenedFile> {
        val normal = FilePaths.normalize(path)
        return when (val read = gateway.fileContent(normal)) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val data =
                    FilePreview.data(normal, read.value)
                        ?: return Outcome.Failed(Failure.Media(MediaProblem.FileNotShowable))
                Outcome.Ok(OpenedFile(data, FilePreview.body(data, line)))
            }
        }
    }

    /** Fuzzy file-name search across the project (`/fs/find`). */
    public suspend fun find(gateway: FileGateway, query: String): Outcome<ImmutableList<FileNode>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Outcome.Ok(emptyList<FileNode>().toImmutableList())
        return when (val found = gateway.findFile(trimmed)) {
            is Outcome.Failed -> found
            is Outcome.Ok ->
                Outcome.Ok(
                    found.value
                        .map(FilePaths::normalize)
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .map { FileNode(FilePaths.name(it), it, isDir = false) }
                        .toImmutableList()
                )
        }
    }
}
