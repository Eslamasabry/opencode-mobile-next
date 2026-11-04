package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.model.FileNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Project-relative paths as the server spells them: `/`-separated, no leading `./`, the root is "".
 */
public object FilePaths {
    /** "" for the project root; trims `./`, leading and trailing slashes and empty segments. */
    public fun normalize(path: String): String =
        path.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

    public fun name(path: String): String = normalize(path).substringAfterLast('/')

    /** The folder that holds [path]; "" for a root entry and for the root itself. */
    public fun parent(path: String): String {
        val normal = normalize(path)
        val cut = normal.lastIndexOf('/')
        return if (cut < 0) "" else normal.substring(0, cut)
    }

    public fun child(folder: String, name: String): String {
        val base = normalize(folder)
        val leaf = normalize(name)
        return if (base.isEmpty()) leaf else "$base/$leaf"
    }

    /**
     * Lower-case extension without the dot; "" when there is none (a leading dot alone is not one:
     * `.env`).
     */
    public fun extension(path: String): String {
        val name = name(path)
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "" else name.substring(dot + 1).lowercase()
    }

    /** Dot entries are hidden until the person asks to see them (files_entries.dart). */
    public fun isHidden(name: String): Boolean = name.startsWith(".")

    /** The folders from the root down to [path], each a project-relative path ("" first). */
    public fun crumbs(path: String): ImmutableList<String> {
        val parts = normalize(path).split('/').filter { it.isNotEmpty() }
        val out = ArrayList<String>(parts.size + 1)
        out += ""
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            out += acc
        }
        return out.toImmutableList()
    }
}

/**
 * One folder as shown: the visible entries in the server's order, and how many dot entries are left
 * out.
 */
public data class FolderListing(val entries: ImmutableList<FileNode>, val hiddenCount: Int) {
    /**
     * A folder whose only entries are hidden ones says so instead of looking empty
     * (files_body.dart).
     */
    val onlyHidden: Boolean
        get() = entries.isEmpty() && hiddenCount > 0

    public companion object {
        /**
         * Stable partition, matching the reader preference: generated entries stay visible after
         * source entries.
         */
        public fun sourceFirst(entries: List<FileNode>, enabled: Boolean): ImmutableList<FileNode> =
            (if (enabled) entries.filterNot(::generated) + entries.filter(::generated) else entries)
                .toImmutableList()

        private val generatedDirectories =
            setOf(
                "node_modules",
                ".git",
                ".dart_tool",
                "build",
                "dist",
                "coverage",
                "__pycache__",
                ".gradle",
            )

        public fun generated(node: FileNode): Boolean {
            val name = node.name.lowercase()
            val directories =
                if (node.isDir) node.path.split('/') else node.path.split('/').dropLast(1)
            return directories.any { it.lowercase() in generatedDirectories } ||
                listOf(".lock", ".g.dart", ".freezed.dart", ".min.js", ".map").any(name::endsWith)
        }

        public fun of(
            all: List<FileNode>,
            showHidden: Boolean,
            sourceFirst: Boolean = false,
        ): FolderListing {
            val ordered =
                if (sourceFirst) all.filterNot(::generated) + all.filter(::generated) else all
            if (showHidden) return FolderListing(ordered.toImmutableList(), 0)
            val (hidden, shown) = ordered.partition { FilePaths.isHidden(it.name) }
            return FolderListing(shown.toImmutableList(), hidden.size)
        }
    }
}
