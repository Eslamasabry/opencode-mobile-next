package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxSetupAddPython
import io.github.eslamasabry.oc.ui.words.res.linuxSetupAddingPython
import io.github.eslamasabry.oc.ui.words.res.linuxSetupContinue
import io.github.eslamasabry.oc.ui.words.res.linuxSetupDone
import io.github.eslamasabry.oc.ui.words.res.linuxSetupFailed
import io.github.eslamasabry.oc.ui.words.res.linuxSetupStart
import io.github.eslamasabry.oc.ui.words.res.linuxSetupStop
import io.github.eslamasabry.oc.ui.words.res.linuxSetupWorking
import org.jetbrains.compose.resources.stringResource

/**
 * A visible route into the durable setup job, with restart-safe Continue and real observed
 * progress.
 */
@Composable
internal fun LinuxSetupPanel(state: LinuxHomeUi, actions: LinuxHomeActions) {
    val progress = state.setup
    val running = progress.state == SetupState.Running
    if (running) {
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
        if (progress.state == SetupState.Done)
            KitNotice(message = stringResource(Res.string.linuxSetupDone))
        if (progress.canContinue)
            KitNotice(
                message = stringResource(Res.string.linuxSetupFailed),
                actions =
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
                )
            if (state.ready)
                KitRow(
                    title = stringResource(Res.string.linuxSetupAddPython),
                    onClick = actions::addPython,
                    enabled = !state.working && !state.job.running,
                )
        }
    }
}
