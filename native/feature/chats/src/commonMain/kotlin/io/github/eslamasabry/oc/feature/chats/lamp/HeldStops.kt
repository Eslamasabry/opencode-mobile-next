package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a Stop is: waiting out its undo window, or sent. */
public enum class StopPhase { Holding, Stopped }

/**
 * Stop's own undo window, the same length as an answer's: a tap holds the stop for [holdMs] ("Stopping ‹title›…" with
 * Undo), then [stopper] stops the run and the row says "Stopped" for [stoppedMs]. It lives in a scope that outlives the
 * panel, so closing the list never cancels a hold. Confined to one dispatcher, like its callers.
 */
public class HeldStops(
    private val scope: CoroutineScope,
    private val stopper: HomeStop,
    private val holdMs: Long = STOP_HOLD_MS,
    private val stoppedMs: Long = STOPPED_SHOWN_MS,
) {
    private val state = MutableStateFlow<Map<ConversationRef, StopPhase>>(emptyMap())
    private val jobs = HashMap<ConversationRef, Job>()
    private val failures = MutableSharedFlow<ConversationRef>(extraBufferCapacity = 8)

    public val phases: StateFlow<Map<ConversationRef, StopPhase>> = state.asStateFlow()

    /** A stop the server did not take. The row goes back to its stop square. */
    public val failed: SharedFlow<ConversationRef> = failures.asSharedFlow()

    /** Starts the window; false when [ref] is already stopping. */
    public fun hold(ref: ConversationRef): Boolean {
        if (ref in state.value) return false
        state.update { it + (ref to StopPhase.Holding) }
        jobs[ref] =
            scope.launch {
                delay(holdMs)
                send(ref)
            }
        return true
    }

    /** Undo: nothing is stopped. False when [ref] is not in its window. */
    public fun undo(ref: ConversationRef): Boolean {
        if (state.value[ref] != StopPhase.Holding) return false
        jobs.remove(ref)?.cancel()
        state.update { it - ref }
        return true
    }

    private suspend fun send(ref: ConversationRef) {
        val result = stopper.stop(ref)
        if (result is Outcome.Failed) {
            state.update { it - ref }
            jobs.remove(ref)
            failures.tryEmit(ref)
            return
        }
        state.update { it + (ref to StopPhase.Stopped) }
        delay(stoppedMs)
        state.update { it - ref }
        jobs.remove(ref)
    }

    public companion object {
        /** The same window as an answer's. */
        public const val STOP_HOLD_MS: Long = 3_000L
        public const val STOPPED_SHOWN_MS: Long = 4_000L
    }
}
