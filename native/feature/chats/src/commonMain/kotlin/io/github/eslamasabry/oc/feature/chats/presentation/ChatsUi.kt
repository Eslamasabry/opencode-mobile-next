package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
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
    val canOpen: Boolean = true,
)

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

/** Filters the person chose. Value equality. */
public data class ChatsFilters(
    val projectDirectory: String? = null,
    val running: Boolean = false,
    val needsYou: Boolean = false,
    val query: String = "",
    val identities: ImmutableSet<String>? = null,
) {
    val any: Boolean
        get() =
            projectDirectory != null ||
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
)

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    public fun openConversation(ref: ConversationRef)

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
    public fun openProjects()
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
