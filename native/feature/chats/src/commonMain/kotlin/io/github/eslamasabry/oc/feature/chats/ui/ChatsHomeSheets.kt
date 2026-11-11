package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.feature.chats.presentation.HomeProjectKind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsMonitorStatus
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.chats.presentation.DiagnosisAction
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsWatchedKind
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.SwitcherServerState
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeFilterChips(state: ChatsUi, actions: ChatsActions) {
    KitChip(
        label = when {
            state.otherFoldersSelected -> stringResource(Res.string.chatsHomeOtherFolders)
            state.filters.projectDirectory == null -> stringResource(Res.string.chatsHomeAllProjects)
            else -> state.projectChoices.firstOrNull { it.directory == state.filters.projectDirectory }?.name
                ?: state.filters.projectDirectory!!.trimEnd('/').substringAfterLast('/')
        },
        kind = KitChipKind.Action,
        // F: lib/ui/screens/chats/chats_home_screen.dart:421-423: a chosen project tints the chip (active tone); it
        // carries no check, which marks the Running filter beside it.
        tone = if (state.filters.projectDirectory != null || state.otherFoldersSelected) KitChipTone.Active else KitChipTone.Neutral,
        opensMenu = true,
        onClick = actions::showProjectChoices,
    )
    if (state.home.sources.size > 1) {
        KitChip(
            label = if (state.home.sources.all { it.shown }) stringResource(Res.string.chatsSourcesAll)
                else stringResource(Res.string.chatsSourcesSome, state.home.sources.count { it.shown }, state.home.sources.size),
            kind = KitChipKind.Action,
            // It opens a sheet of choices, so it carries the same chevron as the project chip beside it (P-023).
            opensMenu = true,
            onClick = actions::showSources,
        )
    }

}

@Composable
internal fun ChatsHomeSheets(state: ChatsUi, actions: ChatsActions) {
    val sheet = state.sheet
    // The in-use server the person asked to disconnect from, waiting for their yes.
    var askDisconnect by remember { mutableStateOf<Pair<io.github.eslamasabry.oc.core.common.ids.ProfileId, String>?>(null) }
    KitSheet(
        visible = sheet != null && sheet != ChatsHomeSheet.OpenProject && sheet != ChatsHomeSheet.Inbox,
        onDismissRequest = actions::closeHomeSheet,
        title =
            when (sheet) {
                ChatsHomeSheet.Projects -> stringResource(Res.string.chatsFilterSheetTitle)
                ChatsHomeSheet.Sources -> stringResource(Res.string.chatsSourcesSheetTitle)
                ChatsHomeSheet.Monitoring -> stringResource(Res.string.homeWatchedServers)
                ChatsHomeSheet.Servers -> stringResource(Res.string.serverSwitcherTitle)
                ChatsHomeSheet.Inbox,
                ChatsHomeSheet.OpenProject,
                null -> stringResource(Res.string.inboxTitle)
            },
        closeLabel = stringResource(Res.string.kitTopBarClose),
    ) {
        // KitSheet scrolls its body; a second vertical scroll here measured with infinite height (N-38).
        Column {
            when (sheet) {
                ChatsHomeSheet.Projects -> ProjectChoices(state, actions)
                ChatsHomeSheet.Sources -> SourceChoices(state, actions)
                ChatsHomeSheet.Inbox -> Unit
                ChatsHomeSheet.Monitoring -> MonitoringSettings(state, actions)
                ChatsHomeSheet.Servers -> ServerSwitcher(state, actions) { profile, name -> askDisconnect = profile to name }
                ChatsHomeSheet.OpenProject,
                null -> Unit
            }
        }
    }
    KitPageOverlay(sheet == ChatsHomeSheet.Inbox, stringResource(Res.string.inboxTitle), actions::closeHomeSheet) {
        ChatsInboxScreen(state, actions)
    }
    KitConfirm(
        spec =
            state.confirmWatched?.let { watched ->
                KitConfirmSpec(
                    title = stringResource(Res.string.monitorSwitchToTitle, watched.sourceName),
                    body = stringResource(Res.string.homeWatchedOpenBody),
                    confirmLabel = stringResource(Res.string.monitorSwitchTo, watched.sourceName),
                    kind = KitConfirmKind.Neutral,
                )
            },
        onConfirm = actions::confirmWatched,
        onCancel = actions::dismissWatched,
    )
    KitConfirm(
        spec =
            askDisconnect?.let { (_, name) ->
                KitConfirmSpec(
                    title = stringResource(Res.string.server40DisconnectConfirm, name),
                    body = stringResource(Res.string.server40DisconnectBody),
                    confirmLabel = stringResource(Res.string.server40Disconnect),
                    kind = KitConfirmKind.Neutral,
                )
            },
        onConfirm = {
            askDisconnect?.let { (profile, _) -> actions.disconnectServer(profile) }
            askDisconnect = null
        },
        onCancel = { askDisconnect = null },
    )
}

@Composable
private fun ProjectChoices(state: ChatsUi, actions: ChatsActions) {
    val all = state.filters.projectDirectory == null && !state.otherFoldersSelected
    KitRowGroup(leadingTiles = true) {
        KitRow(
            title = stringResource(Res.string.chatsHomeAllProjects),
            leading = KitRowLeading.Glyph(KitIcons.folderOpen, current = all),
            supporting = pluralStringResource(Res.plurals.chatsFilterChatCount, state.allCount, state.allCount),
            selected = all,
            trailing = if (all) KitRowTrailing.Check else KitRowTrailing.None,
            onClick = actions::allProjects,
        )
        state.projectChoices.forEach { project ->
            val selected = state.filters.projectDirectory == project.directory
            val parts = buildList {
                add(pluralStringResource(Res.plurals.chatsFilterChatCount, project.chatCount, project.chatCount))
                if (project.runningCount > 0) add(stringResource(Res.string.chatsFilterRunningCount, project.runningCount))
                if (project.directory in state.projectNeedsYou) add(stringResource(Res.string.chatsFilterNeedsYouWord))
            }
            val facts = state.projectFacts[project.directory]
            KitRow(
                title = project.name,
                // F: lib/ui/screens/chats/chats_project_sheet.dart:85-88 (chatsProjectIcon(project.kind))
                leading = KitRowLeading.Glyph(projectKindGlyph(facts?.kind), current = selected),
                supporting = parts.joinToString(" · "),
                supportingMaxLines = 2, // F: lib/ui/screens/chats/chats_project_sheet.dart:94
                selected = selected,
                // F: lib/ui/screens/chats/chats_project_sheet.dart:96-107 (the "Git" pill, then the check)
                trailing =
                    when {
                        facts?.git == true -> KitRowTrailing.Chip(stringResource(Res.string.phoneScanGit), KitIcons.branch, checked = selected)
                        selected -> KitRowTrailing.Check
                        else -> KitRowTrailing.None
                    },
                onClick = { actions.chooseProject(project.directory) },
            )
        }
        if (state.otherFoldersCount > 0) KitRow(
            title = stringResource(Res.string.chatsHomeOtherFolders),
            leading = KitRowLeading.Glyph(KitIcons.folderOpen, current = state.otherFoldersSelected),
            supporting = pluralStringResource(Res.plurals.chatsFilterChatCount, state.otherFoldersCount, state.otherFoldersCount),
            selected = state.otherFoldersSelected,
            trailing = if (state.otherFoldersSelected) KitRowTrailing.Check else KitRowTrailing.None,
            onClick = actions::otherFolders,
        )
    }
    Spacer(Modifier.height(Space.S3.dp))
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.chatsFilterOpenProject),
            titleAccent = true,
            leading = KitRowLeading.Glyph(KitIcons.folderOpen),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openFilterProject,
        )
    }
}

/**
 * F: lib/ui/widgets/server_switcher_sheet.dart:73-95,353-410: every saved server on one panel, the one in use first,
 * each row its name and one line of state in plain words; a tap switches to it. "Add a server" sits on its own quiet
 * panel under them. The in-use row's own menu leads to its settings and the full Servers page.
 */
@Composable
private fun ServerSwitcher(
    state: ChatsUi,
    actions: ChatsActions,
    onAskDisconnect: (io.github.eslamasabry.oc.core.common.ids.ProfileId, String) -> Unit,
) {
    val switcher = state.switcher
    switcher.failure?.let { problem ->
        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
            message = stringResource(Res.string.productErrorComputer),
            failure = problem,
        )
        Spacer(Modifier.height(Space.S3.dp))
    }
    val busy = switcher.switching != null
    KitRowGroup(leadingTiles = true) {
        switcher.servers.forEach { server ->
            val words = buildList {
                add(
                    when (server.state) {
                        SwitcherServerState.Connected -> stringResource(Res.string.serverRowConnected)
                        SwitcherServerState.Connecting -> stringResource(Res.string.e7WorkspaceConnecting)
                        SwitcherServerState.NotAnswering -> stringResource(Res.string.serverSwitcherNotAnswering)
                        SwitcherServerState.Saved ->
                            stringResource(if (server.onThisPhone) Res.string.chatsSourcesOnPhone else Res.string.chatsSourcesElsewhere)
                    }
                )
                if (server.needsYou > 0)
                    add(
                        pluralStringResource(Res.plurals.kitNeedsYouSpan, server.needsYou, Numbers.format(server.needsYou))
                            .trimEnd().removeSuffix("·").trimEnd()
                    )
                if (server.working > 0)
                    add(pluralStringResource(Res.plurals.otherServerWorking, server.working, Numbers.format(server.working)))
            }
            val opening = switcher.switching == server.profile
            KitRow(
                title = server.name,
                leading = KitRowLeading.Glyph(if (server.onThisPhone) KitIcons.phone else KitIcons.server, current = server.current),
                supporting = if (opening) stringResource(Res.string.e7WorkspaceConnecting) else words.joinToString(" · "),
                selected = server.current,
                state =
                    if (server.needsYou > 0 && !opening)
                        io.github.eslamasabry.oc.ui.kit.row.KitRowState(null, io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone.NeedsYou)
                    else null,
                trailing =
                    when {
                        server.current ->
                            KitRowTrailing.Menu(
                                label = stringResource(Res.string.serverSwitcherCurrentMenu),
                                title = server.name,
                                items =
                                    listOf(
                                        io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem(
                                            stringResource(Res.string.serverSettingsTitle),
                                            { actions.serverSettings(server.profile) },
                                            icon = KitIcons.settingsGear,
                                        ),
                                        io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem(
                                            stringResource(Res.string.serversTitle),
                                            actions::openServers,
                                            icon = KitIcons.server,
                                        ),
                                    ) +
                                        // Named for the server it closes, and only while there is a connection to close.
                                        if (server.state == SwitcherServerState.Connected || server.state == SwitcherServerState.Connecting)
                                            listOf(
                                                io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem(
                                                    stringResource(Res.string.serverDisconnectFrom, server.name),
                                                    { onAskDisconnect(server.profile, server.name) },
                                                    icon = KitIcons.unlink,
                                                )
                                            )
                                        else emptyList(),
                            )
                        server.state == SwitcherServerState.NotAnswering && !busy ->
                            KitRowTrailing.Action(
                                io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction(
                                    stringResource(Res.string.kitTryAgain),
                                    { actions.retrySource(server.profile) },
                                )
                            )
                        else -> KitRowTrailing.None
                    },
                enabled = server.current || !busy,
                // A disabled row's reason replaces its supporting line (B-KIT-051), so the server being opened says Connecting here.
                disabledReason =
                    when {
                        opening && !server.current -> stringResource(Res.string.e7WorkspaceConnecting)
                        !server.current && busy -> stringResource(Res.string.serversWorking)
                        else -> null
                    },
                onClick = { actions.switchServer(server.profile) },
            )
        }
    }
    Spacer(Modifier.height(Space.S4.dp))
    KitRowGroup(leadingTiles = true) {
        KitRow(
            title = stringResource(Res.string.serverSwitcherAddServer),
            leading = KitRowLeading.Glyph(KitIcons.add),
            onClick = actions::addServer,
        )
    }
}

@Composable
private fun SourceChoices(state: ChatsUi, actions: ChatsActions) {
    state.home.failure?.let { problem ->
        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
            message = stringResource(Res.string.homeSourcesFailed),
            failure = problem,
            detailsLabel = stringResource(Res.string.kitDetails),
        )
        Spacer(Modifier.height(Space.S3.dp))
    }
    KitRowGroup {
        state.home.sources.forEach { source ->
            KitSwitchRow(
                title = source.name,
                leading = KitRowLeading.Glyph(if (source.onThisPhone) KitIcons.phone else KitIcons.server),
                checked = source.shown,
                locked = source.main,
                lockedLabel = if (source.main) stringResource(Res.string.chatsSourcesAlways) else null,
                below = if (source.unreachable && source.shown) ({
                    KitButton(stringResource(Res.string.refreshRetry), { actions.retrySource(source.profile) }, role = KitButtonRole.Tertiary)
                }) else null,
                supporting =
                    when {
                        source.main -> stringResource(Res.string.chatsSourcesMain)
                        source.unreachable -> stringResource(Res.string.chatsSourcesUnreachable)
                        source.onThisPhone -> stringResource(Res.string.chatsSourcesOnPhone)
                        else -> stringResource(Res.string.chatsSourcesElsewhere)
                    },
                onCheckedChange = { actions.showSource(source.profile, it) },
            )
        }
    }
    KitButton(
        stringResource(Res.string.homeWatchedServers),
        actions::showMonitoring,
        role = KitButtonRole.Tertiary,
    )
}

@Composable
private fun MonitoringSettings(state: ChatsUi, actions: ChatsActions) {
    io.github.eslamasabry.oc.ui.kit.KitText(
        stringResource(Res.string.monitorDisclosure),
        io.github.eslamasabry.oc.ui.kit.KitTextRole.Supporting,
    )
    io.github.eslamasabry.oc.ui.kit.KitText(
        stringResource(Res.string.monitorScope),
        io.github.eslamasabry.oc.ui.kit.KitTextRole.Supporting,
    )
    state.home.monitoring.failure?.let { problem ->
        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
            message = stringResource(Res.string.monitorSaveFailed),
            failure = problem,
            detailsLabel = stringResource(Res.string.kitDetails),
        )
        Spacer(Modifier.height(Space.S3.dp))
    }
    KitRowGroup {
        state.home.monitoring.sources.forEach { source ->
            KitSwitchRow(
                title = source.name,
                checked = source.enabled,
                supporting =
                    stringResource(
                        when (source.status) {
                            ChatsMonitorStatus.Off -> Res.string.homeMonitorOff
                            ChatsMonitorStatus.Waiting -> Res.string.homeMonitorWaiting
                            ChatsMonitorStatus.Checking -> Res.string.homeMonitorChecking
                            ChatsMonitorStatus.Current -> Res.string.homeMonitorCurrent
                            ChatsMonitorStatus.Unavailable -> Res.string.homeMonitorUnavailable
                            ChatsMonitorStatus.WifiRequired -> Res.string.homeMonitorWifi
                            ChatsMonitorStatus.Paused -> Res.string.homeMonitorPaused
                        }
                    ),
                onCheckedChange = { actions.setWatched(source.profile, it) },
            )
        }
    }
    KitButton(
        stringResource(Res.string.homeRefreshWatched),
        actions::refreshWatched,
        role = KitButtonRole.Tertiary,
        working = state.home.monitoring.refreshing,
    )
}

@Composable
internal fun OpeningStatus(opening: ChatsOpeningUi, actions: ChatsActions, blocked: Boolean = false) {
    // Connecting and starting live in the server pill; only a failure needs its own card.
    var askRestart by remember { mutableStateOf(false) }
    // The cause decides the words and the lead action (Flutter ConnectionFailure.diagnose). This phone's own server
    // keeps its own words; only a sign-in or certificate problem there is told the same way.
    val failure = opening.failure
    val diagnosis =
        if (opening.phoneOff || opening.phase != ChatsOpeningPhase.Failed) null
        else if (
            opening.onThisPhone &&
                failure !is io.github.eslamasabry.oc.core.common.Failure.SignIn &&
                failure !is io.github.eslamasabry.oc.core.common.Failure.Certificate
        ) null
        else ConnectionDiagnosis.of(failure, opening.address, opening.usesToken, opening.attempts)
    val words = diagnosis?.let { diagnosisWords(it) }
    val retry = KitAction(stringResource(Res.string.kitTryAgain), actions::retryOpening)
    val change = KitAction(stringResource(Res.string.openingChangeServer), actions::openServers)
    val lead = diagnosis?.lead
    val leadAction =
        when (lead) {
            DiagnosisAction.UpdatePassword ->
                KitAction(stringResource(Res.string.e7BannerUpdatePassword), actions::editOpeningServer)
            DiagnosisAction.UpdateToken ->
                KitAction(stringResource(Res.string.updateConnectionToken), actions::editOpeningServer)
            DiagnosisAction.ReviewCertificate ->
                KitAction(stringResource(Res.string.serverTrustCertificateRecheck), actions::editOpeningServer)
            DiagnosisAction.ChangeServer -> change
            DiagnosisAction.Retry, null -> retry
        }
    KitStateView(
        kind = KitStateKind.Error,
        title =
            when {
                opening.phoneOff -> stringResource(Res.string.homePhoneOffTitle)
                words != null -> words.title
                else -> stringResource(Res.string.homeOpeningFailed, opening.serverName)
            },
        // After a tap on New conversation the card also says that nothing was started, so there is one card, not two.
        body =
            when {
                opening.phoneOff -> stringResource(Res.string.thisPhoneAnswerOffLine)
                blocked -> stringResource(Res.string.homeNewBlockedBody, opening.serverName)
                words != null -> words.body
                else -> stringResource(Res.string.homeOpeningFailedBody)
            },
        failure = if (opening.phoneOff) null else opening.failure,
        primary =
            if (opening.phase == ChatsOpeningPhase.Failed)
                if (opening.phoneOff) KitAction(stringResource(Res.string.homePhoneOffStart), actions::startOpeningPhoneServer)
                else leadAction
            else null,
        // The way to try again stays one tap away when something else leads (a sign-in is fixed where it is typed).
        secondary =
            if (opening.phase == ChatsOpeningPhase.Failed && !opening.phoneOff && diagnosis != null && lead != DiagnosisAction.Retry)
                when (diagnosis.primary) {
                    DiagnosisAction.Retry,
                    DiagnosisAction.UpdatePassword,
                    DiagnosisAction.UpdateToken,
                    DiagnosisAction.ReviewCertificate -> retry
                    DiagnosisAction.ChangeServer -> null
                }
            else null,
        tertiary =
            if (opening.phase == ChatsOpeningPhase.Failed)
                listOfNotNull(
                    // A Tailscale address that did not answer: Tailscale being off is the likely cause.
                    if (diagnosis?.tailnet == true)
                        KitAction(stringResource(Res.string.kitCapNetworkTailscaleEnable), actions::openTailscale)
                    else null,
                    // This phone's own OpenCode can be restarted from here (Flutter root-connecting Restart).
                    if (opening.onThisPhone && !opening.phoneOff)
                        KitAction(stringResource(Res.string.connectionRestartPhoneServer), { askRestart = true })
                    else null,
                    if (lead != DiagnosisAction.ChangeServer) change else null,
                )
            else emptyList(),
    )
    KitConfirm(
        spec =
            if (askRestart)
                KitConfirmSpec(
                    title = stringResource(Res.string.workServerRestartTitle),
                    confirmLabel = stringResource(Res.string.workServerRestart),
                    kind = KitConfirmKind.Stop,
                    icon = KitIcons.restart,
                    body = stringResource(Res.string.workServerRestartBody),
                )
            else null,
        onConfirm = {
            askRestart = false
            actions.restartOpeningPhoneServer()
        },
        onCancel = { askRestart = false },
    )
}

/**
 * New conversation was tapped while its server is still opening (no failure card is on screen): says so. Once the
 * server has failed, the same words live in the opening card, so there is one card.
 */
@Composable
internal fun NewConversationBlocked(opening: ChatsOpeningUi, actions: ChatsActions) {
    KitNotice(
        message = stringResource(Res.string.homeNewBlockedOpening, opening.serverName),
        tone = StatusTone.Neutral,
        dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismissNewBlocked),
    )
}

/** The glyph of a project's kind (Dart `phoneProjectKindIcon`, lib/ui/widgets/phone_project_kind.dart): the plain open folder when unknown. */
internal fun projectKindGlyph(kind: HomeProjectKind?): ImageVector =
    when (kind) {
        HomeProjectKind.Dart -> KitIcons.layers
        HomeProjectKind.Node -> KitIcons.packageBox
        HomeProjectKind.Python -> KitIcons.dataObject
        HomeProjectKind.Rust -> KitIcons.processor
        HomeProjectKind.Go -> KitIcons.terminal
        HomeProjectKind.Java -> KitIcons.code
        HomeProjectKind.Ruby -> KitIcons.database
        HomeProjectKind.Php -> KitIcons.globe
        HomeProjectKind.Dotnet -> KitIcons.category
        HomeProjectKind.Cpp -> KitIcons.function
        null -> KitIcons.folderOpen
    }
