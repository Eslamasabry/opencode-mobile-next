package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchHitUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUntitled
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.p12SearchConversationsWhat
import io.github.eslamasabry.oc.ui.words.res.p12SearchFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchField
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromAgent
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromYou
import io.github.eslamasabry.oc.ui.words.res.p12SearchInside
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideHelp
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideReading
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideSection
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchOneProject
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecent
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentClear
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentEmpty
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentRemove
import io.github.eslamasabry.oc.ui.words.res.p12SearchSearching
import io.github.eslamasabry.oc.ui.words.res.p12SearchTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchTitles
import io.github.eslamasabry.oc.ui.words.res.p12SearchTryAgain
import org.jetbrains.compose.resources.stringResource

/**
 * Search conversations (map `search`, plan 07 B12 search-first): the field under the bar, the server as chips when
 * there are several, "Also search inside recent conversations" as a visible switch row, then recent searches (empty
 * field) or results: Titles first, then Inside conversations with the matching words. Kit parts only.
 */
@Composable
public fun SearchScreen(state: SearchUi, actions: SearchActions, field: TextFieldState = rememberTextFieldState(state.query)) {
    val gutter = LocalKitTokens.current.gutter
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.p12SearchTitle),
                subtitle = state.serverName,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        search = {
            KitSearchField(
                label = stringResource(Res.string.p12SearchField),
                onSearch = actions::search,
                state = field,
                onSubmit = actions::submit,
                resultCount =
                    if (state.phase == SearchPhase.Results || state.phase == SearchPhase.NoMatch) {
                        state.titleHits.size + state.contentHits.size
                    } else {
                        null
                    },
                partial = state.content == ContentPhase.Searching,
                enabled = state.phase != SearchPhase.NoServer,
                disabledReason = stringResource(Res.string.p12SearchNoServerBody),
            )
        },
        loading = state.phase == SearchPhase.Searching,
        loadingLabel = stringResource(Res.string.p12SearchSearching),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (state.servers.size > 1) {
                item(key = "servers") {
                    KitFilterChips {
                        for (server in state.servers) {
                            KitChip(
                                label = server.name,
                                kind = KitChipKind.Action,
                                selected = server.id == state.server,
                                onClick = { actions.chooseServer(server.id) },
                            )
                        }
                    }
                }
            }
            if (state.phase != SearchPhase.NoServer) {
                item(key = "inside") {
                    KitSwitchRow(
                        title = stringResource(Res.string.p12SearchInside),
                        checked = state.content != ContentPhase.Off,
                        supporting = stringResource(Res.string.p12SearchInsideHelp),
                        onCheckedChange = actions::searchInside,
                    )
                }
            }
            if (state.recentFailed) {
                item(key = "recent-failed") { KitNotice(stringResource(Res.string.p12SearchRecentFailed)) }
            }
            when (state.phase) {
                SearchPhase.NoServer ->
                    item(key = "no-server") {
                        KitStateView(
                            kind = KitStateKind.Missing,
                            title = stringResource(Res.string.p12SearchNoServerTitle),
                            body = stringResource(Res.string.p12SearchNoServerBody),
                            primary = KitAction(stringResource(Res.string.p12SearchOpenServers), actions::openServers),
                        )
                    }
                SearchPhase.Idle -> recent(state, actions, field)
                SearchPhase.Searching -> Unit
                SearchPhase.Failed ->
                    item(key = "failed") {
                        KitErrorNotice(
                            stringResource(Res.string.p12SearchFailed),
                            Modifier.padding(horizontal = gutter.dp),
                            failure = state.failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry = KitLineAction(stringResource(Res.string.p12SearchTryAgain), actions::retry),
                        )
                    }
                SearchPhase.NoMatch ->
                    item(key = "no-match") {
                        KitSearchNoMatch(
                            query = state.query,
                            onClear = { field.clearText() },
                            what = stringResource(Res.string.p12SearchConversationsWhat),
                        )
                    }
                SearchPhase.Results -> results(state, actions)
            }
            if (state.phase == SearchPhase.Results || state.phase == SearchPhase.NoMatch || state.phase == SearchPhase.Searching) {
                when (state.content) {
                    ContentPhase.Searching ->
                        item(key = "inside-reading") {
                            KitText(
                                stringResource(Res.string.p12SearchInsideReading),
                                role = KitTextRole.Supporting,
                                modifier = Modifier.padding(horizontal = gutter.dp),
                            )
                        }
                    ContentPhase.Failed ->
                        item(key = "inside-failed") { KitNotice(stringResource(Res.string.p12SearchInsideFailed)) }
                    ContentPhase.Off,
                    ContentPhase.Done -> Unit
                }
            }
        }
    }
}

private fun LazyListScope.recent(
    state: SearchUi,
    actions: SearchActions,
    field: TextFieldState,
) {
    item(key = "recent-label") { KitSectionLabel(stringResource(Res.string.p12SearchRecent)) }
    if (state.recent.isEmpty()) {
        item(key = "recent-empty") {
            KitText(
                stringResource(Res.string.p12SearchRecentEmpty),
                role = KitTextRole.Supporting,
                modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            )
        }
        return
    }
    item(key = "recent-chips") {
        KitChipWrap(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            for (query in state.recent) {
                KitChip(
                    label = query,
                    kind = KitChipKind.Removable,
                    removeLabel = stringResource(Res.string.p12SearchRecentRemove, query),
                    // Filling the field runs the search once typing settles, exactly as typed.
                    onClick = { field.setTextAndPlaceCursorAtEnd(query) },
                    onRemove = { actions.forgetRecent(query) },
                )
            }
        }
    }
    item(key = "recent-clear") {
        KitButton(
            label = stringResource(Res.string.p12SearchRecentClear),
            onClick = actions::clearRecent,
            role = KitButtonRole.Tertiary,
        )
    }
}

private fun LazyListScope.results(state: SearchUi, actions: SearchActions) {
    if (!state.allProjects) {
        item(key = "one-project") { KitNotice(stringResource(Res.string.p12SearchOneProject)) }
    }
    if (state.titleHits.isNotEmpty()) {
        item(key = "titles-label") { KitSectionLabel(stringResource(Res.string.p12SearchTitles)) }
        items(state.titleHits, key = { "t/" + it.key }) { HitRow(it, actions) }
    }
    if (state.loadMoreFailed) {
        item(key = "more-failed") {
            KitNotice(
                stringResource(Res.string.globalSessionsLoadMoreFailed),
                actions = listOf(KitLineAction(stringResource(Res.string.p12SearchTryAgain), actions::loadMore)),
            )
        }
    } else if (state.canLoadMore) {
        item(key = "more") {
            KitButton(
                label = stringResource(Res.string.globalSessionsLoadMore),
                onClick = actions::loadMore,
                role = KitButtonRole.Tertiary,
                working = state.loadingMore,
            )
        }
    }
    if (state.contentHits.isNotEmpty()) {
        item(key = "inside-label") { KitSectionLabel(stringResource(Res.string.p12SearchInsideSection)) }
        items(state.contentHits, key = { "c/" + it.key }) { HitRow(it, actions) }
    }
}

@Composable
private fun HitRow(hit: SearchHitUi, actions: SearchActions) {
    val title = hit.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.globalSessionsUntitled)
    val snippet =
        hit.snippet?.let {
            // The excerpt is the person's or the agent's own words: isolate it so it never reorders the line.
            val words = "⁨$it⁩"
            stringResource(if (hit.snippetFromYou) Res.string.p12SearchFromYou else Res.string.p12SearchFromAgent, words)
        }
    KitRow(
        title = title,
        supporting = snippet ?: hit.project,
        server = if (snippet != null) hit.project else null,
        supportingMaxLines = if (snippet != null) 2 else 1,
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.open(hit) },
    )
}
