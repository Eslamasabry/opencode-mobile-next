package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetActionBridge
import io.github.eslamasabry.opencode_mobile.WidgetActionLease
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.coroutines.sync.Mutex

/** The already-promoted service executes one explicit tap; durable claims survive process death. */
public object NativeWidgetActions {
    private val lease = Mutex()

    public suspend fun execute(context: Context, raw: String) {
        val payload = WidgetV2Bridge.parsePayload(raw) ?: return
        val storage = WidgetFeed.persistence ?: return
        if (!lease.tryLock()) {
            WidgetActionBridge.publishResult(
                context,
                payload,
                WidgetActionBridge.result(payload, "rejected", "busy"),
            )
            return
        }
        try {
            val error =
                WidgetActionBridge.admissionError(context, payload)
                    ?: WidgetActionBridge.claim(context, payload)
            if (error != null) {
                WidgetActionBridge.publishResult(
                    context,
                    payload,
                    WidgetActionBridge.result(
                        payload,
                        if (error == "busy") "uncertain" else "rejected",
                        if (error == "busy") "uncertainDelivery" else error,
                    ),
                )
                return
            }
            val snapshot = WidgetV2Bridge.snapshot(context) ?: return
            WidgetFeed.onWidgetActionPending(true)
            val response =
                awaitWidgetRun(context, payload, snapshot, storage, WidgetActionLease.TIMEOUT_MS)
            val result = WidgetActionLease.safeResult(payload, response)
            if (result["status"] == "rejected") WidgetActionBridge.releaseClaim(context, payload)
            if (result["status"] == "accepted")
                WidgetActionBridge.applyActionSnapshot(
                    context,
                    payload,
                    response?.get("snapshot") as? Map<*, *>,
                )
            WidgetActionBridge.publishResult(context, payload, result)
        } finally {
            WidgetFeed.onWidgetActionPending(false)
            SessionsWidgetProvider.refreshAll(context)
            lease.unlock()
        }
    }
}
