package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchPlace
import io.github.eslamasabry.oc.feature.search.presentation.SearchResults
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.p12SearchField
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideHelp
import io.github.eslamasabry.oc.ui.words.res.p12SearchOneProject
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchSearching
import io.github.eslamasabry.oc.ui.words.res.protoWebSearchHelp
import io.github.eslamasabry.oc.ui.words.res.searchB1AboutOptions
import io.github.eslamasabry.oc.ui.words.res.searchB1ChooseServer
import io.github.eslamasabry.oc.ui.words.res.searchB1InsideMessages
import io.github.eslamasabry.oc.ui.words.res.searchB1OptionsTitle
import io.github.eslamasabry.oc.ui.words.res.searchB1Web
import org.jetbrains.compose.resources.stringResource

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
                    conversationGroup(state, conversations, allConversations, actions) { allConversations = true }
                    more(state, actions)
                    inside(state)
                    projectGroup(state, projects, actions)
                    placeGroup(state, destinations, destinationWords, actions)
                    val found = conversations.isNotEmpty() || projects.isNotEmpty() || destinations.isNotEmpty()
                    if (state.phase == SearchPhase.NoMatch && !found) noMatch(state.query, field)
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
