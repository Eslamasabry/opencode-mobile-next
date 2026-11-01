package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.sse.SseFrame

/**
 * The stream's own status (plan 04 §6.3). TODO(move-to-core): audit codex-04 BLOCKER 3 wants the shared status in a
 * module both `:core:domain` and `:core:net` may use; `:core:model` holds only the 4-value [StreamStatus] today.
 * This richer type stays here until S0-CORE freezes it; [toModel] bridges.
 */
public sealed interface StreamState {
    public data object Connecting : StreamState

    /** [generation] counts successful connects from 1; every generation after the first must refetch (N4-D10). */
    public data class Connected(val generation: Int) : StreamState {
        val isReconnect: Boolean get() = generation > 1
    }

    /** [nextAttemptAtMs] is a real schedule: set when the wait is chosen, kept while that try is in flight. */
    public data class Reconnecting(val attempt: Int, val nextAttemptAtMs: Long?) : StreamState

    /** Same as [Reconnecting] after [BackoffPolicy.disconnectedLabelAfter] failures; it still retries. */
    public data class Disconnected(val attempt: Int, val nextAttemptAtMs: Long?) : StreamState

    public data object WaitingForNetwork : StreamState

    /** Sign-in needed or an incompatible server: no tries until [EventStreamLoop.retryNow]. */
    public data class Stopped(val failure: Failure) : StreamState
}

public fun StreamState.toModel(): StreamStatus =
    when (this) {
        StreamState.Connecting -> StreamStatus.Connecting
        is StreamState.Connected -> StreamStatus.Connected
        is StreamState.Reconnecting -> StreamStatus.Reconnecting
        is StreamState.Disconnected, StreamState.WaitingForNetwork, is StreamState.Stopped -> StreamStatus.Disconnected
    }

public sealed interface StreamSignal {
    public data class Status(val state: StreamState) : StreamSignal

    /** [generation] is the [StreamState.Connected.generation] the frame arrived on. */
    public data class Frame(val frame: SseFrame, val generation: Int) : StreamSignal
}

/** How a stream resumes after a reconnect (plan 04 §6.2). */
public sealed interface ResumeMode {
    /** OpenCode 2 `/api/event`: nothing to resume; the owner refetches after every reconnect (N4-D10). */
    public data object None : ResumeMode

    /** OpenCode 2 session log: the request builder reads the caller's own cursor (`?after=<durable.seq>`). */
    public data class QueryCursor(val param: String = "after") : ResumeMode

    /** Gas City (later): the loop passes the last frame id seen as `Last-Event-ID`. */
    public data object LastEventId : ResumeMode
}
