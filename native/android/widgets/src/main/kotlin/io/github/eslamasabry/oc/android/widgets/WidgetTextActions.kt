package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.opencode_mobile.WidgetActionBridge
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A launch contains identifiers only. The text field never becomes an Intent or stored widget
 * field.
 */
public class WidgetTextTarget internal constructor(internal val payload: Map<String, Any?>) {
    public val profileId: String
        get() = payload["profileID"] as? String ?: ""

    public val sessionId: String
        get() = payload["sessionID"] as? String ?: ""
}

public object WidgetTextActions {
    public const val EXTRA_PAYLOAD: String = "oc.widgetPayload"
    public const val EXTRA_MODE: String = "oc.widgetMode"

    public fun target(intent: Intent): WidgetTextTarget? {
        if (intent.getStringExtra(EXTRA_MODE) != "steer") return null
        val payload =
            WidgetV2Bridge.parsePayload(intent.getStringExtra(EXTRA_PAYLOAD)) ?: return null
        if (
            (payload["profileID"] as? String).isNullOrBlank() ||
                (payload["scopeToken"] as? String).isNullOrBlank() ||
                (payload["sessionID"] as? String).isNullOrBlank()
        )
            return null
        return WidgetTextTarget(payload)
    }

    /**
     * The durable native claim is made before invoking the live dispatcher. No timeout authorizes a
     * retry.
     */
    public suspend fun send(context: Context, target: WidgetTextTarget, text: String): String =
        withTimeoutOrNull(7_000L) {
            suspendCancellableCoroutine { continuation ->
                val now = WidgetFeed.persistence?.clock?.now()?.toEpochMilliseconds()
                if (now == null) {
                    continuation.resumeWith(Result.success("rejected"))
                    return@suspendCancellableCoroutine
                }
                val action =
                    WidgetActionBridge.overlayPayload(target.payload, "steer", text) +
                        mapOf("deadlineEpochMs" to now + 6_000L)
                WidgetActionBridge.submit(context, action) { response ->
                    if (continuation.isActive)
                        continuation.resumeWith(
                            Result.success(response["status"] as? String ?: "uncertain")
                        )
                }
            }
        } ?: "uncertain"
}
