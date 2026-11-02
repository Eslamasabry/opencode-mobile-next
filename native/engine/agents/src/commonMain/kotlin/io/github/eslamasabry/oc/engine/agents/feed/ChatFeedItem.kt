package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Dart `ChatStatus`, in the order the list ranks them (needs you, running, the rest). */
public enum class ChatStatus {
    NeedsYou,
    Running,
    Failed,
    Idle,
}

/** [ChatFeedItem.agentId] of OpenCode chats (Dart `defaultChatAgentId`). */
public const val OPENCODE_AGENT_ID: String = "opencode"

/**
 * One conversation row on Conversations (Dart `ChatFeedItem`, 17 §1.6). Immutable, and it holds
 * **only what a row shows**: equality is what decides whether the feed revision moves (B-CHAT-014).
 * Compare and key by [identity]; session ids collide across sources.
 */
public data class ChatFeedItem(
    val sourceId: String,
    val profileId: ProfileId,
    val sessionId: SessionId,
    /** The server's title, uncleaned; the words layer cleans it for display (B-NAV-066/067). */
    val title: String?,
    /** Absolute folder this chat lives in; may be temporary or a home folder ([inOtherFolder]). */
    val directory: String?,
    /** The project's name, else its folder's last segment, else the other-folder label. */
    val projectName: String,
    val status: ChatStatus,
    /** Last change the server reported, epoch ms. */
    val lastActivityMs: Long,
    val preview: String = "",
    val parentId: SessionId? = null,
    val agentId: String = OPENCODE_AGENT_ID,
    val agentLabel: String? = null,
    /** Set when the merged feed holds more than one source; never route by session id alone. */
    val sourceLabel: String? = null,
    /** The last run finished after the person last opened it (the list's "Done" tag). */
    val finishedUnseen: Boolean = false,
    /** False for an agent chat that cannot resume (always true for OpenCode). */
    val canReopen: Boolean = true,
    /**
     * Opaque `[baseUrl, directory, workspace]` key shared with the return brief; null means
     * unknown.
     */
    val locationKey: String? = null,
) {
    /** The list key: profile, source, session, folder and captured location. */
    val identity: String
        get() =
            "${profileId.raw}|$sourceId|${sessionId.raw}|${directory.orEmpty()}|${locationKey.orEmpty()}"

    val ref: ConversationRef
        get() = ConversationRef(profileId, sessionId)

    val isSubagent: Boolean
        get() = parentId != null

    /** Temporary, home or root folder: listed, never offered as a project (B-CHAT-013). */
    val inOtherFolder: Boolean
        get() = FeedFolders.isOtherFolder(directory)
}

/** One real project (Dart `ProjectSummary`): the recent-projects strip and the project filter. */
public data class ProjectSummary(
    val directory: String,
    val name: String,
    val chatCount: Int,
    val runningCount: Int,
    val lastActivityMs: Long,
)

/**
 * The merged feed as the list reads it (Dart `ChatFeedSnapshot`, reduced to what slice 2 shows).
 */
public data class ChatFeedSnapshot(
    /** Needs-you rows first, then running, then the rest, each newest first; stable. */
    val items: ImmutableList<ChatFeedItem> = persistentListOf(),
    /** Moves only when [items] or a flag below changes (B-CHAT-014). */
    val revision: Long = 0,
    /**
     * A read is in flight and no rows are known yet: show a skeleton. Never true once rows show
     * (B-NAV-031).
     */
    val loading: Boolean = false,
    /**
     * False while a source failed or holds only its first page: rows are real, older ones may be
     * missing.
     */
    val complete: Boolean = true,
    /** Labels of the sources whose rows did not all load (named in one quiet line). */
    val incompleteSources: ImmutableList<String> = persistentListOf(),
    /** False when a source can list its current project only (the "only this project" line). */
    val acrossProjects: Boolean = true,
)

/** Which rows the list shows (Dart `ChatFeedFilter`). Value equality. */
public data class ChatFeedFilter(
    val projectDirectory: String? = null,
    val running: Boolean = false,
    val needsYou: Boolean = false,
    val otherFolders: Boolean = false,
    val includeSubagents: Boolean = false,
) {
    public fun matches(item: ChatFeedItem): Boolean =
        (includeSubagents || !item.isSubagent) &&
            (projectDirectory == null || item.directory == projectDirectory) &&
            (!otherFolders || item.inOtherFolder) &&
            ((!needsYou && !running) ||
                (needsYou && item.status == ChatStatus.NeedsYou) ||
                (running && item.status == ChatStatus.Running))

    public fun apply(items: List<ChatFeedItem>): List<ChatFeedItem> = items.filter(::matches)
}
