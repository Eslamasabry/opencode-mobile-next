package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchRecentConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchResults
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitShortcut
import io.github.eslamasabry.oc.ui.kit.nav.KitShortcutRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceNeedsYou
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUntitled
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyEnable
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecent
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentClear
import io.github.eslamasabry.oc.ui.words.res.p12SearchRecentRemove
import io.github.eslamasabry.oc.ui.words.res.searchB1Clear
import io.github.eslamasabry.oc.ui.words.res.searchB1RecentConversations
import io.github.eslamasabry.oc.ui.words.res.settingsV3Servers
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.noServer(actions: SearchActions) {
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
internal fun LazyListScope.idle(state: SearchUi, actions: SearchActions, field: TextFieldState) {
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
                action =
                    KitLineAction(
                        stringResource(Res.string.searchB1Clear),
                        actions::clearRecent,
                        semanticsLabel = stringResource(Res.string.p12SearchRecentClear),
                    ),
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
