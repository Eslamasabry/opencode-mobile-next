package io.github.eslamasabry.opencode_mobile

import android.app.KeyguardManager
import android.content.Context
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.MainActivity
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge.PREFS
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge.SNAPSHOT_KEY
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge.currentIdentity
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge.snapshot
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Dispatch and durable admission for explicit widget actions. */
internal object WidgetActionBridge {
    private const val MAX_CLAIM_HISTORY = 128

    /** No service restart and no UI fallback. A timed-out server call remains uncertain. */
    internal fun submit(
        context: Context,
        payload: Map<String, Any?>,
        done: (Map<String, Any?>) -> Unit,
    ) {
        val app = context.applicationContext
        fun rejected(error: String) {
            val result = result(payload, "rejected", error)
            publishResult(app, payload, result)
            done(result)
        }
        val error = submissionError(app, payload)
        if (error != null) {
            rejected(error)
            return
        }
        BackgroundConnectionService.dispatchWidgetAction(payload) { result ->
            if (result["status"] == "rejected") releaseClaim(app, payload)
            publishResult(app, payload, result)
            done(result)
        }
    }

    private fun submissionError(context: Context, payload: Map<String, Any?>): String? {
        if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true)
            return "unavailable"
        val validationError =
            WidgetActionPolicy.validate(payload, currentIdentity(context, payload))
        return when {
            validationError != null -> validationError
            !BackgroundConnectionService.active || MainActivity.backgroundChannel == null ->
                "unavailable"
            else -> claim(context, payload)
        }
    }

    /** Claim durably before the server boundary; a timeout or process death never replays. */
    internal fun claim(context: Context, payload: Map<String, Any?>): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "flutter.oc.widgetActions.${payload["profileID"]}"
        val ledger = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
        val ids = (0 until ledger.length()).map { ledger.optString(it) }.toMutableList()
        val id = payload["id"].toString()
        if (id in ids) return "busy"
        ids.add(id)
        val saved =
            prefs
                .edit()
                .putString(key, JSONArray(ids.takeLast(MAX_CLAIM_HISTORY)).toString())
                .commit()
        return if (saved) null else "unavailable"
    }

    /**
     * A matched Dart rejection guarantees nothing was dispatched (or the server explicitly
     * declined). Allow a fresh tap after that condition changes.
     */
    internal fun releaseClaim(context: Context, payload: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "flutter.oc.widgetActions.${payload["profileID"]}"
        // Profile deletion may have swept this key while Dart was settling.
        // Never recreate even an empty profile-owned ledger after that sweep.
        val saved = prefs.getString(key, null) ?: return
        val ledger = runCatching { JSONArray(saved) }.getOrNull() ?: return
        val ids =
            (0 until ledger.length()).map { ledger.optString(it) }.filter { it != payload["id"] }
        val editor = prefs.edit()
        if (ids.isEmpty()) editor.remove(key) else editor.putString(key, JSONArray(ids).toString())
        editor.commit()
    }

    internal fun admissionError(context: Context, payload: Map<String, Any?>): String? =
        when {
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true ->
                "unavailable"
            payload["action"] !in WidgetActionLease.actions -> "unsupported"
            else -> WidgetActionPolicy.validate(payload, currentIdentity(context, payload))
        }

    /** Only reviewed status fields of the already privacy-safe snapshot are patched. */
    internal fun applyActionSnapshot(
        context: Context,
        payload: Map<String, Any?>,
        value: Map<*, *>?,
    ): Boolean = WidgetActionSnapshot.apply(context, payload, value)

    internal fun result(
        payload: Map<String, Any?>,
        status: String,
        error: String? = null,
    ): Map<String, Any?> =
        mutableMapOf<String, Any?>(
                "id" to payload["id"],
                "action" to payload["action"],
                "status" to status,
            )
            .apply {
                if (error != null) put("error", error)
            }

    internal fun publishResult(
        context: Context,
        payload: Map<String, Any?>,
        result: Map<String, Any?>,
    ) {
        val snapshot = snapshot(context) ?: return
        if (
            snapshot.optString("profileID") != payload["profileID"] ||
                snapshot.optString("scopeToken") != payload["scopeToken"]
        )
            return
        // The callback is the final result: success needs no lingering banner.
        // Keep failed/unknown outcomes visible until a newer snapshot replaces
        // them, rather than implying a timed-out action was safely rejected.
        if (WidgetV2Policy.actionResultNotice(result["status"] as? String) == null) {
            snapshot.remove("actionResult")
        } else {
            snapshot.put("actionResult", JSONObject(result))
        }
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SNAPSHOT_KEY, snapshot.toString())
            .apply()
        SessionsWidgetProvider.refreshAll(context)
    }

    internal fun overlayPayload(
        launch: Map<String, Any?>,
        action: String,
        text: String,
    ): Map<String, Any?> =
        launch.filterKeys { it in WidgetActionPolicy.identityFields } +
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "action" to action,
                "text" to text,
            )
}
