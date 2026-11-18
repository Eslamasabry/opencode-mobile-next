package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the phone server should be updated to the pinned OpenCode now (port of lib/state/in_app_auto_update.dart
 * `shouldAutoUpdateInApp`; B-LINUX-078). Only when it is behind, and never under running work, over another job, or on
 * a half-finished switch or install, and never on metered data (the download is over a hundred megabytes; an unknown
 * network counts as metered, as the agents' update rule). One automatic try per app run and pinned version: a failure
 * is left to the manual "Update OpenCode" row, which runs on any network. An unknown version does nothing; a leading
 * `v` is ignored.
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
        metered: Boolean = false,
    ): Boolean {
        val installed = installedVersion?.trim()?.removePrefix("v")
        if (installed.isNullOrEmpty()) return false
        if (installed == pinnedVersion) return false
        if (attemptedThisRun || busy || engineRunning || metered) return false
        if (switchPending || installProblem) return false
        return true
    }
}

/**
 * Why the update is held back while the phone is behind the pin on a network that is free to download over. Said on
 * Home after [OpenCodeAutoUpdater.TOLD_AFTER], never at once (a short hold ends by itself).
 */
public enum class OpenCodeHold {
    /** A reply (or other work on the server) is running: the update restarts the server, so it waits for it. */
    Busy,

    /** A setup job runs, a switch is half done or the server has a problem: it waits until the server is ready. */
    NotReady,
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

    /**
     * The active network charges for data, or cannot be read (then it counts as metered): the automatic update waits
     * for an unmetered one. Read at every look, so it runs at the first look after the phone is on unmetered data.
     */
    public val metered: Boolean get() = false
}

/** The setup engine's face the auto-updater uses: the same job as the "Update OpenCode" row. */
public interface OpenCodeUpdateJobs {
    public val running: Boolean
    public val failed: Boolean

    /**
     * Runs the `opencode` component job for [runtime]. True when the job started; false when it was refused before
     * it started (work running, server not known idle), which leaves the automatic try unspent.
     */
    public suspend fun updateOpenCode(runtime: String): Boolean
}

/**
 * Keeps the phone server on the version this app pins, by itself (port of `InAppAutoUpdater`; B-LINUX-079). The
 * binding calls [changed] once at start and whenever work ends or starts and whenever the host's state changes (no
 * polling of its own); a call while one is deciding returns at once.
 */
public class OpenCodeAutoUpdater(
    private val host: OpenCodeHostFacts,
    private val jobs: OpenCodeUpdateJobs,
    /** One line per change of what the updater sees: which condition holds it back (no personal data). */
    private val log: ((String) -> Unit)? = null,
    /** A monotonic clock in milliseconds; the default (no clock) never tells a hold. */
    private val nowMs: () -> Long = { 0L },
    /**
     * Asks for one more look [delayMs] from now (the binding runs a delayed, cancel-aware [changed]): a retry waiting
     * for its time does not depend on some other state change to wake it.
     */
    private val wake: (delayMs: Long) -> Unit = {},
    private val workBusy: () -> Boolean,
) {
    private var lastLine: String? = null
    private var holdSince: Long? = null
    private val holding = MutableStateFlow<OpenCodeHold?>(null)

    /**
     * The update has been held back for [TOLD_AFTER] without a break while the phone is behind the pin on an unmetered
     * network, and why. Null while it is not behind, not held, or held only briefly. A metered network has its own
     * line ([waitingForUnmetered]); a failed update job shows as failed, so neither is repeated here.
     */
    public val held: StateFlow<OpenCodeHold?> = holding.asStateFlow()

    private val attempted = mutableSetOf<String>()
    private val checking = Mutex()
    private var meteredAllowed = false
    private val waiting = MutableStateFlow(false)

    /**
     * The server is behind the pin and only the metered (or unknown) network holds the update back: Home says it is
     * waiting for Wi-Fi and offers the person the choice ([allowMeteredOnce]).
     */
    public val waitingForUnmetered: StateFlow<Boolean> = waiting.asStateFlow()

    /**
     * The person chose "Download now" on mobile data: the next update try runs on this network, once. The flag is
     * spent when the update job starts; a try refused before it started keeps it.
     */
    public fun allowMeteredOnce() {
        meteredAllowed = true
    }

    /** The update job this updater started has ended and the installed version is still below the pin, for good. */
    public val fellShort: StateFlow<Boolean> get() = short.asStateFlow()
    private val short = MutableStateFlow(false)

    /** A try ended below the pin and the next waits for its time and an idle phone: Home says it is finishing. */
    public val retrying: StateFlow<Boolean> get() = finishing.asStateFlow()
    private val finishing = MutableStateFlow(false)
    private var retryArmed = false
    private var busyStreak = 0
    private var busyWaiting = false
    private var awaiting: String? = null
    private var sawRunning = false
    private var awaitingSince = 0L
    private var retryNotBefore = 0L
    private val shortfalls = mutableMapOf<String, Int>()

    private var lastRefreshAt = 0L

    /**
     * The one read of the server inside [changed]. A read updates the server's facts, which are among the flows that
     * call [changed] again, so an unspaced read here is a loop of requests (thousands of sockets a minute, then the
     * heap): at most one read in [REFRESH_SPACING_MS], whatever triggered the look. No clock (0): no spacing.
     */
    private suspend fun refreshSpaced() {
        val now = nowMs()
        if (now != 0L && lastRefreshAt != 0L && now - lastRefreshAt < REFRESH_SPACING_MS) return
        lastRefreshAt = now
        withTimeoutOrNull(READ_LIMIT) { host.refresh() }
    }

    private fun belowPin(): Boolean {
        val installed = host.installedVersion?.trim()?.removePrefix("v")
        return !installed.isNullOrEmpty() && installed != host.pinnedVersion
    }

    /**
     * Done is not "installed == pin": once the job this updater started has ended, the installed version is read again.
     * Still below the pin is a failure with its reason: tried again at the next safe look (a bounded number of times,
     * spaced), then left on Home's failed row with Try again.
     */
    private suspend fun verifyEnded(key: String) {
        if (jobs.running) {
            sawRunning = true
            return
        }
        if (!sawRunning && nowMs() - awaitingSince < START_GRACE_MS) return
        awaiting = null
        refreshSpaced()
        if (!belowPin()) {
            short.value = false
            shortfalls.remove(key)
        } else {
            // Below the pin, the job done or failed: a failure after the switch leaves the new program whole on disk
            // while the old one still serves, and the next job (whose check finds the new one in place) only restarts
            // the server. So it is tried again at the next idle look, spaced, a bounded number of times.
            val n = (shortfalls[key] ?: 0) + 1
            shortfalls[key] = n
            note("job ended but installed ${host.installedVersion} is below pin ${host.pinnedVersion} (try $n)")
            if (n < MAX_TRIES) {
                attempted -= key
                retryNotBefore = nowMs() + RETRY_SPACING_MS
                wake(RETRY_SPACING_MS)
                retryArmed = true
                finishing.value = true
            } else {
                short.value = true
                finishing.value = false
            }
        }
    }

    public suspend fun changed() {
        if (!checking.tryLock()) return
        try {
            val key = "${host.runtime}:${host.pinnedVersion}"
            if (awaiting == key) verifyEnded(key)
            if (short.value && !jobs.running) {
                // The person's Try again may have brought it to the pin: read again before keeping the failed row.
                refreshSpaced()
                if (!belowPin()) short.value = false
            }
            if (nowMs() < retryNotBefore) {
                // Waiting for its time: nothing is said (neither hold word is true of a phone that is up and idle), and
                // never a failure; the wake at the deadline, or the next look, goes on from here.
                track(null)
                return
            }
            if (busyWaiting) {
                busyWaiting = false
                note("the wait for the other job is over; trying again")
            }
            if (!decide(key, explain = true)) return
            refreshSpaced()
            if (!decide(key)) return
            // A try refused before the job started is not spent: the next look tries again.
            if (jobs.updateOpenCode(host.runtime)) {
                attempted += key
                awaiting = key
                sawRunning = false
                awaitingSince = nowMs()
                short.value = false
                retryArmed = false
                finishing.value = false
                busyStreak = 0
                meteredAllowed = false
                waiting.value = false
            } else {
                // Busy (work running, or another install job holds the one gate): the try is kept, and nothing spins:
                // the next look is after a backoff (30 s, doubling, at most 2 min), not at once.
                busyStreak++
                retryNotBefore = busyDeadline(nowMs(), busyStreak)
                if (retryNotBefore > 0L) wake(retryNotBefore - nowMs())
                if (!busyWaiting) note("refused before it started (busy); waiting, the try is kept")
                busyWaiting = true
            }
        } finally {
            checking.unlock()
        }
    }

    public companion object {
        /** How long a status read may take before the decision goes on without it. */
        public val READ_LIMIT: kotlin.time.Duration = 15.seconds

        /** A hold shorter than this stays silent (it ends by itself); a longer one is told on Home. */
        public val TOLD_AFTER: kotlin.time.Duration = 2.minutes

        /** Tries in all (the first included) before a job that ends below the pin is left to Try again. */
        internal const val MAX_TRIES: Int = 3
        internal const val RETRY_SPACING_MS: Long = 90_000L
        internal const val START_GRACE_MS: Long = 60_000L
    }

    /** Why the update is held while behind the pin on a free network; null when it goes or is not held. */
    private fun holdReason(
        installed: String?,
        tried: Boolean,
        networkMetered: Boolean,
        go: Boolean,
        busy: Boolean,
    ): OpenCodeHold? {
        val behind = !installed.isNullOrEmpty() && installed != host.pinnedVersion && !tried && !networkMetered
        return when {
            !behind || go -> null
            busy -> OpenCodeHold.Busy
            jobs.running || host.switchPending || host.installProblem -> OpenCodeHold.NotReady
            else -> null
        }
    }

    /** Starts, keeps or ends the hold; [held] says its reason only once it has lasted [TOLD_AFTER]. */
    private fun track(why: OpenCodeHold?) {
        if (why == null) {
            holdSince = null
            holding.value = null
            return
        }
        val since = holdSince ?: nowMs().also { holdSince = it }
        holding.value = if (nowMs() - since >= TOLD_AFTER.inWholeMilliseconds) why else null
    }

    private fun note(line: String) {
        if (line == lastLine) return
        lastLine = line
        log?.invoke(line)
    }

    private fun decide(key: String, explain: Boolean = false): Boolean {
        val installed = host.installedVersion
        val busy = workBusy()
        val running = jobs.running
        val switchPending = host.switchPending
        // A try that ended below the pin is tried again although its job failed: the retry is armed.
        val problem = (host.installProblem || jobs.failed) && !retryArmed
        val tried = key in attempted
        val networkMetered = host.metered
        val metered = networkMetered && !meteredAllowed
        val inst = installed?.trim()?.removePrefix("v")
        waiting.value = !inst.isNullOrEmpty() && inst != host.pinnedVersion && !tried && metered
        val go =
            OpenCodeAutoUpdateRule.shouldUpdate(
                installedVersion = installed,
                pinnedVersion = host.pinnedVersion,
                busy = busy,
                engineRunning = running,
                switchPending = switchPending,
                installProblem = problem,
                attemptedThisRun = tried,
                metered = metered,
            )
        track(holdReason(inst, tried, networkMetered, go, busy))
        if (explain) {
            note(
                "installed=${installed ?: "unknown"} pinned=${host.pinnedVersion} busy=$busy engineRunning=$running " +
                    "switchPending=$switchPending installProblem=$problem metered=$metered attempted=$tried -> " +
                    if (go) "update" else "hold",
            )
        }
        return go
    }
}

/** The OpenCode component journal inside Ubuntu (setup's activation step). */
public interface ComponentJournal {
    /** Commits the staged `*.oc-pending` binary (the previous one stays as `*.oc-good`); answers the new version. */
    public suspend fun activate(): Outcome<String>

    /** Puts `*.oc-good` back as the committed binary. */
    public suspend fun rollBack(): Outcome<Unit>
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
                is Outcome.Failed -> return UpdateRestartResult.NotActivated
                is Outcome.Ok -> activated.value
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

    private suspend fun status(): RuntimeStatus? = (port.refreshStatus() as? Outcome.Ok)?.value
}

private const val BUSY_BACKOFF_MS = 30_000L
private const val BUSY_BACKOFF_MAX_MS = 120_000L
private const val BUSY_DOUBLINGS = 3
private const val REFRESH_SPACING_MS = 30_000L

/** When a busy-refused try looks again: 30 s, doubling, at most 2 min. No clock (0) means no backoff. */
private fun busyDeadline(now: Long, streak: Int): Long =
    if (now == 0L) {
        0L
    } else {
        now + minOf(BUSY_BACKOFF_MS shl (streak - 1).coerceAtMost(BUSY_DOUBLINGS), BUSY_BACKOFF_MAX_MS)
    }
