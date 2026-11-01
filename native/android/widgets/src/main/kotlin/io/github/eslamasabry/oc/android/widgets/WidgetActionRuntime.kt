package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import org.json.JSONObject

/**
 * WIDGETS seam (native-authored, not a twin): the same constructor and `start`/`destroy` as the
 * Flutter `WidgetActionRuntime`, which ran a headless Dart engine (plan 11 §4.2: deleted). The work
 * now goes to the bound [WidgetBackgroundRunner]. With none bound the run completes with null at
 * once, which the copied controller reads as "uncertain" and the status worker as "unavailable" —
 * never as a retryable rejection.
 */
internal class WidgetActionRuntime(
    private val context: Context,
    private val payload: Map<String, Any?>,
    private val snapshot: JSONObject,
    private val deadlineEpochMs: Long,
    private val operation: String = "execute",
    private val complete: (Map<*, *>?) -> Unit,
) {
    private val lock = Any()
    private var handle: WidgetBackgroundRunner.Handle? = null
    private var done = false
    private var started = false

    fun start() {
        synchronized(lock) {
            if (started || done) return
            started = true
        }
        val runner = WidgetFeed.backgroundRunner
        if (runner == null) {
            finish(null)
            return
        }
        // start may complete inline, or destroy may run while it is still starting.
        val returned =
            try {
                runner.start(
                    context,
                    payload,
                    snapshot.toString(),
                    deadlineEpochMs,
                    operation,
                    ::finish,
                )
            } catch (_: RuntimeException) {
                finish(null)
                return
            }
        val cancel =
            synchronized(lock) {
                if (done) true
                else {
                    handle = returned
                    false
                }
            }
        if (cancel) returned.cancel()
    }

    fun destroy() {
        val cancel =
            synchronized(lock) {
                done = true
                handle.also { handle = null }
            }
        cancel?.cancel()
    }

    private fun finish(value: Map<*, *>?) {
        val cancel =
            synchronized(lock) {
                if (done) return
                done = true
                handle.also { handle = null }
            }
        try {
            complete(value)
        } finally {
            cancel?.cancel()
        }
    }
}

/**
 * Cancellation and the monotonic coroutine timeout both close the runner, including inline startup.
 */
internal suspend fun awaitWidgetRun(
    context: Context,
    payload: Map<String, Any?>,
    snapshot: JSONObject,
    storage: WidgetPersistence,
    budgetMs: Long,
    operation: String = "execute",
): Map<*, *>? {
    var runtime: WidgetActionRuntime? = null
    return try {
        kotlinx.coroutines.withTimeoutOrNull(budgetMs) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val started =
                    WidgetActionRuntime(
                        context,
                        payload,
                        snapshot,
                        storage.clock.now().toEpochMilliseconds() + budgetMs,
                        operation,
                    ) {
                        continuation.resumeWith(Result.success(it))
                    }
                runtime = started
                continuation.invokeOnCancellation { started.destroy() }
                started.start()
            }
        }
    } finally {
        runtime?.destroy()
    }
}
