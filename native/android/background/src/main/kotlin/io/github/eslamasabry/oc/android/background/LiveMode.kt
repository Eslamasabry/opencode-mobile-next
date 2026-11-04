package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the last live-mode change failed; one sentence each in `strings_background.xml` (`background_error_*`). */
public enum class LiveModeError {
    /** "Notification access is required." */
    NotificationsRequired,

    /** "Android could not change background mode." */
    CouldNotChange,

    /** "Background live mode is available in the Android app." (no Android host answered) */
    AndroidAppOnly,
}

/** Everything a screen reads about live mode (Flutter `BackgroundLiveController` fields). */
public data class LiveModeState(
    val enabled: Boolean,
    val active: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val busy: Boolean = false,
    val lastError: LiveModeError? = null,
    val pause: PauseState = PauseState.Unsupported,
    /**
     * Android's foreground-service time limit stopped the service; cleared the next time live mode is turned back on.
     * Not an error: nothing can be retried right now.
     */
    val stoppedByAndroidTimeout: Boolean = false,
) {
    /**
     * A coding alert may be posted only while live mode is on and Android lets the app notify (Flutter
     * `showCodingAlert`'s gate). A test notification ignores this gate, never the platform permission.
     */
    val codingAlertsAllowed: Boolean get() = enabled && notificationGranted
}

/**
 * Owns the explicit, person-controlled live mode (port of the Flutter `BackgroundLiveController`, minus the alert and
 * status-row halves that live in `:android:notifications`). It turns on only after Android confirms the service runs;
 * a durable pause wins over an old saved opt-in; and it is the [ForegroundLeasePort] the sign-in terminal reserves.
 *
 * **Confinement:** like the Dart controller on its event loop, every method must be called on one thread (the main
 * thread in the app; the test thread in tests) and [scope] must dispatch on that same thread. State changes happen only
 * between suspensions, so the Flutter revision rules port unchanged. Nothing here assumes unbounded lifetime: Android
 * caps `dataSync` at 6 h per 24 h, and the service's timeout arrives through [onServiceStopped].
 */
public class LiveModeController(
    internal val port: LiveServicePort,
    private val optIn: LiveOptInStore,
    internal val support: BackgroundSupport,
    internal val scope: CoroutineScope,
    private val clock: Clock,
) : ForegroundLeasePort {
    internal var enabled: Boolean = support.supported() && (optIn.read() ?: false)
    internal var active: Boolean = false
    internal var notificationGranted: Boolean = false
    internal var batteryOptimizationIgnored: Boolean = false
    internal var busy: Boolean = false
    internal var lastError: LiveModeError? = null
    internal var pause: PauseState = PauseState.Unsupported
    internal var stoppedByAndroidTimeout: Boolean = false
    internal var pauseRevision: Int = 0
    internal var disposed: Boolean = false
    internal val signIn: SignInForegroundState = SignInForegroundState(this)

    private val _state = MutableStateFlow(snapshot())
    public val state: StateFlow<LiveModeState> = _state.asStateFlow()

    override fun reserve(): ForegroundLease = signIn.reserve()

    /** Cold start: a saved opt-in cannot override a durable OS interruption, and unknown state fails closed. */
    public suspend fun restore() {
        if (disableIfUnsupported()) return
        val savedEnabled = enabled
        val read = refreshPause()
        if (!read.supported || read.paused) return
        run(if (savedEnabled) Method.Enable else Method.Status, persist = false)
    }

    /** Re-reads the receipt (on return to the app). A late reply never overwrites a newer state. */
    public suspend fun refreshPause(): PauseState {
        if (disableIfUnsupported()) return pause
        if (busy) return pause
        val revision = ++pauseRevision
        when (val reply = call { port.pause() }) {
            is ServiceReply.Ok -> {
                if (disableIfUnsupported()) return pause
                if (revision != pauseRevision) return pause
                applyPauseState(reply.value)
                if (pause.paused) optIn.write(false)
            }
            is ServiceReply.Refused -> if (revision == pauseRevision) applyPauseState(PauseState.Unsupported)
        }
        publish()
        return pause
    }

    /** Only for a visible Resume tap. The pause clears only after Android's foreground start; never native text. */
    public suspend fun resume(): ResumeResult {
        if (disableIfUnsupported()) return ResumeResult(pause, ResumeError.Unavailable)
        if (busy) return ResumeResult(pause, ResumeError.Busy)
        busy = true
        lastError = null
        publish()
        val previous = pause
        val revision = ++pauseRevision
        try {
            // A start request Android accepted is not yet a success.
            when (val reply = signIn.invokeEnable()) {
                is ServiceReply.Ok -> {
                    if (disableIfUnsupported()) return ResumeResult(pause, ResumeError.Unavailable)
                    if (revision != pauseRevision) return ResumeResult(pause, ResumeError.ResumeFailed)
                    val receipt = reply.value.pause ?: PauseState.Unsupported
                    if (!receipt.supported || !receipt.active) {
                        applyPauseState(previous)
                        return ResumeResult(pause, ResumeError.ResumeFailed)
                    }
                    applyStatus(reply.value)
                    enabled = true
                    optIn.write(true)
                    if (revision != pauseRevision) {
                        if (!enabled) optIn.write(false)
                        return ResumeResult(pause, ResumeError.ResumeFailed)
                    }
                    return ResumeResult(pause)
                }
                is ServiceReply.Refused -> {
                    if (disableIfUnsupported()) return ResumeResult(pause, ResumeError.Unavailable)
                    if (revision == pauseRevision) applyPauseState(previous)
                    val busyNow = reply.refusal.code == ServiceRefusal.PERMISSION_IN_PROGRESS
                    return ResumeResult(pause, if (busyNow) ResumeError.Busy else ResumeError.ResumeFailed)
                }
            }
        } finally {
            busy = false
            disableIfUnsupported()
            publish()
        }
    }

    /** The Settings switch. Returns whether live mode is on afterwards. */
    public suspend fun setEnabled(value: Boolean): Boolean {
        if (disableIfUnsupported()) return false
        if (!value) {
            signIn.cancelForUser()
            if (busy) return signIn.disableUserClaim()
        } else if (signIn.hasClaims) {
            return signIn.enableUserClaim()
        }
        if (busy || value && value == enabled && value == active) return enabled
        val previous = enabled
        enabled = value
        publish()
        val revision = pauseRevision + 1
        val succeeded = run(if (value) Method.Enable else Method.Disable)
        if (disableIfUnsupported()) return false
        if (!succeeded && revision == pauseRevision) {
            enabled = previous
            optIn.write(previous)
            if (revision != pauseRevision && !enabled) optIn.write(false)
            publish()
        }
        return enabled
    }

    public suspend fun refreshStatus() {
        run(Method.Status, persist = false)
    }

    public suspend fun requestBatteryOptimizationExemption(): Boolean = run(Method.BatteryExemption, persist = false)

    /**
     * The service stopped by itself: Android's time limit (`systemTimeout`) or the notification's "Pause background"
     * ([REASON_USER_PAUSE], intentional, so nothing is owed a warning). [nativePause] is the receipt the service sent,
     * if any. The saved opt-in never outlives the service.
     */
    public fun onServiceStopped(reason: String?, nativePause: PauseState? = null) {
        if (disposed) return
        val userPause = reason == REASON_USER_PAUSE
        signIn.serviceStopped(ForegroundFailure.Paused, if (userPause) "paused-by-you" else "android-time-limit")
        ++pauseRevision
        stoppedByAndroidTimeout = !userPause
        pause =
            nativePause
                ?: if (userPause) {
                    PauseState.Off
                } else {
                    PauseState(
                        supported = true,
                        paused = true,
                        reason = PauseReason.TimeLimit,
                        atMs = clock.now().toEpochMilliseconds(),
                        canResume = true,
                    )
                }
        enabled = false
        active = false
        scope.launch { optIn.write(false) }
        publish()
    }

    /** The app is closing: every sign-in lease is cancelled (`app-closed`); an owned service is stopped on release. */
    public fun dispose() {
        if (disposed) return
        disposed = true
        signIn.invalidate(ForegroundFailure.Cancelled, "app-closed")
    }

    // ---- internals shared with SignInForegroundState

    internal fun publish() {
        if (!disposed) _state.value = snapshot()
    }

    internal suspend fun writeOptIn(value: Boolean) {
        optIn.write(value)
    }

    internal suspend fun <T> call(block: suspend () -> ServiceReply<T>): ServiceReply<T> =
        when (val outcome = attempt({ Failure.Device() }) { block() }) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> ServiceReply.Refused(ServiceRefusal(ServiceRefusal.UNKNOWN))
        }

    internal fun markActive(value: Boolean) {
        val wasActive = active
        active = value
        // A status read found the service gone (Android stopped it).
        if (!active && wasActive) signIn.serviceStopped(ForegroundFailure.Unavailable, "service-gone")
    }

    internal fun applyStatus(status: ServiceStatus) {
        markActive(status.active)
        notificationGranted = status.notificationGranted
        batteryOptimizationIgnored = status.batteryOptimizationIgnored
        if (status.enabled != null && !signIn.preservesUserClaim) enabled = status.enabled
        val receipt = status.pause
        if (receipt != null) {
            applyPauseState(receipt)
        } else if (active) {
            pause = PauseState.Running
            stoppedByAndroidTimeout = false
        }
    }

    internal fun applyPauseState(state: PauseState) {
        pause = state
        markActive(state.active)
        stoppedByAndroidTimeout = state.paused && state.reason == PauseReason.TimeLimit
        if (!state.supported || state.paused) {
            signIn.serviceStopped(
                if (state.supported) ForegroundFailure.Paused else ForegroundFailure.Unsupported,
                if (state.supported) "paused-${state.reason.wire}" else "pause-unreadable",
            )
            enabled = false
        }
    }

    private enum class Method { Enable, Disable, Status, BatteryExemption }

    private suspend fun run(method: Method, persist: Boolean = true): Boolean {
        // Unsupported is not an Android failure and must never leave enabled true.
        if (disableIfUnsupported()) return false
        if (busy) return false
        busy = true
        val revision = ++pauseRevision
        lastError = null
        publish()
        try {
            val reply =
                when (method) {
                    Method.Enable -> signIn.invokeEnable()
                    Method.Disable -> call { port.disable() }
                    Method.Status -> call { port.status() }
                    Method.BatteryExemption -> call { port.requestBatteryOptimizationExemption() }
                }
            if (disableIfUnsupported()) return false
            if (revision != pauseRevision) return false
            return when (reply) {
                is ServiceReply.Ok -> {
                    applyStatus(reply.value)
                    if (method == Method.Disable) applyPauseState(PauseState.Off)
                    if (persist) optIn.write(enabled)
                    true
                }
                is ServiceReply.Refused -> {
                    lastError =
                        when (reply.refusal.code) {
                            ServiceRefusal.NOTIFICATION_DENIED -> LiveModeError.NotificationsRequired
                            ServiceRefusal.NO_HOST -> LiveModeError.AndroidAppOnly
                            else -> LiveModeError.CouldNotChange
                        }
                    false
                }
            }
        } finally {
            busy = false
            disableIfUnsupported()
            publish()
        }
    }

    /**
     * A saved Android opt-in is not evidence this platform can stay live: clear runtime state but keep the preference
     * (an unsupported platform is not a person's opt-out). True when the caller must stop.
     */
    private fun disableIfUnsupported(): Boolean {
        if (disposed) return true
        if (support.supported()) return false
        pause = PauseState.Unsupported
        stoppedByAndroidTimeout = false
        applyStatus(ServiceStatus(enabled = false))
        lastError = null
        publish()
        return true
    }

    private fun snapshot(): LiveModeState =
        LiveModeState(
            enabled = enabled,
            active = active,
            notificationGranted = notificationGranted,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            busy = busy,
            lastError = lastError,
            pause = pause,
            stoppedByAndroidTimeout = stoppedByAndroidTimeout,
        )

    public companion object {
        /** The push the service sends when it stops by itself (Flutter `METHOD_TIMEOUT`, verbatim). */
        public const val TIMEOUT_METHOD: String = "backgroundServiceTimeout"

        /** Android's time limit (Flutter `REASON_SYSTEM_TIMEOUT`). */
        public const val REASON_SYSTEM_TIMEOUT: String = "systemTimeout"

        /** The notification's "Pause background" (Flutter `REASON_USER_PAUSE` / `pauseReason`). */
        public const val REASON_USER_PAUSE: String = "userPause"
    }
}

/**
 * What the connection does when the app leaves the screen (Flutter `ConnectionController._suspendForLifecycle`): with
 * live mode opted in, the shared transport stays connected; otherwise it is suspended. Background checks (usage,
 * quota, profile monitors) run only while the service actually runs.
 */
public object BackgroundLifecycle {
    public sealed interface Decision {
        public data class StayConnected(val backgroundChecksAllowed: Boolean) : Decision

        public data object Suspend : Decision
    }

    public fun onAppBackgrounded(state: LiveModeState): Decision =
        if (state.enabled) Decision.StayConnected(backgroundChecksAllowed = state.active) else Decision.Suspend
}
