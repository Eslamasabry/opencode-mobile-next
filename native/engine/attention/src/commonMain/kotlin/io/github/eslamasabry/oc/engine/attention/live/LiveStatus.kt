package io.github.eslamasabry.oc.engine.attention.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Which situation the status notification shows (wire values match Android's `StatusKind`). */
public enum class LiveStatusKind(public val wire: String) { NeedsYou("needs_you"), Working("working"), SeveralWorking("several_working"), Idle("idle") }

/** What the one status notification says, as composed from session truth; the words come from [LiveWords]. */
public data class LiveNotificationStatus(val kind: LiveStatusKind, val title: String, val publicTitle: String) {
    public fun toPayload(): Map<String, Any?> = mapOf("kind" to kind.wire, "title" to title, "publicTitle" to publicTitle)
}

/**
 * The live status the persistent notification shows (port of Flutter `LiveStatus`, B-ANDR-032). [toPayload] carries
 * exactly the four fields Android reads, plus the composed [status] when one was built. Compared by value.
 */
public data class LiveStatus(
    val runningCount: Int = 0,
    val pendingCount: Int = 0,
    val title: String? = null,
    val detail: String? = null,
    val status: LiveNotificationStatus? = null,
) {
    public fun toPayload(): Map<String, Any?> =
        buildMap {
            put("runningCount", runningCount)
            put("pendingCount", pendingCount)
            put("title", title)
            put("detail", detail)
            status?.let { put("status", it.toPayload()) }
        }

    public companion object {
        public val Idle: LiveStatus = LiveStatus()
    }
}

/** The platform side of the live notification (Android: the background service's `updateLiveStatus`). */
public fun interface LivePlatform {
    /** May throw when there is no platform channel; the publisher swallows it. */
    public suspend fun updateLiveStatus(payload: Map<String, Any?>)
}

/**
 * Publishes the live status to the notification (port of `BackgroundLiveController.publishLiveStatus`). A no-op unless
 * live mode is on and the service is running; skips a status equal to what Android shows; sends the first at once and
 * coalesces a burst into one trailing push with the newest; never throws. [scope] runs the trailing push.
 */
public class LiveStatusPublisher(
    private val platform: LivePlatform,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    public val debounce: Duration = 120.milliseconds,
    /** Persists live mode off (the keep-live preference) when the notification pauses it. */
    private val persistDisabled: suspend () -> Unit = {},
) {
    public var enabled: Boolean = false
        private set
    public var active: Boolean = false
        private set

    /** True only when Android's own time limit stopped the service, never for the person's own Pause. */
    public var stoppedByAndroidTimeout: Boolean = false
        private set

    public var lastPublished: LiveStatus? = null
        private set

    /** Always null: a failed push is cosmetic and never surfaces as an error. */
    public val lastError: Failure? get() = null

    private var pending: LiveStatus? = null
    private var timer: Job? = null
    private var lastMark: ComparableTimeMark? = null

    /** The service state as the platform reports it. */
    public fun setServiceState(enabled: Boolean, active: Boolean) {
        this.enabled = enabled
        this.active = active
        if (!enabled || !active) cancelPending()
    }

    public suspend fun publish(status: LiveStatus) {
        if (!enabled || !active) return
        if (pending == null && status == lastPublished) return
        pending = status
        if (timer != null) return
        val elapsed = lastMark?.elapsedNow() ?: debounce
        if (elapsed >= debounce) {
            flush()
            return
        }
        timer =
            scope.launch {
                delay(debounce - elapsed)
                timer = null
                flush()
            }
    }

    private suspend fun flush() {
        val status = pending
        pending = null
        if (status == null || status == lastPublished) return
        if (!enabled || !active) return
        lastMark = timeSource.markNow()
        lastPublished = status
        // The notification keeps its previous text on failure; the next change retries.
        attempt({ Failure.Unexpected() }) { platform.updateLiveStatus(status.toPayload()) }
    }

    private fun cancelPending() {
        timer?.cancel()
        timer = null
        pending = null
    }

    /**
     * The service stopped. [reason] [PAUSE_REASON] is the notification's own "Pause background" action: the person asked,
     * so it is not an Android timeout. Either way live mode turns off and is saved off.
     */
    public suspend fun handleNativeTimeout(reason: String?) {
        stoppedByAndroidTimeout = reason != PAUSE_REASON
        enabled = false
        active = false
        cancelPending()
        attempt({ Failure.StorageRefused() }) { persistDisabled() }
    }

    public companion object {
        public const val PAUSE_REASON: String = "userPause"
    }
}
