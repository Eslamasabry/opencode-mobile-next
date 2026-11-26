package io.github.eslamasabry.oc.feature.chat.work

import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName

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
    public fun of(shells: List<ManagedShell>): List<BackgroundMoment> {
        if (shells.isEmpty()) return emptyList()
        return shells
            .distinctBy { it.id }
            .sortedWith(compareBy<ManagedShell>({ it.startedAtMs }, { it.id }))
            .mapNotNull(::momentOf)
    }

    private fun momentOf(shell: ManagedShell): BackgroundMoment? {
        val end = endOf(shell) ?: return null
        val endedAtMs = if (end == MomentEnd.Running) null else shell.completedAtMs
        val tookMs = endedAtMs?.let { (it - shell.startedAtMs).coerceAtLeast(0L) }
        return BackgroundMoment(
            key = RunningWork.SHELL_KEY + shell.id,
            name = backgroundTaskName(shell.command),
            startedAtMs = shell.startedAtMs,
            endedAtMs = endedAtMs,
            tookMs = tookMs,
            end = end,
        )
    }

    private fun endOf(shell: ManagedShell): MomentEnd? =
        when (shell.status) {
            ManagedShellStatus.Running -> MomentEnd.Running
            ManagedShellStatus.Exited ->
                when (shell.exitCode) {
                    null -> MomentEnd.Ended
                    0 -> MomentEnd.Worked
                    else -> MomentEnd.Failed
                }
            ManagedShellStatus.Timeout, ManagedShellStatus.Killed -> MomentEnd.Stopped
            ManagedShellStatus.Unknown -> null
        }
}
