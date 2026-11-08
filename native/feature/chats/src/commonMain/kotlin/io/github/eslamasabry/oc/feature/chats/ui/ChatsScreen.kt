package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.ui.design.layout.Layout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNothingRunningBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNothingRunning
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.state.KitAnswerScale
import io.github.eslamasabry.oc.ui.kit.state.KitStatusAnswer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import io.github.eslamasabry.oc.ui.kit.motion.kitItemMotion
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.effects.kitGlassSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionUi
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceConnected
import io.github.eslamasabry.oc.ui.words.res.phoneServerRowNotAnswering
import io.github.eslamasabry.oc.ui.words.res.serverSwitcherOpen
import io.github.eslamasabry.oc.ui.words.res.chatsHomeConnectComputer
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouSpan
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chats.presentation.BriefUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import io.github.eslamasabry.oc.feature.chats.presentation.RowAct
import io.github.eslamasabry.oc.feature.chats.presentation.RowActFailure
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.ui.words.res.chatUiShareSession
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDetails
import io.github.eslamasabry.oc.ui.words.res.sessionOpenRelated
import io.github.eslamasabry.oc.ui.words.res.sessionCopyHandoff
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KIT_FAB_CLEARANCE
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRunNamed
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAllowNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysConfirm
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysInProject
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysScope
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.kitRequestReject
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitFeedItem
import io.github.eslamasabry.oc.ui.kit.row.KitSwipeRow
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.words.res.chatsRowActions
import io.github.eslamasabry.oc.ui.words.res.chatsRowArchived
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveFailed
import io.github.eslamasabry.oc.ui.words.res.p12Rename
import io.github.eslamasabry.oc.ui.words.res.p12RenameFailed
import io.github.eslamasabry.oc.ui.words.res.sessionMenuArchive
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.style.KitRunningFeedLine
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSlip
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.statePin
import io.github.eslamasabry.oc.ui.words.res.stateUnpin
import io.github.eslamasabry.oc.ui.words.res.statePinFailed
import io.github.eslamasabry.oc.ui.words.res.p12DeleteFailed
import io.github.eslamasabry.oc.ui.words.res.p12Deleted
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDelete
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.chatHomeAllow
import io.github.eslamasabry.oc.ui.words.res.chatHomeAnswerFailed
import io.github.eslamasabry.oc.ui.words.res.chatHomeMinutes
import io.github.eslamasabry.oc.ui.words.res.chatHomeReject
import io.github.eslamasabry.oc.ui.words.res.chatHomeRunning
import io.github.eslamasabry.oc.ui.words.res.chatsHomeClearFilters
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEarlier
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyCalmBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeIncompleteNamed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNeedsYou
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewShort
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunningCount
import io.github.eslamasabry.oc.ui.words.res.chatsHomeShowAll
import io.github.eslamasabry.oc.ui.words.res.chatsHomeTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeToday
import io.github.eslamasabry.oc.ui.words.res.chatsReturnBriefMore
import io.github.eslamasabry.oc.ui.words.res.chatsReturnBriefUnknown
import io.github.eslamasabry.oc.ui.words.res.chats_back
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceConnecting
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDaysAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceHoursAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceJustNow
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.kitRequestOpen
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.notifStatusMore
import io.github.eslamasabry.oc.ui.words.res.notifStatusNeedsYou
import io.github.eslamasabry.oc.ui.words.res.p12ProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchTitle
import io.github.eslamasabry.oc.ui.words.res.returnBriefSeen
import io.github.eslamasabry.oc.ui.words.res.teamProjectDigest
import io.github.eslamasabry.oc.ui.words.res.stateAttentionTitle
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.eslamasabry.oc.ui.words.res.agentPersonaOtherName
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice

/**
 * The Conversations home (07 §5 B01). Arranges kit parts only (Konsist K1). One list by urgency in
 * Today / Earlier, reading width; no bottom bar (B-NAV-037): New conversation and Servers are
 * top-bar words, search sits under the bar.
 */
@Composable
public fun ChatsScreen(
    state: ChatsUi,
    actions: ChatsActions,
    appStatus: KitStatus? = null,
    replyOffer: (@Composable () -> Unit)? = null,
    root: Boolean = false,
    /**
     * s23-factsbook: sections another feature places under the home's notes (FactsBook's Memory section while its
     * developer switch is on; F: chats_home_screen.dart:682, 856). Each draws nothing when it has nothing to say.
     */
    sections: List<@Composable () -> Unit> = emptyList(),
    /**
     * G1: the app's own bar (server pill, Search, Settings) above the page. Flutter's home draws none: "the server pill
     * and search belong to the shell's own bar" (lib/ui/screens/chats/chats_home_screen.dart:32), so the page alone,
     * as its goldens show it, is this screen with [shellBar] false. The page's ground is the same either way.
     */
    shellBar: Boolean = true,
) {
    // E05-S21: on the bare list (the whole back stack) the first Back says "Press back again to exit" in the one
    // status line; called first so sheets, dialogs and a filled search registered below close before it.
    val backExit = homeBackExitStatus(root)
    // Flutter's home bar (KitTopBar.shell): the server pill opens the switcher; Search and Settings sit on their own
    // round faces. No overflow: Servers is the pill, Inbox is the needs-you line, the rest are rows at the list's end.
    val settingsLabel = stringResource(Res.string.librarySettingsTitle)
    val updateLabel = stringResource(Res.string.appUpdateUpdate)
    // G3: the empty page carries New conversation as its own full-width button at the bottom (Flutter
    // KitStateView.calm), so the floating + only rides over a list.
    val listed = state.phase == ChatsPhase.List
    val showFab = listed && state.home.opening == null
    // Under Glass the floating + is liquid glass over the list (the kit records it only then).
    val listGlass = rememberKitFrostState()
    val barActions = buildList {
        add(KitLineAction(stringResource(Res.string.kitTopBarSearch), actions::openSearch, icon = KitIcons.search))
        if (state.settingsAvailable) add(KitLineAction(settingsLabel, actions::openSettings, icon = KitIcons.settingsGear))
        if (state.updateReady) add(KitLineAction(updateLabel, actions::openUpdate, icon = KitIcons.systemDownload))
    }
    val briefKeys = state.brief?.rows.orEmpty().mapTo(hashSetOf()) { it.key }
    val needsLine =
        if (state.needsYou == 0) null
        else pluralStringResource(Res.plurals.kitNeedsYouSpan, state.needsYou, Numbers.format(state.needsYou)).trimEnd().removeSuffix("·").trimEnd()
    KitScreen(
        // The phone's top is the page's own (Flutter conv-header): title row with plain Search and Settings icons, then
        // the connection pill beside the filters. No separate bar.
        topBar = null,
        // E20-S27: framed without the bar too, so the page lies on one continuous ground behind heading and list.
        page = true,
        header = {
            // The page names itself under the bar (Flutter KitPageHeading); "2 need you" opens the Inbox. Nothing to
            // filter on a list that holds nothing yet, so the chips wait for the first conversation.
            Column {
                // F: lib/ui/screens/chats/chats_home_screen.dart:380-386: room under the bar before the title, and
                // as much under it when no "need you" line (whose own target gives that air) follows.
                KitPageHeading(
                    stringResource(Res.string.chatsHomeTitle),
                    Modifier.padding(top = Space.S2.dp, bottom = if (needsLine == null) Space.S2.dp else 0.dp),
                    line = needsLine,
                    onLine = actions::showInbox,
                    actions = if (!shellBar) null else ({
                        barActions.forEach { KitIconButton(it.icon!!, it.label, it.onClick) }
                    }),
                )
                val showFilters = state.phase == ChatsPhase.List || state.phase == ChatsPhase.NoMatch
                val pill: (@Composable () -> Unit)? = if (!shellBar) null else state.connection?.let { connection -> { ConnectionStatusPill(connection, actions) } }
                if (pill != null) HeaderRow(pill, showFilters) { Filters(state, actions, bleed = true) }
                else if (showFilters) Filters(state, actions)
            }
        },
        status = backExit ?: appStatus,
        // F: lib/ui/screens/chats/chats_home_screen.dart:536: pull the list down to read it again; not while the server
        // is still opening. Refresh wraps the scrolling header too (the title returns before a pull starts). The kit
        // part also answers F5 / Ctrl+R and a TalkBack "Refresh" action.
        refresh =
            KitScreenRefresh(
                refreshing = state.refreshing,
                onRefresh = actions::refreshList,
                enabled = state.home.opening == null && state.phase != ChatsPhase.Opening,
            ),
        width = KitScreenWidth.Reading,
    ) {
      Box(Modifier.fillMaxSize()) {
        // F: lib/ui/screens/chats/chats_home_screen.dart:536: pull the list down to read it again (KitRefresh); not
        // while the server is still opening. The kit part also answers F5 / Ctrl+R and a TalkBack "Refresh" action.
        // G2: rows follow each other with only their hairline between them (Flutter KitSliverRowGroup); a 4 dp gap
        // between list items put every row 4 px lower than the one before it.
        LazyColumn(
            modifier = Modifier.kitGlassSource(listGlass),
            // The calm empty page fills the room and keeps its own end space, so it never scrolls by that much.
            contentPadding =
                PaddingValues(
                    bottom =
                        if ((state.phase == ChatsPhase.Empty || state.phase == ChatsPhase.NoMatch) && state.home.opening == null) 0.dp
                        else Space.S4.dp + if (showFab) KIT_FAB_CLEARANCE.dp else 0.dp
                ),
        ) {
            state.home.opening?.let { opening ->
                item(key = "opening-status") { OpeningStatus(opening, actions) }
                items(opening.rows, key = { "cached:${it.key}" }) { row -> OpeningRow(row) }
                // E20-S09: placeholder rows stand for titles that are on their way; under a failure nothing is.
                if (opening.rows.isEmpty() && opening.phase != ChatsOpeningPhase.Failed)
                    item(key = "opening") { KitSkeletonRows() }
                return@LazyColumn
            }
            replyOffer?.let { offer -> item(key = "reply-notification-offer") { offer() } }
            state.brief?.let { brief -> item(key = "brief") { Box(kitItemMotion()) { BriefBlock(brief, actions, state) } } }
            if (state.archiveFailure != null)
                item(key = "archive-failure") {
                    // E04-S05: the row is already back in the list, so closing the notice changes nothing real.
                    KitNotice(
                        message =
                            stringResource(
                                when (state.archiveFailureAct) {
                                    RowActFailure.Archive -> Res.string.p12ArchiveFailed
                                    RowActFailure.Delete -> Res.string.p12DeleteFailed
                                    RowActFailure.Pin -> Res.string.statePinFailed
                                }
                            ),
                        tone = StatusTone.Failure,
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismissArchiveFailure,
                            ),
                    )
                }
            if (state.renameFailure != null)
                item(key = "rename-failure") {
                    io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                        message = stringResource(Res.string.p12RenameFailed),
                        failure = state.renameFailure,
                    )
                }
            if (state.answerFailure != null)
                item(key = "answer-failure") {
                    io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                        message = stringResource(Res.string.chatHomeAnswerFailed),
                        failure = state.answerFailure,
                    )
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
            if (state.phase != ChatsPhase.Opening)
                sections.forEachIndexed { index, section -> item(key = "home-section-$index") { section() } }
            when (state.phase) {
                ChatsPhase.Opening -> item(key = "opening") { KitSkeletonRows() }
                ChatsPhase.Empty ->
                    item(key = "empty") {
                        // A person with no computer saved may have come for one: the quiet second way in, only then.
                        CalmPage(
                            title = stringResource(Res.string.chatsHomeEmptyTitle),
                            body = stringResource(Res.string.chatsHomeEmptyCalmBody),
                            primary = KitAction(stringResource(Res.string.chatsHomeNewChat), actions::newConversation, icon = KitIcons.add),
                            tertiary =
                                if (state.hasComputer) null
                                else KitAction(stringResource(Res.string.chatsHomeConnectComputer), actions::connectComputer, icon = KitIcons.computer),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                ChatsPhase.NoMatch ->
                    item(key = "no-match") { CalmNoMatchPage(state, actions, Modifier.fillParentMaxSize()) }
                ChatsPhase.List -> {
                    val today = state.today.filterNot { it.key in briefKeys }
                    val earlier = state.earlier.filterNot { it.key in briefKeys }
                    // Lists breathe (motion lead 2026-11-07): a row that arrives fades in, one that leaves fades out
                    // where it was, and the rest glide to their new places (a chat jumping to the top, Mark all seen).
                    if (today.isNotEmpty()) {
                        item(key = "today") {
                            Box(kitItemMotion()) { KitSectionLabel(stringResource(Res.string.chatsHomeToday)) }
                        }
                        items(today, key = { it.key }) { row ->
                            Column(kitItemMotion()) {
                                ConversationRow(row, actions, state)
                                if (row != today.last()) RowDivider(state)
                            }
                        }
                    }
                    if (earlier.isNotEmpty()) {
                        item(key = "earlier") {
                            // F: lib/ui/kit/kit_section_label.dart:28-31: a section after another one keeps the
                            // section gap above its label. In a lazy item the label cannot see what is above it
                            // (it is first in its own box), so the gap is asked for here.
                            Box(kitItemMotion()) {
                                // Alone, "Earlier" divides nothing: no label (Flutter conv-header).
                                if (today.isNotEmpty()) KitSectionLabel(
                                    stringResource(Res.string.chatsHomeEarlier),
                                    gapBefore = if (today.isNotEmpty()) LocalKitTokens.current.sectionGap.dp else 0.dp,
                                )
                            }
                        }
                        items(earlier, key = { it.key }) {
                            Column(kitItemMotion()) {
                                ConversationRow(it, actions, state)
                                if (it != earlier.last()) RowDivider(state)
                            }
                        }
                    }
                }
            }
        }
        // The Undo bar sits at the bottom edge; the New button rides above it while it shows.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            // P-107: Flutter's round 56 dp + on the gutter, 12 dp above the bottom; the words are heard, not drawn, so
            // it no longer covers a row's time. It fades out while the server cannot start one yet.
            if (listed) {
                KitFab(
                    label = stringResource(Res.string.chatsHomeNewChat),
                    icon = KitIcons.add,
                    onClick = actions::newConversation,
                    modifier = Modifier.padding(end = LocalKitTokens.current.gutter.dp, bottom = Space.S3.dp),
                    visible = showFab,
                    backdrop = listGlass,
                )
            }
            val undo = state.archiveUndo
            val message =
                undo?.let {
                    stringResource(
                        if (it.deleted) Res.string.p12Deleted else Res.string.chatsRowArchived,
                        rowTitleText(it.title),
                    )
                }
            KitUndoBar(
                offer = if (undo != null && message != null) KitUndoOffer("archive-${undo.id}", message) else null,
                onUndo = actions::undoArchive,
                onTimeout = actions::commitArchive,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
      }
        ChatsHomeSheets(state, actions)
        ConversationRenameDialog(state.renaming, actions::finishRename)
        AlwaysAllowConfirm(state.confirmAlways, actions)
    }
}

/**
 * G3: a filtered list that holds nothing, as Flutter's calm states (lib/ui/screens/chats/chats_home_screen.dart:686-716):
 * Running on says "Nothing running" with New conversation and "Show all conversations"; any other filter says nothing
 * matches, with Clear filters.
 */
@Composable
private fun CalmNoMatchPage(state: ChatsUi, actions: ChatsActions, modifier: Modifier) {
    if (state.filters.running && !state.filters.needsYou)
        CalmPage(
            title = stringResource(Res.string.chatsHomeNothingRunning),
            body = stringResource(Res.string.chatsHomeNothingRunningBody),
            primary = KitAction(stringResource(Res.string.chatsHomeNewChat), actions::newConversation, icon = KitIcons.add),
            tertiary = KitAction(stringResource(Res.string.chatsHomeShowAll), actions::toggleRunning),
            modifier = modifier,
        )
    else
        CalmPage(
            title = stringResource(Res.string.chatsHomeNoMatchTitle),
            body = stringResource(Res.string.chatsHomeNoMatchBody),
            primary = KitAction(stringResource(Res.string.chatsHomeClearFilters), actions::clearFilters),
            tertiary = null,
            modifier = modifier,
        )
}

/**
 * G3: Flutter's calm page (lib/ui/kit/kit_state_view.dart `_buildCalm`, page size): one calm line and its body a little
 * above the middle of the room, the full-width [primary] at the bottom where the floating + would be, and the quiet
 * [tertiary] under it. Both rise in once (KitEntrance; still under reduced motion). Arranged from kit parts; the kit's
 * KitStateView has no calm page form yet (proposed to the kit owner).
 */
@Composable
private fun CalmPage(title: String, body: String, primary: KitAction, tertiary: KitAction?, modifier: Modifier) {
    val gutter = LocalKitTokens.current.gutter.dp
    BoxWithConstraints(modifier) {
        // F: kit_state_view.dart:438 `_calmLift = .1` of the room, kept under the words.
        val lift = maxHeight * CALM_LIFT
        // F: kit_state_view.dart:542,630-640: the state's room is at most the state width (KitLayout.stateMaxWidth) from
        // the column's start, railed by the gutter; on a phone that is the whole width less the gutters.
        Column(
            Modifier.fillMaxHeight()
                .widthIn(max = Layout.STATE_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(start = gutter, end = gutter, bottom = Space.S4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier)
            KitEntrance(Modifier.fillMaxWidth().padding(top = Space.S5.dp, bottom = Space.S5.dp + lift)) {
                // The answer caps itself at the conversation width; centre that cap in the state's room.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    KitStatusAnswer(answer = title, line = body, scale = KitAnswerScale.Calm)
                }
            }
            KitEntrance(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KitButton(primary, fullWidth = true)
                    tertiary?.let { KitButton(it, role = KitButtonRole.Tertiary, expand = false) }
                }
            }
        }
    }
}

/** How far above the middle of its room a calm page sets its words, as a share of the room's height. */
private const val CALM_LIFT = .1f // F: lib/ui/kit/kit_state_view.dart:438

/** The pill fixed at the start, a short divider, then the filters (which alone scroll) beside it. */
@Composable
private fun HeaderRow(pill: @Composable () -> Unit, showFilters: Boolean, filters: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = LocalKitTokens.current.gutter.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.widthIn(max = 220.dp)) { pill() }
        if (showFilters) {
            Spacer(Modifier.width(Space.S2.dp))
            Box(Modifier.height(24.dp)) { io.github.eslamasabry.oc.ui.kit.foundation.KitDivider(vertical = true) }
            Spacer(Modifier.width(Space.S2.dp))
            Box(Modifier.weight(1f)) { filters() }
        }
    }
}

@Composable
private fun ConnectionStatusPill(connection: HomeConnectionUi, actions: ChatsActions) {
    val state = connection.state
    val status = stringResource(
        when (state) {
            HomeConnectionState.Connected -> Res.string.e7WorkspaceConnected
            HomeConnectionState.Connecting -> Res.string.e7WorkspaceConnecting
            HomeConnectionState.NotAnswering -> Res.string.phoneServerRowNotAnswering
        }
    )
    val switchLabel = stringResource(Res.string.serverSwitcherOpen)
    KitStatusPill(
        label = connection.server,
        tone = when (state) {
            HomeConnectionState.Connected -> KitConnectionTone.Ok
            HomeConnectionState.Connecting -> KitConnectionTone.Working
            HomeConnectionState.NotAnswering -> KitConnectionTone.Problem
        },
        // The dot says connected; a word only when it is not.
        word = status.takeUnless { state == HomeConnectionState.Connected },
        spoken = "${connection.server}, $status, $switchLabel",
        onClickLabel = switchLabel,
        // F: lib/ui/screens/home_screen.dart:434-470: the one-tap switcher, not the Servers page.
        onClick = actions::openServerSwitcher,
    )
}

@Composable
private fun Filters(state: ChatsUi, actions: ChatsActions, bleed: Boolean = false) {
    KitFilterChips(bleed = bleed) {
        HomeFilterChips(state, actions)
        val runningNow = (state.today + state.earlier).count { it.status == RowStatus.Running }
        KitChip(
            label = if (runningNow > 0) stringResource(Res.string.chatsHomeRunningCount, runningNow) else stringResource(Res.string.chatsHomeRunning),
            kind = KitChipKind.Action,
            selected = state.filters.running, onClick = actions::toggleRunning,
        )
    }
}

@Composable
private fun BriefBlock(brief: BriefUi, actions: ChatsActions, state: ChatsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        // On the gutter like the rows under it (its glyph sat on the screen's edge).
        if (brief.unknown)
            KitNotice(
                stringResource(Res.string.chatsReturnBriefUnknown),
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            )
        // Headed and ruled like Today and Earlier, so the top group reads as part of the same list.
        if (brief.rows.isNotEmpty()) KitSectionLabel(stringResource(Res.string.teamProjectDigest))
        brief.rows.forEach { row ->
            ConversationRow(row, actions, state)
            if (row != brief.rows.last()) RowDivider(state)
        }
        // One line on the gutter: the rest of what finished at the start, marking it all seen at the end.
        if (brief.more > 0 || brief.rows.isNotEmpty())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (brief.more > 0)
                    KitButton(
                        label = stringResource(Res.string.chatsReturnBriefMore, brief.more),
                        onClick = { actions.showBriefOverflow(brief) },
                        role = KitButtonRole.Tertiary,
                    )
                else Spacer(Modifier)
                if (brief.rows.isNotEmpty())
                    KitButton(
                        label = stringResource(Res.string.returnBriefSeen),
                        onClick = { actions.dismissBrief(brief.acknowledgement) },
                        role = KitButtonRole.Tertiary,
                    )
            }
    }
}

/**
 * F: lib/ui/screens/chats/chats_host.dart:262-270 (`permissionAlwaysStep`): Always allow asks first, naming what and
 * where, with the way back ("You can take this back in Settings").
 */
@Composable
private fun AlwaysAllowConfirm(row: ConversationRowUi?, actions: ChatsActions) {
    // Masked as in the chat's own confirm: a command can carry a token.
    val mask = io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask()
    val command = row?.permission?.command?.let { KitBidi.ltr(mask(it)) }
    KitConfirm(
        spec =
            command?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.chatRequestAlwaysConfirm, it),
                    body = stringResource(Res.string.chatRequestAlwaysScope, it, stringResource(Res.string.chatRequestAlwaysInProject)),
                    confirmLabel = stringResource(Res.string.chatUiAlwaysAllow),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                )
            },
        onConfirm = actions::confirmAlwaysAllow,
        onCancel = actions::cancelAlwaysAllow,
    )
}

@Composable
private fun rowTitleText(t: RowTitle): String = rowTitleWords(t)

/**
 * The line between two conversation rows (Today, Earlier and the return brief), on the 16 dp page edge where the row's
 * words start; past the agent tile in the Signal look, whose rows still lead with one.
 *
 * B-173 (twin of Flutter #129): Flutter's home groups pass `prominentDividers: true`
 * (lib/ui/screens/chats/chats_home_screen.dart:824), so KitSliverRowGroup draws `KitDivider.prominent(inset: inset,
 * endInset: reach)` (lib/ui/kit/kit_sliver_row_group.dart:148-150): 1 dp (`KitLayout.prominentRule`) in the theme's
 * tertiary ink at 30 % (`roles.text3`, alpha 0.3) unless the style draws its own rule (Brutal's ink keeps it; Soft still
 * draws none). That line reads on OLED dark screens, where the hairline does not.
 */
@Composable
private fun RowDivider(state: ChatsUi) {
    val signal = LocalKitAppearance.current.style == KitSurfaceStyle.Signal
    io.github.eslamasabry.oc.ui.kit.foundation.KitDivider(
        inset =
            if (signal && state.showAgents) io.github.eslamasabry.oc.ui.kit.foundation.KitDividerInset.Text
            else io.github.eslamasabry.oc.ui.kit.foundation.KitDividerInset.Gutter,
        prominent = true,
    )
}

@Composable
private fun ConversationRow(row: ConversationRowUi, actions: ChatsActions, state: ChatsUi? = null) {
    val title = rowTitleText(row.title)
    val canArchive = state != null && row.key in state.archivable
    val status =
        when (row.status) {
            RowStatus.NeedsYou -> stringResource(Res.string.chatsHomeNeedsYou)
            RowStatus.Running -> stringResource(Res.string.chatsHomeRowWorking)
            RowStatus.Failed -> stringResource(Res.string.chatsHomeFailed)
            RowStatus.Done -> stringResource(Res.string.chatsHomeDone)
            RowStatus.None -> null
        }
    val signal = LocalKitAppearance.current.style == KitSurfaceStyle.Signal
    val tone =
        when (row.status) {
            RowStatus.NeedsYou -> KitRowStateTone.NeedsYou
            RowStatus.Running -> KitRowStateTone.Running
            RowStatus.Failed -> KitRowStateTone.Failed
            else -> KitRowStateTone.Quiet
        }
    // The row registers its grow key; the chat page grows out of it and shrinks back into it (P2). Looks pass: the
    // state leads the supporting line in its colour, the time sits in its own column.
    val archiveWord = stringResource(Res.string.sessionMenuArchive)
    var menuOpen by remember(row.key) { mutableStateOf(false) }
    val needsAttention = row.status == RowStatus.NeedsYou || row.status == RowStatus.Running
    val agent =
        if (state?.showAgents == true)
            KitFeedAgent(row.agentMark, row.agentName ?: stringResource(Res.string.agentPersonaOtherName))
        else null
    val time =
        row.updatedAtMs?.takeIf { state != null && state.nowMs > 0 }?.let { kitRelativeAge(it, state!!.nowMs) }
            ?: row.ago?.takeUnless { signal && row.running != null }?.let { agoText(it) }
    val liveLabel = liveRunWords()
    val feedItem: @Composable () -> Unit = {
        // F: lib/ui/screens/chats/chats_home_screen.dart:900-933: every look draws Flutter's KitFeedItem, the title and
        // one meta line ("Needs you · alpha · 2 min ago"); the agent leads that line only when the list mixes agents.
        // A live run says how long it has been working in place of the plain state word.
        KitFeedItem(
            title = title,
            project = row.project,
            agent = agent,
            server = row.server,
            live = needsAttention,
            onLongClick = if (row.canOpen || canArchive) ({ menuOpen = true }) else null,
            onLongClickLabel = stringResource(Res.string.chatsRowActions),
            state =
                KitRowState(
                    if (row.status == RowStatus.Running && !signal && row.running != null)
                        liveLabel(row.running.sinceMs?.let { since -> (state?.nowMs?.takeIf { it > 0 } ?: row.observedAtMs) - since })
                    else status?.takeUnless { signal && row.running != null },
                    tone,
                ),
            time = time,
            done = row.status == RowStatus.Done,
            growKey = row.ref,
            onClick = if (row.canOpen) ({ actions.openConversation(row.ref) }) else null,
            pinned = state != null && row.key in state.pinned,
        )
    }
    KitMenu(
        expanded = menuOpen,
        // N-30: the same acts, in the same order, as a row on All conversations (a tap already opens it).
        items = buildList {
            if (state != null && row.key in state.pinnable) {
                val pinned = row.key in state.pinned
                add(KitMenuItem(stringResource(if (pinned) Res.string.stateUnpin else Res.string.statePin), { menuOpen = false; actions.togglePin(row) }, icon = if (pinned) KitIcons.unpin else KitIcons.pin))
            }
            if (state != null && row.key in state.renamable) add(KitMenuItem(stringResource(Res.string.p12Rename), { menuOpen = false; actions.rename(row) }, icon = KitIcons.edit))
            if (row.canOpen) {
                // Share, Details, Copy handoff and Open related open the conversation's own page and run the same
                // act its menu runs (one path to the server for each).
                add(KitMenuItem(stringResource(Res.string.chatUiShareSession), { menuOpen = false; actions.rowAct(row, RowAct.Share) }, icon = KitIcons.link))
                add(KitMenuItem(stringResource(Res.string.sessionMenuDetails), { menuOpen = false; actions.rowAct(row, RowAct.Details) }, icon = KitIcons.info))
                add(KitMenuItem(stringResource(Res.string.sessionOpenRelated), { menuOpen = false; actions.rowAct(row, RowAct.OpenRelated) }, icon = KitIcons.branch))
                add(KitMenuItem(stringResource(Res.string.sessionCopyHandoff), { menuOpen = false; actions.rowAct(row, RowAct.CopyHandoff) }, icon = KitIcons.terminal))
            }
            if (canArchive) add(KitMenuItem(archiveWord, { menuOpen = false; actions.archive(row) }, icon = KitIcons.archive))
            if (state != null && row.key in state.deletable) add(KitMenuItem(stringResource(Res.string.sessionMenuDelete), { menuOpen = false; actions.delete(row) }, icon = KitIcons.delete, destructive = true))
        },
        onDismissRequest = { menuOpen = false },
        title = title,
    ) {
        // Swipe toward the end of the line archives (mirrors in right-to-left); the menu and the accessibility action
        // are the same act, so the gesture is never the only way.
        if (canArchive) KitSwipeRow(archiveWord, { actions.archive(row) }) { feedItem() } else feedItem()
    }

    // F: lib/ui/screens/chats/chats_host.dart:240-270: the row answers what the chat's card does: Allow once, Reject,
    // and Always allow (after its confirm) where the server keeps the grant.
    val always = state != null && row.key in state.alwaysAllowable
    if (!signal && row.permission != null) {
        val permission = row.permission
        val answering = permission.requestId in state?.answering.orEmpty()
        KitPermissionCard(
            title = stringResource(Res.string.chatRequestAskRunNamed, permission.command),
            summary = null,
            subject = permission.command,
            allowOnceLabel = stringResource(Res.string.kitRequestAllowOnce),
            alwaysLabel = if (always) stringResource(Res.string.chatRequestAlwaysAllowNamed, KitBidi.ltr(permission.command)) else null,
            denyLabel = stringResource(Res.string.kitRequestReject),
            secondary = true,
            onAllowOnce = { actions.answerPermission(row, true) },
            onAlways = if (always) ({ actions.askAlwaysAllow(row) }) else null,
            onDeny = { actions.answerPermission(row, false) },
            enabled = row.canOpen && !answering,
            disabledReason = if (answering) stringResource(Res.string.serversWorking) else if (!row.canOpen) stringResource(Res.string.e7WorkspaceConnecting) else null,
            modifier = Modifier.padding(start = Space.S4.dp, end = Space.S4.dp, bottom = Space.S3.dp),
        )
    }
    if (signal) {
        row.running?.let { running ->
            val minuteTemplate = stringResource(Res.string.chatHomeMinutes)
            KitRunningFeedLine(
                running.sinceMs,
                row.observedAtMs,
                running.command,
                stringResource(Res.string.chatHomeRunning),
                { minuteTemplate.replace("%1\$s", it) },
            )
        }
        row.permission?.let { permission ->
            KitSignalSlip {
                KitText("$ " + permission.command, maxLines = 2)
                KitChipWrap {
                    KitButton(
                        stringResource(Res.string.chatHomeAllow),
                        { actions.answerPermission(row, true) },
                        working = permission.requestId in state?.answering.orEmpty(),
                    )
                    KitButton(
                        stringResource(Res.string.chatHomeReject),
                        { actions.answerPermission(row, false) },
                        role = KitButtonRole.Secondary,
                        working = permission.requestId in state?.answering.orEmpty(),
                    )
                    if (always)
                        KitButton(
                            stringResource(Res.string.chatUiAlwaysAllow),
                            { actions.askAlwaysAllow(row) },
                            role = KitButtonRole.Tertiary,
                            working = permission.requestId in state?.answering.orEmpty(),
                        )
                }
            }
        }
    }
}

@Composable
internal fun agoText(age: RelativeAge): String =
    when (age) {
        RelativeAge.JustNow -> stringResource(Res.string.e7WorkspaceJustNow)
        is RelativeAge.Minutes ->
            stringResource(Res.string.e7WorkspaceMinutesAgo, Numbers.format(age.count))
        is RelativeAge.Hours ->
            stringResource(Res.string.e7WorkspaceHoursAgo, Numbers.format(age.count))
        is RelativeAge.Days ->
            stringResource(Res.string.e7WorkspaceDaysAgo, Numbers.format(age.count))
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
