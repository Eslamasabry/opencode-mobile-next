package io.github.eslamasabry.opencode_mobile

import android.app.KeyguardManager
import android.content.Context
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.MainActivity
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge.currentIdentity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
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
        val storage = WidgetFeed.persistence
        if (storage == null || !WidgetFeed.connectionActive || WidgetFeed.liveDispatcher == null) {
            done(result(payload, "rejected", "unavailable"))
            return
        }
        storage.scope.launch {
            val settled = try {
                withTimeoutOrNull(8000) { submitStored(context, payload) }
                    ?: result(payload, "uncertain", "uncertainDelivery")
            } catch (cancelled: CancellationException) {
                done(result(payload, "uncertain", "uncertainDelivery"))
                throw cancelled
            } catch (_: Exception) {
                result(payload, "uncertain", "uncertainDelivery")
            }
            // Admission, storage and the live callback share one bounded receiver lease. A timeout never replays.
            done(settled)
            if (settled["status"] == "uncertain") {
                withTimeoutOrNull(500) { publishResult(context.applicationContext, payload, settled) }
            }
        }
    }

    private suspend fun submitStored(context: Context, payload: Map<String, Any?>): Map<String, Any?> {
        val app = context.applicationContext
        val error = submissionError(app, payload)
        if (error != null) {
            val refused = result(payload, "rejected", error)
            publishResult(app, payload, refused)
            return refused
        }
        // A delayed durable claim must not dispatch after the outer admission deadline expired.
        currentCoroutineContext().ensureActive()
        val response = suspendCancellableCoroutine<Map<String, Any?>> { continuation ->
            BackgroundConnectionService.dispatchWidgetAction(payload) { value ->
                if (continuation.isActive) continuation.resumeWith(Result.success(value))
            }
        }
        if (response["status"] == "rejected") releaseClaim(app, payload)
        publishResult(app, payload, response)
        return response
    }

    private suspend fun submissionError(context: Context, payload: Map<String, Any?>): String? {
        if (!WidgetFeed.connectionActive || WidgetFeed.liveDispatcher == null ||
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true)
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
    internal suspend fun claim(context: Context, payload: Map<String, Any?>): String? {
        val storage = WidgetFeed.persistence ?: return "unavailable"
        return storage.claims.withLock {
            val key = "oc.widgetActions.${payload["profileID"]}"
            val before = storage.string(key)
            val ledger =
                if (before == null) JSONArray()
                else runCatching { JSONArray(before) }.getOrNull() ?: return@withLock "unavailable"
            val ids = (0 until ledger.length()).map { ledger.optString(it) }.toMutableList()
            val id = payload["id"].toString()
            if (id in ids) return@withLock "busy"
            ids.add(id)
            if (
                storage.cas(
                    payload["profileID"].toString(),
                    key,
                    before,
                    JSONArray(ids.takeLast(MAX_CLAIM_HISTORY)).toString(),
                )
            )
                null
            else "unavailable"
        }
    }

    internal suspend fun releaseClaim(context: Context, payload: Map<String, Any?>) {
        val storage = WidgetFeed.persistence ?: return
        storage.claims.withLock {
            val key = "oc.widgetActions.${payload["profileID"]}"
            val before = storage.string(key) ?: return@withLock
            val ledger = runCatching { JSONArray(before) }.getOrNull() ?: return@withLock
            val ids =
                (0 until ledger.length())
                    .map { ledger.optString(it) }
                    .filter { it != payload["id"] }
            storage.cas(
                payload["profileID"].toString(),
                key,
                before,
                if (ids.isEmpty()) null else JSONArray(ids).toString(),
            )
        }
    }

    internal fun admissionError(context: Context, payload: Map<String, Any?>): String? =
        when {
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true ->
                "unavailable"
            payload["action"] !in WidgetActionLease.actions -> "unsupported"
            else -> WidgetActionPolicy.validate(payload, currentIdentity(context, payload))
        }

    /** Only reviewed status fields of the already privacy-safe snapshot are patched. */
    internal suspend fun applyActionSnapshot(
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

    internal suspend fun publishResult(
        context: Context,
        payload: Map<String, Any?>,
        result: Map<String, Any?>,
    ) {
        val storage = WidgetFeed.persistence ?: return
        val before = storage.string("oc.widgetSessions") ?: return
        val snapshot = runCatching { JSONObject(before) }.getOrNull() ?: return
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
        if (
            storage.cas(
                snapshot.optString("profileID"),
                "oc.widgetSessions",
                before,
                snapshot.toString(),
            )
        ) {
            SessionsWidgetProvider.refreshAll(context)
        }
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
