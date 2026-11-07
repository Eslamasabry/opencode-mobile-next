package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
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
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
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
        label = when {
            state.otherFoldersSelected -> stringResource(Res.string.chatsHomeOtherFolders)
            state.filters.projectDirectory == null -> stringResource(Res.string.chatsHomeAllProjects)
            else -> state.projectChoices.firstOrNull { it.directory == state.filters.projectDirectory }?.name
                ?: state.filters.projectDirectory!!.trimEnd('/').substringAfterLast('/')
        },
        kind = KitChipKind.Action,
        selected = state.filters.projectDirectory != null || state.otherFoldersSelected,
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
    KitSheet(
        visible = sheet != null && sheet != ChatsHomeSheet.OpenProject && sheet != ChatsHomeSheet.Inbox,
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
        // KitSheet scrolls its body; a second vertical scroll here measured with infinite height (N-38).
        Column {
            when (sheet) {
                ChatsHomeSheet.Projects -> ProjectChoices(state, actions)
                ChatsHomeSheet.Sources -> SourceChoices(state, actions)
                ChatsHomeSheet.Inbox -> Unit
                ChatsHomeSheet.Monitoring -> MonitoringSettings(state, actions)
                ChatsHomeSheet.OpenProject,
                null -> Unit
            }
        }
    }
    KitPageOverlay(sheet == ChatsHomeSheet.Inbox, stringResource(Res.string.inboxTitle), actions::closeHomeSheet) {
        ChatsInboxScreen(state, actions)
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
            KitRow(
                title = project.name,
                leading = KitRowLeading.Glyph(KitIcons.folderOpen, current = selected),
                supporting = parts.joinToString(" · "),
                supportingMaxLines = 2, // F: lib/ui/screens/chats/chats_project_sheet.dart:94
                selected = selected,
                trailing = if (selected) KitRowTrailing.Check else KitRowTrailing.None,
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
internal fun OpeningStatus(opening: ChatsOpeningUi, actions: ChatsActions) {
    // E20-S08: while the server opens, the saved titles are a list on the gutter under it; a centred page state
    // over that list read as two layouts. The words start where the rows do.
    // E20-S09: the words speak of saved titles only when there are saved titles to show under them.
    val saved = opening.rows.isNotEmpty()
    if (opening.phase != ChatsOpeningPhase.Failed) {
        KitNotice(
            message = stringResource(if (saved) Res.string.homeOpeningSavedTitles else Res.string.homeOpeningWaiting),
            modifier =
                Modifier.padding(horizontal = io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens.current.gutter.dp)
                    .testTag("home-opening-connecting"),
            // E05-S20: a phone server the app just started is starting, not failing.
            title =
                if (opening.phase == ChatsOpeningPhase.Starting) stringResource(Res.string.homeOpeningStarting)
                else stringResource(Res.string.homeOpeningConnecting, opening.serverName),
            tone = io.github.eslamasabry.oc.ui.design.color.StatusTone.Progress,
        )
        return
    }
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
        body = stringResource(if (saved) Res.string.homeOpeningSavedTitles else Res.string.homeOpeningFailedBody),
        failure = opening.failure,
        primary =
            if (opening.phase == ChatsOpeningPhase.Failed)
                KitAction(stringResource(Res.string.kitTryAgain), actions::retryOpening)
            else null,
        // A rejected or missing sign-in is fixed where it is typed; any failure can switch server.
        secondary =
            if (opening.phase == ChatsOpeningPhase.Failed) {
                val why = (opening.failure as? io.github.eslamasabry.oc.core.common.Failure.SignIn)?.why
                when {
                    opening.profile != null && why != null ->
                        KitAction(
                            stringResource(
                                when (why) {
                                    io.github.eslamasabry.oc.core.common.SignInWhy.TokenMissing,
                                    io.github.eslamasabry.oc.core.common.SignInWhy.TokenRejected ->
                                        Res.string.updateConnectionToken
                                    else -> Res.string.e7BannerUpdatePassword
                                }
                            ),
                            actions::editOpeningServer,
                        )
                    else -> null
                }
            } else null,
        tertiary =
            if (opening.phase == ChatsOpeningPhase.Failed)
                listOf(KitAction(stringResource(Res.string.openingChangeServer), actions::openServers))
            else emptyList(),
    )
}

@Composable
internal fun OpeningRow(row: ChatsOpeningRow) {
    // A saved title reads as a conversation (the glyph All conversations uses), quiet until its server answers.
    KitRow(title = title(row.title), leading = KitRowLeading.Glyph(KitIcons.chat))
}

@Composable
private fun title(value: RowTitle): String = rowTitleWords(value)
