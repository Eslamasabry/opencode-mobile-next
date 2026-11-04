package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/**
 * A visible route into the durable setup job, with restart-safe Continue and real observed
 * progress.
 */
@Composable
internal fun LinuxSetupPanel(
    state: LinuxHomeUi,
    actions: LinuxHomeActions,
    openAppSettings: (() -> Unit)? = null,
) {
    val progress = state.setup
    val running = progress.state == SetupState.Running
    if (running) {
        KitProgressView(
            progress =
                KitProgress.Known(
                    value = progress.overall.toFloat().coerceIn(0f, 1f),
                    caption = progress.current?.let { componentTitle(it) },
                    eta = progress.etaSeconds?.takeIf { it > 0 }?.seconds,
                )
        )
        KitNotice(
            title =
                stringResource(
                    if (progress.adding.contains("python")) Res.string.linuxSetupAddingPython
                    else Res.string.linuxSetupWorking
                ),
            message = stringResource(Res.string.linuxSetupWorking),
            actions =
                listOf(
                    KitLineAction(stringResource(Res.string.linuxSetupStop), actions::askStopSetup)
                ),
        )
    } else {
        if (!progress.canContinue)
            progress.error?.let { error ->
                KitNotice(message = error.words())
            }
        if (progress.state == SetupState.Done)
            KitNotice(message = stringResource(Res.string.linuxSetupDone))
        // Unknown ownership cannot be repaired by rerunning the job (Flutter
        // needsOwnershipRecovery): the way out is Force stop in the app's settings.
        val ownership =
            progress.error == SetupError.OwnershipUnknown ||
                progress.components.any {
                    it.state == ComponentState.Failed && it.error == SetupError.OwnershipUnknown
                }
        if (progress.canContinue)
            KitNotice(
                message =
                    if (ownership) SetupError.OwnershipUnknown.words()
                    else progress.error?.words() ?: stringResource(Res.string.linuxSetupFailed),
                actions =
                    if (ownership)
                        listOfNotNull(
                            openAppSettings?.let {
                                KitLineAction(stringResource(Res.string.e7SetupOpenAppSettings), it)
                            }
                        )
                    else
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.linuxSetupContinue),
                                actions::continueSetup,
                            )
                        ),
            )
        KitRowGroup {
            if (state.presence == LinuxPresence.NotInstalled)
                KitRow(
                    title = stringResource(Res.string.linuxSetupStart),
                    onClick = actions::startSetup,
                    enabled = !state.working && !state.job.running,
                    disabledReason = stringResource(Res.string.linuxToolsWait),
                )
            if (state.ready)
                KitRow(
                    title = stringResource(Res.string.linuxSetupAddPython),
                    onClick = actions::addPython,
                    enabled = !state.working && !state.job.running,
                    disabledReason = stringResource(Res.string.linuxToolsWait),
                )
        }
    }
    if (progress.components.isNotEmpty())
        KitRowGroup {
            for (part in progress.components) {
                val stateWords =
                    stringResource(
                        when (part.state) {
                            ComponentState.Pending -> Res.string.linuxSetupPartPending
                            ComponentState.Checking -> Res.string.linuxSetupCheckingPart
                            ComponentState.Running -> Res.string.linuxSetupPartRunning
                            ComponentState.Done,
                            ComponentState.Skipped -> Res.string.linuxSetupPartDone
                            ComponentState.Failed -> Res.string.linuxSetupPartFailed
                        }
                    )
                val done = part.bytesDone
                val total = part.bytesTotal
                val byteWords =
                    if (done != null && total != null && total > 0)
                        stringResource(
                            Res.string.linuxSetupBytes,
                            size(done.coerceIn(0L, total)),
                            size(total),
                        )
                    else null
                KitRow(
                    title = componentTitle(part.id),
                    supporting =
                        part.error?.words()
                            ?: listOfNotNull(stateWords, part.version, byteWords)
                                .joinToString(" · "),
                    supportingMaxLines = 3,
                )
            }
        }
}

@Composable
private fun SetupError.words(): String =
    when (this) {
        SetupError.OwnershipUnknown -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
        SetupError.PersistenceFailure -> stringResource(Res.string.linuxSetupSavedFailed)
        SetupError.StatusUnreadable -> stringResource(Res.string.linuxSetupStatusUnreadable)
        SetupError.HostMismatch -> stringResource(Res.string.linuxSetupHostMismatch)
        SetupError.ToolsUncheckable -> stringResource(Res.string.linuxToolsInventoryFailed)
        SetupError.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
        SetupError.CannotStart -> stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupError.OpenCodeNoProgram -> stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupError.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupError.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        SetupError.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
        SetupError.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
        is SetupError.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, title)
        is SetupError.Offline -> stringResource(Res.string.phoneSetupErrorOffline, title)
        is SetupError.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, title)
        is SetupError.Install -> stringResource(Res.string.phoneSetupErrorInstall, title)
        SetupError.HostRefused,
        SetupError.Unexpected -> stringResource(Res.string.linuxSetupHostRefused)
    }
