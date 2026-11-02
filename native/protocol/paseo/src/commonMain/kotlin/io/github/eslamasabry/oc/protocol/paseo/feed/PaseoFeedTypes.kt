package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList

/*
 * Local feed shapes, ports of lib/domain/chat_feed.dart (ChatStatus, ChatFeedItem, ChatFeedFilter, ChatFeedSnapshot,
 * ProjectSummary, compareChatFeedItems, chatFeedMatches, isTemporaryProjectDirectory) and
 * lib/domain/workspace_paths.dart (isProtectedWorkspaceDirectory).
 * TODO(move-to-core): the chat feed vocabulary belongs in :core:model (contract request in the S3-PASEO report).
 * No copy: an untitled row has `title = null` ("New chat" is :ui:words), and a row never carries a preview, so no
 * conversation detail can leak into the list (B-AGENT-154).
 */

/** Dart `ChatStatus`, in rank order. */
public enum class PaseoFeedStatus { NeedsYou, Running, Failed, Idle }

/** Dart `ChatFeedItem` minus preview and source fields. */
public data class PaseoFeedRow(
    val sessionId: SessionId,
    val title: String?,
    val directory: String,
    val projectName: String?,
    val isGit: Boolean,
    val status: PaseoFeedStatus,
    val lastActivityMs: Long,
    val parentId: SessionId? = null,
    /** The runtime (`claude`, `codex`, …), or `paseo` when unknown. */
    val agentId: String = UNKNOWN_AGENT,
    /** The runtime's display name from the agent catalog (injected), or null. */
    val agentLabel: String? = null,
) {
    val isSubagent: Boolean get() = parentId != null
    val inOtherFolder: Boolean get() = PaseoFeedPaths.isOtherFolder(directory)

    public companion object {
        public const val UNKNOWN_AGENT: String = "paseo"
    }
}

/** Dart `ChatFeedFilter`. */
public data class PaseoFeedFilter(
    val projectDirectory: String? = null,
    val needsYou: Boolean = false,
    val running: Boolean = false,
    val includeSubagents: Boolean = false,
    val agentId: String? = null,
    val otherFolders: Boolean = false,
) {
    /** Dart `chatFeedMatches`. */
    public fun matches(row: PaseoFeedRow): Boolean =
        (includeSubagents || !row.isSubagent) &&
            (projectDirectory == null || row.directory == projectDirectory) &&
            (agentId == null || row.agentId == agentId) &&
            (!otherFolders || row.inOtherFolder) &&
            ((!needsYou && !running) || (needsYou && row.status == PaseoFeedStatus.NeedsYou) ||
                (running && row.status == PaseoFeedStatus.Running))

    public companion object {
        public val All: PaseoFeedFilter = PaseoFeedFilter()
    }
}

/** Dart `ChatFeedSnapshot` for a single-project source: never across projects. */
public data class PaseoFeedSnapshot(
    /** Needs-you first, then running, then the rest; each group newest first. */
    val rows: ImmutableList<PaseoFeedRow>,
    /** A read is in flight and no rows are known yet. */
    val loading: Boolean,
    /** False while only some pages are read or the last read failed: rows are real but older ones may be missing. */
    val complete: Boolean,
) {
    val acrossProjects: Boolean get() = false
}

/** Dart `ProjectSummary` for the one project this feed reads. */
public data class PaseoProjectSummary(
    val directory: String,
    /** The project name given to the feed, else the folder's last segment; null for "/". */
    val name: String?,
    val isGit: Boolean,
    val chatCount: Int,
    val runningCount: Int,
    val needsYouCount: Int,
    val lastActivityMs: Long?,
)

/** Dart `compareChatFeedItems`: needs-you, running, the rest; newest first; then a stable identity. */
public val PaseoFeedOrder: Comparator<PaseoFeedRow> =
    Comparator { a, b ->
        fun rank(status: PaseoFeedStatus) =
            when (status) {
                PaseoFeedStatus.NeedsYou -> 0
                PaseoFeedStatus.Running -> 1
                else -> 2
            }
        val priority = rank(a.status).compareTo(rank(b.status))
        if (priority != 0) return@Comparator priority
        val newest = b.lastActivityMs.compareTo(a.lastActivityMs)
        if (newest != 0) newest else "${a.sessionId.raw}\u0000${a.directory}".compareTo("${b.sessionId.raw}\u0000${b.directory}")
    }

/** Folder rules of the feed (ports of the Dart predicates). */
public object PaseoFeedPaths {
    private val TEMP_ROOTS = listOf(
        "/tmp", "tmp", "/var/tmp", "/private/tmp", "/private/var/tmp", "/var/folders", "/private/var/folders",
        "/data/local/tmp", "/data/data/com.termux/files/usr/tmp",
    )
    private val WINDOWS_TEMP = Regex("""^[a-z]:/windows/temp(/|$)""")
    private val APPDATA_TEMP = Regex("""/appdata/local/temp(/|$)""")
    private val DRIVE_ROOT = Regex("""^[A-Za-z]:/?$""")
    private val WINDOWS_HOME = Regex("""^[A-Za-z]:/Users/[^/]+$""", RegexOption.IGNORE_CASE)
    private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")

    /** Dart `isTemporaryProjectDirectory`: null, root, `global`, `.`, and the temp roots. */
    public fun isTemporary(directory: String?): Boolean {
        if (directory == null) return true
        var value = directory.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith("/")) value = value.dropLast(1)
        if (value.isEmpty() || value == "/" || value == "global" || value == ".") return true
        val lower = value.lowercase()
        if (TEMP_ROOTS.any { lower == it || lower.startsWith("$it/") }) return true
        return WINDOWS_TEMP.containsMatchIn(lower) || APPDATA_TEMP.containsMatchIn(lower)
    }

    /** Dart `isProtectedWorkspaceDirectory`: root, home folders and drive roots. */
    public fun isProtected(path: String?): Boolean {
        if (path == null) return true
        var value = path.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith("/")) {
            val trimmed = value.dropLast(1)
            if (trimmed.endsWith(":")) break // keep a Windows drive root intact
            value = trimmed
        }
        if (value.isEmpty() || value == "/" || value == "~") return true
        if (DRIVE_ROOT.matches(value) || WINDOWS_HOME.matches(value)) return true
        if (value == "/root" || value == "/home" || value == "/Users") return true
        if (value == "/data/data/com.termux/files/home") return true
        val segments = value.split('/').filter { it.isNotEmpty() }
        return segments.size == 2 && (segments.first() == "home" || segments.first() == "Users")
    }

    /** Dart `isOtherFolderDirectory`. */
    public fun isOtherFolder(directory: String?): Boolean = isTemporary(directory) || isProtected(directory)

    /**
     * Where a chat may be started from this phone's helper (Dart `_checkDirectory`, minus the scope part): absolute, no
     * control characters, no `.`/`..` segments, under `/root/projects/` when under `/root`, never a system root.
     */
    public fun isStartable(directory: String): Boolean =
        !isTemporary(directory) &&
            directory.startsWith("/") &&
            !CONTROL.containsMatchIn(directory) &&
            directory.split('/').none { it == ".." || it == "." } &&
            !(directory.startsWith("/root/") && !directory.startsWith("/root/projects/")) &&
            directory != "/root" &&
            listOf("/etc", "/proc", "/sys", "/dev", "/boot").none { directory == it || directory.startsWith("$it/") }
}
