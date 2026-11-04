package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One live connection All conversations reads (07 §5 B12). [gateway] is that exact live graph's
 * gateway; a row's actions go to the server it came from, never to whichever server is active when
 * the tap lands.
 */
public class ConversationServer(
    public val profile: ProfileId,
    public val name: String,
    public val gateway: ServerGateway,
    public val operations: ServerOperationsGateway?,
    /** Sessions a direct read confirmed gone (404); their rows are removed and stay removed. */
    public val missing: StateFlow<Set<SessionId>> = MutableStateFlow(emptySet()),
)

/**
 * The live connections. The app binds the real one (ProjectsWiring) with `replaces =
 * [NoConversationServers::class]`.
 */
public interface ConversationServers {
    public val servers: StateFlow<ImmutableList<ConversationServer>>

    public fun prefetch(ref: ConversationRef) {}

    public fun forget(ref: ConversationRef) {}
}

@ContributesBinding(AppScope::class)
@Inject
public class NoConversationServers : ConversationServers {
    override val servers: StateFlow<ImmutableList<ConversationServer>> =
        MutableStateFlow<ImmutableList<ConversationServer>>(persistentListOf()).asStateFlow()
}

/** What All conversations shows as a whole. */
public enum class AllPhase {
    /** First pages not read yet: skeleton rows. */
    Opening,

    /** No server is connected: says so and offers Servers. */
    NoServer,

    /** Every server failed its first page: the plain line, Details and Try again. */
    Failed,

    /** Nothing on any server. */
    Empty,

    /** Rows exist, none fit the search or filters. */
    NoMatch,
    List,
}

/**
 * One row. [key] is `<profile>/<session>`; [project] null = the server did not say where it works.
 */
public data class AllRowUi(
    val key: String,
    val ref: ConversationRef,
    val title: RowTitle,
    val project: String?,
    val server: String?,
    val running: Boolean,
    val archived: Boolean,
    val selected: Boolean,
    /**
     * Its server can archive conversations (`sessionArchive`); otherwise Archive says why it can't.
     */
    val canArchive: Boolean = false,
    val pinned: Boolean = false,
    val contextPercent: Int? = null,
    val readOnly: Boolean = false,
)

/** The rename dialog for one row; [initial] is the readable title the field starts with. */
public data class RenameUi(val key: String, val initial: String)

/** The one undo offer after a delete; [title] is the row's title when exactly one was deleted. */
public data class DeleteUndoUi(val id: Int, val count: Int, val title: RowTitle?)

public enum class AllProblemKind {
    Rename,
    Delete,
    Archive,
    Pin,
}

/** An action that did not happen: a plain line by [kind], the failure only under Details. */
public data class AllProblem(val kind: AllProblemKind, val failure: Failure)

/**
 * All conversations (ChatsRoute.All): every conversation on every connected server, paged, newest
 * first.
 */
public data class AllConversationsUi(
    val phase: AllPhase = AllPhase.Opening,
    val rows: ImmutableList<AllRowUi> = persistentListOf(),
    val query: String = "",
    val projects: ImmutableList<ProjectChipUi> = persistentListOf(),
    val running: Boolean = false,
    val archived: Boolean = false,
    /** Some loaded conversation is archived, so the Archived filter has something to show. */
    val hasArchived: Boolean = false,
    val selecting: Boolean = false,
    val selectedCount: Int = 0,
    /** The server of every selected row can archive. */
    val archiveAvailable: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    /**
     * Older pages read so far; it moves on with the cursor, so reaching the end asks again after
     * each one.
     */
    val loadedPages: Int = 0,
    val loadMoreFailure: Failure? = null,
    /** The first-page failure when every server failed. */
    val failure: Failure? = null,
    /** Servers whose rows did not all load, named in one quiet line. */
    val incomplete: ImmutableList<String> = persistentListOf(),
    val problem: AllProblem? = null,
    val renaming: RenameUi? = null,
    val undo: DeleteUndoUi? = null,
)

/** Intents, never widget names (ADR §9.1). */
public interface ChatsAllActions {
    public fun back()

    public fun search(query: String)

    public fun toggleRunning()

    public fun toggleArchived()

    /** Filters to [directory]; the same project again clears it. */
    public fun chooseProject(directory: String)

    public fun clearFilters()

    /** Opens the row's chat; in select mode it toggles the row instead. */
    public fun tapRow(key: String)

    /** Select mode from the bar's Select (null) or from a row's menu (that row starts selected). */
    public fun startSelecting(key: String? = null)

    public fun stopSelecting()

    public fun rename(key: String)

    public fun renameSelected()

    public fun togglePin(key: String) {}

    public fun toggleSelectedPin() {}

    /** The dialog's answer; null = cancelled or unchanged. */
    public fun finishRename(title: String?)

    public fun delete(key: String)

    public fun deleteSelected()

    public fun undoDelete()

    /** The undo window ended: the deletes are sent now. */
    public fun commitDelete()

    public fun archive(key: String)

    public fun archiveSelected()

    public fun loadMore()

    public fun retry()

    public fun dismissProblem()

    public fun openServers()
}
