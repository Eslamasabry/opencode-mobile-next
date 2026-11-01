package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.engine.runtime.host.RuntimeCall
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.sync.Mutex

/**
 * Whether the phone server should be updated to the pinned OpenCode now (port of lib/state/in_app_auto_update.dart
 * `shouldAutoUpdateInApp`; B-LINUX-078). Only when it is behind, and never under running work, over another job, or on
 * a half-finished switch or install. One automatic try per app run and pinned version: a failure is left to the manual
 * "Update OpenCode" row. An unknown version does nothing; a leading `v` is ignored.
 */
public object OpenCodeAutoUpdateRule {
    public fun shouldUpdate(
        installedVersion: String?,
        pinnedVersion: String,
        busy: Boolean,
        engineRunning: Boolean,
        switchPending: Boolean,
        installProblem: Boolean,
        attemptedThisRun: Boolean,
    ): Boolean {
        val installed = installedVersion?.trim()?.removePrefix("v")
        if (installed.isNullOrEmpty()) return false
        if (installed == pinnedVersion) return false
        if (attemptedThisRun || busy || engineRunning) return false
        if (switchPending || installProblem) return false
        return true
    }
}

/** What the auto-updater reads about the phone server's OpenCode (This phone's host model). */
public interface OpenCodeHostFacts {
    /** Reads the status again: it may be older than the work that just ended. */
    public suspend fun refresh()

    /** The installed OpenCode version; null when not installed or unknown. */
    public val installedVersion: String?

    /** The version this app release pins (ComponentRegistry; S3-PINS moves pins only with an app release). */
    public val pinnedVersion: String

    /** The runtime the pin belongs to (`openCode2`). */
    public val runtime: String
    public val switchPending: Boolean
    public val installProblem: Boolean
}

/** The setup engine's face the auto-updater uses: the same job as the "Update OpenCode" row. */
public interface OpenCodeUpdateJobs {
    public val running: Boolean
    public val failed: Boolean

    /** Runs the `opencode` component job for [runtime]. */
    public suspend fun updateOpenCode(runtime: String)
}

/**
 * Keeps the phone server on the version this app pins, by itself (port of `InAppAutoUpdater`; B-LINUX-079). The
 * binding calls [changed] once at start and whenever work ends or starts and whenever the host's state changes (no
 * polling of its own); a call while one is deciding returns at once.
 */
public class OpenCodeAutoUpdater(
    private val host: OpenCodeHostFacts,
    private val jobs: OpenCodeUpdateJobs,
    private val workBusy: () -> Boolean,
) {
    private val attempted = mutableSetOf<String>()
    private val checking = Mutex()

    public suspend fun changed() {
        if (!checking.tryLock()) return
        try {
            val key = "${host.runtime}:${host.pinnedVersion}"
            if (!decide(key)) return
            host.refresh()
            if (!decide(key)) return
            attempted += key
            jobs.updateOpenCode(host.runtime)
        } finally {
            checking.unlock()
        }
    }

    private fun decide(key: String): Boolean =
        OpenCodeAutoUpdateRule.shouldUpdate(
            installedVersion = host.installedVersion,
            pinnedVersion = host.pinnedVersion,
            busy = workBusy(),
            engineRunning = jobs.running,
            switchPending = host.switchPending,
            installProblem = host.installProblem || jobs.failed,
            attemptedThisRun = key in attempted,
        )
}

/** The OpenCode component journal inside Ubuntu (setup's activation step). */
public interface ComponentJournal {
    /** Commits the staged `*.oc-pending` binary (the previous one stays as `*.oc-good`); answers the new version. */
    public suspend fun activate(): RuntimeCall<String>

    /** Puts `*.oc-good` back as the committed binary. */
    public suspend fun rollBack(): RuntimeCall<Unit>
}

/** How the restart after an OpenCode update ended. The caller words it (one "Updated OpenCode to …" line). */
public sealed interface UpdateRestartResult {
    /** The work is not idle: nothing was activated; ask again when it is. */
    public data object NotIdle : UpdateRestartResult

    /** The staged binary could not be committed: the old one stays, nothing restarted. */
    public data object NotActivated : UpdateRestartResult

    /**
     * The new version is committed. [restarted] is false when the server was not running or the person had stopped
     * it: it stays stopped and picks the new version up at its next start.
     */
    public data class Updated(val version: String, val restarted: Boolean) : UpdateRestartResult

    /** The new version did not come up: the journal went back to the last good binary, which was started again. */
    public data class RolledBack(val version: String, val oldRestarted: Boolean, val failure: StartFailure) : UpdateRestartResult
}

/**
 * The phone server restarts itself after an OpenCode component update (plan 11 §6.4 R7; freeze tip). It runs only when
 * the phone is idle (no setup job, no reply, no handoff: [idle]); after the journalled activation the server restarts
 * **once** through the one start path with `server.restart(…, expectedGeneration)`; a failed restart rolls the journal
 * back to the last good binary and starts the old one. It never restarts a server the person stopped (R6): a stopped
 * or not-running server only gets the new binary for its next start, and a Stop during the restart wins.
 */
public class OpenCodeUpdateRestart(
    private val journal: ComponentJournal,
    private val port: PhoneServerPort,
    private val starter: PhoneServerStarter,
) {
    public suspend fun afterInstall(target: StartTarget, idle: () -> Boolean): UpdateRestartResult {
        if (!idle()) return UpdateRestartResult.NotIdle
        val before = status()
        val version =
            when (val activated = journal.activate()) {
                is RuntimeCall.Refused -> return UpdateRestartResult.NotActivated
                is RuntimeCall.Done -> activated.value
            }
        val generation = before?.serverRecoveryGeneration
        if (before == null || !before.serverRunning || !before.serverRestartWanted || generation == null) {
            return UpdateRestartResult.Updated(version, restarted = false)
        }
        val failure =
            starter.start(target, LaunchMode.AfterUpdate(generation))
                ?: return UpdateRestartResult.Updated(version, restarted = true)
        val now = status()
        // A Stop (or a withdrawn start) is not a broken binary: keep the new version and leave the server stopped.
        if (failure.problem == StartProblem.Interrupted || now == null || !now.serverRestartWanted) {
            return UpdateRestartResult.Updated(version, restarted = false)
        }
        journal.rollBack()
        val again = now.serverRecoveryGeneration
        val oldBack = again != null && starter.start(target, LaunchMode.AfterUpdate(again)) == null
        return UpdateRestartResult.RolledBack(version, oldBack, failure)
    }

    private suspend fun status(): RuntimeStatus? = (port.refreshStatus() as? RuntimeCall.Done)?.value
}
