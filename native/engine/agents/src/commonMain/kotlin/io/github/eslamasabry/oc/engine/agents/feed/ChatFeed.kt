package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.json.DartJsonText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Port of lib/domain/chat_feed.dart (AGENTS-BACKENDS-05, plan 17 §1.6): pure types and rules of the one
// Conversations list. No copy: a row with no title has `title == null` and the UI says "New chat".

/** What a conversation needs from the person right now. */
public enum class ChatStatus { NeedsYou, Running, Failed, Idle }

/** One conversation row. Immutable; list keys are [identity] (session ids collide across sources). */
public data class ChatFeedItem(
    val sessionId: String,
    /** The session title; null when the server has none. */
    val title: String?,
    /** Absolute folder of the chat's project. */
    val directory: String,
    val projectName: String,
    val isGit: Boolean,
    val status: ChatStatus,
    val lastActivityMillis: Long,
    /** Plain-text last line; never an error, stack trace or credential. Empty when unknown. */
    val preview: String = "",
    /** Set for a sub-agent's child session. */
    val parentId: String? = null,
    /** Which agent backend owns this chat ([AgentId.OPENCODE] for OpenCode chats). */
    val agentId: AgentId = AgentId.OPENCODE,
    /** App-authored agent name; shown only when the list holds more than one agent. */
    val agentLabel: String? = null,
    /** Set by a merged feed: never route a merged row by session id alone. */
    val sourceId: String? = null,
    val sourceLabel: String? = null,
    /** Its last run finished after the person last opened it ("Done"); false for saved rows and while running. */
    val finishedUnseen: Boolean = false,
    val stoppedByPerson: Boolean = false,
    /** Share of the context window last used (09 owns it); null draws nothing. */
    val contextUsage: Double? = null,
    /** Whether the conversation can be reopened with memory; false offers the explicit Start new chat. */
    val canReopen: Boolean = true,
) {
    public val isSubagent: Boolean get() = parentId != null

    /** Listed, but its folder is never offered as a project (temporary, home or root folder). */
    public val inOtherFolder: Boolean get() = FeedFolders.isOtherFolder(directory)

    /** Dart `jsonEncode([sourceId, sessionID, directory])`, byte for byte. */
    public val identity: String get() = DartJsonText.encode(listOf(sourceId, sessionId, directory))

    override fun toString(): String = "ChatFeedItem($identity, $status)"
}

/** Which rows a snapshot returns. The chips are additive: needsYou + running keeps rows matching either. */
public data class ChatFeedFilter(
    val projectDirectory: String? = null,
    val needsYou: Boolean = false,
    val running: Boolean = false,
    val includeSubagents: Boolean = false,
    val agentId: AgentId? = null,
    /** Only chats outside every project (temporary, home, root). */
    val otherFolders: Boolean = false,
) {
    public fun matches(item: ChatFeedItem): Boolean =
        (includeSubagents || !item.isSubagent) &&
            (projectDirectory == null || item.directory == projectDirectory) &&
            (agentId == null || item.agentId == agentId) &&
            (!otherFolders || item.inOtherFolder) &&
            ((!needsYou && !running) || (needsYou && item.status == ChatStatus.NeedsYou) ||
                (running && item.status == ChatStatus.Running))

    public companion object {
        public val All: ChatFeedFilter = ChatFeedFilter()
        public val Everything: ChatFeedFilter = ChatFeedFilter(includeSubagents = true)
    }
}

/** Needs-you rows first, then running, then the rest; each group newest first; ties by identity. */
public object ChatFeedOrder : Comparator<ChatFeedItem> {
    private fun rank(status: ChatStatus) =
        when (status) {
            ChatStatus.NeedsYou -> 0
            ChatStatus.Running -> 1
            else -> 2
        }

    override fun compare(a: ChatFeedItem, b: ChatFeedItem): Int {
        val priority = rank(a.status).compareTo(rank(b.status))
        if (priority != 0) return priority
        val newest = b.lastActivityMillis.compareTo(a.lastActivityMillis)
        return if (newest != 0) newest else a.identity.compareTo(b.identity)
    }
}

/** One real project, for the projects sheet. */
public data class ProjectSummary(
    val directory: String,
    val name: String,
    val isGit: Boolean,
    val chatCount: Int,
    val runningCount: Int,
    val needsYouCount: Int,
    val lastActivityMillis: Long? = null,
    val kind: String? = null,
    val sourceId: String? = null,
    val sourceLabel: String? = null,
)

/** The feed for one filter. Source names are app-authored labels the caller passed in. */
public data class ChatFeedSnapshot(
    val items: ImmutableList<ChatFeedItem>,
    /** False when a source lists only its current project. */
    val acrossProjects: Boolean = true,
    /** A read is in flight and no rows are known anywhere yet (skeleton). */
    val loading: Boolean = false,
    /** False while only part is loaded or the last read failed: rows are real, older ones may be missing. */
    val complete: Boolean = true,
    /** Labels of the sources whose conversations did not all load. */
    val incompleteSources: ImmutableList<String> = persistentListOf(),
)

/** The folder rules (port of `isTemporaryProjectDirectory`, `isProtectedWorkspaceDirectory`). */
public object FeedFolders {
    private val tempRoots = listOf(
        "/tmp", "tmp", "/var/tmp", "/private/tmp", "/private/var/tmp", "/var/folders", "/private/var/folders",
        "/data/local/tmp", "/data/data/com.termux/files/usr/tmp",
    )
    private val windowsTemp = Regex("^[a-z]:/windows/temp(/|$)")
    private val appDataTemp = Regex("/appdata/local/temp(/|$)")
    private val driveRoot = Regex("^[A-Za-z]:/?$")
    private val windowsHome = Regex("^[A-Za-z]:/Users/[^/]+$", RegexOption.IGNORE_CASE)

    /** Never shown as projects: temp folders and anything under them, `/`, `global`, `.`, empty or missing. */
    public fun isTemporary(directory: String?): Boolean {
        if (directory == null) return true
        var value = directory.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith('/')) value = value.dropLast(1)
        if (value.isEmpty() || value == "/" || value == "global" || value == ".") return true
        val lower = value.lowercase()
        if (tempRoots.any { lower == it || lower.startsWith("$it/") }) return true
        return windowsTemp.containsMatchIn(lower) || appDataTemp.containsMatchIn(lower)
    }

    /** `/`, a drive root and every home folder shape (`/root`, `/home/<u>`, `/Users/<u>`, Termux home, `~`). */
    public fun isProtected(path: String?): Boolean {
        if (path == null) return true
        var value = path.trim().replace('\\', '/')
        while (value.length > 1 && value.endsWith('/')) {
            val trimmed = value.dropLast(1)
            if (trimmed.endsWith(':')) break
            value = trimmed
        }
        if (value.isEmpty() || value == "/" || value == "~") return true
        if (driveRoot.matches(value) || windowsHome.matches(value)) return true
        if (value == "/root" || value == "/home" || value == "/Users") return true
        if (value == "/data/data/com.termux/files/home") return true
        val segments = value.split('/').filter { it.isNotEmpty() }
        return segments.size == 2 && (segments[0] == "home" || segments[0] == "Users")
    }

    public fun isOtherFolder(directory: String?): Boolean = isTemporary(directory) || isProtected(directory)
}
