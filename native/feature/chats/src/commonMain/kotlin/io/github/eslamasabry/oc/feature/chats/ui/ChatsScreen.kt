package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitReportPageScroll
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.row.KitFootBar
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitFloatingBar
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.kitRequestAnswer
import io.github.eslamasabry.oc.ui.words.res.kitRequestMoreAnswers
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherAnswer
import io.github.eslamasabry.oc.ui.words.res.chatUiInputRequested
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysConfirm
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysInProject
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysScope
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitRecentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.feature.chats.presentation.HomePreviewRules
import androidx.compose.runtime.key
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRightNow
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRecent
import io.github.eslamasabry.oc.ui.words.res.chatsHomeAsksOther
import io.github.eslamasabry.oc.ui.words.res.chatsHomeAsksToEdit
import io.github.eslamasabry.oc.ui.words.res.chatsHomeAsksToRead
import io.github.eslamasabry.oc.ui.words.res.chatsHomeAsksToRun
import io.github.eslamasabry.oc.ui.words.res.chats_all_title
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
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.statePin
import io.github.eslamasabry.oc.ui.words.res.stateUnpin
import io.github.eslamasabry.oc.ui.words.res.statePinFailed
import io.github.eslamasabry.oc.ui.words.res.p12DeleteFailed
import io.github.eslamasabry.oc.ui.words.res.p12Deleted
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDelete
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.chatHomeAnswerFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeClearFilters
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.chatsHomeStopped
import io.github.eslamasabry.oc.ui.words.res.dockStop
import io.github.eslamasabry.oc.ui.words.res.chatsHomeInterrupted
import io.github.eslamasabry.oc.ui.words.res.agentsStateCantReopen
import io.github.eslamasabry.oc.feature.chats.presentation.HomeRowStatusRules
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyCalmBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNotReadBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNotReadTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeFailed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeIncompleteNamed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeUnreachableShort
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.agentsRestartLine
import io.github.eslamasabry.oc.ui.words.res.agentsResumeAction
import io.github.eslamasabry.oc.ui.words.res.agentsSignInAction
import io.github.eslamasabry.oc.ui.words.res.agentsSignedOutLine
import io.github.eslamasabry.oc.ui.words.res.agentsStoppedLine
import io.github.eslamasabry.oc.ui.words.res.agentsLimitUnknown
import io.github.eslamasabry.oc.ui.words.res.gateSheetOpenAgent
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysOn
import io.github.eslamasabry.oc.ui.words.res.chatRequestAnswerAllowed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAnswerRejected
import io.github.eslamasabry.oc.ui.words.res.signalRowRunningCommand
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeFootBarRules
import io.github.eslamasabry.oc.feature.chats.presentation.HomeNotices
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNeedsYou
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchBody
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNoMatchTitle
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunningCount
import io.github.eslamasabry.oc.ui.words.res.chatsHomeShowAll
import io.github.eslamasabry.oc.ui.words.res.chatsHomeTitle
import io.github.eslamasabry.oc.ui.words.res.chatsReturnBriefMore
import io.github.eslamasabry.oc.ui.words.res.chats_back
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceConnecting
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDaysAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceHoursAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceJustNow
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.returnBriefSeen
import io.github.eslamasabry.oc.ui.words.res.teamProjectDigest
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.eslamasabry.oc.ui.words.res.agentPersonaOtherName
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity

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
    root: () -> Boolean = { false },
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
    /** The edge dock over the list, under the home's sheets and dialogs; null = none. */
    edgeDock: io.github.eslamasabry.oc.feature.chats.dock.HomeEdgeDock? = null,
) {
    // E05-S21: on the bare list (the whole back stack) the first Back says "Press back again to exit" in the one
    // status line; called first so sheets, dialogs and a filled search registered below close before it.
    val backExit = homeBackExitStatus(root)
    // Flutter's home bar (KitTopBar.shell): the server pill opens the switcher; Search and Settings sit on their own
    // round faces. No overflow: Servers is the pill, Inbox is the needs-you line, the rest are rows at the list's end.
    val settingsLabel = stringResource(Res.string.librarySettingsTitle)
    val updateLabel = stringResource(Res.string.appUpdateUpdate)
    // G3: the empty page (and the list that could not be read) carries New conversation as its own full-width button
    // at the bottom (Flutter KitStateView.calm). Every other state of the home has the foot bar, and the bar always
    // holds the round + (RC1 device pass: while the server was opening or not answering the + left the bar, and the
    // skeleton or a filter that matched nothing had no way to start at all). A + tapped before the server is open is
    // answered by the home's "still opening" notice, so it never has to hide.
    val footBar = HomeFootBarRules.shown(state.phase)
    // Under Glass the floating + is liquid glass over the list (the kit records it only then), and the floating bar's
    // pane bends the same recording (glass step 4).
    val listGlass = rememberKitFrostState()
    val barActions = buildList {
        add(KitLineAction(stringResource(Res.string.kitTopBarSearch), actions::openSearch, icon = KitIcons.search))
        if (state.settingsAvailable) add(KitLineAction(settingsLabel, actions::openSettings, icon = KitIcons.settingsGear))
        if (state.updateReady) add(KitLineAction(updateLabel, actions::openUpdate, icon = KitIcons.systemDownload))
    }
    val briefKeys = state.brief?.rows.orEmpty().mapTo(hashSetOf()) { it.key }
    // At most one notice at a time, in the notice place; the order is HomeNotices' (a pure, tested rule).
    val shownNotice = HomeNotices.shown(HomeNotices.active(state, appCondition = appStatus != null, replyOffer = replyOffer != null))
    // The words every row shares are read once here, not again by each row; a row's menu words are only used when it opens.
    val rowWords = rememberHomeRowWords()
    // A command or a message can carry a token: masked as the chat masks it.
    val mask = io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask()
    val sectionGap = LocalKitTokens.current.sectionGap.dp
    val needsLine =
        if (state.needsYou == 0) null
        else pluralStringResource(Res.plurals.kitNeedsYouSpan, state.needsYou, Numbers.format(state.needsYou)).trimEnd().removeSuffix("·").trimEnd()
    KitScreen(
        // The phone's top is the page's own (Flutter conv-header): title row with plain Search and Settings icons, then
        // the connection pill beside the filters. Glass step 4: where the lens is drawn the two rows lie on one floating
        // liquid pane over the list, which scrolls on under it; everywhere else they are the page's scrolling header, as
        // before (KitFloatingBar's scrollsElsewhere).
        topBar = {
            // The page names itself under the bar (Flutter KitPageHeading); "2 need you" opens the Inbox.
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
        },
        // E20-S27: framed without the bar too, so the page lies on one continuous ground behind heading and list.
        page = true,
        floatingBar =
            KitFloatingBar(
                scrollsElsewhere = true,
                source = listGlass,
                below = {
                    // Nothing to filter on a list that holds nothing yet, so the chips wait for the first conversation.
                    val showFilters = state.phase == ChatsPhase.List || state.phase == ChatsPhase.NoMatch
                    val pill: (@Composable () -> Unit)? = if (!shellBar) null else state.connection?.let { connection -> { ConnectionStatusPill(connection, actions) } }
                    // Until the saved servers are read the row keeps its room and stays empty, so the pill and the
                    // filters arrive together in their places instead of the pill pushing the filters aside.
                    if (shellBar && !state.home.known) Spacer(Modifier.height(LocalKitTokens.current.minTarget.dp))
                    // One composition whether or not the pill has arrived, so the chips keep their scroll and press state
                    // when the connection shows up beside them (they run to the edge only beside the pill).
                    else HeaderRow(pill, showFilters) { Filters(state, actions, bleed = pill != null) }
                },
            ),
        // The foot bar: All conversations and the round + on one line, margin to margin, under the list (the frame
        // gives the list the room above it), so no row ever passes under the button.
        bottom =
            if (!footBar) null
            else ({
                KitFootBar(
                    label = stringResource(Res.string.chats_all_title),
                    // Nothing is counted yet while the server first opens (the skeleton): no number, never a 0.
                    count = if (state.phase == ChatsPhase.Opening) null else Numbers.format(state.shownCount ?: state.allCount),
                    onLink = actions::showAll,
                    fabLabel = stringResource(Res.string.chatsHomeNewChat),
                    fabIcon = KitIcons.add,
                    onFab = actions::newConversation,
                )
            }),
        status = backExit ?: appStatus.takeIf { shownNotice == HomeNoticeKind.AppCondition },
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
        edges = KitScreenEdges.Bleed,
    ) {
      Box(Modifier.fillMaxSize()) {
        // F: lib/ui/screens/chats/chats_home_screen.dart:536: pull the list down to read it again (KitRefresh); not
        // while the server is still opening. The kit part also answers F5 / Ctrl+R and a TalkBack "Refresh" action.
        // G2: rows follow each other with only their hairline between them (Flutter KitSliverRowGroup); a 4 dp gap
        // between list items put every row 4 px lower than the one before it.
        val list = rememberLazyListState()
        LazyColumn(
            state = list.also { KitReportPageScroll(it) },
            modifier = Modifier.kitGlassSource(listGlass),
            // The calm empty page fills the room and keeps its own end space, so it never scrolls by that much.
            // The foot bar is the frame's own block under the list; under Glass the list runs on under its pane and the
            // frame's chrome room keeps the last row above it at rest.
            contentPadding = PaddingValues(bottom = kitScreenChromePadding().calculateBottomPadding() + if ((state.phase == ChatsPhase.Empty || state.phase == ChatsPhase.NotRead || state.phase == ChatsPhase.NoMatch) && state.home.opening == null) 0.dp else Space.S4.dp),
        ) {
            // The saved rows draw as the normal list (the view model puts them in the feed); only a failure adds its card.
            state.home.opening?.takeIf { it.phase == ChatsOpeningPhase.Failed }?.let { opening ->
                item(key = "opening-status", contentType = "opening-status") { Box(kitItemMotion()) { OpeningStatus(opening, actions, blocked = state.newBlocked) } }
            }
            state.home.opening?.takeIf { state.newBlocked && it.phase != ChatsOpeningPhase.Failed }?.let { opening ->
                item(key = "new-blocked", contentType = "new-blocked") { Box(kitItemMotion()) { NewConversationBlocked(opening, actions) } }
            }
            replyOffer?.takeIf { shownNotice == HomeNoticeKind.ReplyOffer }?.let { offer -> item(key = "reply-notification-offer", contentType = "reply-notification-offer") { Box(kitItemMotion()) { offer() } } }
            state.brief?.let { brief -> item(key = "brief", contentType = "brief") { Box(kitItemMotion()) { BriefBlock(brief, rowWords, mask, actions, state) } } }
            if (state.archiveFailure != null && shownNotice == HomeNoticeKind.ArchiveFailure)
                item(key = "archive-failure", contentType = "archive-failure") {
                  Box(kitItemMotion().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
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
                }
            if (state.renameFailure != null && shownNotice == HomeNoticeKind.RenameFailure)
                item(key = "rename-failure", contentType = "rename-failure") {
                    Box(kitItemMotion()) {
                        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                            message = stringResource(Res.string.p12RenameFailed),
                            failure = state.renameFailure,
                        )
                    }
                }
            if (state.answerFailure != null && shownNotice == HomeNoticeKind.AnswerFailure)
                item(key = "answer-failure", contentType = "answer-failure") {
                    Box(kitItemMotion()) {
                        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                            message = stringResource(Res.string.chatHomeAnswerFailed),
                            failure = state.answerFailure,
                        )
                    }
                }
            // S4 / H3 (F: chats_home_screen.dart:511-632): a connection that did not answer says so by name and offers
            // Try again on the page; the one still opening has its own card. A list that is only partly read says what
            // could not be read and offers Try again (a refresh in flight says nothing: the indicator shows it); a server
            // that lists one project names it on the projects chip instead of a line.
            // Flutter shows only "{servers} isn't answering" (chatsHomeUnreachableShort); the longer "conversations
            // aren't shown" is its screen-reader label, because the visible list may still hold remembered rows.
            // One notice at a time: the first connection that did not answer; the next shows once this one is back.
            val unreachable = HomeNotices.unreachable(state)
            if (shownNotice == HomeNoticeKind.ServerUnreachable) unreachable.take(1).forEach { source ->
                item(key = "unreachable-${source.profile.raw}", contentType = "unreachable") {
                    Box(kitItemMotion().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                        KitNotice(
                            message = stringResource(Res.string.chatsHomeUnreachableShort, KitBidi.auto(source.name)),
                            actions = listOf(KitLineAction(stringResource(Res.string.kitTryAgain), { actions.retrySource(source.profile) })),
                        )
                    }
                }
            }
            // One place per server: a server with its own line, or the one opening (pill or failure card), is not
            // named again here.
            val partial = HomeNotices.partial(state)
            if (shownNotice == HomeNoticeKind.ListIncomplete) {
                item(key = "incomplete", contentType = "incomplete") {
                    Box(kitItemMotion().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                        val names = KitBidi.auto(partial.joinToString(", "))
                        KitNotice(
                            stringResource(Res.string.chatsHomeIncompleteNamed, names),
                            actions = listOf(KitLineAction(stringResource(Res.string.kitTryAgain), actions::retryList)),
                        )
                    }
                }
            }
            // G6 (F: agent_notices.dart:84-157): the phone agents' restart offer, sign-in, stopped and plan-limit lines.
            if (state.phase != ChatsPhase.Opening) {
                if (state.agentNotices.restart && shownNotice == HomeNoticeKind.AgentRestart)
                    item(key = "agents-restart", contentType = "agent-notice") {
                        Box(kitItemMotion().padding(horizontal = LocalKitTokens.current.gutter.dp)) { KitNotice(stringResource(Res.string.agentsRestartLine)) }
                    }
                // The phone's agents, one line: a sign-in first, then a stop, then a plan limit.
                state.agentNotices.lines.filter { HomeNotices.agentKind(it.kind) == shownNotice }.take(1).forEach { notice ->
                    item(key = "agent-${notice.agentId}-${notice.kind.name}", contentType = "agent-notice") {
                        Box(kitItemMotion().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                            val name = KitBidi.auto(notice.agentName)
                            when (notice.kind) {
                                HomeAgentNoticeKind.SignedOut ->
                                    KitNotice(
                                        stringResource(Res.string.agentsSignedOutLine, name),
                                        actions = listOf(KitLineAction(stringResource(Res.string.agentsSignInAction, name), actions::openAgents)),
                                    )
                                HomeAgentNoticeKind.Stopped ->
                                    KitNotice(
                                        stringResource(Res.string.agentsStoppedLine, name),
                                        actions = listOf(KitLineAction(stringResource(Res.string.agentsResumeAction, name), actions::openAgents)),
                                    )
                                HomeAgentNoticeKind.LimitReached ->
                                    KitNotice(
                                        stringResource(Res.string.agentsLimitUnknown, name),
                                        actions =
                                            listOf(
                                                KitLineAction(stringResource(Res.string.gateSheetOpenAgent, name), { actions.openAgent(notice.agentId) })
                                            ),
                                    )
                            }
                        }
                    }
                }
            }
            if (state.phase != ChatsPhase.Opening)
                sections.forEachIndexed { index, section -> item(key = "home-section-$index", contentType = "home-section") { section() } }
            when (state.phase) {
                ChatsPhase.Opening ->
                    if (state.home.opening?.phase != ChatsOpeningPhase.Failed) item(key = "opening", contentType = "opening") { KitSkeletonRows() }
                ChatsPhase.Empty ->
                    item(key = "empty", contentType = "empty") {
                        // A person with no computer saved may have come for one: the quiet second way in, only then.
                        CalmPage(
                            title = stringResource(Res.string.chatsHomeEmptyTitle),
                            body = stringResource(Res.string.chatsHomeEmptyCalmBody),
                            primary = KitAction(stringResource(Res.string.chatsHomeNewChat), actions::newConversation, icon = KitIcons.add),
                            tertiary =
                                if (state.hasComputer) null
                                else KitAction(stringResource(Res.string.chatsHomeConnectComputer), actions::connectComputer, icon = KitIcons.computer),
                            modifier = calmRoom(list, "empty"),
                        )
                    }
                ChatsPhase.NotRead ->
                    item(key = "not-read", contentType = "not-read") {
                        CalmPage(
                            title = stringResource(Res.string.chatsHomeNotReadTitle),
                            body = stringResource(Res.string.chatsHomeNotReadBody),
                            primary = KitAction(stringResource(Res.string.kitTryAgain), actions::retryList, icon = KitIcons.retry),
                            tertiary = KitAction(stringResource(Res.string.chatsHomeNewChat), actions::newConversation, icon = KitIcons.add),
                            modifier = calmRoom(list, "not-read"),
                        )
                    }
                ChatsPhase.NoMatch ->
                    item(key = "no-match", contentType = "no-match") { CalmNoMatchPage(state, actions, calmRoom(list, "no-match")) }
                ChatsPhase.List -> {
                    // A1 (owner, 2026-11-08): "Right now" holds everything working or waiting on the person, never
                    // capped; "Recent" the five latest of the rest; then the way to the whole list. Each section is
                    // one grouped panel in the Style in force; the panel eases its height as rows come and go.
                    val above = state.brief?.rows.orEmpty().isNotEmpty()
                    // A conversation the return brief shows is said once, there.
                    val rightNow = state.rightNow.filterNot { it.key in briefKeys }
                    val recent = state.recent.filterNot { it.key in briefKeys }
                    if (rightNow.isNotEmpty())
                        item(key = "right-now", contentType = "right-now") {
                            Box(kitItemMotion()) {
                                // P69: the panel is built afresh when the last answers in it go, so it never keeps
                                // their height (seen: a 55 dp empty band after Allow once).
                                key(rememberAnswersGoneKey(rightNow)) {
                                    KitRowPanel(
                                        label = stringResource(Res.string.chatsHomeRightNow),
                                        // A step of air under the chips, as above Recent, so the first label is not pressed up.
                                        gapBefore = if (above) sectionGap else Space.S4.dp,
                                        prominentDividers = true,
                                    ) {
                                        rightNow.forEach { row -> key(row.key) { ConversationRow(row, rowWords, rowFlags(row, state), mask, actions) } }
                                    }
                                }
                            }
                        }
                    if (recent.isNotEmpty())
                        item(key = "recent", contentType = "recent") {
                            Box(kitItemMotion()) {
                                KitRowPanel(
                                    label = stringResource(Res.string.chatsHomeRecent),
                                    gapBefore = if (above || rightNow.isNotEmpty()) sectionGap else Space.S4.dp,
                                    prominentDividers = true,
                                ) {
                                    recent.forEach { row -> key(row.key) { ConversationRow(row, rowWords, rowFlags(row, state), mask, actions) } }
                                }
                            }
                        }
                }
            }
        }
        // P20 (F: lib/ui/kit/kit_undo.dart:63, the bar floats above a pinned primary): the Undo bar rides above the New
        // button, which never moves. Before, the button rode above the bar and dropped into Undo's place as the bar left,
        // so a late tap meant for Undo opened New conversation.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
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
        // The edge dock over the list, clear of the foot bar; under Glass it bends the list's recording.
        androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.dock.LocalKitDockGlass provides listGlass) {
            edgeDock?.Content(clearBottom = if (footBar) io.github.eslamasabry.oc.ui.kit.action.KIT_FAB_CLEARANCE.dp else 0.dp)
        }
      }
        ChatsHomeSheets(state, actions)
        ConversationRenameDialog(state.renaming, actions::finishRename)
        AlwaysAllowConfirm(state.confirmAlways, mask, actions)
    }
}

/**
 * G3: a filtered list that holds nothing, as Flutter's calm states (lib/ui/screens/chats/chats_home_screen.dart:686-716):
 * Running on says "Nothing running" with New conversation and "Show all conversations"; any other filter says nothing
 * matches, with Clear filters.
 */
@Composable
private fun CalmNoMatchPage(state: ChatsUi, actions: ChatsActions, modifier: Modifier) {
    // New conversation is the foot bar's +, under this page as under the list: never a second button here.
    if (state.filters.running && !state.filters.needsYou)
        CalmPage(
            title = stringResource(Res.string.chatsHomeNothingRunning),
            body = stringResource(Res.string.chatsHomeNothingRunningBody),
            primary = KitAction(stringResource(Res.string.chatsHomeShowAll), actions::toggleRunning),
            tertiary = null,
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
/** The least room a calm page keeps, however much stands above it. */
private const val CALM_MIN_ROOM_DP: Int = 320

/**
 * The calm page's room: what is left of the list's window under whatever stands above it, so its action stays on
 * screen. It used to take a whole window's height, and any notice above it (a new person gets "Notify you when the
 * agent needs you?" right after setting up This phone) pushed "New conversation" below the fold: the home said "No
 * conversations yet" and showed no way to start one (emulator walk of the release candidate, 2026-11-10). Before the
 * list's first layout the page takes the window's height, as before.
 */
@Composable
private fun LazyItemScope.calmRoom(list: LazyListState, key: String): Modifier {
    val room by remember(list, key) {
        derivedStateOf {
            val info = list.layoutInfo
            val self = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (self == null) null else (info.viewportEndOffset - info.afterContentPadding - self.offset).takeIf { it > 0 }
        }
    }
    val px = room ?: return Modifier.fillParentMaxSize()
    return Modifier.fillParentMaxWidth().height(maxOf(with(LocalDensity.current) { px.toDp() }, CALM_MIN_ROOM_DP.dp))
}

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

/** The pill fixed at the start, a short divider, then the filters (which alone scroll) beside it. With no pill, the filters alone. */
@Composable
private fun HeaderRow(pill: (@Composable () -> Unit)?, showFilters: Boolean, filters: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (pill != null) Modifier.padding(start = LocalKitTokens.current.gutter.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pill != null) Box(Modifier.widthIn(max = 220.dp)) { pill() }
        if (showFilters) {
            if (pill != null) {
                Spacer(Modifier.width(Space.S2.dp))
                Box(Modifier.height(24.dp)) { io.github.eslamasabry.oc.ui.kit.foundation.KitDivider(vertical = true) }
                Spacer(Modifier.width(Space.S2.dp))
            }
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
        label = serverNameShown(connection.server),
        tone = when (state) {
            HomeConnectionState.Connected -> KitConnectionTone.Ok
            HomeConnectionState.Connecting -> KitConnectionTone.Working
            HomeConnectionState.NotAnswering -> KitConnectionTone.Problem
        },
        // The dot says connected; a word only when it is not.
        word = status.takeUnless { state == HomeConnectionState.Connected },
        spoken = "${serverNameShown(connection.server)}, $status, $switchLabel",
        onClickLabel = switchLabel,
        // F: lib/ui/screens/home_screen.dart:434-470: the one-tap switcher, not the Servers page.
        onClick = actions::openServerSwitcher,
    )
}

@Composable
private fun Filters(state: ChatsUi, actions: ChatsActions, bleed: Boolean = false) {
    KitFilterChips(bleed = bleed) {
        HomeFilterChips(state, actions)
        val runningNow = state.runningShown
        KitChip(
            label = if (runningNow > 0) stringResource(Res.string.chatsHomeRunningCount, runningNow) else stringResource(Res.string.chatsHomeRunning),
            kind = KitChipKind.Action,
            selected = state.filters.running, onClick = actions::toggleRunning,
        )
    }
}

@Composable
private fun BriefBlock(brief: BriefUi, words: HomeRowWords, mask: (String) -> String, actions: ChatsActions, state: ChatsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        // On the gutter like the rows under it (its glyph sat on the screen's edge).
        // Headed and grouped like Right now and Recent, so the top group reads as part of the same list.
        if (brief.rows.isNotEmpty())
            // P69: rebuilt when the last answers in it go, as Right now.
            key(rememberAnswersGoneKey(brief.rows)) {
                KitRowPanel(label = stringResource(Res.string.teamProjectDigest)) {
                    brief.rows.forEach { row -> key(row.key) { ConversationRow(row, words, rowFlags(row, state), mask, actions) } }
                }
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
                        role = KitButtonRole.Secondary,
                    )
                else Spacer(Modifier)
                if (brief.rows.isNotEmpty())
                    KitButton(
                        label = stringResource(Res.string.returnBriefSeen),
                        onClick = { actions.dismissBrief(brief.acknowledgement) },
                        role = KitButtonRole.Secondary,
                    )
            }
    }
}

/**
 * F: lib/ui/screens/chats/chats_host.dart:262-270 (`permissionAlwaysStep`): Always allow asks first, naming what and
 * where, with the way back ("You can take this back in Settings").
 */
@Composable
private fun AlwaysAllowConfirm(row: ConversationRowUi?, mask: (String) -> String, actions: ChatsActions) {
    // Masked as in the chat's own confirm: a command can carry a token.
    val command = row?.permission?.command?.let { KitBidi.ltr(mask(it)) }
    KitConfirm(
        spec =
            command?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.chatRequestAlwaysConfirm, it),
                    body = stringResource(Res.string.chatRequestAlwaysScope, it, stringResource(Res.string.chatRequestAlwaysInProject)),
                    confirmLabel = stringResource(Res.string.chatUiAlwaysAllow),
                    kind = KitConfirmKind.Neutral,
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
 * P69 (a workaround; the cause, most likely KitRowPanel's eased height under the shell's lookahead, is unproven): a
 * panel's key, which moves on only when the last answers in it go (a waiting permission, question or form). Rebuilding
 * a panel drops what its rows hold for the moment (an answer half tapped, an open row menu, a swipe), so the key never
 * moves while any row in the panel still carries answers, nor when answers arrive: nobody is answering in it then.
 */
internal fun answersGoneKey(key: Int, hadAnswers: Boolean, hasAnswers: Boolean): Int =
    if (hadAnswers && !hasAnswers) key + 1 else key

/** Whether any of [rows] carries answers under its words. */
internal fun carriesAnswers(rows: List<ConversationRowUi>): Boolean =
    rows.any { it.permission != null || it.question != null || it.form != null }

private class AnswersGone {
    var had = false
    var key = 0
}

@Composable
private fun rememberAnswersGoneKey(rows: List<ConversationRowUi>): Int {
    val seen = remember { AnswersGone() }
    val has = carriesAnswers(rows)
    seen.key = answersGoneKey(seen.key, seen.had, has)
    seen.had = has
    return seen.key
}

/**
 * One conversation as A1's roomy row (KitRecentRow): the agent's bare mark, the title, one line of the last message
 * (a waiting request's own words first: "Asks to run: npm test"), the time and the state word. Long-press opens the
 * row's acts; a waiting permission keeps its answers (Allow once, Reject, Always allow) under the words, as Flutter's
 * list does (F: lib/ui/screens/chats/chats_host.dart:240-270).
 */
/** The words a home row and its menu use, resolved once for the whole list. */
private data class HomeRowWords(
    val needsYou: String,
    val working: String,
    val failed: String,
    val done: String,
    val stopped: String,
    val interrupted: String,
    val cantReopen: String,
    val archive: String,
    val otherAgent: String,
    val rowActions: String,
    val pin: String,
    val unpin: String,
    val rename: String,
    val share: String,
    val details: String,
    val related: String,
    val handoff: String,
    val delete: String,
    val stop: String,
    /** "Working", or "Working 34m" once the run's start is known (B-161). */
    val runningFor: (Long?) -> String,
)

@Composable
private fun rememberHomeRowWords(): HomeRowWords =
    HomeRowWords(
        needsYou = stringResource(Res.string.chatsHomeNeedsYou),
        working = stringResource(Res.string.chatsHomeRowWorking),
        failed = stringResource(Res.string.chatsHomeFailed),
        done = stringResource(Res.string.chatsHomeDone),
        stopped = stringResource(Res.string.chatsHomeStopped),
        interrupted = stringResource(Res.string.chatsHomeInterrupted),
        cantReopen = stringResource(Res.string.agentsStateCantReopen),
        archive = stringResource(Res.string.sessionMenuArchive),
        otherAgent = stringResource(Res.string.agentPersonaOtherName),
        rowActions = stringResource(Res.string.chatsRowActions),
        pin = stringResource(Res.string.statePin),
        unpin = stringResource(Res.string.stateUnpin),
        rename = stringResource(Res.string.p12Rename),
        share = stringResource(Res.string.chatUiShareSession),
        details = stringResource(Res.string.sessionMenuDetails),
        related = stringResource(Res.string.sessionOpenRelated),
        handoff = stringResource(Res.string.sessionCopyHandoff),
        delete = stringResource(Res.string.sessionMenuDelete),
        stop = stringResource(Res.string.dockStop),
        runningFor = liveRunWords(),
    )

/** What a row needs to know of the whole screen, and nothing else: equal for a row nothing happened to. */
private data class HomeRowFlags(
    val canArchive: Boolean,
    val pinnable: Boolean,
    val shareable: Boolean,
    val stoppable: Boolean,
    val pinned: Boolean,
    val renamable: Boolean,
    val deletable: Boolean,
    val always: Boolean,
    val answering: Boolean,
    val held: HomeHeldReply?,
    /** H1: a waiting question's answer inside its undo window (the words chosen), on its way, or refused. */
    val heldQuestion: String?,
    val questionSending: Boolean,
    val questionRefused: Boolean,
    val showProject: Boolean,
    val showAgents: Boolean,
    val nowMs: Long,
)

private fun rowFlags(row: ConversationRowUi, state: ChatsUi): HomeRowFlags =
    HomeRowFlags(
        canArchive = row.key in state.archivable,
        pinnable = row.key in state.pinnable,
        shareable = row.key in state.shareable,
        stoppable = state.stoppable && row.running != null,
        pinned = row.key in state.pinned,
        renamable = row.key in state.renamable,
        deletable = row.key in state.deletable,
        always = row.key in state.alwaysAllowable,
        answering = row.permission?.requestId?.let { it in state.answering } == true,
        held = row.permission?.requestId?.let { state.held[it] },
        heldQuestion = row.question?.requestId?.let { state.heldQuestions[it] },
        questionSending = row.question?.requestId?.let { it in state.sendingQuestions } == true,
        questionRefused = row.question?.requestId?.let { it in state.refusedQuestions } == true,
        showProject = state.showProjects,
        showAgents = state.showAgents,
        nowMs = state.nowMs,
    )

/**
 * One conversation as A1's roomy row (KitRecentRow): the agent's bare mark, the title, one line of the last message
 * (a waiting request's own words first: "Asks to run: npm test"), the time and the state word. Long-press opens the
 * row's acts; a waiting permission keeps its answers (Allow once, Reject, Always allow) under the words, as Flutter's
 * list does (F: lib/ui/screens/chats/chats_host.dart:240-270).
 */
@Composable
private fun ConversationRow(
    row: ConversationRowUi,
    words: HomeRowWords,
    flags: HomeRowFlags,
    mask: (String) -> String,
    actions: ChatsActions,
) {
    val title = rowTitleText(row.title)
    val canArchive = flags.canArchive
    val status =
        // While the person's answer is held ("Allowed · Undo" under the row) or a chosen answer to a question is held or
        // on its way, the state word must not say "Needs you".
        if (flags.held != null || flags.heldQuestion != null || flags.questionSending) null
        // H4 (F: chats_home_screen.dart:901): a finished conversation that cannot reopen says so before Done/Stopped.
        else if (HomeRowStatusRules.saysCantReopen(row)) words.cantReopen
        else when (row.status) {
            RowStatus.NeedsYou -> words.needsYou
            RowStatus.Running ->
                // H5 (F: chats_host.dart:188-199): how long the run has gone, when its start is known.
                words.runningFor(row.running?.sinceMs?.takeIf { flags.nowMs > 0 }?.let { flags.nowMs - it })
            RowStatus.Failed -> words.failed
            RowStatus.Done -> words.done
            RowStatus.Stopped -> words.stopped
            RowStatus.Interrupted -> words.interrupted
            RowStatus.None -> null
        }
    val tone =
        if (flags.held != null || flags.heldQuestion != null || flags.questionSending) KitRowStateTone.Quiet
        else when (row.status) {
            RowStatus.NeedsYou -> KitRowStateTone.NeedsYou
            RowStatus.Running -> KitRowStateTone.Running
            RowStatus.Failed -> KitRowStateTone.Failed
            else -> KitRowStateTone.Quiet
        }
    var menuOpen by remember(row.key) { mutableStateOf(false) }
    val agent = KitFeedAgent(row.agentMark, row.agentName ?: words.otherAgent)
    val time =
        row.updatedAtMs?.takeIf { flags.nowMs > 0 }?.let { kitRelativeAge(it, flags.nowMs) }
            ?: row.ago?.let { agoText(it) }
    val preview =
        row.permission?.let {
            val target = KitBidi.ltr(mask(it.command))
            when (HomePreviewRules.askKind(it.kind)) {
                HomePreviewRules.AskKind.Run -> stringResource(Res.string.chatsHomeAsksToRun, target)
                HomePreviewRules.AskKind.Edit -> stringResource(Res.string.chatsHomeAsksToEdit, target)
                HomePreviewRules.AskKind.Read -> stringResource(Res.string.chatsHomeAsksToRead, target)
                HomePreviewRules.AskKind.Other -> stringResource(Res.string.chatsHomeAsksOther, target)
            }
        }
            // H1: a waiting question's own words, or the form's title, where no permission speaks first.
            ?: row.question?.question?.takeIf { it.isNotBlank() }?.let { mask(it) }
            ?: row.question?.title?.takeIf { it.isNotBlank() }?.let { mask(it) }
            ?: row.form?.let { mask(it.title?.takeIf { t -> t.isNotBlank() } ?: stringResource(Res.string.chatUiInputRequested)) }
            // H5: the shell command a running row is on ("Running npm test"), read left to right.
            ?: row.running?.command?.takeIf { row.status == RowStatus.Running && it.isNotBlank() }
                ?.let { stringResource(Res.string.signalRowRunningCommand, KitBidi.ltr(mask(it))) }
            ?: row.preview?.let {
                HomePreviewRules.forRow(
                    mask(it),
                    row.directory,
                    // The status column says it; the line does not say it again.
                    if (row.status == RowStatus.Done) listOfNotNull(words.done, "Done") else emptyList(),
                )
            }
    val project = row.project.takeIf { flags.showProject && it.isNotBlank() }
    // No line to show: the row says where it lives instead (its project when no dot names it, and its server).
    val meta = HomePreviewRules.fallback(row.project, serverNameShownOrNull(row.server), projectShown = project != null)
    val permission = row.permission
    val question = row.question
    val form = row.form
    val below: (@Composable () -> Unit)? =
        when {
            permission != null -> ({ PermissionAnswers(row, permission, flags.always, flags.answering, flags.held, actions) })
            question != null -> ({ QuestionAnswers(row, question, flags, mask, actions) })
            form != null -> ({ FormAnswers(row, actions) })
            else -> null
        }
    KitMenu(
        expanded = menuOpen,
        // N-30: the same acts, in the same order, as a row on All conversations (a tap already opens it). Built only
        // while the menu is open; a closed menu keeps what it last showed for its fade.
        items = if (!menuOpen) emptyList() else buildList {
            if (flags.stoppable) add(KitMenuItem(words.stop, { menuOpen = false; actions.stopRow(row) }, icon = KitIcons.stop))
            if (flags.pinnable) {
                add(KitMenuItem(if (flags.pinned) words.unpin else words.pin, { menuOpen = false; actions.togglePin(row) }, icon = if (flags.pinned) KitIcons.unpin else KitIcons.pin))
            }
            if (flags.renamable) add(KitMenuItem(words.rename, { menuOpen = false; actions.rename(row) }, icon = KitIcons.edit))
            if (row.canOpen) {
                // Share, Details, Copy handoff and Open related open the conversation's own page and run the same
                // act its menu runs (one path to the server for each).
                if (flags.shareable) add(KitMenuItem(words.share, { menuOpen = false; actions.rowAct(row, RowAct.Share) }, icon = KitIcons.link))
                add(KitMenuItem(words.details, { menuOpen = false; actions.rowAct(row, RowAct.Details) }, icon = KitIcons.info))
                add(KitMenuItem(words.related, { menuOpen = false; actions.rowAct(row, RowAct.OpenRelated) }, icon = KitIcons.branch))
                add(KitMenuItem(words.handoff, { menuOpen = false; actions.rowAct(row, RowAct.CopyHandoff) }, icon = KitIcons.terminal))
            }
            if (canArchive) add(KitMenuItem(words.archive, { menuOpen = false; actions.archive(row) }, icon = KitIcons.archive))
            if (flags.deletable) add(KitMenuItem(words.delete, { menuOpen = false; actions.delete(row) }, icon = KitIcons.delete, destructive = true))
        },
        onDismissRequest = { menuOpen = false },
        title = title,
    ) {
        val item: @Composable () -> Unit = {
            KitRecentRow(
                title = title,
                agent = agent,
                time = time,
                onClick = if (row.canOpen) ({ actions.openConversation(row.ref) }) else null,
                preview = preview,
                meta = meta,
                project = project,
                state = status?.let { KitRowState(it, tone) },
                pinned = flags.pinned,
                growKey = row.ref,
                onLongClick = if (row.canOpen || canArchive) ({ menuOpen = true }) else null,
                onLongClickLabel = words.rowActions,
                below = below,
                speakAgent = flags.showAgents,
            )
        }
        // Swipe toward the end of the line archives (mirrors in right-to-left); the menu and the accessibility action
        // are the same act, so the gesture is never the only way. One wrapper whether or not the row can archive, so a
        // row that becomes archivable keeps its place in the tree (and its press state) instead of being rebuilt.
        KitSwipeRow(words.archive, { actions.archive(row) }, enabled = canArchive) { item() }
    }
}

/** A waiting permission's answers under its row: Allow once, Reject and, where the server keeps it, Always allow. */
@Composable
private fun PermissionAnswers(
    row: ConversationRowUi,
    permission: io.github.eslamasabry.oc.engine.agents.feed.FeedPermission,
    always: Boolean,
    answering: Boolean,
    held: HomeHeldReply?,
    actions: ChatsActions,
) {
    // H1 (F: chats_host.dart:236-246): the answer waits 3 s; the row says what was chosen and offers Undo.
    if (held != null) {
        KitReceipt(
            state = KitReceiptState.Confirmed,
            label =
                stringResource(
                    when (held) {
                        HomeHeldReply.Allowed -> Res.string.chatRequestAnswerAllowed
                        HomeHeldReply.Rejected -> Res.string.chatRequestAnswerRejected
                        HomeHeldReply.AlwaysAllowed -> Res.string.chatRequestAlwaysOn
                    }
                ),
            onUndo = { actions.undoAnswer(row) },
        )
        return
    }
    val enabled =
        when {
            answering -> KitEnabled.Disabled(stringResource(Res.string.serversWorking))
            !row.canOpen -> KitEnabled.Disabled(stringResource(Res.string.e7WorkspaceConnecting))
            else -> KitEnabled.Enabled
        }
    KitChipWrap {
        KitButton(
            stringResource(Res.string.kitRequestAllowOnce),
            { actions.answerPermission(row, true) },
            role = KitButtonRole.Secondary,
            enabled = enabled,
            working = answering,
            expand = false,
        )
        KitButton(
            stringResource(Res.string.approvalDeny),
            { actions.answerPermission(row, false) },
            role = KitButtonRole.Tertiary,
            enabled = enabled,
            expand = false,
        )
        if (always)
            KitButton(
                stringResource(Res.string.chatUiAlwaysAllow),
                { actions.askAlwaysAllow(row) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
                expand = false,
            )
    }
}

/**
 * H1 (F: chats_host.dart:300-310, attention_card.dart): a waiting question under its row. One prompt with one answer
 * shows its choices (at most [QUESTION_ROW_CHOICES]; the rest are behind "More answers", which opens the
 * conversation); a tap holds the answer for the undo window, then sends it. Anything else (several prompts, many
 * answers, "something else") is answered in the conversation: the same Answer button the chat's card has.
 */
@Composable
private fun QuestionAnswers(
    row: ConversationRowUi,
    question: io.github.eslamasabry.oc.engine.agents.feed.FeedQuestion,
    flags: HomeRowFlags,
    mask: (String) -> String,
    actions: ChatsActions,
) {
    val held = flags.heldQuestion
    if (held != null) {
        KitReceipt(
            state = KitReceiptState.Confirmed,
            label = mask(held),
            onUndo = { actions.undoAnswer(row) },
        )
        return
    }
    if (flags.questionSending) {
        KitReceipt(state = KitReceiptState.Sending)
        return
    }
    if (flags.questionRefused) {
        KitReceipt(state = KitReceiptState.Refused(), onRetry = { actions.retryQuestion(row) })
        return
    }
    val enabled = if (row.canOpen) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.e7WorkspaceConnecting))
    val inPlace = question.inPlace && question.choices.isNotEmpty()
    val shown = if (question.choices.size > QUESTION_ROW_CHOICES) question.choices.take(QUESTION_ROW_CHOICES - 1) else question.choices
    KitChipWrap {
        if (inPlace) {
            shown.forEach { choice ->
                KitButton(
                    mask(choice),
                    { actions.answerQuestion(row, listOf(listOf(choice)), choice) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                    expand = false,
                )
            }
            val remaining = question.choices.size - shown.size
            if (remaining > 0)
                KitButton(
                    pluralStringResource(Res.plurals.kitRequestMoreAnswers, remaining, Numbers.format(remaining)),
                    { actions.openRequest(row) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                    expand = false,
                )
            if (question.custom)
                KitButton(
                    stringResource(Res.string.chatRequestOtherAnswer),
                    { actions.openRequest(row) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                    expand = false,
                )
        } else {
            KitButton(
                stringResource(Res.string.kitRequestAnswer),
                { actions.openRequest(row) },
                role = KitButtonRole.Secondary,
                enabled = enabled,
                expand = false,
            )
        }
    }
}

/** H1: a waiting form under its row: the form itself is filled in the conversation, so Answer opens it there. */
@Composable
private fun FormAnswers(row: ConversationRowUi, actions: ChatsActions) {
    KitChipWrap {
        KitButton(
            stringResource(Res.string.kitRequestAnswer),
            { actions.openRequest(row) },
            role = KitButtonRole.Secondary,
            enabled = if (row.canOpen) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.e7WorkspaceConnecting)),
            expand = false,
        )
    }
}

/** Choices shown on a row before "More answers" (the chat's question card shows the same five, F: Kit question card). */
private const val QUESTION_ROW_CHOICES: Int = 5

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
