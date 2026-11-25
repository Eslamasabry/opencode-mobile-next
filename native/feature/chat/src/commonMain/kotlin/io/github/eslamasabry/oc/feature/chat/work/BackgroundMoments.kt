package io.github.eslamasabry.oc.feature.chat.work

import io.github.eslamasabry.oc.core.model.ManagedShell

public enum class MomentEnd {
    Running,
    Worked,
    Failed,
    Stopped,
    Ended,
}

public data class BackgroundMoment(
    val key: String,
    val name: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val tookMs: Long?,
    val end: MomentEnd,
)

public object BackgroundMoments {
    public fun of(shells: List<ManagedShell>): List<BackgroundMoment> = emptyList()
}
