package io.github.eslamasabry.oc.core.net

/**
 * Watches every [NetClient.exchange] (port of Dart `PerfTraceInterceptor`, B-BUILD-026): the diagnostics tracer turns
 * each call into a span named by method and path template with its status and byte count. Only the method and the
 * request's path (never its query, host or credentials) are handed over. Observers must not throw; a throwing observer
 * is ignored so tracing never breaks the request it measures.
 */
public interface HttpCallObserver {
    /** A call is about to be sent. The returned [HttpCallEnd] is told how it ended. Runs in the caller's coroutine. */
    public suspend fun begin(method: String, path: String): HttpCallEnd
}

/** How one observed call ended: [status] null when no reply arrived; [bytes] from the reply's length when known. */
public fun interface HttpCallEnd {
    public fun end(status: Int?, bytes: Long?, failed: Boolean)
}
