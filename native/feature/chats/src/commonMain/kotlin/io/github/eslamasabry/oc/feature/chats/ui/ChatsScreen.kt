package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.BriefUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeClearFilters
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEarlier
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyCalmBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeIncompleteNamed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNeedsYou
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.chatsHomeShowAll
import io.github.eslamasabry.oc.ui.words.res.chatsHomeTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeToday
import io.github.eslamasabry.oc.ui.words.res.chatsReturnBriefMore
import io.github.eslamasabry.oc.ui.words.res.chatsReturnBriefUnknown
import io.github.eslamasabry.oc.ui.words.res.chats_all_filter
import io.github.eslamasabry.oc.ui.words.res.chats_all_none
import io.github.eslamasabry.oc.ui.words.res.chats_all_title
import io.github.eslamasabry.oc.ui.words.res.chats_back
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSettings
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.notifStatusNeedsYou
import io.github.eslamasabry.oc.ui.words.res.returnBriefContinue
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Conversations home (07 §5 B01). Arranges kit parts only (Konsist K1). One list by urgency in
 * Today / Earlier, reading width; no bottom bar (B-NAV-037): New conversation and Settings are
 * top-bar words, search sits under the bar.
 */
@Composable
public fun ChatsScreen(state: ChatsUi, actions: ChatsActions) {
    val newLabel = stringResource(Res.string.chatsHomeNewChat)
    val settingsLabel = stringResource(Res.string.e7LocaleUiSettings)
    val barActions = buildList {
        // Hidden while the empty state offers it (one way, not two).
        if (state.phase != ChatsPhase.Empty) add(KitLineAction(newLabel, actions::newConversation))
        if (state.settingsAvailable) add(KitLineAction(settingsLabel, actions::openSettings))
    }
    KitScreen(
        topBar = {
            KitTopBar(title = stringResource(Res.string.chatsHomeTitle), actions = barActions)
        },
        search =
            if (state.phase == ChatsPhase.Empty || state.phase == ChatsPhase.Opening) null
            else ({ SearchField(actions) }),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            state.brief?.let { brief -> item(key = "brief") { BriefBlock(brief, actions) } }
            if (state.phase == ChatsPhase.List || state.phase == ChatsPhase.NoMatch) {
                item(key = "filters") { Filters(state, actions) }
            }
            if (state.incomplete.isNotEmpty()) {
                item(key = "incomplete") {
                    KitNotice(
                        stringResource(
                            Res.string.chatsHomeIncompleteNamed,
                            state.incomplete.joinToString(", "),
                        )
                    )
                }
            }
            when (state.phase) {
                ChatsPhase.Opening -> item(key = "opening") { KitSkeletonRows() }
                ChatsPhase.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.chatsHomeEmptyTitle),
                            body = stringResource(Res.string.chatsHomeEmptyCalmBody),
                            primary = KitAction(newLabel, actions::newConversation),
                        )
                    }
                ChatsPhase.NoMatch ->
                    item(key = "no-match") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.chatsHomeNoMatchTitle),
                            body = stringResource(Res.string.chatsHomeNoMatchBody),
                            primary =
                                KitAction(
                                    stringResource(Res.string.chatsHomeClearFilters),
                                    actions::clearFilters,
                                ),
                        )
                    }
                ChatsPhase.List -> {
                    if (state.today.isNotEmpty()) {
                        item(key = "today") {
                            KitSectionLabel(stringResource(Res.string.chatsHomeToday))
                        }
                        items(state.today, key = { it.key }) { ConversationRow(it, actions) }
                    }
                    if (state.earlier.isNotEmpty()) {
                        item(key = "earlier") {
                            KitSectionLabel(stringResource(Res.string.chatsHomeEarlier))
                        }
                        items(state.earlier, key = { it.key }) { ConversationRow(it, actions) }
                    }
                }
            }
            if (state.phase != ChatsPhase.Opening) {
                // The visible way to the full list (All conversations, B12) besides Search.
                item(key = "show-all") {
                    KitButton(
                        label = stringResource(Res.string.chatsHomeShowAll),
                        onClick = actions::showAll,
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(actions: ChatsActions) {
    val field = rememberTextFieldState()
    // The first value is the empty field itself, not something the person typed.
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { actions.search(it) }
    }
    KitField(state = field, label = stringResource(Res.string.kitTopBarSearch))
}

@Composable
private fun Filters(state: ChatsUi, actions: ChatsActions) {
    KitChipWrap {
        if (state.needsYou > 0) {
            KitChip(
                label =
                    pluralStringResource(
                        Res.plurals.notifStatusNeedsYou,
                        state.needsYou,
                        state.needsYou,
                    ),
                kind = KitChipKind.Action,
                tone = KitChipTone.Attention,
                selected = state.filters.needsYou,
                onClick = actions::toggleNeedsYou,
            )
        }
        if (state.running > 0 || state.filters.running) {
            KitChip(
                label = stringResource(Res.string.chatsHomeRunning),
                kind = KitChipKind.Action,
                selected = state.filters.running,
                onClick = actions::toggleRunning,
            )
        }
        // Recent projects: the newest real folders; the chosen one filters the list.
        state.projects.forEach { project ->
            KitChip(
                label = project.name,
                kind = KitChipKind.Action,
                tone = if (project.selected) KitChipTone.Active else KitChipTone.Neutral,
                selected = project.selected,
                onClick = { actions.chooseProject(project.directory) },
            )
        }
    }
}

@Composable
private fun BriefBlock(brief: BriefUi, actions: ChatsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (brief.unknown) KitNotice(stringResource(Res.string.chatsReturnBriefUnknown))
        brief.rows.forEach { ConversationRow(it, actions) }
        if (brief.more > 0) {
            KitButton(
                label = stringResource(Res.string.chatsReturnBriefMore, brief.more),
                onClick = { actions.showBriefOverflow(brief) },
                role = KitButtonRole.Tertiary,
            )
        }
        if (brief.rows.isNotEmpty()) {
            KitButton(
                label = stringResource(Res.string.returnBriefContinue),
                onClick = { actions.dismissBrief(brief.acknowledgement) },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

@Composable
private fun ConversationRow(row: ConversationRowUi, actions: ChatsActions) {
    val title =
        when (val t = row.title) {
            is RowTitle.Text -> t.text
            RowTitle.New,
            RowTitle.Untitled -> stringResource(Res.string.workspaceNewSession)
        }
    val status =
        when (row.status) {
            RowStatus.NeedsYou -> stringResource(Res.string.chatsHomeNeedsYou)
            RowStatus.Running -> stringResource(Res.string.chatsHomeRunning)
            RowStatus.Failed -> stringResource(Res.string.chatsHomeFailed)
            RowStatus.Done -> stringResource(Res.string.chatsHomeDone)
            RowStatus.None -> null
        }
    // The row registers its grow key; the chat page grows out of it and shrinks back into it (P2).
    KitRow(
        title = title,
        supporting = row.project,
        trailing = status?.let { KitRowTrailing.Value(it, chevron = false) } ?: KitRowTrailing.None,
        growKey = row.ref,
        onClick = { actions.openConversation(row.ref) },
    )
}

/** P2 proof page for ChatRoute: the box a row grows into (plan 08 §5.4). */
@Composable
public fun ChatPreviewScreen(state: ChatPreviewUi, actions: ChatPreviewActions) {
    KitGrowPage(growKey = state.ref) {
        KitScreen(
            title = state.title,
            backLabel = stringResource(Res.string.chats_back),
            onBack = actions::back,
        ) {
            state.lines.forEach { KitText(it, KitTextRole.Body) }
        }
    }
}

@Composable
public fun ChatsAllScreen(state: ChatsAllUi, actions: ChatsAllActions) {
    KitScreen(
        title = stringResource(Res.string.chats_all_title),
        backLabel = stringResource(Res.string.chats_back),
        onBack = actions::back,
    ) {
        val filter = state.filter ?: stringResource(Res.string.chats_all_none)
        KitText(stringResource(Res.string.chats_all_filter, filter), KitTextRole.Supporting)
    }
}
