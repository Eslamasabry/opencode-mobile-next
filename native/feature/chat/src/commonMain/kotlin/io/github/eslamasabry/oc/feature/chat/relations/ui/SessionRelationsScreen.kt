package io.github.eslamasabry.oc.feature.chat.relations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
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
                title = stringResource(Res.string.r40RelationsTitle),
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
                            )
                        ),
            )
        },
        width = KitScreenWidth.List,
    ) {
        LazyColumn(
            Modifier.testTag("session-relations-list"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.stale) {
                item("stale") { KitNotice(message = stringResource(Res.string.r40RelationsStale)) }
            } else {
                state.failure?.let { failure ->
                    if (state.menu == null && state.stopping == null)
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
                if (state.loading) item("loading") { KitSkeletonRows() }
            } else {
                item("parent") {
                    KitRowGroup(label = stringResource(Res.string.r40RelationsParent)) {
                        RelationRowView(parent, state, actions)
                    }
                }
                if (state.children.isEmpty())
                    item("empty") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.r40RelationsEmpty),
                            body = stringResource(Res.string.r40RelationsEmptyBody),
                        )
                    }
                else
                    item("children") {
                        KitRowGroup(
                            label =
                                stringResource(
                                    Res.string.r40RelationsChildren,
                                    Numbers.format(state.children.size),
                                )
                        ) {
                            state.children.forEach { RelationRowView(it, state, actions) }
                        }
                    }
            }
        }
        RelationMenu(state, actions)
        StopSheet(state, actions)
        ResumeSheet(state, actions)
        KitClipboardEffect(state.clip?.id, state.clip?.text, actions::clipDone)
    }
}

@Composable
private fun title(row: RelationRow): String =
    row.title ?: stringResource(Res.string.r40RelationsUntitled)

@Composable
private fun RelationRowView(
    row: RelationRow,
    state: SessionRelationsUi,
    actions: SessionRelationsActions,
) {
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
            RelationStatus.NeedsYou -> stringResource(Res.string.r40RelationsNeedsYou)
            RelationStatus.Working -> stringResource(Res.string.r40RelationsWorking)
            RelationStatus.Idle -> stringResource(Res.string.r40RelationsIdle)
            RelationStatus.Done -> stringResource(Res.string.r40RelationsDone)
        }
    KitRow(
        title = title(row),
        modifier = Modifier.testTag("session-relation-${row.id.raw}"),
        supporting =
            listOfNotNull(
                    status,
                    ageWords,
                    if (row.current) stringResource(Res.string.r40RelationsCurrent) else null,
                    if (row.pinned) stringResource(Res.string.r40RelationsPinned) else null,
                )
                .joinToString(" · "),
        selected = row.current,
        enabled = !state.stale && !state.working,
        disabledReason =
            if (state.stale) stringResource(Res.string.r40RelationsStale)
            else if (state.working) stringResource(Res.string.r40RelationsOpening) else null,
        supportingMaxLines = 2,
        trailing =
            KitRowTrailing.Action(
                KitLineAction(
                    stringResource(Res.string.r40RelationsActions),
                    { actions.showMenu(row.id) },
                )
            ),
        onClick = if (row.current) null else ({ actions.open(row.id) }),
    )
}

@Composable
private fun RelationMenu(state: SessionRelationsUi, actions: SessionRelationsActions) {
    val row = state.menu
    KitSheet(
        visible = row != null,
        onDismissRequest = actions::dismissMenu,
        title = row?.let { title(it) } ?: stringResource(Res.string.r40RelationsActions),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        if (row != null) {
            state.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.r40RelationsFailure),
                    failure = it,
                )
            }
            KitRowGroup {
                if (!row.current)
                    KitRow(
                        title = stringResource(Res.string.r40RelationsOpen),
                        onClick = { actions.open(row.id) },
                    )
                if (state.canResume)
                    KitRow(
                        title = stringResource(Res.string.r40RelationsResume),
                        onClick = actions::showResume,
                    )
                if (state.canPin)
                    KitRow(
                        title =
                            stringResource(
                                if (row.pinned) Res.string.r40RelationsUnpin
                                else Res.string.r40RelationsPin
                            ),
                        onClick = actions::togglePin,
                    )
                if (!row.parent && row.status == RelationStatus.Working)
                    KitRow(
                        title = stringResource(Res.string.r40RelationsStop),
                        onClick = actions::requestStop,
                    )
            }
            if (state.working) KitNotice(message = stringResource(Res.string.r40RelationsOpening))
        }
    }
}

@Composable
private fun StopSheet(state: SessionRelationsUi, actions: SessionRelationsActions) {
    val row = state.stopping
    KitSheet(
        visible = row != null,
        onDismissRequest = actions::cancelStop,
        title =
            row?.let { stringResource(Res.string.r40RelationsStopTitle, title(it)) }
                ?: stringResource(Res.string.r40RelationsStop),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        KitText(stringResource(Res.string.r40RelationsStopBody))
        state.failure?.let {
            KitErrorNotice(message = stringResource(Res.string.r40RelationsFailure), failure = it)
        }
        KitActionBlock(
            primary =
                KitAction(
                    stringResource(Res.string.r40RelationsStop),
                    actions::confirmStop,
                    working = state.working,
                    destructive = true,
                ),
            tertiary =
                listOf(
                    KitAction(
                        stringResource(Res.string.r40RelationsKeepRunning),
                        actions::cancelStop,
                    )
                ),
        )
    }
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
