package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
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
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The weekday's own name, Sunday as 0 (a cron weekday). */
@Composable
private fun dayName(day: Int): String =
    stringResource(
        when (day) {
            0 -> Res.string.composerLimitSunday
            1 -> Res.string.composerLimitMonday
            2 -> Res.string.composerLimitTuesday
            3 -> Res.string.composerLimitWednesday
            4 -> Res.string.composerLimitThursday
            5 -> Res.string.composerLimitFriday
            else -> Res.string.composerLimitSaturday
        },
    )

/** Dart `teamJobWhen`: when a job runs, in plain words. */
@Composable
internal fun jobWhenWords(w: FleetJobWhen): String =
    when (w) {
        FleetJobWhen.OnSchedule -> stringResource(Res.string.teamJobOnSchedule)
        FleetJobWhen.Repeats -> stringResource(Res.string.teamJobRepeats)
        FleetJobWhen.Event -> stringResource(Res.string.teamJobWhenEvent)
        FleetJobWhen.Manual -> stringResource(Res.string.teamJobManual)
        FleetJobWhen.Webhook -> stringResource(Res.string.teamJobWebhook)
        FleetJobWhen.Condition -> stringResource(Res.string.teamJobCondition)
        FleetJobWhen.Other -> stringResource(Res.string.teamJobOther)
        FleetJobWhen.EveryHour -> stringResource(Res.string.teamJobEveryHour)
        is FleetJobWhen.EverySeconds -> pluralStringResource(Res.plurals.teamJobEverySeconds, w.n, w.n)
        is FleetJobWhen.EveryMinutes -> pluralStringResource(Res.plurals.teamJobEveryMinutes, w.n, w.n)
        is FleetJobWhen.EveryHours -> pluralStringResource(Res.plurals.teamJobEveryHours, w.n, w.n)
        is FleetJobWhen.EveryDayAt -> stringResource(Res.string.teamJobEveryDayAt, w.time)
        is FleetJobWhen.EveryWeekdayAt -> stringResource(Res.string.teamJobEveryWeekdayAt, w.time)
        is FleetJobWhen.EveryDowAt -> stringResource(Res.string.teamJobEveryDowAt, dayName(w.day), w.time)
    }

/**
 * A write's receipt and, when the host answered in its own words, those words under Details (never as the copy).
 * [onRetry] shows Try again on a write that was not confirmed or was refused.
 */
@Composable
internal fun FleetWriteLine(write: FleetWrite, tag: String, onRetry: (() -> Unit)?) {
    val state =
        when (write.status) {
            FleetWriteStatus.Sending -> KitReceiptState.Sending
            FleetWriteStatus.Confirmed -> KitReceiptState.Confirmed
            FleetWriteStatus.NotConfirmed -> KitReceiptState.NotConfirmed
            FleetWriteStatus.Refused -> KitReceiptState.Refused()
        }
    KitReceipt(state, Modifier.testTag(tag), onRetry = onRetry)
    val words = write.hostWords?.trim().orEmpty()
    if (words.isNotEmpty() && write.status != FleetWriteStatus.Confirmed) {
        KitDetailsFold(
            label = stringResource(Res.string.teamUiTechnicalDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            modifier = Modifier.testTag("$tag-details"),
            notes = listOf(stringResource(Res.string.teamDispatchHostWords)),
            text = LocalKitRedactor.current.details(words),
        )
    }
}

/**
 * One project (a Gas City rig) of a computer's team (Dart `TeamRigScreen`): where it stands and what a person can do with
 * it. Pause and Delete name the project and ask first, saying what goes and what stays; the scheduled jobs are switches
 * with their plain "when"; the host's supervision rules for the project are shown read-only.
 */
@Composable
public fun FleetRigScreen(ui: FleetRigUi, actions: FleetRigActions) {
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = ui.name.ifEmpty { ui.projectId },
                    subtitle =
                        if (ui.kind == FleetRigKind.Ready) {
                            stringResource(if (ui.paused) Res.string.teamRigStatePaused else Res.string.teamRigStateActive)
                        } else {
                            null
                        },
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            loading = ui.kind == FleetRigKind.Starting,
            loadingLabel = stringResource(Res.string.teamJobsLoading),
            width = KitScreenWidth.Reading,
        ) {
            when (ui.kind) {
                FleetRigKind.Starting -> Unit
                FleetRigKind.Failed ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamUiStateUnreachableTitle),
                        Modifier.testTag("team-rig-failed"),
                        body = stringResource(Res.string.teamUiCardErrorUnreachable),
                        icon = KitIcons.cloudOff,
                        primary = KitAction(stringResource(Res.string.teamUiCardRetry), actions::retryTeam),
                    )
                FleetRigKind.Gone ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamRigNotFoundTitle),
                        Modifier.testTag("team-rig-gone"),
                        body = stringResource(Res.string.teamRigNotFoundBody),
                        icon = KitIcons.projects,
                    )
                FleetRigKind.Ready -> Ready(ui, actions)
            }
        }
        Asking(ui, actions)
    }
}

@Composable
private fun Ready(ui: FleetRigUi, actions: FleetRigActions) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("team-rig"),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        state = kitPageListState(),
    ) {
        ui.write?.let { write ->
            item(key = "write") {
                val again = write.status == FleetWriteStatus.NotConfirmed || write.status == FleetWriteStatus.Refused
                FleetWriteLine(write, "team-rig-receipt", if (again) actions::retryWrite else null)
            }
        }
        if (ui.paused) {
            item(key = "paused") {
                KitNotice(
                    stringResource(Res.string.teamRigPausedBody),
                    Modifier.testTag("team-rig-paused"),
                    title = stringResource(Res.string.teamRigPausedTitle, ui.name),
                    icon = KitIcons.pause,
                )
            }
        }
        if (ui.canControl || ui.canRemove) {
            item(key = "acts") {
                KitRowGroup(
                    destructive =
                        if (ui.canRemove) {
                            {
                                KitRow(
                                    title = stringResource(Res.string.teamRigDelete, ui.name),
                                    titleMaxLines = 2,
                                    leading = KitRowLeading.Glyph(KitIcons.delete),
                                    enabled = !ui.busy,
                                    modifier = Modifier.testTag("team-rig-delete"),
                                    onClick = actions::askDelete,
                                )
                            }
                        } else {
                            null
                        },
                ) {
                    if (ui.canControl) {
                        KitRow(
                            title = stringResource(if (ui.paused) Res.string.teamRigResume else Res.string.teamRigPause, ui.name),
                            titleMaxLines = 2,
                            leading = KitRowLeading.Glyph(if (ui.paused) KitIcons.play else KitIcons.pause),
                            enabled = !ui.busy,
                            modifier = Modifier.testTag(if (ui.paused) "team-rig-resume" else "team-rig-pause"),
                            onClick = if (ui.paused) actions::resume else actions::askPause,
                        )
                    }
                }
            }
        }
        if (ui.jobsShown) jobs(ui, actions)
        ui.policy?.let { policy -> item(key = "policy") { FleetPolicyBlock(policy) } }
        item(key = "technical") {
            KitDetailsFold(
                label = stringResource(Res.string.teamUiTechnicalDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                modifier = Modifier.testTag("team-rig-technical"),
                values =
                    listOfNotNull(
                        ui.directory?.let { KitTechnicalValue(stringResource(Res.string.teamRigFolderLabel), LocalKitRedactor.current.details(it)) },
                        KitTechnicalValue(stringResource(Res.string.teamAgentScreenLabelId), LocalKitRedactor.current.details(ui.projectId)),
                    ),
            )
        }
    }
}

/** The project's scheduled jobs, each a switch with its plain "when"; the team's own follow under their own label. */
private fun LazyListScope.jobs(ui: FleetRigUi, actions: FleetRigActions) {
    if (ui.jobsFailed) {
        item(key = "jobs-failed") {
            KitNotice(
                stringResource(Res.string.teamJobsFailedBody),
                Modifier.testTag("team-jobs-failed"),
                title = stringResource(Res.string.teamJobsFailedTitle),
                tone = StatusTone.Failure,
                icon = KitIcons.warning,
                actions = listOf(KitLineAction(stringResource(Res.string.teamUiCardRetry), actions::retryJobs, testTag = "team-jobs-retry")),
            )
        }
        return
    }
    if (!ui.jobsRead) {
        if (ui.jobsLoading) {
            item(key = "jobs-loading") {
                KitStateView(
                    KitStateKind.Loading,
                    stringResource(Res.string.teamJobsLoading),
                    Modifier.testTag("team-jobs-loading"),
                    size = KitStateSize.Inline,
                )
            }
        }
        return
    }
    item(key = "jobs") {
        if (ui.mine.isEmpty()) {
            KitStateView(
                KitStateKind.Empty,
                stringResource(Res.string.teamJobsEmptyTitle),
                Modifier.testTag("team-jobs-empty"),
                body = stringResource(Res.string.teamJobsEmptyBody),
                icon = KitIcons.clock,
                size = KitStateSize.Inline,
            )
        } else {
            KitRowGroup(Modifier.testTag("team-jobs"), label = stringResource(Res.string.teamJobsTitle)) {
                ui.mine.forEach { job -> JobRow(job, ui.canSwitchJobs, actions) }
            }
        }
    }
    if (ui.team.isNotEmpty()) {
        item(key = "jobs-team") {
            KitRowGroup(Modifier.testTag("team-jobs-team"), label = stringResource(Res.string.teamJobsWholeTeamTitle)) {
                ui.team.forEach { job -> JobRow(job, ui.canSwitchJobs, actions) }
            }
        }
    }
}

@Composable
private fun JobRow(job: FleetJobRow, canSwitch: Boolean, actions: FleetRigActions) {
    val whenWords = jobWhenWords(job.whenWords)
    val write = job.write?.takeIf { it.status != FleetWriteStatus.Confirmed }
    KitSwitchRow(
        title = job.name,
        checked = job.enabled,
        modifier = Modifier.testTag("team-job-${job.id}"),
        supporting = whenWords,
        // Starting or pausing a job is the host's to allow; without it the switch shows the state and says why it is still.
        enabled = if (canSwitch && write?.status != FleetWriteStatus.Sending) KitEnabled.Enabled else KitEnabled.Disabled(whenWords),
        below =
            write?.let { w ->
                {
                    FleetWriteLine(
                        w,
                        "team-job-receipt-${job.id}",
                        if (w.status != FleetWriteStatus.Sending) ({ actions.setJob(job.id, !job.enabled) }) else null,
                    )
                }
            },
        switchTag = "team-job-switch-${job.id}",
        onCheckedChange = { on -> actions.setJob(job.id, on) },
    )
}

/** Pause and Delete ask first (Dart `showKitConfirm`), naming the project and saying what goes and what stays. */
@Composable
private fun Asking(ui: FleetRigUi, actions: FleetRigActions) {
    val spec =
        when (ui.confirming) {
            FleetRigConfirm.Pause ->
                KitConfirmSpec(
                    title = stringResource(Res.string.teamRigPauseTitle, ui.name),
                    confirmLabel = stringResource(Res.string.teamRigPauseConfirm),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.teamRigPauseBody, ui.name),
                    cancelLabel = stringResource(Res.string.teamUiKeep),
                )
            FleetRigConfirm.Delete ->
                KitConfirmSpec(
                    title = stringResource(Res.string.teamRigDeleteTitle, ui.name),
                    confirmLabel = stringResource(Res.string.teamRigDeleteConfirm),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.teamRigDeleteBody, ui.name),
                    consequenceItems =
                        listOf(
                            KitConsequence(stringResource(Res.string.teamRigDeleteLost), KitConsequenceMark.Lost),
                            KitConsequence(stringResource(Res.string.teamRigDeleteKeptFiles), KitConsequenceMark.Kept),
                            KitConsequence(stringResource(Res.string.teamRigDeleteKeptHistory), KitConsequenceMark.Kept),
                        ),
                    cancelLabel = stringResource(Res.string.teamUiKeep),
                )
            null -> null
        }
    KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::cancelConfirm)
}
