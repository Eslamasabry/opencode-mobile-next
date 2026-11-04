package io.github.eslamasabry.oc.feature.chats.attention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticUndoResult
import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Needs you and confirmed automatic actions remain separate; reading never acknowledges either. */
@Composable
public fun AttentionScreen(state: AttentionUi, actions: AttentionActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.stateAttentionTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.stateAttentionRefresh),
                            actions::refresh,
                        )
                    ),
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (state.loading) item("loading") { KitSkeletonRows() }
            if (state.incomplete)
                item("incomplete") {
                    KitNotice(stringResource(Res.string.stateAttentionIncomplete))
                }
            if (state.corrupt)
                item("corrupt") { KitNotice(stringResource(Res.string.stateAttentionCorrupt)) }
            if (state.saveFailed)
                item("save-failed") {
                    KitNotice(stringResource(Res.string.stateAttentionSaveFailed))
                }
            item("needs-you") { KitSectionLabel(stringResource(Res.string.stateAttentionNeedsYou)) }
            if (!state.loading && state.requests.isEmpty())
                item("no-requests") {
                    KitNotice(
                        stringResource(
                            if (state.incomplete) Res.string.stateAttentionNoneKnown
                            else Res.string.stateAttentionNone
                        )
                    )
                }
            items(state.requests, key = { it.key }) { row ->
                val kind =
                    stringResource(
                        when (row.kind) {
                            AttentionKind.Permission -> Res.string.stateAttentionPermission
                            AttentionKind.Question -> Res.string.stateAttentionQuestion
                            AttentionKind.Form -> Res.string.stateAttentionForm
                            AttentionKind.FailedRun -> Res.string.stateAttentionFailure
                            AttentionKind.TeamGate -> Res.string.stateAttentionTeam
                        }
                    )
                KitRow(
                    title = row.title?.takeIf { it.isNotBlank() } ?: kind,
                    supporting =
                        listOfNotNull(
                                row.server,
                                kind,
                                if (!row.fresh) stringResource(Res.string.stateAttentionStale)
                                else null,
                            )
                            .joinToString(" · "),
                    onClick = row.ref?.let { ref -> { actions.open(ref) } },
                )
            }
            if (state.monitors.isNotEmpty()) {
                item("monitor-title") {
                    KitSectionLabel(stringResource(Res.string.stateAttentionMonitorTitle))
                }
                item("monitor-explanation") {
                    KitNotice(stringResource(Res.string.stateAttentionMonitorCopy))
                }
                items(state.monitors, key = { "monitor-" + it.profile }) { row ->
                    KitChoiceRow(
                        title = row.name,
                        supporting = stringResource(Res.string.stateAttentionMonitorChoice),
                        selected = row.enabled,
                        mark = KitChoiceMark.Check,
                        onClick = { actions.setMonitored(row.profile, !row.enabled) },
                    )
                }
            }
            item("history") { KitSectionLabel(stringResource(Res.string.stateAttentionHistory)) }
            if (!state.loading && state.history.isEmpty())
                item("no-history") { KitNotice(stringResource(Res.string.stateAttentionNoHistory)) }
            items(state.history, key = { it.identity.profile + "/" + it.identity.id }) { row ->
                val kind =
                    stringResource(
                        when (row.kind) {
                            AutomaticActKind.Reconnect -> Res.string.stateActReconnect
                            AutomaticActKind.Restart -> Res.string.stateActRestart
                            AutomaticActKind.HeatPause -> Res.string.stateActHeatPause
                            AutomaticActKind.HeatStop -> Res.string.stateActHeatStop
                            AutomaticActKind.HeatResume -> Res.string.stateActHeatResume
                            AutomaticActKind.Update -> Res.string.stateActUpdate
                            AutomaticActKind.PermissionApproval -> Res.string.stateActPermission
                            AutomaticActKind.QueuedSend -> Res.string.stateActQueued
                            AutomaticActKind.Other -> Res.string.stateActOther
                        }
                    )
                val undoStatus =
                    when {
                        row.undoing -> Res.string.activityUndoing
                        row.undone -> Res.string.activityUndone
                        row.undoResult == AutomaticUndoResult.Unconfirmed ->
                            Res.string.activityUndoUnconfirmed
                        row.undoResult == AutomaticUndoResult.Failed ->
                            Res.string.activityUndoFailed
                        // Only restarts ever carry an inverse; other kinds never offered Undo.
                        row.kind == AutomaticActKind.Restart && !row.undoable ->
                            Res.string.activityUndoUnavailable
                        else -> null
                    }
                KitRow(
                    title = kind,
                    supporting =
                        listOfNotNull(
                                row.target,
                                row.at,
                                undoStatus?.let { stringResource(it) },
                            )
                            .joinToString(" · "),
                    supportingMaxLines = 3,
                    trailing =
                        if (row.undoable || row.undoing) {
                            KitRowTrailing.Action(
                                KitLineAction(
                                    stringResource(Res.string.activityUndoRestart),
                                    { actions.undo(row.identity) },
                                    enabled = !row.undoing,
                                    disabledReason =
                                        if (row.undoing) stringResource(Res.string.activityUndoing)
                                        else null,
                                )
                            )
                        } else KitRowTrailing.None,
                )
            }
            if (state.history.isNotEmpty())
                item("acknowledge") {
                    KitButton(
                        stringResource(Res.string.stateAttentionAcknowledge),
                        actions::acknowledge,
                        role = KitButtonRole.Tertiary,
                    )
                }
        }
    }
}
