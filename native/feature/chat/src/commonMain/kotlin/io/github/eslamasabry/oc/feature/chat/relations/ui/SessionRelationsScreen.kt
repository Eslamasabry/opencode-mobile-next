package io.github.eslamasabry.oc.feature.chat.relations.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeUnavailable
import io.github.eslamasabry.oc.feature.chat.relations.presentation.RelationRow
import io.github.eslamasabry.oc.feature.chat.relations.presentation.RelationStatus
import io.github.eslamasabry.oc.feature.chat.relations.presentation.SessionRelationsActions
import io.github.eslamasabry.oc.feature.chat.relations.presentation.SessionRelationsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuDismissal
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import org.jetbrains.compose.resources.pluralStringResource
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A single urgency-ordered list, with a visible Actions button on every row; no hidden gesture. */
@Composable
public fun SessionRelationsScreen(state: SessionRelationsUi, actions: SessionRelationsActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.sessionRelationsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    if (state.stale) emptyList()
                    else
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.r40RelationsRefresh),
                                actions::refresh,
                                icon = KitIcons.retry,
                            )
                        ),
            )
        },
        width = KitScreenWidth.List,
    ) {
        val tokens = LocalKitTokens.current
        KitRefresh(refreshing = state.loading && state.parent != null, onRefresh = actions::refresh, enabled = !state.stale) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("session-relations-list"),
            contentPadding = PaddingValues(top = Space.S2.dp),
        ) {
            if (state.stale) {
                item("stale") { KitNotice(message = stringResource(Res.string.r40RelationsStale)) }
            } else {
                state.failure?.takeIf { state.parent != null }?.let { failure ->
                    if (state.stopping == null)
                        item("failure") {
                            KitErrorNotice(
                                message = stringResource(Res.string.r40RelationsFailure),
                                failure = failure,
                                retry =
                                    KitLineAction(
                                        stringResource(Res.string.kitTryAgain),
                                        actions::refresh,
                                    ),
                            )
                        }
                }
            }
            val parent = state.parent
            if (parent == null) {
                item("loading-or-failed") {
                    if (state.loading) KitStateView(KitStateKind.Loading, stringResource(Res.string.sessionRelationsLoading), icon = KitIcons.nested)
                    else KitStateView(KitStateKind.Error, stringResource(Res.string.sessionRelationsFailedTitle), failure = state.failure, onRetry = actions::refresh)
                }
            } else {
                item("parent") {
                    KitRowGroup(label = stringResource(Res.string.sessionRelationsStartedFrom)) {
                        RelationRowView(parent, state, actions)
                    }
                }
                item("section-gap") { Spacer(Modifier.height(tokens.sectionGap.dp)) }
                if (state.children.isEmpty())
                    item("empty") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.r40RelationsEmpty),
                            body = stringResource(Res.string.r40RelationsEmptyBody),
                            icon = KitIcons.nested,
                        )
                    }
                else
                    item("children") {
                        KitRowGroup(
                            label =
                                pluralStringResource(Res.plurals.sessionRelationsSubagentCount, state.children.size, state.children.size)
                        ) {
                            state.children.forEach { RelationRowView(it, state, actions) }
                        }
                    }
            }
        }
        }
        StopSheet(state, actions)
        ResumeSheet(state, actions)
        KitClipboardEffect(state.clip?.id, state.clip?.text, actions::clipDone)
    }
}

@Composable
private fun title(row: RelationRow): String =
    row.title ?: stringResource(if (row.parent) Res.string.e7SharedParentSession else Res.string.r40RelationsUntitled)

@Composable
private fun RelationRowView(
    row: RelationRow,
    state: SessionRelationsUi,
    actions: SessionRelationsActions,
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    val rowTitle = title(row)
    val age = row.ageMinutes
    val ageWords =
        when {
            age == null -> null
            age < 1 -> stringResource(Res.string.r40RelationsJustNow)
            age < 60 -> stringResource(Res.string.r40RelationsMinutes, Numbers.format(age))
            age < 1440 -> stringResource(Res.string.r40RelationsHours, Numbers.format(age / 60))
            else -> stringResource(Res.string.r40RelationsDays, Numbers.format(age / 1440))
        }
    val status =
        when (row.status) {
            RelationStatus.NeedsYou -> stringResource(Res.string.kitTaskNeedsYou)
            RelationStatus.Working -> stringResource(Res.string.r40RelationsWorking)
            RelationStatus.Idle, RelationStatus.Done -> stringResource(if (row.parent) Res.string.sessionRelationsIdle else Res.string.r40RelationsDone)
        }
    val menuLabel = stringResource(Res.string.sessionRelationsRowMenu, rowTitle)
    val actionEnabled = when {
        state.stale -> KitEnabled.Disabled(stringResource(Res.string.r40RelationsStale))
        state.working -> KitEnabled.Disabled(stringResource(Res.string.sessionRelationsOpening))
        else -> KitEnabled.Enabled
    }
    // The model's menu is the reviewed target. Hiding the popup must not clear it before an intent reads it.
    fun select(action: () -> Unit) { menuOpen = false; action() }
    val items = buildList {
        if (!row.current) add(KitMenuItem(stringResource(Res.string.sessionRelationsOpen, rowTitle), { select { actions.open(row.id) } }, icon = KitIcons.chat, enabled = actionEnabled, dismissal = KitMenuDismissal.ByAction))
        if (state.canResume) add(KitMenuItem(stringResource(Res.string.sessionRelationsCopyHandoff, rowTitle), { select(actions::showResume) }, icon = KitIcons.copy, enabled = actionEnabled, dismissal = KitMenuDismissal.ByAction))
        if (state.canPin) add(KitMenuItem(stringResource(if (row.pinned) Res.string.sessionRelationsUnpin else Res.string.sessionRelationsPin, rowTitle), { select(actions::togglePin) }, icon = KitIcons.pin, enabled = actionEnabled, dismissal = KitMenuDismissal.ByAction))
        if (!row.parent && row.status == RelationStatus.Working) add(KitMenuItem(stringResource(Res.string.sessionRelationsStop, rowTitle), { select(actions::requestStop) }, icon = KitIcons.stopCircle, destructive = true, enabled = actionEnabled, dismissal = KitMenuDismissal.ByAction))
    }
    KitMenu(
        expanded = menuOpen && state.menu?.id == row.id && !state.stale,
        items = items,
        title = menuLabel,
        onDismissRequest = { menuOpen = false; actions.dismissMenu() },
        anchor = {
            KitRow(
                title = rowTitle,
                titleMaxLines = 2, // F: lib/ui/screens/session_relations_screen.dart:466
                modifier = Modifier.testTag("session-relation-${row.id.raw}"),
                leading = when {
                    row.status == RelationStatus.NeedsYou || row.status == RelationStatus.Working -> null
                    row.parent -> KitRowLeading.Glyph(KitIcons.chat)
                    else -> KitRowLeading.Glyph(KitIcons.check)
                },
                state = KitRowState(status, when (row.status) {
                    RelationStatus.NeedsYou -> KitRowStateTone.NeedsYou
                    RelationStatus.Working -> KitRowStateTone.Running
                    else -> KitRowStateTone.Quiet
                }),
                supporting = if (row.status == RelationStatus.NeedsYou) stringResource(Res.string.sessionRelationsOpenToAnswer)
                    else listOfNotNull(ageWords, if (row.current) stringResource(Res.string.sessionRelationsThisConversation) else null).joinToString(" · ").takeIf { it.isNotBlank() },
                selected = row.current,
                enabled = !state.stale && !state.working,
                disabledReason =
                    if (state.stale) stringResource(Res.string.r40RelationsStale)
                    else if (state.working) stringResource(Res.string.sessionRelationsOpening) else null,
                trailing =
                    KitRowTrailing.Action(
                        KitLineAction(
                            stringResource(Res.string.r40RelationsActions),
                            { menuOpen = true; actions.showMenu(row.id) },
                            semanticsLabel = menuLabel,
                        )
                    ),
                onClick = if (row.current) null else ({ actions.open(row.id) }),
            )
        },
    )
}

@Composable
private fun StopSheet(state: SessionRelationsUi, actions: SessionRelationsActions) {
    KitConfirm(
        spec = state.stopping?.let { row ->
            KitConfirmSpec(
                title = stringResource(Res.string.sessionRelationsStopTitle, title(row)),
                body = stringResource(Res.string.sessionRelationsStopBody),
                confirmLabel = stringResource(Res.string.sessionRelationsStopConfirm),
                kind = KitConfirmKind.Stop,
                icon = KitIcons.stopCircle,
            )
        },
        onConfirm = actions::confirmStop,
        onCancel = actions::cancelStop,
        working = state.working,
        failed = state.failure != null,
    )
}

@Composable
private fun ResumeSheet(state: SessionRelationsUi, actions: SessionRelationsActions) {
    val resume = state.resume
    KitSheet(
        visible = resume != null,
        onDismissRequest = actions::dismissResume,
        title = stringResource(Res.string.r40RelationsResume),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        val command = resume?.command
        if (command != null) {
            KitNotice(message = stringResource(Res.string.r40RelationsResumeBody))
            KitCodeBlock(
                source = command,
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.r40RelationsCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = { actions.copyResume() },
            )
        } else {
            KitNotice(
                message =
                    stringResource(
                        if (resume?.unavailable == SessionResumeUnavailable.ManagedWorkspace)
                            Res.string.handoffUiUnavailableWorkspace
                        else Res.string.handoffUiUnavailableDirectory
                    )
            )
        }
    }
}
