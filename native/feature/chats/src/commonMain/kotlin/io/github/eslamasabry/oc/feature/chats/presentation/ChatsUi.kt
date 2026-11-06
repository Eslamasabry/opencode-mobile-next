package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.feed.ProjectSummary
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the Conversations home shows as a whole (07 §5 B01). */
public enum class ChatsPhase {
    /** Reading, nothing known yet: skeleton rows (the opening state, SCREENS-NAV-03). */
    Opening,

    /** Nothing at all on this server: the calm empty state with New conversation. */
    Empty,

    /** Rows exist but none fit the search or filters. */
    NoMatch,
    List,
}

/** The one status word a row carries (Dart chats home row tags). */
public enum class RowStatus {
    NeedsYou,
    Running,
    Failed,
    Done,
    None,
}

/**
 * One conversation row. [key] is the feed identity (list key); [ref] is the grow key and what
 * opens.
 */
public data class ConversationRowUi(
    val key: String,
    val ref: ConversationRef,
    val title: RowTitle,
    val project: String,
    val status: RowStatus,
    /** How long ago it last moved; null when the clock is unknown. */
    val ago: RelativeAge? = null,
    val running: io.github.eslamasabry.oc.engine.agents.feed.FeedRunning? = null,
    val permission: io.github.eslamasabry.oc.engine.agents.feed.FeedPermission? = null,
    val observedAtMs: Long = 0,
    val updatedAtMs: Long? = null,
    val canOpen: Boolean = true,
    /** The agent's bundled mark slug (empty: a neutral mark) and its plain name, from the persona table. */
    val agentMark: String = "",
    val agentName: String? = null,
    /** The server's name, set only when the list holds more than one source. */
    val server: String? = null,
)

/**
 * A relative time, localized by the screen (B-L10N-004): under a minute, minutes, hours, then days.
 */
public sealed interface RelativeAge {
    public data object JustNow : RelativeAge

    public data class Minutes(val count: Int) : RelativeAge

    public data class Hours(val count: Int) : RelativeAge

    public data class Days(val count: Int) : RelativeAge

    public companion object {
        public fun of(thenMs: Long, nowMs: Long): RelativeAge? {
            if (nowMs <= 0 || thenMs <= 0) return null
            val minutes = ((nowMs - thenMs).coerceAtLeast(0) / 60_000L).toInt()
            return when {
                minutes < 1 -> JustNow
                minutes < 60 -> Minutes(minutes)
                minutes < 60 * 24 -> Hours(minutes / 60)
                else -> Days(minutes / (60 * 24))
            }
        }
    }
}

/** A recent project chip; [selected] when it is the project filter. */
public data class ProjectChipUi(val directory: String, val name: String, val selected: Boolean)

/**
 * The return brief block (B15). [unknown] = "can't tell what's new", distinct from no brief at all.
 */
public data class BriefUi(
    val rows: ImmutableList<ConversationRowUi>,
    val more: Int,
    val unknown: Boolean,
    val acknowledgement: ReturnBrief = ReturnBrief.None,
    val overflowKeys: ImmutableSet<String> = persistentSetOf(),
)

/** The connection the home's server pill names (Flutter `KitShellControls` pill). */
public enum class HomeConnectionState {
    Connected,
    Connecting,
    NotAnswering,
}

/** The pill's words: the server new conversations start on, and how it answers now. */
public data class HomeConnectionUi(val server: String, val state: HomeConnectionState) {
    public companion object {
        /**
         * From the home's sources: the server still opening (Connecting, or Not answering when it failed), else the
         * main source (Not answering while it is unreachable). Null when nothing is saved yet.
         */
        public fun of(extra: ChatsHomeExtraState): HomeConnectionUi? {
            extra.opening?.let { opening ->
                return HomeConnectionUi(
                    opening.serverName,
                    if (opening.phase == ChatsOpeningPhase.Failed) HomeConnectionState.NotAnswering
                    else HomeConnectionState.Connecting,
                )
            }
            val main = extra.sources.firstOrNull { it.main } ?: extra.sources.firstOrNull() ?: return null
            return HomeConnectionUi(
                main.name,
                if (main.unreachable) HomeConnectionState.NotAnswering else HomeConnectionState.Connected,
            )
        }
    }
}

/** Filters the person chose. Value equality. */
public data class ChatsFilters(
    val projectDirectory: String? = null,
    val running: Boolean = false,
    val needsYou: Boolean = false,
    val query: String = "",
    val identities: ImmutableSet<String>? = null,
    val otherFolders: Boolean = false,
) {
    val any: Boolean
        get() =
            projectDirectory != null ||
                otherFolders ||
                running ||
                needsYou ||
                query.isNotBlank() ||
                identities != null
}

/**
 * The Conversations home (ChatsRoute.Home). No bottom bar: the gear is the way to Settings
 * (B-NAV-037).
 */
public data class ChatsUi(
    val phase: ChatsPhase = ChatsPhase.Opening,
    val nowMs: Long = 0,
    val today: ImmutableList<ConversationRowUi> = persistentListOf(),
    val earlier: ImmutableList<ConversationRowUi> = persistentListOf(),
    val needsYou: Int = 0,
    val running: Int = 0,
    val projects: ImmutableList<ProjectChipUi> = persistentListOf(),
    val filters: ChatsFilters = ChatsFilters(),
    val brief: BriefUi? = null,
    /** Connections whose rows did not all load, named in one quiet line. */
    val incomplete: ImmutableList<String> = persistentListOf(),
    val settingsAvailable: Boolean = false,
    /** A newer app release waits (the Flutter gear dot): the bar offers Update. */
    val updateReady: Boolean = false,
    val projectChoices: ImmutableList<ProjectSummary> = persistentListOf(),
    val projectNeedsYou: ImmutableSet<String> = persistentSetOf(),
    val allCount: Int = 0,
    val otherFoldersCount: Int = 0,
    val otherFoldersSelected: Boolean = false,
    val sheet: ChatsHomeSheet? = null,
    val home: ChatsHomeExtraState = ChatsHomeExtraState(),
    val inbox: ImmutableList<ConversationRowUi> = persistentListOf(),
    val finished: ImmutableList<ConversationRowUi> = persistentListOf(),
    val confirmWatched: ChatsWatchedRequestUi? = null,
    val answering: ImmutableSet<String> = persistentSetOf(),
    val answerFailure: io.github.eslamasabry.oc.core.common.Failure? = null,
    /** Row keys whose server can archive them (OpenCode conversations on a server with `sessionArchive`). */
    val archivable: ImmutableSet<String> = persistentSetOf(),
    /** The one Undo offer after an archive; [ArchiveUndoUi.id] restarts the bar's window. */
    val archiveUndo: ArchiveUndoUi? = null,
    val archiveFailure: io.github.eslamasabry.oc.core.common.Failure? = null,
    /** N-30: which row act [archiveFailure] belongs to (archive, delete or pin), for its words. */
    val archiveFailureAct: RowActFailure = RowActFailure.Archive,
    /** Row keys whose server can rename them: OpenCode conversations on a live connection (N-30). */
    val renamable: ImmutableSet<String> = persistentSetOf(),
    /** N-30: row keys whose server deletes them. */
    val deletable: ImmutableSet<String> = persistentSetOf(),
    /** N-30: row keys that can be pinned on this phone, and those pinned now. */
    val pinnable: ImmutableSet<String> = persistentSetOf(),
    val pinned: ImmutableSet<String> = persistentSetOf(),
    /** The rename dialog (N-30: Rename from the row menu), or null. */
    val renaming: RenameUi? = null,
    val renameFailure: io.github.eslamasabry.oc.core.common.Failure? = null,
) {
    /** The server pill's words; null before anything is saved. */
    val connection: HomeConnectionUi?
        get() = HomeConnectionUi.of(home)

    /** A computer is saved (not only this phone): the first-run page then skips "Connect a computer". */
    val hasComputer: Boolean
        get() = home.sources.any { !it.onThisPhone }
}

/** The conversation just archived (or, with [deleted], deleted), waiting out its undo window. */
public data class ArchiveUndoUi(val id: Int, val title: RowTitle, val deleted: Boolean = false)

/** N-30: the row act a home failure notice speaks for. */
public enum class RowActFailure { Archive, Delete, Pin }

/**
 * What a list row's menu does beyond Open and Archive (Flutter global-sessions row menu). Each
 * opens the conversation's own page and runs the same act the conversation menu runs, so there is
 * one path to the server for each.
 */
public enum class RowAct(internal val headerAction: String?) {
    Details("Details"),
    Share("Share"),
    /** "Copy handoff": the command that resumes it on a computer. */
    CopyHandoff("ContinueOnComputer"),
    /** "Open related": the parent and sub-agent conversations. */
    OpenRelated(null),
}

/** Opens [ref] for [act]; shared by the home list and All conversations. */
internal fun io.github.eslamasabry.oc.ui.routes.AppNavigator.openRowAct(
    ref: ConversationRef,
    act: RowAct,
) {
    val name = act.headerAction
    if (name == null) open(io.github.eslamasabry.oc.ui.routes.SessionRelationsRoute(ref))
    else open(io.github.eslamasabry.oc.ui.routes.ChatRoute(ref, thenAction = name))
}

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    /** A row's menu act other than Open and Archive; no-op where the list cannot offer it. */
    public fun rowAct(row: ConversationRowUi, act: RowAct) {}

    public fun answerPermission(row: ConversationRowUi, allow: Boolean) {}
    public fun openInbox() {}

    public fun openConversation(ref: ConversationRef)

    /** Hides the row at once and archives it when the undo window ends; one offer at a time. */
    public fun archive(row: ConversationRowUi) {}

    /** Brings the row back and cancels its archive. */
    public fun undoArchive() {}

    /** The undo window ended (or another offer replaced it): send the archive now. */
    public fun commitArchive() {}

    public fun dismissArchiveFailure() {}

    /** Opens the rename dialog for [row]; no-op where its server cannot rename. */
    public fun rename(row: ConversationRowUi) {}

    /** The dialog's answer: the new title, or null when the person cancelled. */
    public fun finishRename(title: String?) {}

    public fun dismissRenameFailure() {}

    /** N-30: hides the row at once and deletes it when the undo window ends (the same window as archive). */
    public fun delete(row: ConversationRowUi) {}

    /** N-30: pins or unpins the row's conversation on this phone (the same pins as All conversations). */
    public fun togglePin(row: ConversationRowUi) {}

    public fun newConversation()

    public fun openSettings()

    /** Opens the Updates page (only offered while [ChatsUi.updateReady]). */
    public fun openUpdate() {}

    public fun search(query: String)

    public fun toggleRunning()

    public fun toggleNeedsYou()

    /** Filters to [directory]; the same project again clears the filter. */
    public fun chooseProject(directory: String)

    public fun clearFilters()

    public fun dismissBrief(shown: ReturnBrief)

    public fun showBriefOverflow(shown: BriefUi)

    public fun showAll()

    /** Search conversations across the server (unit P12-SEARCH-USAGE). */
    public fun openSearch() {}

    /**
     * The Projects page (P12): New project, Open a folder, Opened before, then the server's
     * projects.
     */
    public fun openAttention() {}

    public fun openProjects()

    public fun showProjectChoices() {}

    public fun showSources() {}

    public fun showInbox() {}

    public fun closeHomeSheet() {}

    public fun allProjects() {}

    public fun otherFolders() {}

    public fun showSource(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        shown: Boolean,
    ) {}

    public fun retrySource(profile: io.github.eslamasabry.oc.core.common.ids.ProfileId) {}

    public fun retryOpening() {}

    public fun openServers() {}

    /** Edit the sign-in (password or token) of the server that is failing to open, inline in its form. */
    public fun editOpeningServer() {}
    /** First run: add a computer (the server form). */
    public fun connectComputer() {}

    /** First run: the offline example conversation. */
    public fun openDemo() {}

    public fun openFilterProject() {}

    public fun finishFilterProject(directory: String?) {}

    public fun showMonitoring() {}

    public fun setWatched(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        enabled: Boolean,
    ) {}

    public fun refreshWatched() {}

    public fun openWatched(key: String) {}

    public fun confirmWatched() {}

    public fun dismissWatched() {}
}

/** P2 proof page for ChatRoute until :feature:chat installs the real one. */
public data class ChatPreviewUi(
    val ref: ConversationRef,
    val title: String,
    val lines: ImmutableList<String>,
)

public interface ChatPreviewActions {
    public fun back()
}
