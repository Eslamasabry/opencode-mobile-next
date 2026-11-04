package io.github.eslamasabry.oc.engine.connection

/**
 * Directory comparison for the restored location (Dart `ConnectionController.normalizeDirectoryPath` and friends, row
 * B-STATE-011): trailing separators never make two folders differ; case does; a root (`/`, `C:\`) stays a root.
 */
public object DirectoryPaths {
    public fun normalize(path: String): String {
        var value = path.trim()
        while (value.length > 1 && (value.endsWith('/') || value.endsWith('\\'))) {
            val trimmed = value.dropLast(1)
            if (trimmed.endsWith(':')) break // Keep a Windows drive root intact.
            value = trimmed
        }
        return value
    }

    public fun same(a: String?, b: String?): Boolean =
        if (a == null || b == null) a == b else normalize(a) == normalize(b)

    /**
     * True when [directory] is [root] or one of [worktrees], or a folder inside one of them. The server's catch-all `/`
     * project covers nothing but itself.
     */
    public fun covers(root: String, worktrees: List<String>, directory: String): Boolean {
        val target = normalize(directory)
        if (target.isEmpty()) return false
        fun coversOne(candidate: String): Boolean {
            val base = normalize(candidate)
            if (base.isEmpty() || base == "/") return base == target
            if (base == target) return true
            return target.startsWith("$base/") || target.startsWith("$base\\")
        }
        return coversOne(root) || worktrees.any(::coversOne)
    }
}
