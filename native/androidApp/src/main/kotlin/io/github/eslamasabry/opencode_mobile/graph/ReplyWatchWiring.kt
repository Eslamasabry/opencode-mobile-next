package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKeys
import io.github.eslamasabry.oc.android.notifications.AndroidAlertShade
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.android.notifications.ReplyAlert
import io.github.eslamasabry.oc.android.notifications.ReplyProgressTracker
import io.github.eslamasabry.oc.android.notifications.ReplyRun
import io.github.eslamasabry.oc.android.notifications.ReplyRunFact
import io.github.eslamasabry.oc.android.notifications.ReplyRunSource
import io.github.eslamasabry.oc.android.notifications.ReplyWatchPolicy
import io.github.eslamasabry.oc.android.notifications.ServerRowPolicy
import io.github.eslamasabry.oc.android.notifications.ServerRowStep
import io.github.eslamasabry.oc.android.notifications.StatusThrottle
import io.github.eslamasabry.oc.android.notifications.LiveUpdatePolicy
import io.github.eslamasabry.oc.android.notifications.NotificationIds
import io.github.eslamasabry.oc.android.notifications.ReplyWatchServiceBody
import io.github.eslamasabry.oc.android.notifications.ReplyWatchStart
import io.github.eslamasabry.oc.android.notifications.ReplyWatchStartFacts
import io.github.eslamasabry.oc.android.notifications.StatusContent
import io.github.eslamasabry.oc.android.notifications.StatusOwner
import io.github.eslamasabry.oc.android.notifications.StatusOwners
import io.github.eslamasabry.oc.android.notifications.StatusRenderer
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Whether reply watch is on now. Its own tiny holder, so the alert hub can read it without a dependency cycle. */
@SingleIn(AppScope::class)
@Inject
class ReplyWatchFlag {
    private val mutable = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutable

    internal fun set(on: Boolean) {
        mutable.value = on
    }
}

/**
 * Reply watch made real (plan 11 §9.3, S11-4; owner decision D11-2). The person leaves the app while a reply runs and
 * "Stay connected" is off: a short `dataSync` foreground service in the frozen `BackgroundConnectionService` keeps the
 * process (and so the event stream) alive until the reply settles, so the finished reply, a request or a failure
 * reaches them as an alert (posted by [PlatformHub], which treats an active watch as it treats live mode). When the
 * phone server's own `specialUse` service already keeps the process, the watch joins that row and spends no budget:
 * it shows its working words there while it runs and gives the server's own row back at the end.
 *
 * Starts on the last activity's `onStop` ([ReplyWatchPolicy.onStop]); ends 10 s after nothing progresses, at the 2 h
 * cap, when the app shows again, when the row is swiped away, on Android's `onTimeout`, or when live mode takes over
 * ([ReplyWatchPolicy.end]). It never restarts by itself (`START_NOT_STICKY`); a refused start is a skip, not a crash.
 */
@SingleIn(AppScope::class)
@Inject
class ReplyWatchHub(
    private val application: Application,
    appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val background: BackgroundHub,
    private val attention: AttentionRuntime,
    private val visibility: AppVisibility,
    private val preferences: AppPreferences,
    private val flag: ReplyWatchFlag,
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val progress = ReplyProgressTracker()
    private val progressing = MutableStateFlow(false)
    private val serverHoldsRow = MutableStateFlow(false)
    private val started = mutableSetOf<Activity>()
    private var hubStarted = false
    private var decision: Job? = null
    private var follow: Job? = null
    private var rearmJob: Job? = null

    /** The one owner set of row 4747 that the reply-watch body and the service shell share (plan 11 §9.2 rule 2). */
    val owners = StatusOwners()

    private val source =
        object : ReplyRunSource {
            override val progressing: StateFlow<Boolean> = this@ReplyWatchHub.progressing
            override val status: StateFlow<StatusContent?> = attention.conversationsRow
            override val runs: StateFlow<Set<ReplyRun>> =
                attention.conversationsRow
                    .map { setOfNotNull(it?.run) }
                    .stateIn(scope, SharingStarted.Eagerly, setOfNotNull(attention.conversationsRow.value?.run))

            // PlatformHub posts every alert (one producer, its dedupe and claims); the watch only keeps them possible.
            override val pendingAlerts: StateFlow<List<ReplyAlert>> = MutableStateFlow(emptyList())

            override fun acknowledgeAlert(id: String) = Unit

            override val appVisible: StateFlow<Boolean> =
                visibility.state
                    .map { it == Visibility.Foreground }
                    .stateIn(scope, SharingStarted.Eagerly, visibility.state.value == Visibility.Foreground)
            override val serverHoldsRow: StateFlow<Boolean> = this@ReplyWatchHub.serverHoldsRow

            override fun returnRowToServer(): Boolean = this@ReplyWatchHub.settleServerRow(watchEnding = true)

            override fun alertFacts(alert: CodingAlert): AlertFacts =
                AlertFacts(
                    liveModeActive = background.live.value.let { it.enabled && it.active },
                    replyWatchActive = true,
                    appVisible = appVisible.value,
                    permissionGranted = application.platformHub.backgroundAlertsPossible(),
                    serverAlertsOn = true,
                    quietHours = false,
                    onWifi = null,
                    wifiOnly = false,
                )
        }

    val body: ReplyWatchServiceBody by lazy {
        val manager = application.getSystemService(NotificationManager::class.java)
        ReplyWatchServiceBody(
            context = application,
            words = words,
            renderer = StatusRenderer(application, words),
            shade = AndroidAlertShade(application, words, ChannelRegistry(manager, words)),
            source = source,
            owners = owners,
            scope = scope,
            elapsedMs = SystemClock::elapsedRealtime,
        )
    }

    /** Called once from `OcApplication.startProduct`, before the first activity starts. */
    fun start() {
        if (hubStarted) return
        hubStarted = true
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            body.ended.collect { end ->
                if (end == null) return@collect
                flag.set(false)
                follow?.cancel()
                follow = null
                // Live mode still holds the row: its own content and Pause action come back at once.
                if (background.live.value.active) attention.repost()
                if (ReplyWatchPolicy.rearms(end)) rearm()
            }
        }
        scope.launch { background.live.collect { if (it.active && flag.active.value) body.yieldToLive() } }
        // The server's own row must not say "nothing running" while a conversation works or waits for the person.
        // A flow collection in the app process only (no service, no timer of its own): it follows the dock's state.
        scope.launch {
            combine(attention.conversationsRow, source.appVisible, flag.active, background.live) { _, _, _, _ -> Unit }
                .collect { settleServerRow(watchEnding = false) }
        }
    }

    /**
     * Whether the conversations' words stand in the phone server's row now (they replace "Ready · nothing running"
     * until nothing runs and nothing waits). Only touched on the main dispatcher.
     */
    private var lent = false
    private val lendThrottle = StatusThrottle(StatusThrottle.DOCK_GAP_MS)
    private var lendTrailing: Job? = null
    private val lendRenderer by lazy { StatusRenderer(application, words) }

    /**
     * Puts the right words into the phone server's row: the conversations' (working, needs you) when any exist, the
     * server's own otherwise. [watchEnding] is the reply watch giving the row back. False when no service holds the row.
     */
    private fun settleServerRow(watchEnding: Boolean): Boolean {
        val row = attention.conversationsRow.value
        val visible = source.appVisible.value
        val step =
            ServerRowPolicy.step(
                conversationsShown = row != null,
                lent = lent,
                replyWatchActive = !watchEnding && flag.active.value,
                liveModeActive = background.live.value.active,
                appVisible = visible,
            )
        if (step == ServerRowStep.Leave && !watchEnding) return true
        return try {
            val linux = BuiltinLinux.get(application)
            if (step == ServerRowStep.Lend && row != null) {
                lendRow(linux, row)
            } else {
                lent = false
                lendTrailing?.cancel()
                lendTrailing = null
                lendThrottle.reset()
                linux.repostStatusRow()
            }
        } catch (_: RuntimeException) {
            // Never into the watch. A cancel cannot remove a row a live foreground service still holds.
            false
        }
    }

    private fun lendRow(linux: BuiltinLinux, row: StatusContent): Boolean {
        if (!linux.holdsStatusRow()) {
            lent = false
            return false
        }
        lent = true
        when (val step = lendThrottle.offer(row, SystemClock.elapsedRealtime())) {
            is StatusThrottle.Step.Post -> postLent(step.content)
            is StatusThrottle.Step.Wait ->
                if (lendTrailing == null)
                    lendTrailing =
                        scope.launch {
                            delay(step.delayMs)
                            lendTrailing = null
                            if (lent) lendThrottle.due(SystemClock.elapsedRealtime())?.let(::postLent)
                        }
            StatusThrottle.Step.Skip -> Unit
        }
        return true
    }

    private fun postLent(content: StatusContent) {
        try {
            application.getSystemService(NotificationManager::class.java)
                .notify(
                    NotificationIds.STATUS,
                    lendRenderer.build(content, ongoing = true, chip = LiveUpdatePolicy.chipFor(content, false)),
                )
        } catch (_: SecurityException) {
            // Permission revoked: nothing of ours can show anyway.
        }
    }

    /** `BackgroundConnectionService.onStartCommand` for [ACTION_REPLY_WATCH]: the body keeps the promise first. */
    fun onServiceStart(service: Service): Int {
        val result = body.onStartCommand(service)
        activated()
        return result
    }

    /** The service shell is being destroyed: a watch it held ends with it (a joined watch does not). */
    fun onServiceDestroy(service: Service) {
        if (body.holds(service)) body.onDestroy()
    }

    /** The service holds a reply-watch `dataSync` promise now (the widget path borrows it). */
    fun holds(service: Service): Boolean = body.holds(service)

    /** Live mode's share of the row, so a reply watch ending on the same service never stops it (§9.2 rule 2). */
    fun liveOwnerBegan() {
        owners.begin(StatusOwner.Live, attention.row.value)
    }

    fun liveOwnerEnded() {
        owners.end(StatusOwner.Live) { }
    }

    override fun onActivityStarted(activity: Activity) {
        started += activity
        decision?.cancel()
        decision = null
        rearmJob?.cancel()
        rearmJob = null
    }

    override fun onActivityStopped(activity: Activity) {
        started -= activity
        if (started.isNotEmpty()) return
        val changing = activity.isChangingConfigurations
        val covering = ownRequestCovering()
        decision?.cancel()
        decision = scope.launch { decide(changing, covering) }
    }

    private suspend fun decide(changing: Boolean, covering: Boolean) {
        // A short read off the main thread; Android allows the start for a few seconds after the app hides.
        val server = withTimeoutOrNull(SERVER_READ_MS) { serverRunning() } == true
        if (started.isNotEmpty()) return
        val now = SystemClock.elapsedRealtime()
        progressing.value = measure(now)
        serverHoldsRow.value = server
        val live = background.live.value
        val facts =
            ReplyWatchStartFacts(
                changingConfigurations = changing,
                runProgressing = progressing.value,
                wouldAlert = application.platformHub.backgroundAlertsPossible(),
                liveModeActive = live.enabled && live.active,
                userPaused = live.pause?.reason == PauseReason.UserStopped,
                ownRequestCovering = covering,
                serverHoldsRow = server,
            )
        when (ReplyWatchPolicy.onStop(facts)) {
            ReplyWatchStart.StartService -> startService()
            ReplyWatchStart.JoinServerRow -> {
                body.joinServerRow()
                activated()
            }
            is ReplyWatchStart.Skip -> Unit
        }
    }

    /**
     * The watch ended because nothing progressed (a request alert was shown, say) while the app stayed hidden: when a
     * run moves again (the person answered from the shade), the same start decision runs again, so its finished reply
     * is still announced. Only a flow collection, no service; it stops when the app shows or at the 2 h cap.
     */
    private fun rearm() {
        rearmJob?.cancel()
        if (started.isNotEmpty()) return
        rearmJob =
            scope.launch {
                withTimeoutOrNull(ReplyWatchPolicy.HARD_CAP_MS) {
                    runtime.snapshot.first {
                        started.isEmpty() && !body.active && measure(SystemClock.elapsedRealtime())
                    }
                } ?: return@launch
                if (started.isEmpty() && !body.active) decide(changing = false, covering = false)
            }
    }

    override fun onActivityDestroyed(activity: Activity) {
        started -= activity
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun startService() {
        val intent =
            Intent()
                .setClassName(application.packageName, SERVICE_FQCN)
                .setAction(ACTION_REPLY_WATCH)
        try {
            ContextCompat.startForegroundService(application, intent)
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+), the spent dataSync budget (Android 15+):
            // no watch this time; the next leave tries again.
        } catch (_: SecurityException) {
            // Same: never a crash.
        }
    }

    private fun activated() {
        if (!body.active) return
        flag.set(true)
        follow?.cancel()
        // While the watch runs only: what "progressing" means and whether the server still holds the row.
        follow =
            scope.launch {
                var serverReadAt = SystemClock.elapsedRealtime()
                while (isActive) {
                    if (!body.active) {
                        // Ended between two looks at `ended` (two quick ends with the same reason): never stay on.
                        flag.set(false)
                        break
                    }
                    val now = SystemClock.elapsedRealtime()
                    progressing.value = measure(now)
                    if (now - serverReadAt >= SERVER_POLL_MS) {
                        serverHoldsRow.value = serverRunning()
                        serverReadAt = now
                    }
                    delay(FOLLOW_TICK_MS)
                }
            }
    }

    private fun measure(nowMs: Long): Boolean {
        val live = registry.live.value
        val hub = application.platformHub
        val facts =
            runtime.snapshot.value.items
                .filter { it.profileId in live }
                .map {
                    ReplyRunFact(
                        requestKey = AlertKeys.input(it.profileId.raw, it.sessionId.raw),
                        running = it.status == ChatStatus.Running,
                        waiting = it.status == ChatStatus.NeedsYou,
                    )
                }
        return progress.progressing(facts, hub.shownRequestKeys(), hub.requestAlertsOn(), nowMs)
    }

    private suspend fun serverRunning(): Boolean =
        withContext(dispatchers.io) {
            try {
                BuiltinLinux.get(application).serverRunning
            } catch (_: RuntimeException) {
                false
            }
        }

    /**
     * A picker, permission prompt or camera of ours runs on top of the app in the app's own task: the person comes
     * back from it, so its `onStop` starts nothing (plan 11 §9.3). A settings page opened in its own task is not seen
     * here; the watch then ends as soon as the app shows again.
     */
    private fun ownRequestCovering(): Boolean =
        try {
            application.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty().any { task ->
                val top = task.taskInfo?.topActivity
                top != null && top.packageName != application.packageName
            }
        } catch (_: RuntimeException) {
            false
        }

    companion object {
        /** Not a frozen name: only this app's own start reaches the service with it. */
        const val ACTION_REPLY_WATCH = "io.github.eslamasabry.opencode_mobile.action.REPLY_WATCH"
        private const val SERVICE_FQCN = "io.github.eslamasabry.opencode_mobile.BackgroundConnectionService"
        private const val SERVER_READ_MS = 400L
        private const val SERVER_POLL_MS = 5_000L
        private const val FOLLOW_TICK_MS = 1_000L
    }
}

@ContributesTo(AppScope::class)
interface ReplyWatchAccessors {
    val replyWatch: ReplyWatchHub
}

internal val Context.replyWatch: ReplyWatchHub
    get() = (appGraph as ReplyWatchAccessors).replyWatch
