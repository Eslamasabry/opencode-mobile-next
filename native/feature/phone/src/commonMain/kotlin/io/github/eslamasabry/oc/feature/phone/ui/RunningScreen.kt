package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.procs.CpuLevel
import io.github.eslamasabry.oc.engine.runtime.procs.DurationWords
import io.github.eslamasabry.oc.engine.runtime.procs.runningCpuWords
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCardKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.RunningUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.childLimitBody
import io.github.eslamasabry.oc.ui.words.res.childLimitHide
import io.github.eslamasabry.oc.ui.words.res.childLimitOpenAbout
import io.github.eslamasabry.oc.ui.words.res.childLimitOpenDeveloper
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsEnable
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsOpen
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsText
import io.github.eslamasabry.oc.ui.words.res.childLimitTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxProcessHeavy
import io.github.eslamasabry.oc.ui.words.res.linuxProcessMembers
import io.github.eslamasabry.oc.ui.words.res.runningDetailHelpers
import io.github.eslamasabry.oc.ui.words.res.runningDetailPid
import io.github.eslamasabry.oc.ui.words.res.runningOrphanCpuWithoutOwner
import io.github.eslamasabry.oc.ui.words.res.runningOrphanParentGone
import io.github.eslamasabry.oc.ui.words.res.runningPhoneEmpty
import io.github.eslamasabry.oc.ui.words.res.runningPhoneEmptyBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLimitBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLimitTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneMarkStuck
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRefreshFailed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneServerRoot
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStop
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopFailed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopNamed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.termuxProcsAutoRefresh
import io.github.eslamasabry.oc.ui.words.res.termuxProcsMemoryMb
import io.github.eslamasabry.oc.ui.words.res.termuxProcsStopping
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

/**
 * PhoneRoute.Running (plan 07 F08): the near-limit notice, the guided child-process-limit card
 * (plan 11 §7.2), then one row per program. Protected rows carry no Stop and say where they are
 * controlled. Kit parts only (K1).
 */
@Composable
public fun RunningScreen(state: RunningUi, actions: RunningActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.runningPhoneTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.workRefresh),
                            actions::retry,
                            icon = KitIcons.retry,
                            enabled = !state.loading && state.stopping == null,
                            disabledReason = stringResource(Res.string.termuxProcsStopping),
                        )
                    ),
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.stopping != null,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S2.dp)
        ) {
            if (state.loading && state.rows.isEmpty()) KitSkeletonRows()
            if (state.loadFailed) {
                KitErrorNotice(
                    title = stringResource(Res.string.runningPhoneLoadFailedTitle),
                    message = stringResource(Res.string.runningPhoneLoadFailedBody),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            if (state.refreshFailed)
                KitNotice(message = stringResource(Res.string.runningPhoneRefreshFailed))
            state.leftover?.let { notice ->
                KitNotice(
                    message = notice.message.words(),
                    tone = StatusTone.Neutral,
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(
                                    Res.string.runningPhoneStopNamed,
                                    notice.stopLabelName,
                                ),
                                { actions.askStop(notice.identity.pid) },
                                enabled = state.stopping == null,
                                disabledReason = stringResource(Res.string.termuxProcsStopping),
                            )
                        ),
                )
            }
            state.status?.let { KitGroupNote(it.words()) }
            val count = state.count
            if (state.nearLimit && count != null) {
                KitNotice(
                    title = stringResource(Res.string.runningPhoneLimitTitle),
                    message = stringResource(Res.string.runningPhoneLimitBody, count, state.cap),
                    tone = StatusTone.Neutral,
                )
            }
            state.childLimit?.let { ChildLimitCard(it, actions) }
            if (state.stopFailed) {
                KitNotice(
                    message = stringResource(Res.string.runningPhoneStopFailed),
                    tone = StatusTone.Failure,
                    dismiss =
                        KitLineAction(
                            stringResource(Res.string.workspaceDismissNotice),
                            actions::dismiss,
                        ),
                )
            }
            if (!state.loading && !state.loadFailed && state.rows.isEmpty()) {
                KitInlineState(
                    title = stringResource(Res.string.runningPhoneEmpty),
                    body = stringResource(Res.string.runningPhoneEmptyBody),
                    icon = KitIcons.processor,
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                )
            }
            if (state.rows.isNotEmpty()) {
                KitRowGroup(
                    leadingTiles = true,
                    note = stringResource(Res.string.termuxProcsAutoRefresh),
                ) {
                    state.rows.forEach {
                        ProcessRow(
                            it,
                            stopping = state.stopping == it.pid,
                            busy = state.stopping != null,
                            actions,
                        )
                    }
                }
            } else if (!state.loadFailed) {
                KitGroupNote(stringResource(Res.string.termuxProcsAutoRefresh))
            }
        }
        val asked = state.confirmStop
        KitConfirm(
            spec =
                asked?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.runningPhoneStopTitle, it.name),
                        confirmLabel = stringResource(Res.string.runningPhoneStopNamed, it.name),
                        kind = KitConfirmKind.Stop,
                        body = stringResource(Res.string.runningPhoneStopBody),
                        consequences =
                            if (it.stopIdentities.size > 1)
                                listOf(
                                    stringResource(
                                        Res.string.runningDetailHelpers,
                                        it.stopIdentities.size - 1,
                                    )
                                )
                            else emptyList(),
                    )
                },
            onConfirm = actions::confirmStop,
            onCancel = actions::dismiss,
        )
        val selected = state.selected
        val copy =
            androidx.compose.runtime.key(selected?.pid) {
                selected?.command?.let { command ->
                    rememberKitCopyAction(
                        stringResource(Res.string.termuxProcsCopyCommand),
                        stringResource(Res.string.kitCopied),
                        { command.text },
                    )
                }
            }
        KitSheet(
            visible = selected != null,
            title = selected?.name ?: stringResource(Res.string.runningPhoneTitle),
            onDismissRequest = actions::dismiss,
            subtitle = selected?.kind?.words(),
            icon = selected?.let { processGlyph(it) },
            tertiary = listOfNotNull(copy),
            secondary =
                selected
                    ?.takeIf { it.stoppable }
                    ?.let { row ->
                        KitAction(
                            stringResource(Res.string.runningPhoneStopNamed, row.name),
                            { actions.askStop(row.pid) },
                            icon = KitIcons.stop,
                            destructive = true,
                            enabled =
                                KitEnabled.of(
                                    state.stopping == null,
                                    stringResource(Res.string.termuxProcsStopping),
                                ),
                        )
                    },
        ) {
            selected?.let { row ->
                KitText(
                    stringResource(
                        when {
                            row.leftBehind -> Res.string.runningPhoneAbout__leftBehind
                            row.protectedAs == ProcessRole.App -> Res.string.runningPhoneAbout__app
                            row.kind == ProcessKind.OpenCode ->
                                Res.string.runningPhoneAbout__openCode
                            row.kind == ProcessKind.ClaudeCode ->
                                Res.string.runningPhoneAbout__claudeCode
                            row.kind == ProcessKind.Terminal ->
                                Res.string.runningPhoneAbout__terminal
                            row.kind == ProcessKind.Helper -> Res.string.runningPhoneAbout__helpers
                            else -> Res.string.runningPhoneAbout__other
                        }
                    )
                )
                if (row.protectedAs == ProcessRole.ServerRoot)
                    KitText(
                        stringResource(Res.string.runningPhoneServerRoot),
                        role = TypeRole.Secondary,
                    )
                processFacts(row)
                    .takeIf { it.isNotEmpty() }
                    ?.let { KitText(it, role = TypeRole.Secondary) }
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes = listOf(stringResource(Res.string.runningDetailPid, row.pid)),
                    text = row.command,
                )
            }
        }
    }
}

@Composable
private fun ProcessRow(
    row: ProcessRowUi,
    stopping: Boolean,
    busy: Boolean,
    actions: RunningActions,
) {
    val details =
        listOfNotNull(
                processFacts(row).takeIf { it.isNotEmpty() },
                if (row.memberCount > 1)
                    stringResource(Res.string.linuxProcessMembers, row.memberCount)
                else null,
                if (row.heavy) stringResource(Res.string.linuxProcessHeavy) else null,
                when (row.orphanReason) {
                    io.github.eslamasabry.oc.feature.phone.presentation.ProcessOrphanReason
                        .ParentGone -> stringResource(Res.string.runningOrphanParentGone)
                    io.github.eslamasabry.oc.feature.phone.presentation.ProcessOrphanReason
                        .CpuWithoutOwner -> stringResource(Res.string.runningOrphanCpuWithoutOwner)
                    null ->
                        if (row.leftBehind) stringResource(Res.string.runningPhoneMarkStuck)
                        else null
                },
            )
            .joinToString(" · ")
    val supporting =
        if (stopping) stringResource(Res.string.termuxProcsStopping) else details.ifBlank { null }
    KitRow(
        title = row.name,
        supporting = supporting,
        leading = KitRowLeading.Glyph(processGlyph(row)),
        supportingMaxLines = 2, // F: lib/ui/screens/running_on_phone_screen.dart:594
        onClick = { actions.details(row.pid) },
        trailing =
            if (offersInlineStop(row) && !stopping) {
                KitRowTrailing.Action(
                    KitLineAction(
                        stringResource(Res.string.runningPhoneStop),
                        { actions.askStop(row.pid) },
                        enabled = !busy,
                        disabledReason = stringResource(Res.string.termuxProcsStopping),
                        semanticsLabel = stringResource(Res.string.runningPhoneStopNamed, row.name),
                    )
                )
            } else {
                KitRowTrailing.Chevron
            },
    )
}

/**
 * Flutter running_on_phone_text.dart:99-112: one load word, one percent and only measured facts.
 */
@Composable
private fun processFacts(row: ProcessRowUi): String =
    listOfNotNull(
            (row.cpuWords ?: row.cpuPercent?.let { runningCpuWords(it.toDouble()) })?.let {
                // Very busy belongs to a group heading; an individual row says Busy.
                (if (it.level == CpuLevel.VeryBusy) it.copy(level = CpuLevel.Busy) else it).words()
            },
            row.memoryBytes?.takeIf { it > 0 }?.let { runningMemory(it) }
                ?: row.memoryMb
                    ?.takeIf { it > 0 }
                    ?.let { stringResource(Res.string.termuxProcsMemoryMb, it) },
            row.elapsedSeconds
                ?.takeIf { it > 0 }
                ?.let {
                    stringResource(Res.string.termuxProcsRunningFor, DurationWords.of(it).words())
                },
        )
        .joinToString(" · ")

@Composable
private fun runningMemory(bytes: Long): String {
    val mb = (bytes / 1048576.0).roundToLong() // F: lib/ui/widgets/running_on_phone_text.dart:93
    if (mb < 1024) return stringResource(Res.string.termuxProcsMemoryMb, mb)
    return runningLargeMemory(bytes)
}

/** Locale-neutral byte unit data, as Flutter formatPhoneStorage's fixed Latin units. */
internal fun runningLargeMemory(bytes: Long): String {
    // F: lib/ui/widgets/phone_server_card.dart:542-550
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10} ${units[unit]}"
}

internal fun offersInlineStop(row: ProcessRowUi): Boolean =
    row.stoppable && (row.leftBehind || row.heavy || row.orphanReason != null)

private fun processGlyph(row: ProcessRowUi) =
    when {
        row.leftBehind || row.orphanReason != null -> KitIcons.warning
        row.heavy -> KitIcons.speed
        else ->
            when (row.kind) {
                ProcessKind.OpenCode -> KitIcons.server
                ProcessKind.ClaudeCode -> KitIcons.code
                ProcessKind.Terminal -> KitIcons.terminal
                ProcessKind.Helper -> KitIcons.agent
                ProcessKind.DevService -> KitIcons.network
                else -> KitIcons.processor
            }
    }

/**
 * The guided card (port of child_limit_card.dart): one notice with its steps, one button when one
 * can open, Hide.
 */
@Composable
private fun ChildLimitCard(kind: ChildLimitCardKind, actions: RunningActions) {
    val steps =
        when (kind) {
            ChildLimitCardKind.OpenSettings -> Res.string.childLimitStepsOpen
            ChildLimitCardKind.EnableDeveloperOptions -> Res.string.childLimitStepsEnable
            ChildLimitCardKind.InstructionsOnly -> Res.string.childLimitStepsText
        }
    val open =
        when (kind) {
            ChildLimitCardKind.OpenSettings -> Res.string.childLimitOpenDeveloper
            ChildLimitCardKind.EnableDeveloperOptions -> Res.string.childLimitOpenAbout
            ChildLimitCardKind.InstructionsOnly -> null
        }
    KitNotice(
        title = stringResource(Res.string.childLimitTitle),
        message = stringResource(Res.string.childLimitBody),
        notes = listOf(stringResource(steps)),
        actions =
            listOfNotNull(
                open?.let {
                    KitLineAction(
                        stringResource(it),
                        actions::openChildLimitSettings,
                        testTag = "child-limit-open",
                    )
                }
            ),
        dismiss =
            KitLineAction(
                stringResource(Res.string.childLimitHide),
                actions::hideChildLimit,
                testTag = "child-limit-hide",
            ),
        liveRegion = false,
    )
}
