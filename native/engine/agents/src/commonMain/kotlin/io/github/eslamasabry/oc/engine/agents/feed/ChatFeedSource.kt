package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRunOutcome
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What one source holds right now. Sources keep partial failures: a failed read keeps the last rows
 * (17 §1.6).
 */
public data class FeedSourceState(
    val items: ImmutableList<ChatFeedItem> = persistentListOf(),
    val loading: Boolean = false,
    val complete: Boolean = true,
    val failed: Boolean = false,
    val acrossProjects: Boolean = true,
)

/**
 * One feed source (`opencode`, later `paseo:<directory>` and side connections, 17 §1.6). A source
 * may be shared by several feeds; a feed never closes it (B-STATE-029).
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
 * What the connection engine knows about one OpenCode server's sessions (read-only port; bound by
 * the connection owner over its session map, status map and pending requests). Times and ids only;
 * never prompt text.
 */
public data class OpenCodeListing(
    val sessions: List<Session> = emptyList(),
    val statuses: Map<SessionId, SessionStatus> = emptyMap(),
    /** Sessions blocked on a permission, question or form. */
    val needsYou: Set<SessionId> = emptySet(),
    /**
     * Sessions whose last run finished after the person last opened them (device read watermarks).
     */
    val finishedUnseen: Set<SessionId> = emptySet(),
    /** Sessions whose latest turn the person stopped from this app (`ConnectionDelivery.stoppedByPerson`). */
    val stoppedByPerson: Set<SessionId> = emptySet(),
    val loading: Boolean = false,
    val complete: Boolean = true,
    val failed: Boolean = false,
    /** `ServerCapabilities.globalSessionSearch`: false = the current project only. */
    val acrossProjects: Boolean = true,
    val running: Map<SessionId, FeedRunning> = emptyMap(),
    val permissions: Map<SessionId, FeedPermission> = emptyMap(),
    val questions: Map<SessionId, FeedQuestion> = emptyMap(),
    val forms: Map<SessionId, FeedForm> = emptyMap(),
)

/**
 * The OpenCode source (Dart `ConnectionController` as `ChatFeedSource`), one per connected server.
 */
public class OpenCodeChatFeedSource(
    private val profileId: ProfileId,
    override val label: String?,
    private val listing: StateFlow<OpenCodeListing>,
    private val locationKey: String? = null,
    /**
     * Who runs a row, as (agent id, agent label), for a server whose conversations are an agent's (the phone's
     * agents). Null keeps the row an OpenCode one.
     */
    private val agentOf: (Session) -> Pair<String, String?>? = { null },
    /** Whether reopening the session needs a new conversation (the agents helper's answer); null = unknown. */
    private val needsNewChat: (Session) -> Boolean? = { null },
    private val refresh: suspend () -> Outcome<Unit> = { Unit.ok() },
) : ChatFeedSource {
    override val id: String = "opencode:${profileId.raw}"

    override fun current(): FeedSourceState = map(listing.value)

    override val changes: Flow<FeedSourceState> = listing.map(::map).distinctUntilChanged()

    override suspend fun refresh(): Outcome<Unit> = refresh.invoke()

    private fun map(listing: OpenCodeListing): FeedSourceState {
        // A parent whose DIRECT child waits on the person reads "Needs you" too. Worked out at every read, never
        // stored, so it goes the moment the child's request does (background sub-agents end the parent's turn).
        val needyParents =
            listing.sessions.filter { it.id in listing.needsYou }.mapNotNullTo(hashSetOf()) { it.parentId }
        return FeedSourceState(
            items =
                listing.sessions
                    .filter { it.time?.archivedMs == null }
                    .map { row(it, listing, needyParents) }
                    .sortedWith(ChatFeedRules.order)
                    .toImmutableList(),
            loading = listing.loading && listing.sessions.isEmpty(),
            complete = listing.complete && !listing.failed,
            failed = listing.failed,
            acrossProjects = listing.acrossProjects,
        )
    }

    /**
     * Failed only when the last run really failed. When the server reports how the last run ended, that word decides
     * (a run it says succeeded or was cut is not a failure, whatever flag an earlier failed run left here); the flag
     * speaks only where the server reported nothing.
     */
    private fun lastRunFailed(session: Session): Boolean =
        when (session.lastRunOutcome) {
            SessionRunOutcome.Failed -> true
            SessionRunOutcome.Succeeded,
            SessionRunOutcome.Interrupted -> false
            null -> session.lastRunFailed
        }

    private fun statusOf(session: Session, listing: OpenCodeListing, needyParents: Set<SessionId>): ChatStatus {
        val status = listing.statuses[session.id]
        return when {
            session.id in listing.needsYou || session.id in needyParents -> ChatStatus.NeedsYou
            status == SessionStatus.Busy || status == SessionStatus.Retry -> ChatStatus.Running
            lastRunFailed(session) -> ChatStatus.Failed
            else -> ChatStatus.Idle
        }
    }

    private fun row(session: Session, listing: OpenCodeListing, needyParents: Set<SessionId>): ChatFeedItem {
        val chatStatus = statusOf(session, listing, needyParents)
        val directory = session.directory
        val agent = agentOf(session)
        return ChatFeedItem(
            sourceId = id,
            agentId = agent?.first ?: OPENCODE_AGENT_ID,
            agentLabel = agent?.second,
            locationKey = locationKey,
            running = listing.running[session.id].takeIf { chatStatus == ChatStatus.Running },
            permission = listing.permissions[session.id],
            question = listing.questions[session.id],
            form = listing.forms[session.id],
            profileId = profileId,
            sessionId = session.id,
            title = session.title,
            directory = directory,
            projectName = FeedFolders.rowLabel(directory),
            status = chatStatus,
            lastActivityMs = session.time?.updatedMs ?: session.time?.createdMs ?: 0L,
            parentId = session.parentId,
            sourceLabel = label,
            finishedUnseen = chatStatus == ChatStatus.Idle && session.id in listing.finishedUnseen,
            // OpenCode 2 can save a stopped text-only reply as Succeeded; only this memory knows the person stopped it.
            stoppedByPerson = chatStatus == ChatStatus.Idle && session.id in listing.stoppedByPerson,
            interrupted =
                chatStatus == ChatStatus.Idle &&
                    session.lastRunOutcome == SessionRunOutcome.Interrupted &&
                    session.id !in listing.stoppedByPerson &&
                    session.id in listing.finishedUnseen,
            needsNewChat = chatStatus != ChatStatus.Running && needsNewChat(session) == true,
        )
    }
}
