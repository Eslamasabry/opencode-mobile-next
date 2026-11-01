package io.github.eslamasabry.oc.engine.agents.feed

/**
 * Folder rules of the feed, ported from Dart `isTemporaryProjectDirectory`, `isProtectedWorkspaceDirectory`
 * (`workspace_paths.dart`), `isOtherFolderDirectory` and `otherFolderLabel` (`chat_feed.dart`). Pure.
 */
public object FeedFolders {
    private val temporaryRoots =
        listOf(
            "/tmp", "tmp", "/var/tmp", "/private/tmp", "/private/var/tmp", "/var/folders", "/private/var/folders",
            "/data/local/tmp", "/data/data/com.termux/files/usr/tmp",
        )
    private val windowsTemp = Regex("^[a-z]:/windows/temp(/|$)")
    private val appDataTemp = Regex("/appdata/local/temp(/|$)")
    private val driveRoot = Regex("^[A-Za-z]:/?$")
    private val windowsHome = Regex("^[A-Za-z]:/Users/[^/]+$", RegexOption.IGNORE_CASE)

    /** `/tmp`-like folders, anything under them, the server's global project (`/`, `global`) and empty or missing. */
    public fun isTemporary(directory: String?): Boolean {
        if (directory == null) return true
        var value = directory.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith('/')) value = value.dropLast(1)
        if (value.isEmpty() || value == "/" || value == "global" || value == ".") return true
        val lower = value.lowercase()
        if (temporaryRoots.any { lower == it || lower.startsWith("$it/") }) return true
        return windowsTemp.containsMatchIn(lower) || appDataTemp.containsMatchIn(lower)
    }

    /** `/`, a drive root and every home-folder shape (`/root`, `/home/<u>`, `/Users/<u>`, the Termux home, `~`). */
    public fun isProtectedHome(directory: String?): Boolean {
        if (directory == null) return true
        val value = normalize(directory)
        if (value.isEmpty() || value == "/" || value == "~") return true
        if (driveRoot.matches(value) || windowsHome.matches(value)) return true
        if (value == "/root" || value == "/home" || value == "/Users") return true
        if (value == "/data/data/com.termux/files/home") return true
        val segments = value.split('/').filter { it.isNotEmpty() }
        return segments.size == 2 && (segments[0] == "home" || segments[0] == "Users")
    }

    /** Folders whose chats are listed but never offered as projects. */
    public fun isOtherFolder(directory: String?): Boolean = isTemporary(directory) || isProtectedHome(directory)

    /** "Home" for a home folder, "/" for root, else the folder's last segment. */
    public fun otherFolderLabel(directory: String?): String {
        val value = directory.orEmpty().trim().replace('\\', '/')
        val trimmed = if (value.length > 1 && value.endsWith('/')) value.dropLast(1) else value
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        val parts = trimmed.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "/"
        if (!isTemporary(trimmed) && isProtectedHome(trimmed)) return "Home"
        return parts.last()
    }

    /** A project's display name from its folder: the last segment. */
    public fun folderName(directory: String): String =
        normalize(directory).split('/').lastOrNull { it.isNotEmpty() } ?: directory

    private fun normalize(path: String): String {
        var value = path.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith('/')) {
            val trimmed = value.dropLast(1)
            if (trimmed.endsWith(':')) break
            value = trimmed
        }
        return value
    }
}
