package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What one source holds right now. Sources keep partial failures: a failed read keeps the last rows (17 §1.6). */
public data class FeedSourceState(
    val items: ImmutableList<ChatFeedItem> = persistentListOf(),
    val loading: Boolean = false,
    val complete: Boolean = true,
    val failed: Boolean = false,
    val acrossProjects: Boolean = true,
)

/**
 * One feed source (`opencode`, later `paseo:<directory>` and side connections, 17 §1.6). A source may be shared by
 * several feeds; a feed never closes it (B-STATE-029).
 */
public interface ChatFeedSource {
    /** Stable id; part of every row's identity. */
    public val id: String

    /** Display name for "N couldn't load" lines; null for the only source. */
    public val label: String?

    public fun current(): FeedSourceState

    /** Every change of [current], distinct. */
    public val changes: Flow<FeedSourceState>

    /** Ask for a fresh list. Completes quietly when offline: the source keeps what it had. */
    public suspend fun refresh(): Outcome<Unit>
}

/**
 * What the connection engine knows about one OpenCode server's sessions (read-only port; bound by the connection
 * owner over its session map, status map and pending requests). Times and ids only; never prompt text.
 */
public data class OpenCodeListing(
    val sessions: List<Session> = emptyList(),
    val statuses: Map<SessionId, SessionStatus> = emptyMap(),
    /** Sessions blocked on a permission, question or form. */
    val needsYou: Set<SessionId> = emptySet(),
    /** Sessions whose last run finished after the person last opened them (device read watermarks). */
    val finishedUnseen: Set<SessionId> = emptySet(),
    val loading: Boolean = false,
    val complete: Boolean = true,
    val failed: Boolean = false,
    /** `ServerCapabilities.globalSessionSearch`: false = the current project only. */
    val acrossProjects: Boolean = true,
)

/** The OpenCode source (Dart `ConnectionController` as `ChatFeedSource`), one per connected server. */
public class OpenCodeChatFeedSource(
    private val profileId: ProfileId,
    override val label: String?,
    private val listing: StateFlow<OpenCodeListing>,
    private val refresh: suspend () -> Outcome<Unit> = { Unit.ok() },
) : ChatFeedSource {
    override val id: String = "opencode:${profileId.raw}"

    override fun current(): FeedSourceState = map(listing.value)

    override val changes: Flow<FeedSourceState> = listing.map(::map).distinctUntilChanged()

    override suspend fun refresh(): Outcome<Unit> = refresh.invoke()

    private fun map(listing: OpenCodeListing): FeedSourceState =
        FeedSourceState(
            items =
                listing.sessions
                    .filter { it.time?.archivedMs == null }
                    .map { row(it, listing) }
                    .sortedWith(ChatFeedRules.order)
                    .toImmutableList(),
            loading = listing.loading && listing.sessions.isEmpty(),
            complete = listing.complete && !listing.failed,
            failed = listing.failed,
            acrossProjects = listing.acrossProjects,
        )

    private fun row(session: Session, listing: OpenCodeListing): ChatFeedItem {
        val status = listing.statuses[session.id]
        val chatStatus =
            when {
                session.id in listing.needsYou -> ChatStatus.NeedsYou
                status == SessionStatus.Busy || status == SessionStatus.Retry -> ChatStatus.Running
                session.lastRunFailed -> ChatStatus.Failed
                else -> ChatStatus.Idle
            }
        val directory = session.directory
        return ChatFeedItem(
            sourceId = id,
            profileId = profileId,
            sessionId = session.id,
            title = session.title,
            directory = directory,
            projectName =
                if (FeedFolders.isOtherFolder(directory)) FeedFolders.otherFolderLabel(directory)
                else FeedFolders.folderName(directory!!),
            status = chatStatus,
            lastActivityMs = session.time?.updatedMs ?: session.time?.createdMs ?: 0L,
            parentId = session.parentId,
            sourceLabel = label,
            finishedUnseen = chatStatus == ChatStatus.Idle && session.id in listing.finishedUnseen,
        )
    }
}
