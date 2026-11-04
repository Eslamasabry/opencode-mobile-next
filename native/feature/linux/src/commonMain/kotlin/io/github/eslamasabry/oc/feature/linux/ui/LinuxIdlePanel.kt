package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxIdleDetail
import io.github.eslamasabry.oc.ui.words.res.linuxIdleFailed
import io.github.eslamasabry.oc.ui.words.res.linuxIdleInvalid
import io.github.eslamasabry.oc.ui.words.res.linuxIdleMinutes
import io.github.eslamasabry.oc.ui.words.res.linuxIdleMinutesValue
import io.github.eslamasabry.oc.ui.words.res.linuxIdleResume
import io.github.eslamasabry.oc.ui.words.res.linuxIdleSave
import io.github.eslamasabry.oc.ui.words.res.linuxIdleTitle
import io.github.eslamasabry.oc.ui.words.res.linuxIdleWorking
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LinuxIdlePanel(state: LinuxHomeUi, actions: LinuxHomeActions) {
    val idle = state.idle
    if (idle.failed) KitNotice(message = stringResource(Res.string.linuxIdleFailed))
    if (!idle.supported) return
    val working = stringResource(Res.string.linuxIdleWorking)
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.linuxIdleTitle),
            supporting = stringResource(Res.string.linuxIdleDetail),
            checked = idle.enabled,
            enabled = if (state.idleWorking) KitEnabled.Disabled(working) else KitEnabled.Enabled,
            onCheckedChange = actions::setIdleEnabled,
        )
        idle.minutes?.let { minutes ->
            KitRow(
                title = stringResource(Res.string.linuxIdleMinutes),
                supporting = stringResource(Res.string.linuxIdleMinutesValue, minutes),
                enabled = !state.idleWorking,
                onClick = actions::askIdleMinutes,
            )
        }
        if (idle.stopped || idle.helperStopped)
            KitRow(
                title = stringResource(Res.string.linuxIdleResume),
                enabled = !state.idleWorking,
                onClick = actions::resumeIdle,
            )
    }
    val invalid = stringResource(Res.string.linuxIdleInvalid)
    KitInputDialog(
        visible = state.askingIdleMinutes,
        title = stringResource(Res.string.linuxIdleMinutes),
        fieldLabel = stringResource(Res.string.linuxIdleMinutes),
        confirmLabel = stringResource(Res.string.linuxIdleSave),
        initial = idle.minutes?.toString().orEmpty(),
        validate = {
            if (it.toIntOrNull()?.let { minutes -> minutes in 1..60 } == true) null else invalid
        },
        onResult = actions::finishIdleMinutes,
    )
}
