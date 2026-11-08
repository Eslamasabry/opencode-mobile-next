package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.TextFieldState
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.words.res.p12SearchConversationsWhat
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationResult
import io.github.eslamasabry.oc.feature.search.presentation.SearchPlace
import io.github.eslamasabry.oc.feature.search.presentation.SearchProject
import io.github.eslamasabry.oc.feature.search.presentation.SearchResults
import io.github.eslamasabry.oc.feature.search.presentation.SearchRowStatus
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentPersonaOtherName
import io.github.eslamasabry.oc.ui.words.res.chatsHomeFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNeedsYou
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUntitled
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.p12SearchFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromAgent
import io.github.eslamasabry.oc.ui.words.res.p12SearchFromYou
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideFailed
import io.github.eslamasabry.oc.ui.words.res.p12SearchInsideReading
import io.github.eslamasabry.oc.ui.words.res.p12SearchTryAgain
import io.github.eslamasabry.oc.ui.words.res.searchB1Conversations
import io.github.eslamasabry.oc.ui.words.res.searchB1Places
import io.github.eslamasabry.oc.ui.words.res.searchB1ProjectConversations
import io.github.eslamasabry.oc.ui.words.res.searchB1SeeAll
import io.github.eslamasabry.oc.ui.words.res.searchB1SeeAllSpoken
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.failed(state: SearchUi, actions: SearchActions) {
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
internal fun LazyListScope.conversationGroup(
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
        KitSectionLabel(
            stringResource(Res.string.searchB1Conversations),
            gapBefore = Space.S2.dp,
            action = if (folded) KitLineAction(label, seeAll, semanticsLabel = spoken) else null,
        )
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
internal fun LazyListScope.more(state: SearchUi, actions: SearchActions) {
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
internal fun LazyListScope.inside(state: SearchUi) {
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

internal fun LazyListScope.projectGroup(
    state: SearchUi,
    projects: List<SearchProject>,
    actions: SearchActions,
    first: Boolean,
) {
    if (projects.isEmpty()) return
    item(key = "projects-label") {
        KitSectionLabel(stringResource(Res.string.e7ProjectProjectsTitle), gapBefore = if (first) Space.S2.dp else null)
    }
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

internal fun LazyListScope.placeGroup(
    state: SearchUi,
    destinations: List<io.github.eslamasabry.oc.feature.search.presentation.SearchIndexEntry>,
    words: Map<SearchPlace, Pair<String, String>>,
    actions: SearchActions,
    first: Boolean,
) {
    if (destinations.isEmpty()) return
    item(key = "places-label") {
        KitSectionLabel(stringResource(Res.string.searchB1Places), gapBefore = if (first) Space.S2.dp else null)
    }
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
internal fun agentLeading(mark: String, name: String?): KitRowLeading =
    if (mark.isBlank()) KitRowLeading.Glyph(KitIcons.chat)
    else KitRowLeading.Logo(mark, name ?: stringResource(Res.string.agentPersonaOtherName), agent = true, bare = true)

/** The home's state words: Working in the accent, Needs you in amber, Failed plain; nothing when idle. */
@Composable
internal fun statusOf(status: SearchRowStatus): KitRowState? =
    when (status) {
        SearchRowStatus.Working -> KitRowState(stringResource(Res.string.chatsHomeRowWorking), KitRowStateTone.Running)
        SearchRowStatus.NeedsYou -> KitRowState(stringResource(Res.string.chatsHomeNeedsYou), KitRowStateTone.NeedsYou)
        SearchRowStatus.Failed -> KitRowState(stringResource(Res.string.chatsHomeFailed), KitRowStateTone.Failed)
        SearchRowStatus.None -> null
    }

/** Nothing matched anywhere: the person's words, and Clear search. */
internal fun LazyListScope.noMatch(query: String, field: TextFieldState) {
    item(key = "no-match") {
        KitSearchNoMatch(
            query = query,
            onClear = { field.edit { replace(0, length, "") } },
            what = stringResource(Res.string.p12SearchConversationsWhat),
        )
    }
}
