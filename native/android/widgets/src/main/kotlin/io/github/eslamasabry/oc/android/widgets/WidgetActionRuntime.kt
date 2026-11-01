package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import org.json.JSONObject

/**
 * WIDGETS seam (native-authored, not a twin): the same constructor and `start`/`destroy` as the Flutter
 * `WidgetActionRuntime`, which ran a headless Dart engine (plan 11 §4.2: deleted). The work now goes to the bound
 * [WidgetBackgroundRunner]. With none bound the run completes with null at once, which the copied controller reads
 * as "uncertain" and the status worker as "unavailable" — never as a retryable rejection.
 */
internal class WidgetActionRuntime(
    private val context: Context,
    private val payload: Map<String, Any?>,
    private val snapshot: JSONObject,
    private val deadlineEpochMs: Long,
    private val operation: String = "execute",
    private val complete: (Map<*, *>?) -> Unit,
) {
    private var handle: WidgetBackgroundRunner.Handle? = null
    private var done = false

    fun start() {
        val runner = WidgetFeed.backgroundRunner
        if (runner == null) {
            finish(null)
            return
        }
        handle = runner.start(context, payload, snapshot.toString(), deadlineEpochMs, operation, ::finish)
    }

    fun destroy() {
        done = true
        handle?.cancel()
        handle = null
    }

    private fun finish(value: Map<*, *>?) {
        if (done) return
        done = true
        complete(value)
    }
}
