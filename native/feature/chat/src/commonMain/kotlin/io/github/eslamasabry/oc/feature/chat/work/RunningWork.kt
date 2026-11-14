package io.github.eslamasabry.oc.feature.chat.work

import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName

/*
 * What a conversation says about the work its agent keeps running besides the reply (owner 2026-11-14: "Where is the
 * option to see the background activity and the background agent and send to background?"). Two sources, read by
 * what the server reports, never by its kind: the agent's own stream (a step that went on in the background until a
 * notice ends it, as Claude Code's `run_in_background` commands arrive through the agent helper) and the server's
 * command list (OpenCode 2's `/shell`, this conversation's rows). Sub-agents are the Relations page's; the sheet links
 * there where the server has sub-agent conversations.
 */
public object RunningWork {
    /** The key prefix of a server command shown as background work (never a step's call id). */
    public const val SHELL_KEY: String = "shell:"

    /**
     * What the transcript's background line counts, oldest first: the stream's [tasks], then this conversation's
     * [shells] still running once the reply has ended ([replyBusy] false). While the reply runs, its own command is
     * the live line's, not background work.
     */
    public fun lineTasks(
        tasks: List<BackgroundTask>,
        shells: List<ManagedShell>,
        replyBusy: Boolean,
    ): List<BackgroundTask> {
        if (replyBusy) return tasks
        val running = shells.filter { it.status == ManagedShellStatus.Running }
        if (running.isEmpty()) return tasks
        return tasks + running.sortedBy { it.startedAtMs }.map(::asTask)
    }

    /** A server command as the line words it: a plain name, never the command itself (that is the opened row's). */
    public fun asTask(shell: ManagedShell): BackgroundTask =
        BackgroundTask(
            callId = SHELL_KEY + shell.id,
            messageId = "",
            sinceMs = shell.startedAtMs,
            name = backgroundTaskName(shell.command),
            command = shell.command,
        )

    /** The "…" menu's count: every piece of work running besides the reply. */
    public fun count(tasks: List<BackgroundTask>, shells: List<ManagedShell>): Int =
        tasks.size + shells.count { it.status == ManagedShellStatus.Running }

    /**
     * Whether this server tells the app about background work at all: it lists commands ([shellsSupported]), keeps
     * sub-agent conversations ([subagentSessions]), or its stream has just reported some ([tasks]). False only where
     * the sheet must say the server does not report it.
     */
    public fun reported(shellsSupported: Boolean, subagentSessions: Boolean, tasks: List<BackgroundTask>): Boolean =
        shellsSupported || subagentSessions || tasks.isNotEmpty()
}
