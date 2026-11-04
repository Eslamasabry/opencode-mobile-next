package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.seconds

public enum class LinuxHeat {
    Cool,
    Warm,
    Hot,
    TooHot,
}

public data class LinuxVitalsUi(
    val heat: LinuxHeat? = null,
    val phoneFreeBytes: Long? = null,
    val linuxMemoryBytes: Long? = null,
    val linuxCpuPercent: Int? = null,
    val heavyProgram: String? = null,
    val heavyPercent: Int? = null,
)

/** Read only while the Linux manager is visible. Unknown observations remain absent. */
public interface LinuxVitalsSource {
    public suspend fun read(): Outcome<LinuxVitalsUi>
}

public val LinuxVitalsTimer: TimerSpec =
    TimerSpec(
        "linux.vitals",
        Cadence(foreground = 5.seconds, hiddenWatching = null, hiddenIdle = null),
        "The visible Linux manager shows measured heat, free memory and Linux CPU.",
        listOf("B-LINUX-008"),
    )
