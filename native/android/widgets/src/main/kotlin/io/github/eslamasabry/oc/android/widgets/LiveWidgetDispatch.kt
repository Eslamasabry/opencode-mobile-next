package io.github.eslamasabry.oc.android.widgets

import android.os.Handler
import android.os.Looper

/**
 * Port of the Flutter `BackgroundConnectionService.dispatchWidgetAction` body (plan 11 §11.3 rule 7, seam E7) with the
 * `oc/background` MethodChannel replaced by [WidgetLiveDispatcher]. Same rules: on the main thread; no live connection
 * or no dispatcher → rejected/unavailable; 6.5 s without an answer → uncertain/uncertainDelivery; an answer for another
 * action or with an unknown status → uncertain; only the bounded error tokens cross back into the snapshot; a thrown
 * dispatcher → uncertain. Never starts or restarts a foreground service.
 */
internal object LiveWidgetDispatch {
    internal const val WIDGET_DISPATCH_TIMEOUT_MS = 6500L
    private val statuses = setOf("accepted", "rejected", "uncertain")
    private val errors = setOf("invalidRequest", "staleRequest", "unavailable", "unsupported", "busy", "uncertainDelivery")

    fun dispatch(payload: Map<String, Any?>, complete: (Map<String, Any?>) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val dispatcher = WidgetFeed.liveDispatcher
            if (!WidgetFeed.connectionActive || dispatcher == null) {
                complete(WidgetActionResults.of(payload, "rejected", "unavailable"))
                return@post
            }
            var finished = false
            val timeout = Runnable {
                if (!finished) {
                    finished = true
                    complete(WidgetActionResults.of(payload, "uncertain", "uncertainDelivery"))
                }
            }
            fun finish(value: Map<String, Any?>) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeout)
                complete(value)
            }
            handler.postDelayed(timeout, WIDGET_DISPATCH_TIMEOUT_MS)
            try {
                dispatcher.dispatch(payload) { response -> handler.post { finish(sanitize(payload, response)) } }
            } catch (_: RuntimeException) {
                finish(WidgetActionResults.of(payload, "uncertain", "uncertainDelivery"))
            }
        }
    }

    internal fun sanitize(payload: Map<String, Any?>, response: Map<String, Any?>?): Map<String, Any?> {
        val status = response?.get("status") as? String
        val sameAction = response != null && response["id"] == payload["id"] && response["action"] == payload["action"]
        if (!sameAction || status !in statuses) return WidgetActionResults.of(payload, "uncertain", "uncertainDelivery")
        val error = (response?.get("error") as? String)?.takeIf { it in errors }
        return WidgetActionResults.of(payload, status!!, error)
    }
}
