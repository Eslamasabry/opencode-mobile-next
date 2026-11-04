package io.github.eslamasabry.oc.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import io.github.eslamasabry.opencode_mobile.WidgetActionBridge
import io.github.eslamasabry.opencode_mobile.WidgetActionPolicy
import io.github.eslamasabry.opencode_mobile.WidgetProviders
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.coroutines.suspendCancellableCoroutine

public data class WidgetComposeInspection(val available: Boolean, val steerMode: String)
public data class WidgetComposeResponse(val status: String, val error: String?)

/** Transient widget overlay identity and text; only the existing identifier-only claim ledger is persisted. */
public class WidgetComposeBridge(
    context: Context,
    private val widgetId: Int,
    launchJson: String?,
    private val voice: Boolean,
    private val submissionId: String,
) {
    private val app = context.applicationContext
    private val launch = WidgetV2Bridge.parsePayload(launchJson)
    @Volatile private var submitted = false

    public fun inspect(): WidgetComposeInspection {
        val provider = AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId)?.provider ?: return unavailable()
        if (provider.packageName != app.packageName || provider.className !in WidgetProviders.allNames) return unavailable()
        val identity = launch ?: return unavailable()
        val payload = identity + mapOf("action" to if (voice) "voice" else "steer")
        val current = WidgetV2Bridge.currentIdentity(app, payload) ?: return unavailable()
        if (!WidgetFeed.connectionActive || WidgetFeed.liveDispatcher == null ||
            (identity["profileID"] as? String).isNullOrBlank() || (identity["scopeToken"] as? String).isNullOrBlank() ||
            WidgetActionPolicy.identityFields.any { identity[it]?.toString().orEmpty() != current[it]?.toString().orEmpty() }) return unavailable()
        if (voice) return WidgetComposeInspection(!(identity["lastProjectID"] as? String).isNullOrBlank(), "steer")
        if (current["busy"] != true || (identity["expectedStartedAt"] as? Number)?.toLong()?.let { it <= 0 } != false) return unavailable()
        val snapshot = WidgetV2Bridge.snapshot(app) ?: return unavailable()
        val rows = snapshot.optJSONArray("sessions") ?: return unavailable()
        val row = (0 until rows.length()).mapNotNull(rows::optJSONObject).firstOrNull { it.optString("id") == identity["sessionID"] }
        return WidgetComposeInspection(true, row?.optString("steerMode").orEmpty().ifBlank { "unknown" })
    }

    public suspend fun submit(text: String): WidgetComposeResponse {
        val identity = launch ?: return WidgetComposeResponse("rejected", "staleRequest")
        if (submitted) return WidgetComposeResponse("uncertain", "uncertainDelivery")
        if (!inspect().available) return WidgetComposeResponse("rejected", "staleRequest")
        val payload = identity.filterKeys { it in WidgetActionPolicy.identityFields } + mapOf(
            "id" to submissionId,
            "action" to if (voice) "voice" else "steer",
            "text" to text,
        )
        submitted = true
        return suspendCancellableCoroutine { continuation ->
            WidgetActionBridge.submit(app, payload) { result ->
                val status = result["status"] as? String ?: "uncertain"
                if (status == "rejected") submitted = false
                if (continuation.isActive) continuation.resumeWith(Result.success(WidgetComposeResponse(status, result["error"] as? String)))
            }
        }
    }

    private fun unavailable(): WidgetComposeInspection = WidgetComposeInspection(false, "unknown")
}
