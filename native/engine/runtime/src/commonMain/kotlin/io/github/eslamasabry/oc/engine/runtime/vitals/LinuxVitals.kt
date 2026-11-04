package io.github.eslamasabry.oc.engine.runtime.vitals

import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.engine.runtime.procs.PhoneProcessOwner
import io.github.eslamasabry.oc.engine.runtime.procs.RunningSnapshot

public enum class LinuxHeat { Cool, Warm, Hot, TooHot }

public fun linuxHeatOf(status: ThermalStatus): LinuxHeat? = when (status) {
    ThermalStatus.Unknown -> null
    ThermalStatus.None, ThermalStatus.Light -> LinuxHeat.Cool
    ThermalStatus.Moderate -> LinuxHeat.Warm
    ThermalStatus.Severe -> LinuxHeat.Hot
    ThermalStatus.Critical, ThermalStatus.Emergency, ThermalStatus.Shutdown -> LinuxHeat.TooHot
}

/** Unknown sources stay absent; the UI never turns a failed read into a zero. */
public data class LinuxVitals(
    val heat: LinuxHeat? = null,
    val phoneMemoryFreeBytes: Long? = null,
    val linuxCpuPercent: Double? = null,
    val linuxMemoryBytes: Long? = null,
    val heaviestProgram: String? = null,
    val heaviestCpuPercent: Double? = null,
) {
    val hasHeavyProgram: Boolean get() = heaviestProgram != null && (heaviestCpuPercent ?: 0.0) >= 50.0
}

public fun parseMemAvailable(text: String): Long? {
    val line = text.lineSequence().firstOrNull { it.startsWith("MemAvailable:") } ?: return null
    val kb = line.substringAfter(':').trim().substringBefore(' ').substringBefore('\t').toLongOrNull() ?: return null
    return kb.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1024 }?.times(1024)
}

/** CPU is a share of the whole phone; a named program retains its share of one core. */
public fun linuxShareOf(snapshot: RunningSnapshot, cores: Int): LinuxVitals {
    val processes = snapshot.processes.filter { it.owner != PhoneProcessOwner.App }
    val top = processes.maxByOrNull { it.cpuPercent }
    val cpu = processes.sumOf { it.cpuPercent.takeIf { n -> n.isFinite() && n >= 0 } ?: 0.0 }
    var memory = 0L
    for (process in processes) {
        val bytes = process.rssBytes.coerceAtLeast(0)
        memory = if (memory > Long.MAX_VALUE - bytes) Long.MAX_VALUE else memory + bytes
    }
    return LinuxVitals(
        linuxCpuPercent = (if (cores > 0) cpu / cores else cpu).coerceIn(0.0, 100.0),
        linuxMemoryBytes = memory,
        heaviestProgram = top?.name?.takeIf { it.isNotEmpty() },
        heaviestCpuPercent = top?.cpuPercent?.takeIf { it.isFinite() && it >= 0 },
    )
}
