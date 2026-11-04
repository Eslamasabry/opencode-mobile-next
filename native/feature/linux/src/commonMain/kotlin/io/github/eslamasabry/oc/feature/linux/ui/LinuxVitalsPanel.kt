package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.runtime.vitals.LinuxHeat
import io.github.eslamasabry.oc.engine.runtime.vitals.LinuxVitals
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxSize
import io.github.eslamasabry.oc.ui.kit.status.KitVital
import io.github.eslamasabry.oc.ui.kit.status.KitVitalsStrip
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsCool
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsCpuLabel
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsHeat
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsHeatLabel
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsHeavy
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsHot
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsLinuxCpu
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsMemoryFree
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsMemoryLabel
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsNotLinux
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsOpenRunning
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsSummary
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsSummaryCool
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsTooHot
import io.github.eslamasabry.oc.ui.words.res.linuxVitalsWarm
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LinuxVitalsPanel(vitals: LinuxVitals?, openRunning: () -> Unit) {
    val heat = when (vitals?.heat) {
        LinuxHeat.Cool -> stringResource(Res.string.linuxVitalsCool)
        LinuxHeat.Warm -> stringResource(Res.string.linuxVitalsWarm)
        LinuxHeat.Hot -> stringResource(Res.string.linuxVitalsHot)
        LinuxHeat.TooHot -> stringResource(Res.string.linuxVitalsTooHot)
        null -> null
    }
    val cells = buildList {
        if (vitals == null || heat != null) {
            add(KitVital(heat.orEmpty(), stringResource(Res.string.linuxVitalsHeat), stringResource(Res.string.linuxVitalsHeatLabel, heat.orEmpty())))
        }
        val memory = vitals?.phoneMemoryFreeBytes
        if (vitals == null || memory != null) {
            val size = memory?.let { Numbers.digits(LinuxSize.format(it)) }.orEmpty()
            add(KitVital(size, stringResource(Res.string.linuxVitalsMemoryFree), stringResource(Res.string.linuxVitalsMemoryLabel, size)))
        }
        val cpu = vitals?.linuxCpuPercent
        if (vitals == null || cpu != null) {
            val share = cpu?.let { "${Numbers.format(it.roundToInt())}%" }.orEmpty()
            add(KitVital(share, stringResource(Res.string.linuxVitalsLinuxCpu), stringResource(Res.string.linuxVitalsCpuLabel, share)))
        }
    }
    val named = vitals != null && heat != null && vitals.heat != LinuxHeat.Cool && vitals.hasHeavyProgram
    val summary = when {
        vitals == null -> null
        vitals.heat == null || vitals.heat == LinuxHeat.Cool -> vitals.linuxMemoryBytes?.let { memory ->
            stringResource(if (vitals.heat == null) Res.string.linuxVitalsSummary else Res.string.linuxVitalsSummaryCool, Numbers.digits(LinuxSize.format(memory)))
        }
        named -> stringResource(Res.string.linuxVitalsHeavy, heat.orEmpty(), vitals.heaviestProgram.orEmpty(), Numbers.format(vitals.heaviestCpuPercent!!.roundToInt()))
        else -> vitals.linuxCpuPercent?.let { cpu -> stringResource(Res.string.linuxVitalsNotLinux, heat.orEmpty(), Numbers.format(cpu.roundToInt())) }
    }
    KitVitalsStrip(
        vitals = cells,
        summary = summary,
        loading = vitals == null,
        onSummaryTap = openRunning.takeIf { named },
        summaryLabel = if (named) stringResource(Res.string.linuxVitalsOpenRunning) else null,
    )
}
