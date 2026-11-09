package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.FileNode
import kotlinx.serialization.Serializable

/** One kept row of a folder: only what the real row draws. */
@Serializable public data class KeptNode(val name: String, val path: String, val isDir: Boolean)

/** A folder listing as last seen. */
@Serializable
public data class KeptFolder(val entries: List<KeptNode>) {
    public fun nodes(): List<FileNode> = entries.map { FileNode(it.name, it.path, it.isDir) }

    public companion object {
        public fun of(nodes: List<FileNode>): KeptFolder = KeptFolder(nodes.map { KeptNode(it.name, it.path, it.isDir) })
    }
}

/** A text file as last read (bounded by [FilesLastKnown.MAX_TEXT_CHARS]). */
@Serializable public data class KeptFile(val text: String, val mime: String? = null)

/**
 * Cache-first (owner rule, 2026-11-09): a folder or text file opened again draws its real rows at once from what it
 * last showed, then the live read replaces them in place. There is no loading or not-connected variant when
 * something is kept; the status slot alone says the server is away. The app binds this to its last-known store
 * (per profile and server, size-capped, cleared with the profile); without a binding screens open as before.
 * [key] names the folder or file within the profile (the place and the path).
 */
public interface FilesLastKnown {
    public fun peekFolder(profile: ProfileId, key: String): KeptFolder?

    public suspend fun readFolder(profile: ProfileId, key: String): KeptFolder?

    public suspend fun keepFolder(profile: ProfileId, key: String, folder: KeptFolder)

    public fun peekFile(profile: ProfileId, key: String): KeptFile?

    public suspend fun readFile(profile: ProfileId, key: String): KeptFile?

    /** Callers keep only text no longer than [MAX_TEXT_CHARS]; the store refuses more. */
    public suspend fun keepFile(profile: ProfileId, key: String, file: KeptFile)

    public companion object {
        /** About 256 KB of text (UTF-8 may be wider; the store's byte cap is the hard limit). */
        public const val MAX_TEXT_CHARS: Int = 128 * 1024
    }
}

internal fun io.github.eslamasabry.oc.ui.routes.FilesPlace.keptKey(path: String): String =
    when (this) {
        is io.github.eslamasabry.oc.ui.routes.FilesPlace.Conversation -> "c:${ref.sessionId.raw}|$path"
        is io.github.eslamasabry.oc.ui.routes.FilesPlace.Project -> "p:$directory|$path"
    }
