package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHeat
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxVitalsUi
import io.github.eslamasabry.oc.ui.kit.status.KitVital
import io.github.eslamasabry.oc.ui.kit.status.KitVitalsStrip
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Every word follows a real observation; absent readings never become zeros or a cool claim. */
@Composable
internal fun LinuxVitalsPanel(state: LinuxVitalsUi, openRunning: () -> Unit) {
    val heat =
        state.heat?.let { value ->
            stringResource(
                when (value) {
                    LinuxHeat.Cool -> Res.string.linuxVitalsCool
                    LinuxHeat.Warm -> Res.string.linuxVitalsWarm
                    LinuxHeat.Hot -> Res.string.linuxVitalsHot
                    LinuxHeat.TooHot -> Res.string.linuxVitalsTooHot
                }
            )
        }
    val metrics = listOfNotNull(
        heat?.let { KitVital(it, stringResource(Res.string.linuxVitalsHeat), stringResource(Res.string.linuxVitalsHeatLabel, it)) },
        state.phoneFreeBytes?.let { value ->
            val words = size(value)
            KitVital(words, stringResource(Res.string.linuxVitalsMemoryFree), stringResource(Res.string.linuxVitalsMemoryLabel, words))
        },
        state.linuxCpuPercent?.let { value ->
            val words = "$value%"
            KitVital(words, stringResource(Res.string.linuxVitalsLinuxCpu), stringResource(Res.string.linuxVitalsCpuLabel, words))
        },
    )
    val heavy =
        state.heat != null &&
            state.heat != LinuxHeat.Cool &&
            state.heavyProgram != null &&
            state.heavyPercent != null
    val summary =
        when {
            state.heat == null ->
                state.linuxMemoryBytes?.let {
                    stringResource(Res.string.linuxVitalsSummary, size(it))
                }
            state.heat == LinuxHeat.Cool ->
                state.linuxMemoryBytes?.let {
                    stringResource(Res.string.linuxVitalsSummaryCool, size(it))
                }
            heavy ->
                stringResource(
                    Res.string.linuxVitalsHeavy,
                    heat.orEmpty(),
                    state.heavyProgram.orEmpty(),
                    state.heavyPercent.toString(),
                )
            state.linuxCpuPercent != null ->
                stringResource(
                    Res.string.linuxVitalsNotLinux,
                    heat.orEmpty(),
                    state.linuxCpuPercent.toString(),
                )
            else -> null
        }
    if (metrics.isNotEmpty() || summary != null) KitVitalsStrip(
        vitals = metrics, summary = summary,
        onSummaryTap = if (heavy) openRunning else null,
        summaryLabel = if (heavy) stringResource(Res.string.linuxVitalsOpenRunning) else null,
    )
}
