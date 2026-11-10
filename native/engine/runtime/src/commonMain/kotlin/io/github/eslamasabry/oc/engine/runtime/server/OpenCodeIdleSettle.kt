package io.github.eslamasabry.oc.engine.runtime.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Flutter's `inAppAutoUpdateSettle` rule: the work counts as busy until "known idle" has held for [settle] without a
 * break. A restarted server may be resuming a reply while its busy list is still empty, and an update restarts the
 * server, so a single idle reading is not trusted. Any not-idle reading (not connected, unknown or busy) starts the
 * wait over. Once per wait, [onSettled] is called when the time has elapsed so the caller decides again (Flutter's
 * `Timer(settle - waited, updater._changed)`). [now] is in milliseconds.
 */
public class OpenCodeIdleSettle(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val settle: Duration = DEFAULT,
    private val onSettled: () -> Unit,
) {
    private var since: Long? = null
    private var timer: Job? = null

    /** True while the work must be treated as busy. [knownIdle] is the caller's current reading. */
    public fun busy(knownIdle: Boolean): Boolean {
        if (!knownIdle) {
            since = null
            timer?.cancel()
            timer = null
            return true
        }
        val start = since ?: now().also { since = it }
        val left = settle.inWholeMilliseconds - (now() - start)
        if (left <= 0) return false
        if (timer?.isActive != true) {
            timer = scope.launch {
                delay(left)
                onSettled()
            }
        }
        return true
    }

    public fun cancel() {
        timer?.cancel()
        timer = null
    }

    public companion object {
        public val DEFAULT: Duration = 30.seconds
    }
}
