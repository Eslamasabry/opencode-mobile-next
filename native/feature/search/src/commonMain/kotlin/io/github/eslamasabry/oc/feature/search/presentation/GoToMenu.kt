package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/** A place Search's Go to menu can open. */
public enum class GoToPlace { NeedsYou, Files, Projects, Settings, Servers, AddServer, Terminal, Help, NewConversation }

/** Where a row sits: the Go to places, the recent conversations, or an action found only once typed. */
public enum class GoToSection { GoTo, Recent, Action }

/** One row of the menu. [place] null = a recent conversation ([ref], [title]). */
public data class GoToRow(
    val section: GoToSection,
    val place: GoToPlace? = null,
    /** Needs you: how many requests wait (0 = "Nothing waits"). */
    val waiting: Int = 0,
    /** Files: the current project's name (also found by typing it). */
    val project: String? = null,
    val ref: ConversationRef? = null,
    val title: String? = null,
)

/** What the menu is built from: what the connection supports (never its flavour) and the person's recent work. */
public data class GoToInput(
    val fileBrowsing: Boolean,
    val terminal: Boolean,
    val projectManagement: Boolean,
    val hasSavedServer: Boolean,
    val waiting: Int,
    val project: String?,
    /** Newest first. */
    val recent: List<Pair<ConversationRef, String?>>,
)

/**
 * Search's Go to menu (B-NAV-056), port of the Dart search launcher (`search_go_to_test`): with nothing typed it lists
 * the places first — Needs you (with how many wait), then Files, Projects, Settings, Servers (or Add a server), Terminal
 * and Help, each only when the connection supports it — then the last three conversations. Typing filters the same
 * rows by their words; Recent rows step aside (conversations are searched as themselves) and New conversation is found
 * only once typed. No copy here: [match] takes the row's words from the screen.
 */
public object GoToMenu {
    public const val RECENT_LIMIT: Int = 3

    public fun rows(input: GoToInput): List<GoToRow> =
        buildList {
            add(GoToRow(GoToSection.GoTo, GoToPlace.NeedsYou, waiting = input.waiting))
            if (input.fileBrowsing) add(GoToRow(GoToSection.GoTo, GoToPlace.Files, project = input.project))
            if (input.projectManagement) add(GoToRow(GoToSection.GoTo, GoToPlace.Projects))
            add(GoToRow(GoToSection.GoTo, GoToPlace.Settings))
            add(GoToRow(GoToSection.GoTo, if (input.hasSavedServer) GoToPlace.Servers else GoToPlace.AddServer))
            if (input.terminal) add(GoToRow(GoToSection.GoTo, GoToPlace.Terminal))
            add(GoToRow(GoToSection.GoTo, GoToPlace.Help))
            input.recent.take(RECENT_LIMIT).forEach { (ref, title) -> add(GoToRow(GoToSection.Recent, ref = ref, title = title)) }
            add(GoToRow(GoToSection.Action, GoToPlace.NewConversation))
        }

    /**
     * The rows shown for [query]. Empty: Go to then Recent, no actions. Typed: every Go to and action row whose words
     * ([words] gives the label and any extra names, e.g. "Inbox" for Needs you, the project's name for Files) contain
     * each typed word at a word start.
     */
    public fun match(rows: List<GoToRow>, query: String, words: (GoToRow) -> List<String>): List<GoToRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return rows.filter { it.section == GoToSection.GoTo } + rows.filter { it.section == GoToSection.Recent }
        }
        val parts = q.split(Regex("""\s+"""))
        return rows.filter { it.section != GoToSection.Recent }.filter { row ->
            val tokens = words(row).flatMap { it.lowercase().split(Regex("""[^\p{L}\p{N}]+""")) }.filter { it.isNotEmpty() }
            parts.all { p -> tokens.any { it.startsWith(p) } }
        }
    }
}
