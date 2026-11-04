package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.linux.presentation.*
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun LinuxMigrationScreen(state: LinuxMigrationUi, actions: LinuxMigrationActions) {
    KitBackGuard(state.busy, actions::back)
    KitScreen(
        topBar = {
            KitTopBar(
                title = migrationText(LinuxMigrationText(state.title)),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("termux-migration"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.access?.let { access ->
                val (body, fix) =
                    when (access) {
                        LinuxMigrationAccess.Missing ->
                            Res.string.termuxProblemNotInstalled to Res.string.termuxFixOpenTermux
                        LinuxMigrationAccess.Permission ->
                            Res.string.termuxProblemAccessNeeded to Res.string.termuxFixAllowAccess
                        LinuxMigrationAccess.Blocked ->
                            Res.string.termuxProblemAccessBlocked to
                                Res.string.termuxFixOpenPermissions
                        LinuxMigrationAccess.Unavailable ->
                            Res.string.termuxProblemOutdated to Res.string.termuxFixOpenTermux
                        LinuxMigrationAccess.OtherAppsOff ->
                            Res.string.termuxProblemOtherAppsOff to
                                Res.string.termuxFixAllowOtherApps
                        LinuxMigrationAccess.Asleep ->
                            Res.string.termuxProblemAsleep to Res.string.termuxFixOpenTermux
                        LinuxMigrationAccess.Unknown ->
                            Res.string.migrationTermuxUnavailable to Res.string.commonRetry
                    }
                KitNotice(message = stringResource(body), tone = StatusTone.Attention)
                if (access == LinuxMigrationAccess.OtherAppsOff)
                    KitText(stringResource(Res.string.termuxOtherAppsBody))
                KitButton(stringResource(fix), { actions.command(LinuxMigrationCommand.FixTermux) })
            }
            state.paragraphs.forEach { KitText(migrationText(it)) }
            if (state.showSpace) {
                val required = migrationSize(state.requiredBytes ?: 0)
                KitNotice(
                    message =
                        if (state.availableBytes == null)
                            stringResource(Res.string.migrationReviewSpaceUnknown, required)
                        else
                            stringResource(
                                Res.string.migrationReviewSpace,
                                required,
                                migrationSize(state.availableBytes),
                            )
                )
            }
            state.choices.forEach { row ->
                val detail = migrationText(LinuxMigrationText(row.detail))
                val measured =
                    if (row.bytes == null || row.files == null)
                        stringResource(Res.string.migrationSizeUnknown)
                    else
                        pluralStringResource(
                            Res.plurals.migrationItemSize,
                            row.files.toInt(),
                            migrationSize(row.bytes),
                            row.files.toInt(),
                        )
                KitSwitchRow(
                    title = migrationText(LinuxMigrationText(row.title)),
                    checked = row.selected,
                    supporting = "$detail\n$measured",
                    enabled =
                        if (row.enabled && !state.busy) KitEnabled.Enabled
                        else KitEnabled.Disabled(detail),
                    onCheckedChange = { actions.toggle(row.id, it) },
                )
            }
            if (state.busy) KitProgressView(KitProgress.Waiting())
            state.buttons.forEach { button ->
                KitButton(
                    migrationText(LinuxMigrationText(button.word)),
                    { actions.command(button.command) },
                    enabled =
                        if (button.enabled) KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.migrationChooseOne)),
                )
            }
        }
    }
    val stop = state.confirmation == LinuxMigrationCommand.Stop
    KitConfirm(
        state.confirmation?.let {
            KitConfirmSpec(
                title =
                    stringResource(
                        if (stop) Res.string.migrationStopTitle
                        else Res.string.migrationDiscardTitle
                    ),
                confirmLabel =
                    stringResource(
                        if (stop) Res.string.migrationStop else Res.string.migrationDiscard
                    ),
                body =
                    stringResource(
                        if (stop) Res.string.migrationStopBody else Res.string.migrationDiscardBody
                    ),
                kind = if (stop) KitConfirmKind.Stop else KitConfirmKind.Destructive,
            )
        },
        actions::confirm,
        actions::dismiss,
    )
}

/** Flutter formatPhoneStorage: one decimal above a kilobyte, Latin digits in an LTR isolate. */
@Composable
private fun migrationSize(bytes: Long): String {
    val value = bytes.coerceAtLeast(0)
    if (value < 1024)
        return "⁦${stringResource(Res.string.linuxMigrationBytes, "$value")}⁩"
    var scaled = value / 1024.0
    var unit = 0
    while (scaled >= 1024 && unit < 2) {
        scaled /= 1024
        unit++
    }
    val tenths = kotlin.math.round(scaled * 10).toLong()
    val number = "${tenths / 10}.${tenths % 10}"
    val resource =
        when (unit) {
            0 -> Res.string.linuxMigrationKilobytes
            1 -> Res.string.linuxMigrationMegabytes
            else -> Res.string.linuxMigrationGigabytes
        }
    return "⁦${stringResource(resource, number)}⁩"
}

@Composable
private fun migrationText(text: LinuxMigrationText): String {
    val resource =
        when (text.word) {
            LinuxMigrationWord.migrationTitle -> Res.string.migrationTitle
            LinuxMigrationWord.migrationChecking -> Res.string.migrationChecking
            LinuxMigrationWord.migrationReviewIntro -> Res.string.migrationReviewIntro
            LinuxMigrationWord.migrationGroupMoves -> Res.string.migrationGroupMoves
            LinuxMigrationWord.migrationGroupExports -> Res.string.migrationGroupExports
            LinuxMigrationWord.migrationGroupNotMoved -> Res.string.migrationGroupNotMoved
            LinuxMigrationWord.migrationItemProjects -> Res.string.migrationItemProjects
            LinuxMigrationWord.migrationItemConfig -> Res.string.migrationItemConfig
            LinuxMigrationWord.migrationItemSessions -> Res.string.migrationItemSessions
            LinuxMigrationWord.migrationItemGitConfig -> Res.string.migrationItemGitConfig
            LinuxMigrationWord.migrationItemShellFiles -> Res.string.migrationItemShellFiles
            LinuxMigrationWord.migrationItemAiTeam -> Res.string.migrationItemAiTeam
            LinuxMigrationWord.migrationItemProjectsWhat -> Res.string.migrationItemProjectsWhat
            LinuxMigrationWord.migrationItemConfigWhat -> Res.string.migrationItemConfigWhat
            LinuxMigrationWord.migrationItemSessionsWhat -> Res.string.migrationItemSessionsWhat
            LinuxMigrationWord.migrationItemGitConfigWhat -> Res.string.migrationItemGitConfigWhat
            LinuxMigrationWord.migrationItemShellFilesWhat -> Res.string.migrationItemShellFilesWhat
            LinuxMigrationWord.migrationItemAiTeamWhat -> Res.string.migrationItemAiTeamWhat
            LinuxMigrationWord.migrationSizeUnknown -> Res.string.migrationSizeUnknown
            LinuxMigrationWord.migrationExportsNote -> Res.string.migrationExportsNote
            LinuxMigrationWord.migrationNotMovedSignIn -> Res.string.migrationNotMovedSignIn
            LinuxMigrationWord.migrationNotMovedSignInNamed ->
                Res.string.migrationNotMovedSignInNamed
            LinuxMigrationWord.migrationNotMovedKeys -> Res.string.migrationNotMovedKeys
            LinuxMigrationWord.migrationNotMovedTools -> Res.string.migrationNotMovedTools
            LinuxMigrationWord.migrationTermuxKept -> Res.string.migrationTermuxKept
            LinuxMigrationWord.migrationKeepOpen -> Res.string.migrationKeepOpen
            LinuxMigrationWord.migrationStart -> Res.string.migrationStart
            LinuxMigrationWord.migrationChooseOne -> Res.string.migrationChooseOne
            LinuxMigrationWord.migrationPacking -> Res.string.migrationPacking
            LinuxMigrationWord.migrationCopying -> Res.string.migrationCopying
            LinuxMigrationWord.migrationUnpacking -> Res.string.migrationUnpacking
            LinuxMigrationWord.migrationVerifying -> Res.string.migrationVerifying
            LinuxMigrationWord.migrationSwitching -> Res.string.migrationSwitching
            LinuxMigrationWord.migrationStepConnect -> Res.string.migrationStepConnect
            LinuxMigrationWord.migrationStop -> Res.string.migrationStop
            LinuxMigrationWord.migrationStopTitle -> Res.string.migrationStopTitle
            LinuxMigrationWord.migrationStopBody -> Res.string.migrationStopBody
            LinuxMigrationWord.migrationStopKept -> Res.string.migrationStopKept
            LinuxMigrationWord.migrationKeepGoing -> Res.string.migrationKeepGoing
            LinuxMigrationWord.migrationCancelled -> Res.string.migrationCancelled
            LinuxMigrationWord.migrationCancelledBody -> Res.string.migrationCancelledBody
            LinuxMigrationWord.migrationStoppedLeaving -> Res.string.migrationStoppedLeaving
            LinuxMigrationWord.migrationResume -> Res.string.migrationResume
            LinuxMigrationWord.migrationNeedsSpace -> Res.string.migrationNeedsSpace
            LinuxMigrationWord.migrationNeedsSpaceBody -> Res.string.migrationNeedsSpaceBody
            LinuxMigrationWord.migrationNeedsSpaceBodyUnknown ->
                Res.string.migrationNeedsSpaceBodyUnknown
            LinuxMigrationWord.migrationChooseFewer -> Res.string.migrationChooseFewer
            LinuxMigrationWord.migrationNeedsBuiltin -> Res.string.migrationNeedsBuiltin
            LinuxMigrationWord.migrationNeedsBuiltinBody -> Res.string.migrationNeedsBuiltinBody
            LinuxMigrationWord.migrationSetUpBuiltin -> Res.string.migrationSetUpBuiltin
            LinuxMigrationWord.migrationSetupFailed -> Res.string.migrationSetupFailed
            LinuxMigrationWord.migrationTermuxNotAnswering -> Res.string.migrationTermuxNotAnswering
            LinuxMigrationWord.migrationTermuxUnavailable -> Res.string.migrationTermuxUnavailable
            LinuxMigrationWord.migrationOpenTermux -> Res.string.migrationOpenTermux
            LinuxMigrationWord.migrationFailedTitle -> Res.string.migrationFailedTitle
            LinuxMigrationWord.migrationSourceChanged -> Res.string.migrationSourceChanged
            LinuxMigrationWord.migrationSourceBusy -> Res.string.migrationSourceBusy
            LinuxMigrationWord.migrationUnsupportedFiles -> Res.string.migrationUnsupportedFiles
            LinuxMigrationWord.migrationUnsupportedFilesFix ->
                Res.string.migrationUnsupportedFilesFix
            LinuxMigrationWord.migrationLinkedFileUnreadable ->
                Res.string.migrationLinkedFileUnreadable
            LinuxMigrationWord.migrationLinkedFileUnreadableFix ->
                Res.string.migrationLinkedFileUnreadableFix
            LinuxMigrationWord.migrationTooLarge -> Res.string.migrationTooLarge
            LinuxMigrationWord.migrationTooLargeFix -> Res.string.migrationTooLargeFix
            LinuxMigrationWord.migrationVerificationFailed -> Res.string.migrationVerificationFailed
            LinuxMigrationWord.migrationDestinationChanged -> Res.string.migrationDestinationChanged
            LinuxMigrationWord.migrationDestinationChangedFix ->
                Res.string.migrationDestinationChangedFix
            LinuxMigrationWord.migrationStorageFailed -> Res.string.migrationStorageFailed
            LinuxMigrationWord.migrationTimedOut -> Res.string.migrationTimedOut
            LinuxMigrationWord.migrationSelectionChanged -> Res.string.migrationSelectionChanged
            LinuxMigrationWord.migrationConnectionFailed -> Res.string.migrationConnectionFailed
            LinuxMigrationWord.migrationFailureCode -> Res.string.migrationFailureCode
            LinuxMigrationWord.migrationFailureItem -> Res.string.migrationFailureItem
            LinuxMigrationWord.migrationOpenThisPhone -> Res.string.migrationOpenThisPhone
            LinuxMigrationWord.migrationDoneTitle -> Res.string.migrationDoneTitle
            LinuxMigrationWord.migrationDone -> Res.string.migrationDone
            LinuxMigrationWord.migrationSignInAgain -> Res.string.migrationSignInAgain
            LinuxMigrationWord.migrationSignInAgainNamed -> Res.string.migrationSignInAgainNamed
            LinuxMigrationWord.migrationSignInAgainAny -> Res.string.migrationSignInAgainAny
            LinuxMigrationWord.migrationProjectsWhere -> Res.string.migrationProjectsWhere
            LinuxMigrationWord.migrationProjectsWhereBody -> Res.string.migrationProjectsWhereBody
            LinuxMigrationWord.migrationExportsWhere -> Res.string.migrationExportsWhere
            LinuxMigrationWord.migrationExportsWhereBody -> Res.string.migrationExportsWhereBody
            LinuxMigrationWord.migrationRemoveTermux -> Res.string.migrationRemoveTermux
            LinuxMigrationWord.migrationRemoveTermuxBody -> Res.string.migrationRemoveTermuxBody
            LinuxMigrationWord.migrationOpenBuiltin -> Res.string.migrationOpenBuiltin
            LinuxMigrationWord.migrationDetailProjects -> Res.string.migrationDetailProjects
            LinuxMigrationWord.migrationDetailExports -> Res.string.migrationDetailExports
            LinuxMigrationWord.migrationUnfinishedTitle -> Res.string.migrationUnfinishedTitle
            LinuxMigrationWord.migrationUnfinishedBody -> Res.string.migrationUnfinishedBody
            LinuxMigrationWord.migrationUnavailableTitle -> Res.string.migrationUnavailableTitle
            LinuxMigrationWord.migrationUnavailableBody -> Res.string.migrationUnavailableBody
            LinuxMigrationWord.migrationRowBody -> Res.string.migrationRowBody
            LinuxMigrationWord.migrationRowResume -> Res.string.migrationRowResume
            LinuxMigrationWord.migrationRowResumeBody -> Res.string.migrationRowResumeBody
            LinuxMigrationWord.migrationRowRunning -> Res.string.migrationRowRunning
            LinuxMigrationWord.migrationRowDoneBody -> Res.string.migrationRowDoneBody
            LinuxMigrationWord.migrationOffer -> Res.string.migrationOffer
            LinuxMigrationWord.migrationOfferAction -> Res.string.migrationOfferAction
            LinuxMigrationWord.migrationReviewSpace -> Res.string.migrationReviewSpace
            LinuxMigrationWord.migrationReviewSpaceUnknown -> Res.string.migrationReviewSpaceUnknown
            LinuxMigrationWord.migrationReviewSpaceShort -> Res.string.migrationReviewSpaceShort
            LinuxMigrationWord.migrationDiscard -> Res.string.migrationDiscard
            LinuxMigrationWord.migrationDiscardTitle -> Res.string.migrationDiscardTitle
            LinuxMigrationWord.migrationDiscardBody -> Res.string.migrationDiscardBody
            LinuxMigrationWord.migrationDiscardFailed -> Res.string.migrationDiscardFailed
            LinuxMigrationWord.migrationStopping -> Res.string.migrationStopping
            LinuxMigrationWord.commonRetry -> Res.string.commonRetry
            LinuxMigrationWord.migrationItemSize -> Res.string.migrationSizeUnknown
        }
    return stringResource(resource, *text.arguments.toTypedArray())
}
