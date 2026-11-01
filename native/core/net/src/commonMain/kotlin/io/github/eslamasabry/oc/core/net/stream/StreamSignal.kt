package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.sse.SseFrame

/**
 * What [EventStreamLoop.signals] emits (plan 04 §6.2). The status is core's [StreamStatus] (`:core:model`, plan 04
 * §6.3), the single source of truth; this module has no status type of its own.
 *
 * The connect generation is the number of `Status(StreamStatus.Connected)` signals seen so far (plan 04 §6.2): every
 * generation after the first must refetch (N4-D10). A [Frame] carries the generation it arrived on, so a consumer can
 * drop a frame from a connection it has already replaced.
 */
public sealed interface StreamSignal {
    public data class Status(val status: StreamStatus) : StreamSignal

    /** [generation] counts `Status(Connected)` signals from 1; the frame arrived on that connection. */
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
