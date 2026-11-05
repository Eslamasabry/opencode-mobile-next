package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsMonitorStatus
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningRow
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsWatchedKind
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeFilterChips(state: ChatsUi, actions: ChatsActions) {
    KitChip(
        label = stringResource(Res.string.chatsFilterSheetTitle),
        kind = KitChipKind.Action,
        selected = state.filters.projectDirectory != null || state.otherFoldersSelected,
        opensMenu = true,
        onClick = actions::showProjectChoices,
    )
    if (state.home.sources.size > 1) {
        KitChip(
            label = stringResource(Res.string.chatsSourcesSheetTitle),
            kind = KitChipKind.Action,
            onClick = actions::showSources,
        )
    }
    val inboxCount = state.inbox.size + state.home.monitoring.requests.size
    KitChip(
        label =
            if (inboxCount > 0)
                stringResource(Res.string.homeInboxCount, Numbers.format(inboxCount))
            else stringResource(Res.string.inboxTitle),
        kind = KitChipKind.Action,
        onClick = actions::showInbox,
    )
}

@Composable
internal fun ChatsHomeSheets(state: ChatsUi, actions: ChatsActions) {
    val sheet = state.sheet
    KitSheet(
        visible = sheet != null && sheet != ChatsHomeSheet.OpenProject,
        onDismissRequest = actions::closeHomeSheet,
        title =
            when (sheet) {
                ChatsHomeSheet.Projects -> stringResource(Res.string.chatsFilterSheetTitle)
                ChatsHomeSheet.Sources -> stringResource(Res.string.chatsSourcesSheetTitle)
                ChatsHomeSheet.Monitoring -> stringResource(Res.string.homeWatchedServers)
                ChatsHomeSheet.Inbox,
                ChatsHomeSheet.OpenProject,
                null -> stringResource(Res.string.inboxTitle)
            },
        closeLabel = stringResource(Res.string.kitTopBarClose),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (sheet) {
                ChatsHomeSheet.Projects -> ProjectChoices(state, actions)
                ChatsHomeSheet.Sources -> SourceChoices(state, actions)
                ChatsHomeSheet.Inbox -> InboxRows(state, actions)
                ChatsHomeSheet.Monitoring -> MonitoringSettings(state, actions)
                ChatsHomeSheet.OpenProject,
                null -> Unit
            }
        }
    }
    val notAbsolute = stringResource(Res.string.p12PathNotAbsolute)
    val badParts = stringResource(Res.string.p12PathBadParts)
    KitInputDialog(
        visible = sheet == ChatsHomeSheet.OpenProject,
        title = stringResource(Res.string.chatsFilterOpenProject),
        fieldLabel = stringResource(Res.string.projectFolderPathLabel),
        confirmLabel = stringResource(Res.string.projectFolderOpenAction),
        initial = state.filters.projectDirectory.orEmpty(),
        kind = KitFieldKind.Path,
        operationKey = "filter-project",
        validate = { path ->
            when {
                !path.trim().startsWith('/') -> notAbsolute
                path.trim().split('/').any { it == ".." || it == "." } ||
                    path.any { it.code <= 0x1f || it.code == 0x7f } -> badParts
                else -> null
            }
        },
        onResult = actions::finishFilterProject,
    )
    KitConfirm(
        spec =
            state.confirmWatched?.let { watched ->
                KitConfirmSpec(
                    title = stringResource(Res.string.monitorSwitchToTitle, watched.sourceName),
                    body = stringResource(Res.string.homeWatchedOpenBody),
                    confirmLabel = stringResource(Res.string.monitorSwitchTo, watched.sourceName),
                )
            },
        onConfirm = actions::confirmWatched,
        onCancel = actions::dismissWatched,
    )
}

@Composable
private fun ProjectChoices(state: ChatsUi, actions: ChatsActions) {
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.chatsHomeAllProjects),
            supporting =
                pluralStringResource(
                    Res.plurals.chatsFilterChatCount,
                    state.allCount,
                    state.allCount,
                ),
            selected = state.filters.projectDirectory == null && !state.otherFoldersSelected,
            onClick = actions::allProjects,
        )
        state.projectChoices.forEach { project ->
            val parts = buildList {
                add(
                    pluralStringResource(
                        Res.plurals.chatsFilterChatCount,
                        project.chatCount,
                        project.chatCount,
                    )
                )
                if (project.runningCount > 0)
                    add(stringResource(Res.string.chatsFilterRunningCount, project.runningCount))
                if (project.directory in state.projectNeedsYou)
                    add(stringResource(Res.string.chatsFilterNeedsYouWord))
            }
            KitRow(
                title = project.name,
                supporting = parts.joinToString(" · "),
                selected = state.filters.projectDirectory == project.directory,
                onClick = { actions.chooseProject(project.directory) },
            )
        }
        if (state.otherFoldersCount > 0) {
            KitRow(
                title = stringResource(Res.string.chatsHomeOtherFolders),
                supporting =
                    pluralStringResource(
                        Res.plurals.chatsFilterChatCount,
                        state.otherFoldersCount,
                        state.otherFoldersCount,
                    ),
                selected = state.otherFoldersSelected,
                onClick = actions::otherFolders,
            )
        }
        KitRow(
            title = stringResource(Res.string.chatsFilterOpenProject),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openFilterProject,
        )
    }
}

@Composable
private fun SourceChoices(state: ChatsUi, actions: ChatsActions) {
    state.home.failure?.let { problem ->
        KitStateView(
            kind = KitStateKind.Error,
            title = stringResource(Res.string.homeSourcesFailed),
            failure = problem,
        )
    }
    KitRowGroup {
        state.home.sources.forEach { source ->
            KitSwitchRow(
                title = source.name,
                checked = source.shown,
                locked = source.main,
                supporting =
                    when {
                        source.main ->
                            stringResource(Res.string.chatsSourcesMain) +
                                " · " +
                                stringResource(Res.string.chatsSourcesAlways)
                        source.unreachable -> stringResource(Res.string.chatsSourcesUnreachable)
                        source.onThisPhone -> stringResource(Res.string.chatsSourcesOnPhone)
                        else -> stringResource(Res.string.chatsSourcesElsewhere)
                    },
                onCheckedChange = { actions.showSource(source.profile, it) },
            )
            if (source.unreachable && source.shown) {
                KitButton(
                    stringResource(Res.string.kitTryAgain),
                    { actions.retrySource(source.profile) },
                    role = KitButtonRole.Tertiary,
                )
            }
        }
    }
    KitButton(
        stringResource(Res.string.homeWatchedServers),
        actions::showMonitoring,
        role = KitButtonRole.Tertiary,
    )
}

@Composable
private fun InboxRows(state: ChatsUi, actions: ChatsActions) {
    if (state.inbox.isEmpty() && state.home.monitoring.requests.isEmpty()) {
        KitStateView(kind = KitStateKind.Calm, title = stringResource(Res.string.inboxEmptyTitle))
        if (state.finished.isNotEmpty()) {
            KitRowGroup(label = stringResource(Res.string.inboxFinishedLabel)) {
                state.finished.forEach { InboxRow(it, actions) }
            }
        }
    } else
        KitRowGroup {
            state.inbox.forEach { InboxRow(it, actions) }
            state.home.monitoring.requests.forEach { watched ->
                KitRow(
                    title = title(watched.title),
                    supporting =
                        when (watched.kind) {
                            ChatsWatchedKind.Permission ->
                                stringResource(Res.string.inboxAsksPermission)
                            ChatsWatchedKind.Question,
                            ChatsWatchedKind.Form -> stringResource(Res.string.inboxAsksQuestion)
                        },
                    server = watched.sourceName,
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.openWatched(watched.key) },
                )
            }
        }
    state.home.monitoring.failure?.let { problem ->
        KitStateView(
            kind = KitStateKind.Error,
            title = stringResource(Res.string.homeWatchedOpenFailed),
            failure = problem,
        )
    }
    KitButton(
        stringResource(Res.string.homeRefreshWatched),
        actions::refreshWatched,
        role = KitButtonRole.Tertiary,
        working = state.home.monitoring.refreshing,
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
        KitStateView(
            kind = KitStateKind.Error,
            title = stringResource(Res.string.monitorSaveFailed),
            failure = problem,
        )
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
private fun InboxRow(row: ConversationRowUi, actions: ChatsActions) {
    KitRow(
        title = title(row.title),
        supporting = row.project,
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openConversation(row.ref) },
    )
}

@Composable
internal fun OpeningStatus(opening: ChatsOpeningUi, actions: ChatsActions) {
    KitStateView(
        kind =
            if (opening.phase == ChatsOpeningPhase.Failed) KitStateKind.Error
            else KitStateKind.Calm,
        title =
            stringResource(
                if (opening.phase == ChatsOpeningPhase.Failed) Res.string.homeOpeningFailed
                else Res.string.homeOpeningConnecting,
                opening.serverName,
            ),
        body = stringResource(Res.string.homeOpeningSavedTitles),
        failure = opening.failure,
        primary =
            if (opening.phase == ChatsOpeningPhase.Failed)
                KitAction(stringResource(Res.string.kitTryAgain), actions::retryOpening)
            else null,
    )
}

@Composable
internal fun OpeningRow(row: ChatsOpeningRow) {
    KitRow(title = title(row.title))
}

@Composable
private fun title(value: RowTitle): String =
    when (value) {
        is RowTitle.Text -> value.text
        RowTitle.New,
        RowTitle.Untitled -> stringResource(Res.string.workspaceNewSession)
    }
