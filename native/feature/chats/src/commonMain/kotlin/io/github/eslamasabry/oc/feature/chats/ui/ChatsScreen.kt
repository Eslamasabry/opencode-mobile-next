package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import io.github.eslamasabry.oc.ui.kit.frame.KitHeadingLine
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouSpan
import io.github.eslamasabry.oc.ui.words.res.kitTopBarMore
import io.github.eslamasabry.oc.ui.words.res.inboxTitle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chats.presentation.BriefUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRunNamed
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.kitRequestReject
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.kit.row.KitFeedItem
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.style.KitRunningFeedLine
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSlip
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
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
import io.github.eslamasabry.oc.ui.words.res.returnBriefContinue
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.stateAttentionTitle
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
) {
    // B15 (QA wave 1): short words so "Conversations" is never cut, and the door says where it goes
    // (Settings).
    val settingsLabel = stringResource(Res.string.librarySettingsTitle)
    val updateLabel = stringResource(Res.string.appUpdateUpdate)
    val showFab = state.phase == ChatsPhase.List && state.home.opening == null
    var menuOpen by remember { mutableStateOf(false) }
    // Looks pass (concept A): a hub's large title with icon actions; New conversation is the floating button, so
    // the bar keeps Search and Settings (and Servers or Update only while they apply).
    val barActions = buildList {
        add(KitLineAction(stringResource(Res.string.kitTopBarSearch), actions::openSearch, icon = KitIcons.search))
        if (state.home.opening != null)
            add(KitLineAction(stringResource(Res.string.serversTitle), actions::openServers, icon = KitIcons.server))
        if (state.updateReady) add(KitLineAction(updateLabel, actions::openUpdate, icon = KitIcons.systemDownload))
        if (state.settingsAvailable) add(KitLineAction(settingsLabel, actions::openSettings, icon = KitIcons.settingsGear))
    }
    val briefKeys = state.brief?.rows.orEmpty().mapTo(hashSetOf()) { it.key }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.chatsHomeTitle), actions = barActions, large = true,
                scope = if (state.needsYou == 0) null else ({
                    val line = pluralStringResource(Res.plurals.kitNeedsYouSpan, state.needsYou, Numbers.format(state.needsYou)).trimEnd().removeSuffix("·").trimEnd()
                    KitHeadingLine(line, actions::showInbox)
                }),
                more = { hidden ->
                    val items = listOf(
                        KitMenuItem(stringResource(Res.string.inboxTitle), { actions.showInbox() }, icon = KitIcons.inbox),
                        KitMenuItem(stringResource(Res.string.serversTitle), { actions.openServers() }, icon = KitIcons.server),
                        KitMenuItem(stringResource(Res.string.chatsHomeShowAll), { actions.showAll() }, icon = KitIcons.chat),
                        KitMenuItem(stringResource(Res.string.p12ProjectsTitle), { actions.openProjects() }, icon = KitIcons.projects),
                        KitMenuItem(stringResource(Res.string.stateAttentionTitle), { actions.openAttention() }, icon = KitIcons.activity),
                    ) + hidden.map { KitMenuItem(it.label, it.onClick, icon = it.icon) }
                    KitMenu(menuOpen, items.map { item -> item.copy(onSelect = { menuOpen = false; item.onSelect() }) }, { menuOpen = false }, title = stringResource(Res.string.kitTopBarMore)) {
                        KitIconButton(KitIcons.more, stringResource(Res.string.kitTopBarMore), { menuOpen = true })
                    }
                },
            )
        },
        header = if (state.phase == ChatsPhase.List || state.phase == ChatsPhase.NoMatch) ({ Filters(state, actions) }) else null,
        status = appStatus,
        width = KitScreenWidth.Reading,
    ) {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
            contentPadding = PaddingValues(bottom = if (showFab) 96.dp else Space.S4.dp),
        ) {
            state.home.opening?.let { opening ->
                item(key = "opening-status") { OpeningStatus(opening, actions) }
                items(opening.rows, key = { "cached:${it.key}" }) { row -> OpeningRow(row) }
                if (opening.rows.isEmpty()) item(key = "opening") { KitSkeletonRows() }
                return@LazyColumn
            }
            replyOffer?.let { offer -> item(key = "reply-notification-offer") { offer() } }
            state.brief?.let { brief -> item(key = "brief") { BriefBlock(brief, actions, state) } }
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
            when (state.phase) {
                ChatsPhase.Opening -> item(key = "opening") { KitSkeletonRows() }
                ChatsPhase.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                            title = stringResource(Res.string.chatsHomeEmptyTitle),
                            body = stringResource(Res.string.chatsHomeEmptyCalmBody),
                            primary =
                                KitAction(
                                    stringResource(Res.string.chatsHomeNewChat),
                                    actions::newConversation,
                                ),
                        )
                    }
                ChatsPhase.NoMatch ->
                    item(key = "no-match") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
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
                    val today = state.today.filterNot { it.key in briefKeys }
                    val earlier = state.earlier.filterNot { it.key in briefKeys }
                    if (today.isNotEmpty()) {
                        item(key = "today") {
                            KitSectionLabel(stringResource(Res.string.chatsHomeToday))
                        }
                        items(today, key = { it.key }) { row ->
                            ConversationRow(row, actions, state)
                            if (row != today.last()) io.github.eslamasabry.oc.ui.kit.foundation.KitDivider(Modifier.padding(horizontal = Space.S4.dp))
                        }
                    }
                    if (earlier.isNotEmpty()) {
                        item(key = "earlier") {
                            KitSectionLabel(stringResource(Res.string.chatsHomeEarlier))
                        }
                        items(earlier, key = { it.key }) {
                            ConversationRow(it, actions, state)
                            if (it != earlier.last()) io.github.eslamasabry.oc.ui.kit.foundation.KitDivider(Modifier.padding(horizontal = Space.S4.dp))
                        }
                    }
                }
            }

        }
        if (showFab) {
            KitFab(
                label = stringResource(Res.string.chatsHomeNewShort),
                icon = KitIcons.add,
                onClick = actions::newConversation,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.S4.dp),
            )
        }
      }
        ChatsHomeSheets(state, actions)
    }
}

@Composable
private fun SearchField(actions: ChatsActions) {
    val field = rememberTextFieldState()
    // The first value is the empty field itself, not something the person typed.
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { actions.search(it) }
    }
    Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
        KitSearchField(label = stringResource(Res.string.kitTopBarSearch), onSearch = {}, state = field)
    }
}

@Composable
private fun Filters(state: ChatsUi, actions: ChatsActions) {
    KitFilterChips {
        HomeFilterChips(state, actions)
        KitChip(
            label = stringResource(Res.string.chatsHomeRunning), kind = KitChipKind.Action,
            selected = state.filters.running, onClick = actions::toggleRunning,
        )
    }
}

@Composable
private fun BriefBlock(brief: BriefUi, actions: ChatsActions, state: ChatsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (brief.unknown) KitNotice(stringResource(Res.string.chatsReturnBriefUnknown))
        brief.rows.forEach { ConversationRow(it, actions, state) }
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
private fun ConversationRow(row: ConversationRowUi, actions: ChatsActions, state: ChatsUi? = null) {
    val title =
        when (val t = row.title) {
            is RowTitle.Text -> t.text
            RowTitle.New,
            RowTitle.Untitled -> stringResource(Res.string.workspaceNewSession)
        }
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
    KitFeedItem(
        title = title,
        project = row.project,
        state = KitRowState(status?.takeUnless { signal && row.running != null }, tone),
        time = row.updatedAtMs?.takeIf { state != null && state.nowMs > 0 }?.let { kitRelativeAge(it, state!!.nowMs) }
            ?: row.ago?.takeUnless { signal && row.running != null }?.let { agoText(it) },
        done = row.status == RowStatus.Done,
        growKey = row.ref,
        onClick = if (row.canOpen) ({ actions.openConversation(row.ref) }) else null,
    )

    if (!signal && row.permission != null) {
        val permission = row.permission
        val answering = permission.requestId in state?.answering.orEmpty()
        KitPermissionCard(
            title = stringResource(Res.string.chatRequestAskRunNamed, permission.command),
            summary = null,
            subject = permission.command,
            allowOnceLabel = stringResource(Res.string.kitRequestAllowOnce),
            alwaysLabel = null,
            denyLabel = stringResource(Res.string.kitRequestReject),
            secondary = true,
            onAllowOnce = { actions.answerPermission(row, true) },
            onAlways = null,
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
