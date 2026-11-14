package io.github.eslamasabry.oc.feature.phone.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.text.kitUnits
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
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
import io.github.eslamasabry.oc.feature.phone.presentation.RunningGroupUi
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import androidx.compose.ui.platform.testTag
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
import org.jetbrains.compose.resources.pluralStringResource
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
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(kitPageScrollState())
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
            // This phone's row line ("4 running · 582 MB") counts processes from its own reading; here the
            // summary over the list says how many run (Flutter running_on_phone_screen.dart shows only that), so
            // the plain count is not repeated. Left behind, heavy and near-limit lines still lead the page.
            state.status
                ?.takeUnless { it is io.github.eslamasabry.oc.engine.runtime.procs.RunningRowStatus.Normal }
                ?.let { KitGroupNote(it.words()) }
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
            state.stoppedCount?.let { stopped ->
                KitNotice(
                    message = pluralStringResource(Res.plurals.runningPhoneStopped, stopped, stopped),
                    tone = StatusTone.Neutral,
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
                // Flutter running-summary: how many run and the memory they hold, then one group per thing that
                // started them (OpenCode, each Claude Code conversation by its title, the agent helper, terminals,
                // setup and checks, ...), each heading saying its load so heat shows where it comes from.
                state.memoryBytes?.let { bytes ->
                    KitText(
                        kitUnits(
                            pluralStringResource(
                                Res.plurals.runningPhoneSummary,
                                state.summaryCount,
                                state.summaryCount,
                                PhoneSizes.memory(bytes),
                            )
                        ),
                        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).testTag("running-summary"),
                        role = TypeRole.Headline,
                    )
                }
                val leftBehind = state.leftBehind.size
                state.groups.forEach { group ->
                    KitRowGroup(
                        Modifier.testTag("running-group-${group.id}"),
                        // Flutter puts the group's figures on its heading line, so heat shows where it comes from.
                        label = listOf(groupLabel(group), groupFacts(group)).filter { it.isNotEmpty() }.joinToString(" · "),
                        leadingTiles = group.kind != RunningGroupKind.LeftBehind,
                    ) {
                        group.rows.forEach {
                            ProcessRow(
                                it,
                                stopping = state.stopping == it.pid,
                                busy = state.stopping != null,
                                actions,
                            )
                        }
                        // Flutter: the one bulk stop, last in Left behind, while something is left behind.
                        if (group.kind == RunningGroupKind.LeftBehind && leftBehind > 0)
                            StopLeftBehindRow(leftBehind, state, actions)
                    }
                }
                if (leftBehind > 0 && state.groups.none { it.kind == RunningGroupKind.LeftBehind })
                    KitRowGroup { StopLeftBehindRow(leftBehind, state, actions) }
                KitGroupNote(stringResource(Res.string.termuxProcsAutoRefresh))
            } else if (!state.loadFailed) {
                KitGroupNote(stringResource(Res.string.termuxProcsAutoRefresh))
            }
        }
        val all = state.confirmStopLeftBehind
        KitConfirm(
            spec =
                all?.let { rows ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.runningPhoneStopAllTitle),
                        confirmLabel =
                            pluralStringResource(
                                Res.plurals.runningPhoneStopAllLeftBehind,
                                rows.size,
                                rows.size,
                            ),
                        kind = KitConfirmKind.Stop,
                        body =
                            stringResource(
                                Res.string.runningPhoneStopAllBody,
                                rows.joinToString(", ") { it.name },
                            ),
                    )
                },
            onConfirm = actions::confirmStopLeftBehind,
            onCancel = actions::dismiss,
        )
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
                                    pluralStringResource(
                                        Res.plurals.runningDetailHelpers,
                                        it.stopIdentities.size - 1,
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
            subtitle = selected?.let { row -> state.groups.firstOrNull { g -> g.rows.any { it.pid == row.pid } }?.let { groupLabel(it) } ?: row.kind.words() },
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
                startedBy(row)?.let { KitText(it, role = TypeRole.Secondary) }
                row.heavyForSeconds?.takeIf { row.heavy && it > 0 }?.let {
                    KitText(
                        stringResource(Res.string.runningPhoneHeavyFor, DurationWords.of(it).words()),
                        role = TypeRole.Secondary,
                    )
                }
                processFacts(row)
                    .takeIf { it.isNotEmpty() }
                    ?.let { KitText(it, role = TypeRole.Secondary) }
                // Flutter _Details: the technical values once, under one Details fold.
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes =
                        listOfNotNull(
                            stringResource(Res.string.runningDetailPid, row.pid),
                            row.parentPid?.let { "${stringResource(Res.string.termuxProcsParentId)}: $it" },
                            row.owner?.let { "${stringResource(Res.string.runningPhoneOwner)}: $it" },
                            row.session?.let { "${stringResource(Res.string.runningPhoneConversationId)}: $it" },
                        ),
                    text = row.command,
                )
            }
        }
    }
}

@Composable
private fun StopLeftBehindRow(count: Int, state: RunningUi, actions: RunningActions) {
    KitRow(
        title = pluralStringResource(Res.plurals.runningPhoneStopAllLeftBehind, count, count),
        supporting = stringResource(Res.string.runningPhoneStopAllDetail),
        titleMaxLines = 2,
        supportingMaxLines = 2,
        leading = KitRowLeading.Glyph(KitIcons.stop),
        enabled = state.stopping == null,
        disabledReason = stringResource(Res.string.termuxProcsStopping),
        modifier = Modifier.testTag("running-stop-left-behind"),
        onClick = actions::askStopLeftBehind,
    )
}

/** Flutter `runningGroupLabel`: "OpenCode", "Left behind", "Claude Code · Fix login". */
@Composable
internal fun groupLabel(group: RunningGroupUi): String =
    group.title?.takeIf { group.kind == RunningGroupKind.ClaudeCode }?.let { stringResource(Res.string.runningPhoneGroupChat, it) }
        ?: stringResource(
            when (group.kind) {
                RunningGroupKind.LeftBehind -> Res.string.runningPhoneGroup__leftBehind
                RunningGroupKind.OpenCode -> Res.string.runningPhoneGroup__openCode
                RunningGroupKind.ClaudeCode -> Res.string.runningPhoneGroup__claudeCode
                RunningGroupKind.AgentHelper -> Res.string.runningPhoneGroup__agentHelper
                RunningGroupKind.AiTeam -> Res.string.runningPhoneGroup__aiTeam
                RunningGroupKind.Terminal -> Res.string.runningPhoneGroup__terminal
                RunningGroupKind.Setup -> Res.string.runningPhoneGroup__setup
                RunningGroupKind.Helpers -> Res.string.runningPhoneGroup__helpers
                RunningGroupKind.App -> Res.string.runningPhoneGroup__app
                RunningGroupKind.Other -> Res.string.runningPhoneGroup__other
            }
        )

/** The heading's figures (Flutter `_headingFacts`): the group's load in words (Very busy lives here) and memory. */
@Composable
private fun groupFacts(group: RunningGroupUi): String =
    listOfNotNull(
            group.cpuWords?.words(),
            group.memoryBytes?.takeIf { it > 0 }?.let { runningMemory(it) },
            if (group.hot) stringResource(Res.string.linuxProcessHeavy) else null,
        )
        .joinToString(" · ")

/** "Started by Claude Code · <title>", said on the top of what a conversation left behind. */
@Composable
private fun startedBy(row: ProcessRowUi): String? =
    if (row.session == null) null
    else row.conversationTitle?.let { stringResource(Res.string.runningPhoneFromChat, it) }
        ?: stringResource(Res.string.runningPhoneFromClaude)

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
                    pluralStringResource(Res.plurals.linuxProcessMembers, row.memberCount, row.memberCount)
                else null,
                if (row.heavy) stringResource(Res.string.linuxProcessHeavy) else null,
                if (row.group == RunningGroupKind.LeftBehind && row.depth == 0) startedBy(row) else null,
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
                        // C05: the row's Stop carries its glyph, so it reads as an action, not bold words.
                        icon = KitIcons.stopCircle,
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

private fun runningMemory(bytes: Long): String = PhoneSizes.memory(bytes)

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
