package io.github.eslamasabry.oc.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each shown stage stays at least this long before the next shows. */
public const val STAGE_HOLD_MS: Long = 500L

/**
 * A stage shown stays for at least [holdMs] before another non-null value replaces it (the line never flickers through
 * quick stages); the newest value waits and shows when the hold ends. A null (the page is ready) always passes at once and
 * ends the hold. Values that are not a stage ([isStage] false) are not held and never hold.
 */
public fun <T : Any> Flow<T?>.holdStages(holdMs: Long = STAGE_HOLD_MS, isStage: (T) -> Boolean): Flow<T?> =
    channelFlow {
        val lock = Mutex()
        var first = true
        var shown: T? = null
        var pending: T? = null
        var holdJob: Job? = null
        val scope: CoroutineScope = this

        suspend fun show(value: T?) {
            first = false
            shown = value
            send(value)
            if (value != null && isStage(value)) {
                holdJob =
                    scope.launch {
                        delay(holdMs)
                        lock.withLock {
                            holdJob = null
                            val next = pending
                            pending = null
                            if (next != null && next != shown) show(next)
                        }
                    }
            }
        }

        collect { value ->
            lock.withLock {
                when {
                    value == null -> {
                        holdJob?.cancel()
                        holdJob = null
                        pending = null
                        if (first || shown != null) show(null)
                    }
                    holdJob != null -> pending = if (value == shown) null else value
                    first || value != shown -> show(value)
                }
                Unit
            }
        }
    }
