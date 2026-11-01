package io.github.eslamasabry.oc.android.notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What `MainActivity.onStop` knows when it asks whether to start reply watch (plan 11 §9.3). */
public data class ReplyWatchStartFacts(
    val changingConfigurations: Boolean,
    /** Some live connection has a run progressing; a run only waiting on a shown needs-you alert does not count. */
    val runProgressing: Boolean,
    /** `NotificationPolicy.wouldAlert(connection, {FinishedReply, NeedsYou})` (quiet hours: NeedsYou only). */
    val wouldAlert: Boolean,
    val liveModeActive: Boolean,
    /** A durable pause whose reason is the person's own Pause (`userPause`); other reasons do not block. */
    val userPaused: Boolean,
    /** A picker, permission prompt, settings page or installer confirmation of ours is covering the app. */
    val ownRequestCovering: Boolean,
    /** The phone server's `specialUse` service holds row 4747 (owner `server`). */
    val serverHoldsRow: Boolean,
)

public sealed interface ReplyWatchStart {
    /** Start `BackgroundConnectionService` as a `dataSync` foreground service, owner `reply`. */
    public data object StartService : ReplyWatchStart

    /** Join the server-held row as owner `reply`, with no `dataSync` start and no budget spent. */
    public data object JoinServerRow : ReplyWatchStart

    public data class Skip(val why: ReplyWatchSkip) : ReplyWatchStart
}

public enum class ReplyWatchSkip { ConfigurationChange, NothingRunning, WouldNotAlert, LiveModeOn, UserPaused, OwnRequestCovering }

public enum class ReplyWatchEnd { NoProgress, HardCap, AppVisible, Dismissed, SystemTimeout, ServerLeft, StartRefused }

/** The moment-to-moment state the end rule reads. Times are monotonic (`elapsedRealtime`) milliseconds. */
public data class ReplyWatchState(
    val startedAtMs: Long,
    val nowMs: Long,
    val progressing: Boolean,
    /** When the last progressing run stopped progressing; null while one progresses. */
    val idleSinceMs: Long?,
    val appVisible: Boolean,
    val joined: Boolean,
    val serverStillHoldsRow: Boolean,
)

/**
 * Reply watch (S11-4; ADR §9.5; amends B-ANDR-013, D11-2): after the app leaves the screen with a reply still
 * running, a short `dataSync` foreground service keeps the process alive so the finished reply (or the needs-you
 * request) is announced. It costs minutes of Android's 6 h `dataSync` budget per long turn, never unbounded lifetime.
 */
public object ReplyWatchPolicy {
    public const val IDLE_GRACE_MS: Long = 10_000L
    public const val HARD_CAP_MS: Long = 2 * 60 * 60 * 1000L

    public fun onStop(facts: ReplyWatchStartFacts): ReplyWatchStart =
        when {
            facts.changingConfigurations -> ReplyWatchStart.Skip(ReplyWatchSkip.ConfigurationChange)
            // A picker or permission prompt returns to the app; the next clean onStop starts it.
            facts.ownRequestCovering -> ReplyWatchStart.Skip(ReplyWatchSkip.OwnRequestCovering)
            !facts.runProgressing -> ReplyWatchStart.Skip(ReplyWatchSkip.NothingRunning)
            !facts.wouldAlert -> ReplyWatchStart.Skip(ReplyWatchSkip.WouldNotAlert)
            facts.liveModeActive -> ReplyWatchStart.Skip(ReplyWatchSkip.LiveModeOn)
            facts.userPaused -> ReplyWatchStart.Skip(ReplyWatchSkip.UserPaused)
            facts.serverHoldsRow -> ReplyWatchStart.JoinServerRow
            else -> ReplyWatchStart.StartService
        }

    /** Null while the watch should go on. Dismissal and the system timeout arrive as events, not as state. */
    public fun end(state: ReplyWatchState): ReplyWatchEnd? =
        when {
            state.appVisible -> ReplyWatchEnd.AppVisible
            // A joined watch ends with the server owner: a background dataSync start would be refused.
            state.joined && !state.serverStillHoldsRow -> ReplyWatchEnd.ServerLeft
            state.nowMs - state.startedAtMs >= HARD_CAP_MS -> ReplyWatchEnd.HardCap
            !state.progressing && state.idleSinceMs != null && state.nowMs - state.idleSinceMs >= IDLE_GRACE_MS ->
                ReplyWatchEnd.NoProgress
            else -> null
        }
}

/**
 * What reply watch reads from the engines (`:engine:connection` run state, `:engine:attention` copy and policy).
 * Bound in the app graph; this module never reaches into a connection itself.
 */
public interface ReplyRunSource {
    /** Some run is progressing (a run only waiting on a shown needs-you alert is not). */
    public val progressing: StateFlow<Boolean>

    /** The row's words while watching (the "working" content of live mode); null = the module's fallback words. */
    public val status: StateFlow<StatusContent?>

    /** Finished, stopped and needs-you alerts as runs settle or ask. */
    public val alerts: Flow<CodingAlert>

    public val appVisible: StateFlow<Boolean>

    /** The phone server's service still holds row 4747 (for a joined watch). */
    public val serverHoldsRow: StateFlow<Boolean>

    /** The posting facts at this moment (permission, quiet hours, the server's alert rules, …). */
    public fun alertFacts(alert: CodingAlert): AlertFacts
}

/**
 * The reply-watch half of `BackgroundConnectionServiceBody` (plan 11 §9.2 rules 1, 3, 5, 6; §9.3). The
 * `BackgroundConnectionService` shell in `:androidApp` forwards `onStartCommand`, both `onTimeout`s and `onDestroy`.
 *
 * **Promise first:** [onStartCommand] calls `ServiceCompat.startForeground(…, dataSync)` before any other work. A
 * refused start (`ForegroundServiceStartNotAllowedException`, background limit) is a stop, never a crash.
 */
public class ReplyWatchServiceBody(
    private val context: Context,
    private val words: NotifyWords,
    private val renderer: StatusRenderer,
    private val shade: AlertShade,
    private val source: ReplyRunSource,
    private val scope: CoroutineScope,
    /** Monotonic milliseconds (`SystemClock::elapsedRealtime` in the app; virtual time in tests). */
    private val elapsedMs: () -> Long,
    private val tickMs: Long = 1_000L,
) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val throttle = StatusThrottle()
    private val _ended = MutableStateFlow<ReplyWatchEnd?>(null)
    private val dismissedRuns = mutableSetOf<String>()
    private var job: Job? = null
    private var service: Service? = null

    /** Why the last watch ended; null while one runs or before the first. */
    public val ended: StateFlow<ReplyWatchEnd?> = _ended.asStateFlow()

    public val active: Boolean get() = job?.isActive == true

    /** The service's `onStartCommand`. Returns `START_NOT_STICKY`: a killed watch is never restarted by itself. */
    public fun onStartCommand(service: Service): Int {
        this.service = service
        val promised =
            try {
                ServiceCompat.startForeground(
                    service,
                    NotificationIds.STATUS,
                    renderer.build(currentContent(), ongoing = true, chip = chip(currentContent())),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                true
            } catch (_: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) and the API 34+ type checks: a pause, not a crash.
                false
            } catch (_: SecurityException) {
                false
            }
        if (!promised) {
            finish(ReplyWatchEnd.StartRefused)
            return Service.START_NOT_STICKY
        }
        watch(joined = false)
        return Service.START_NOT_STICKY
    }

    /** Joins the server-held row as owner `reply` (no foreground start, no budget spent). */
    public fun joinServerRow() {
        watch(joined = true)
    }

    /** `onTimeout(startId, fgsType)` (API 35+) and `onTimeout(startId)` (API 34): stop within seconds. */
    public fun onTimeout() {
        finish(ReplyWatchEnd.SystemTimeout)
    }

    /** The person swiped the row away while `reply` was its only owner: the watch ends for that run only. */
    public fun onRowDismissed(session: String) {
        synchronized(dismissedRuns) { dismissedRuns += session }
        if (active) finish(ReplyWatchEnd.Dismissed)
    }

    public fun onDestroy() {
        job?.cancel()
        job = null
        service = null
    }

    private fun watch(joined: Boolean) {
        if (active) return
        _ended.value = null
        throttle.reset()
        val startedAt = elapsedMs()
        job =
            scope.launch {
                launch { source.alerts.collect { announce(it) } }
                var idleSince: Long? = null
                while (true) {
                    val now = elapsedMs()
                    val progressing = source.progressing.value
                    idleSince = if (progressing) null else idleSince ?: now
                    val end =
                        ReplyWatchPolicy.end(
                            ReplyWatchState(
                                startedAtMs = startedAt,
                                nowMs = now,
                                progressing = progressing,
                                idleSinceMs = idleSince,
                                appVisible = source.appVisible.value,
                                joined = joined,
                                serverStillHoldsRow = source.serverHoldsRow.value,
                            ),
                        )
                    if (end != null) {
                        finish(end)
                        break
                    }
                    if (!joined) refreshRow(now)
                    delay(tickMs)
                }
            }
    }

    private fun announce(alert: CodingAlert) {
        val route = AlertPolicy.route(alert, source.alertFacts(alert).copy(replyWatchActive = true))
        if (route is AlertRoute.Post) shade.show(alert, route)
    }

    private fun refreshRow(now: Long) {
        val content = currentContent()
        val next =
            when (val step = throttle.offer(content, now)) {
                is StatusThrottle.Step.Post -> step.content
                else -> throttle.due(now)
            } ?: return
        try {
            manager.notify(NotificationIds.STATUS, renderer.build(next, ongoing = true, chip = chip(next)))
        } catch (_: SecurityException) {
            // Permission revoked while watching: the alerts are blocked too; the end rules still apply.
        }
    }

    private fun currentContent(): StatusContent =
        source.status.value ?: StatusContent(
            kind = StatusKind.Working,
            title = words.text(R.string.notify_reply_watch_title),
            publicTitle = words.text(R.string.notify_status_public_working),
        )

    private fun chip(content: StatusContent): LiveChip? {
        val dismissed = synchronized(dismissedRuns) { content.sessionId.isNotEmpty() && content.sessionId in dismissedRuns }
        return LiveUpdatePolicy.chipFor(content, dismissedRun = dismissed)
    }

    private fun finish(reason: ReplyWatchEnd) {
        _ended.value = reason
        val running = job
        job = null
        service?.let { s ->
            ServiceCompat.stopForeground(s, ServiceCompat.STOP_FOREGROUND_REMOVE)
            s.stopSelf()
        }
        running?.cancel()
    }
}
