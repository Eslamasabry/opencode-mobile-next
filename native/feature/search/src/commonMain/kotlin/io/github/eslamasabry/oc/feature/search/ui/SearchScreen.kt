package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationResult
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchPlace
import io.github.eslamasabry.oc.feature.search.presentation.SearchProject
import io.github.eslamasabry.oc.feature.search.presentation.SearchRecentConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchResults
import io.github.eslamasabry.oc.feature.search.presentation.SearchRowStatus
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.nav.KitShortcut
import io.github.eslamasabry.oc.ui.kit.nav.KitShortcutRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentPersonaOtherName
import io.github.eslamasabry.oc.ui.words.res.chatsHomeFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNeedsYou
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceNeedsYou
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUntitled
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyEnable
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchConversationsWhat
import io.github.eslamasabry.oc.ui.words.res.p12SearchFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchField
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromAgent
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromYou
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideHelp
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideReading
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchOneProject
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecent
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentClear
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentRemove
import io.github.eslamasabry.oc.ui.words.res.p12SearchSearching
import io.github.eslamasabry.oc.ui.words.res.p12SearchTryAgain
import io.github.eslamasabry.oc.ui.words.res.protoWebSearchHelp
import io.github.eslamasabry.oc.ui.words.res.searchB1AboutOptions
import io.github.eslamasabry.oc.ui.words.res.searchB1ChooseServer
import io.github.eslamasabry.oc.ui.words.res.searchB1Clear
import io.github.eslamasabry.oc.ui.words.res.searchB1Conversations
import io.github.eslamasabry.oc.ui.words.res.searchB1InsideMessages
import io.github.eslamasabry.oc.ui.words.res.searchB1OptionsTitle
import io.github.eslamasabry.oc.ui.words.res.searchB1Places
import io.github.eslamasabry.oc.ui.words.res.searchB1ProjectConversations
import io.github.eslamasabry.oc.ui.words.res.searchB1RecentConversations
import io.github.eslamasabry.oc.ui.words.res.searchB1SeeAll
import io.github.eslamasabry.oc.ui.words.res.searchB1SeeAllSpoken
import io.github.eslamasabry.oc.ui.words.res.searchB1Web
import io.github.eslamasabry.oc.ui.words.res.settingsV3Servers
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The Web chip: shown only when the server can search the web (or its panel is open), and what tapping it does. */
public data class SearchWebOption(val open: Boolean, val onToggle: () -> Unit)

/**
 * Search (map `search`, owner pick B1 2026-11-08). One rounded bar in the Style's look holds the back arrow, the field
 * (focused, keyboard up) and the server pill, which opens the choice of server to search. Under it two option chips,
 * Web and Inside messages, with ⓘ explaining both. With nothing typed: the places as bare glyphs (Needs you with its
 * count), recent searches as chips with Clear, then the server's recent conversations. While typing the results come in
 * groups (Conversations with See all, Projects, Settings and pages), each with its glyph or agent mark and the matched
 * words in the accent ink. Kit parts only.
 */
@Composable
public fun SearchScreen(
    state: SearchUi,
    actions: SearchActions,
    field: TextFieldState = rememberTextFieldState(state.query),
    web: SearchWebOption? = null,
    webSearch: @Composable () -> Unit = {},
) {
    var explaining by remember { mutableStateOf(false) }
    var choosingServer by remember { mutableStateOf(false) }
    var allConversations by remember(state.query) { mutableStateOf(false) }
    val destinationWords = SearchPlace.entries.associateWith { searchPlaceWords(it) }
    val destinations =
        SearchIndex.find(state.query, state.destinations) { destinationWords.getValue(it) }
    val conversations = SearchResults.conversations(state)
    val projects = SearchResults.projects(state.activity.projects, state.query)
    KitScreen(
        page = true,
        search = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                SearchBar(state, actions, field) { choosingServer = true }
                Options(state, actions, web) { explaining = true }
            }
        },
        loading = state.phase == SearchPhase.Searching,
        loadingLabel = stringResource(Res.string.p12SearchSearching),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            item(key = "web-search") { webSearch() }
            if (state.recentFailed) {
                item(key = "recent-failed") { KitNotice(stringResource(Res.string.p12SearchRecentFailed)) }
            }
            when {
                state.phase == SearchPhase.NoServer -> noServer(actions)
                state.query.isBlank() -> idle(state, actions, field)
                else -> {
                    if (state.phase == SearchPhase.Failed) failed(state, actions)
                    if (!state.allProjects && conversations.isNotEmpty()) {
                        item(key = "one-project") { KitNotice(stringResource(Res.string.p12SearchOneProject)) }
                    }
                    conversations(state, conversations, allConversations, actions) { allConversations = true }
                    more(state, actions)
                    inside(state)
                    projects(state, projects, actions)
                    places(state, destinations, destinationWords, actions)
                    if (
                        state.phase == SearchPhase.NoMatch &&
                            conversations.isEmpty() &&
                            projects.isEmpty() &&
                            destinations.isEmpty()
                    )
                        item(key = "no-match") {
                            KitSearchNoMatch(
                                query = state.query,
                                onClear = { field.edit { replace(0, length, "") } },
                                what = stringResource(Res.string.p12SearchConversationsWhat),
                            )
                        }
                }
            }
            item(key = "end") { Box(Modifier.padding(bottom = Space.S6.dp)) }
        }
        OptionsSheet(explaining, web != null) { explaining = false }
        ServerSheet(choosingServer, state, actions) { choosingServer = false }
    }
}

/** The bar: back, the field and the server pill, in one rounded field that takes the Style's look (Glass frosts it). */
@Composable
private fun SearchBar(state: SearchUi, actions: SearchActions, field: TextFieldState, chooseServer: () -> Unit) {
    val name = state.serverName
    val choose = stringResource(Res.string.searchB1ChooseServer)
    // The home's own pill (same dot, same chevron), composed here; it opens the choice of server.
    val pill: (@Composable () -> Unit)? =
        if (name == null) null
        else ({
            Box(Modifier.widthIn(max = 168.dp)) {
                KitStatusPill(label = name, tone = KitConnectionTone.Ok, spoken = "$name, $choose", onClick = chooseServer, onClickLabel = choose)
            }
        })
    KitSearchField(
        label = stringResource(Res.string.p12SearchField),
        onSearch = actions::search,
        modifier = Modifier.padding(top = Space.S2.dp),
        state = field,
        onSubmit = actions::submit,
        resultCount =
            if (state.phase == SearchPhase.Results || state.phase == SearchPhase.NoMatch) {
                state.titleHits.size + state.contentHits.size
            } else {
                null
            },
        partial = state.content == ContentPhase.Searching,
        prominent = true,
        onBack = actions::back,
        backLabel = stringResource(Res.string.kitTopBarBack),
        autoFocus = true,
        trailing = pill,
    )
}

/** Web and Inside messages as two toggle chips, then ⓘ: the two switch rows they replaced, one tap each. */
@Composable
private fun Options(state: SearchUi, actions: SearchActions, web: SearchWebOption?, explain: () -> Unit) {
    if (state.phase == SearchPhase.NoServer && web == null) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (web != null) {
            KitChip(
                label = stringResource(Res.string.searchB1Web),
                kind = KitChipKind.Action,
                icon = KitIcons.globe,
                selected = web.open,
                onClick = web.onToggle,
            )
        }
        if (state.phase != SearchPhase.NoServer) {
            val inside = state.content != ContentPhase.Off
            KitChip(
                label = stringResource(Res.string.searchB1InsideMessages),
                kind = KitChipKind.Action,
                icon = KitIcons.chat,
                selected = inside,
                onClick = { actions.searchInside(!inside) },
            )
        }
        KitIconButton(
            icon = KitIcons.info,
            label = stringResource(Res.string.searchB1AboutOptions),
            onClick = explain,
            tone = TextTone.Secondary,
        )
    }
}

/** ⓘ: what each chip does, in the words its old switch row used. */
@Composable
private fun OptionsSheet(visible: Boolean, web: Boolean, close: () -> Unit) {
    KitSheet(visible = visible, onDismissRequest = close, title = stringResource(Res.string.searchB1OptionsTitle)) {
        if (web) {
            KitRow(
                title = stringResource(Res.string.searchB1Web),
                leading = KitRowLeading.Glyph(KitIcons.globe),
                supporting = stringResource(Res.string.protoWebSearchHelp),
                supportingMaxLines = 4,
            )
        }
        KitRow(
            title = stringResource(Res.string.searchB1InsideMessages),
            leading = KitRowLeading.Glyph(KitIcons.chat),
            supporting = stringResource(Res.string.p12SearchInsideHelp),
            supportingMaxLines = 4,
        )
    }
}

/** The pill's sheet: the connected servers to search (the one searched now checked), then Servers. */
@Composable
private fun ServerSheet(visible: Boolean, state: SearchUi, actions: SearchActions, close: () -> Unit) {
    KitSheet(visible = visible, onDismissRequest = close, title = stringResource(Res.string.searchB1ChooseServer)) {
        for (server in state.servers) {
            val chosen = server.id == state.server
            KitRow(
                title = server.name,
                leading = KitRowLeading.Glyph(KitIcons.server, current = chosen),
                selected = chosen,
                trailing = if (chosen) KitRowTrailing.Check else KitRowTrailing.None,
                onClick = {
                    close()
                    actions.chooseServer(server.id)
                },
            )
        }
        KitRow(
            title = stringResource(Res.string.p12SearchOpenServers),
            leading = KitRowLeading.Glyph(KitIcons.settingsAdvanced),
            trailing = KitRowTrailing.Chevron,
            onClick = {
                close()
                actions.openServers()
            },
        )
    }
}

private fun LazyListScope.noServer(actions: SearchActions) {
    item(key = "no-server") {
        KitStateView(
            kind = KitStateKind.Missing,
            title = stringResource(Res.string.p12SearchNoServerTitle),
            body = stringResource(Res.string.p12SearchNoServerBody),
            primary = KitAction(stringResource(Res.string.p12SearchOpenServers), actions::openServers),
        )
    }
}

/** Nothing typed: the places, recent searches, then recent conversations. */
private fun LazyListScope.idle(state: SearchUi, actions: SearchActions, field: TextFieldState) {
    item(key = "shortcuts") {
        KitShortcutRow(
            SearchResults.shortcuts(state.goTo).map { place ->
                KitShortcut(
                    icon = placeIcon(place),
                    label = shortcutLabel(place),
                    onClick = { actions.goTo(GoToRow(GoToSection.GoTo, place)) },
                    needsYou = if (place == GoToPlace.NeedsYou) state.activity.needsYou else 0,
                )
            },
            Modifier.padding(top = Space.S2.dp),
        )
    }
    if (state.recent.isNotEmpty()) {
        item(key = "recent-label") {
            KitSectionLabel(
                stringResource(Res.string.p12SearchRecent),
                trailing = {
                    KitButton(
                        label = stringResource(Res.string.searchB1Clear),
                        onClick = actions::clearRecent,
                        role = KitButtonRole.Tertiary,
                        semanticsLabel = stringResource(Res.string.p12SearchRecentClear),
                    )
                },
            )
        }
        item(key = "recent-chips") {
            KitChipWrap(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                for (query in state.recent) {
                    KitChip(
                        label = query,
                        kind = KitChipKind.Removable,
                        removeLabel = stringResource(Res.string.p12SearchRecentRemove, query),
                        // Filling the field runs the search once typing settles, exactly as typed.
                        onClick = {
                            field.edit {
                                replace(0, length, query)
                                placeCursorBeforeCharAt(length)
                            }
                        },
                        onRemove = { actions.forgetRecent(query) },
                    )
                }
            }
        }
    }
    val recent = SearchResults.recent(state)
    if (recent.isNotEmpty()) {
        item(key = "recent-conversations-label") {
            KitSectionLabel(stringResource(Res.string.searchB1RecentConversations))
        }
        items(recent, key = { "r/" + it.ref.profileId.raw + "/" + it.ref.sessionId.raw }) { row ->
            RecentRow(row, state.nowMs, actions)
        }
    }
}

@Composable
private fun RecentRow(row: SearchRecentConversation, nowMs: Long, actions: SearchActions) {
    KitRow(
        title = row.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.globalSessionsUntitled),
        leading = agentLeading(row.agentMark, row.agentName),
        supporting = row.project,
        state = statusOf(row.status),
        meta = row.updatedMs?.takeIf { nowMs > 0 }?.let { kitRelativeAge(it, nowMs) },
        onClick = { actions.openRecent(row) },
    )
}

private fun LazyListScope.failed(state: SearchUi, actions: SearchActions) {
    item(key = "failed") {
        KitErrorNotice(
            stringResource(Res.string.p12SearchFailed),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            failure = state.failure,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            retry = KitLineAction(stringResource(Res.string.p12SearchTryAgain), actions::retry),
        )
    }
}

/** Conversations: the first [SearchResults.COLLAPSED], then See all N opens the rest in place. */
private fun LazyListScope.conversations(
    state: SearchUi,
    rows: List<SearchConversationResult>,
    all: Boolean,
    actions: SearchActions,
    seeAll: () -> Unit,
) {
    if (rows.isEmpty()) return
    val folded = !all && rows.size > SearchResults.COLLAPSED
    item(key = "conversations-label") {
        val n = Numbers.format(rows.size)
        val label = stringResource(Res.string.searchB1SeeAll, n)
        val spoken = stringResource(Res.string.searchB1SeeAllSpoken, n)
        val more: (@Composable () -> Unit)? =
            if (!folded) null
            else ({ KitButton(label = label, onClick = seeAll, role = KitButtonRole.Tertiary, semanticsLabel = spoken) })
        KitSectionLabel(stringResource(Res.string.searchB1Conversations), trailing = more)
    }
    val shown = if (folded) rows.take(SearchResults.COLLAPSED) else rows
    items(shown, key = { "c/" + it.hit.key }) { ConversationRow(it, state, actions) }
}

@Composable
private fun ConversationRow(row: SearchConversationResult, state: SearchUi, actions: SearchActions) {
    val hit = row.hit
    val title = hit.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.globalSessionsUntitled)
    val snippet =
        hit.snippet?.let {
            // The excerpt is the person's or the agent's own words: isolated so it never reorders the line.
            stringResource(if (hit.snippetFromYou) Res.string.p12SearchFromYou else Res.string.p12SearchFromAgent, "⁨$it⁩")
        }
    KitRow(
        title = title,
        leading = agentLeading(row.agentMark, row.agentName),
        supporting = snippet ?: hit.project,
        supportingMaxLines = if (snippet != null) 2 else 1,
        state = if (snippet == null) statusOf(row.status) else null,
        meta = row.updatedMs?.takeIf { state.nowMs > 0 }?.let { kitRelativeAge(it, state.nowMs) },
        highlight = state.query,
        onClick = { actions.open(hit) },
    )
}

/** Load more while the server has another page, or Try again after that page failed. */
private fun LazyListScope.more(state: SearchUi, actions: SearchActions) {
    if (state.loadMoreFailed) {
        item(key = "more-failed") {
            KitNotice(
                stringResource(Res.string.globalSessionsLoadMoreFailed),
                actions = listOf(KitLineAction(stringResource(Res.string.p12SearchTryAgain), actions::loadMore)),
            )
        }
    } else if (state.canLoadMore) {
        item(key = "more") {
            Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                KitButton(
                    label = stringResource(Res.string.globalSessionsLoadMore),
                    onClick = actions::loadMore,
                    role = KitButtonRole.Tertiary,
                    working = state.loadingMore,
                )
            }
        }
    }
}

/** The inside-messages read's own line while it runs or after it failed. */
private fun LazyListScope.inside(state: SearchUi) {
    when (state.content) {
        ContentPhase.Searching ->
            item(key = "inside-reading") {
                KitText(
                    stringResource(Res.string.p12SearchInsideReading),
                    role = KitTextRole.Supporting,
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                )
            }
        ContentPhase.Failed -> item(key = "inside-failed") { KitNotice(stringResource(Res.string.p12SearchInsideFailed)) }
        ContentPhase.Off,
        ContentPhase.Done -> Unit
    }
}

private fun LazyListScope.projects(state: SearchUi, projects: List<SearchProject>, actions: SearchActions) {
    if (projects.isEmpty()) return
    item(key = "projects-label") { KitSectionLabel(stringResource(Res.string.e7ProjectProjectsTitle)) }
    items(projects, key = { "p/" + it.directory }) { project ->
        val count = pluralStringResource(Res.plurals.searchB1ProjectConversations, project.conversations, Numbers.format(project.conversations))
        KitRow(
            title = project.name,
            leading = KitRowLeading.Glyph(KitIcons.folders),
            supporting = listOfNotNull(state.serverName, count).joinToString(" · "),
            trailing = KitRowTrailing.Chevron,
            highlight = state.query,
            onClick = { actions.openProject(project) },
        )
    }
}

private fun LazyListScope.places(
    state: SearchUi,
    destinations: List<io.github.eslamasabry.oc.feature.search.presentation.SearchIndexEntry>,
    words: Map<SearchPlace, Pair<String, String>>,
    actions: SearchActions,
) {
    if (destinations.isEmpty()) return
    item(key = "places-label") { KitSectionLabel(stringResource(Res.string.searchB1Places)) }
    items(destinations, key = { "d/" + it.id }) { entry ->
        KitRow(
            title = words.getValue(entry.place).first,
            leading = KitRowLeading.Glyph(searchPlaceIcon(entry.place)),
            trailing = KitRowTrailing.Chevron,
            highlight = state.query,
            onClick = { actions.openDestination(entry) },
        )
    }
}

/** The agent's own mark, bare; the neutral chat glyph when the agent has none bundled. */
@Composable
private fun agentLeading(mark: String, name: String?): KitRowLeading =
    if (mark.isBlank()) KitRowLeading.Glyph(KitIcons.chat)
    else KitRowLeading.Logo(mark, name ?: stringResource(Res.string.agentPersonaOtherName), agent = true, bare = true)

/** The home's state words: Working in the accent, Needs you in amber, Failed plain; nothing when idle. */
@Composable
private fun statusOf(status: SearchRowStatus): KitRowState? =
    when (status) {
        SearchRowStatus.Working -> KitRowState(stringResource(Res.string.chatsHomeRowWorking), KitRowStateTone.Running)
        SearchRowStatus.NeedsYou -> KitRowState(stringResource(Res.string.chatsHomeNeedsYou), KitRowStateTone.NeedsYou)
        SearchRowStatus.Failed -> KitRowState(stringResource(Res.string.chatsHomeFailed), KitRowStateTone.Failed)
        SearchRowStatus.None -> null
    }

@Composable
private fun shortcutLabel(place: GoToPlace): String =
    stringResource(
        when (place) {
            GoToPlace.NeedsYou -> Res.string.e7WorkspaceNeedsYou
            GoToPlace.Projects -> Res.string.e7ProjectProjectsTitle
            GoToPlace.Settings -> Res.string.librarySettingsTitle
            GoToPlace.AddServer -> Res.string.kitCapServerAnyEnable
            else -> Res.string.settingsV3Servers
        }
    )

private fun placeIcon(place: GoToPlace) =
    when (place) {
        GoToPlace.NeedsYou -> KitIcons.notificationImportant
        GoToPlace.Projects -> KitIcons.folders
        GoToPlace.Settings -> KitIcons.settingsGear
        GoToPlace.AddServer -> KitIcons.addCircle
        else -> KitIcons.server
    }
