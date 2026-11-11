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

    /**
     * Cache-first: saved servers the list shows that are not connected yet (connecting, starting or away). All
     * conversations draws their kept rows ([ConversationPreviews.read] by profile) instead of a "no server" or
     * skeleton page; the live rows replace them when the server answers.
     */
    public val waiting: StateFlow<ImmutableList<ServerName>>
        get() = NO_WAITING

    public fun prefetch(ref: ConversationRef) {}

    public fun forget(ref: ConversationRef) {}

    /**
     * The person archived or deleted [ref] and the server took it (after any Undo window). The app tells what follows
     * conversations by identity (the edge dock drops its ring); the server sends nothing for an archive.
     */
    public fun left(ref: ConversationRef) {}
}

private val NO_WAITING: StateFlow<ImmutableList<ServerName>> = MutableStateFlow(persistentListOf())

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
    val pinAvailable: Boolean = false,
    val pinned: Boolean = false,
    val contextPercent: Int? = null,
    val readOnly: Boolean = false,
    /** Its server can move it into the project that is open (Flutter "Continue here"). */
    val canContinueHere: Boolean = false,
    /** Last change the server reported, epoch ms; 0 = not told. The row says how long ago. */
    val updatedMs: Long = 0L,
    /** A run finished after the person last opened it ("Unread result"). */
    val unread: Boolean = false,
    /** The person stopped the run that ended it ("Stopped"). */
    val stopped: Boolean = false,
    /** The server ended the run and the person has not opened it since ("Interrupted"). */
    val interrupted: Boolean = false,
    /** The last run failed ("Failed"). */
    val failed: Boolean = false,
    /** Blocked on a permission, question or form ("Needs you"). */
    val needsYou: Boolean = false,
    /** The folder it works in, for the row menu's Copy folder path; null = the server did not say. */
    val folder: String? = null,
)

/**
 * What the home feed knows about a row that the server's session list does not (Flutter
 * `controller.isSessionUnread`, the needs-you set).
 */
public data class RowAttention(
    val needsYou: Boolean = false,
    val unread: Boolean = false,
    /** The person pressed Stop on the run that ended it. */
    val stopped: Boolean = false,
    val interrupted: Boolean = false,
    val failed: Boolean = false,
)

/**
 * "Import a conversation" from All conversations (Flutter `SessionImportScreen`): [choices] while more than one
 * connected server can import and none is picked yet, then the reviewed import on [server].
 */
public data class ImportUi(
    val choices: ImmutableList<ServerName> = persistentListOf(),
    val server: String? = null,
    val session: io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportUi? = null,
)

/**
 * The "Continue here" question: names both projects, says a working conversation is interrupted.
 */
public data class MoveHereUi(
    val key: String,
    val title: RowTitle,
    val from: String?,
    val to: String?,
    val sessionWorking: Boolean,
    val working: Boolean,
    val failed: Boolean,
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
    val moveHere: MoveHereUi? = null,
    /** H4: see [StartNewUi]. */
    val startNew: StartNewUi? = null,
    val undo: DeleteUndoUi? = null,
    /** Some connected server can import a conversation file: the bar offers it. */
    val canImport: Boolean = false,
    /** The import sheet while it is open. */
    val import: ImportUi? = null,
    /** Now, epoch ms, for the rows' "how long ago"; 0 = unknown. */
    val nowMs: Long = 0L,
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

    /** Asks "Move to this project?" for the row; nothing moves until [confirmContinueHere]. */
    public fun continueHere(key: String) {}

    /** H4: the "Start a new conversation?" question said yes / no. */
    public fun confirmStartNew() {}

    public fun dismissStartNew() {}

    public fun confirmContinueHere() {}

    public fun cancelContinueHere() {}

    /** A row's Share, Details, Copy handoff or Open related. */
    public fun rowAct(key: String, act: RowAct) {}

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

    /** Opens Import a conversation (the one server that can, else a choice of servers first). */
    public fun openImport() {}

    public fun importPick(profile: ProfileId) {}

    public fun importChoose() {}

    public fun importReview(text: String) {}

    public fun importDestination(directory: String, workspace: String) {}

    public fun importSend() {}

    /** Opens the imported conversation and closes the sheet. */
    public fun importOpen() {}

    public fun closeImport() {}
}
