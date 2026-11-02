package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import org.json.JSONArray
import org.json.JSONObject

/** Whitelisted action settlement patches; never persists arbitrary plugin data. */
internal object WidgetActionSnapshot {
    suspend fun apply(context: Context, payload: Map<String, Any?>, value: Map<*, *>?): Boolean {
        val before = WidgetFeed.persistence?.string("oc.widgetSessions") ?: return false
        val current = value?.let { runCatching { JSONObject(before) }.getOrNull() }
        if (current == null) return false
        return matchesScope(current, payload, value) &&
            patchAndSave(context, payload, current, value, before)
    }

    private suspend fun patchAndSave(
        context: Context,
        payload: Map<String, Any?>,
        current: JSONObject,
        value: Map<*, *>,
        before: String,
    ): Boolean {
        val rows = current.optJSONArray("sessions")
        val updated = (value["sessions"] as? List<*>)?.filterIsInstance<Map<*, *>>()
        return rows != null &&
            updated != null &&
            patchRows(context, payload, rows, updated) &&
            save(context, current, rows, before)
    }

    private fun matchesScope(
        current: JSONObject,
        payload: Map<String, Any?>,
        value: Map<*, *>,
    ): Boolean =
        current.optString("profileID") == payload["profileID"] &&
            current.optString("scopeToken") == payload["scopeToken"] &&
            matchesPatchScope(payload, value)

    private fun matchesPatchScope(payload: Map<String, Any?>, value: Map<*, *>): Boolean =
        value["profileID"] == payload["profileID"] &&
            value["scopeToken"] == payload["scopeToken"] &&
            value["version"] == 2

    private fun patchRows(
        context: Context,
        payload: Map<String, Any?>,
        rows: JSONArray,
        updated: List<Map<*, *>>,
    ): Boolean {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index)
            if (row != null && row.optString("id") == payload["sessionID"]) {
                // A concurrent foreground update wins over the old action result.
                if (
                    WidgetActionPolicy.validate(
                        payload,
                        WidgetV2Bridge.currentIdentity(context, payload),
                    ) != null
                ) {
                    return false
                }
                updated
                    .firstOrNull { it["id"] == payload["sessionID"] }
                    ?.let { patchRow(row, payload, it) }
            }
        }
        return true
    }

    private fun patchRow(row: JSONObject, payload: Map<String, Any?>, patch: Map<*, *>) {
        (patch["runMarker"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { row.put("runMarker", it) }
        (patch["busy"] as? Boolean)?.let { row.put("busy", it) }
        // Only removal of an answered request is permitted here.
        if ((patch["requestID"] as? String).isNullOrEmpty()) row.remove("requestID")
        if (patch["busy"] == false) {
            row.remove("startedAt")
            row.remove("step")
        }
        if (
            payload["action"] in setOf("approvePlan", "resume") &&
                (patch["team"] as? Map<*, *>)?.get("action").let { it == null || it == "" }
        ) {
            row.optJSONObject("team")?.remove("action")
        }
        val allowed =
            (patch["onDemandActions"] as? List<*>)?.filterIsInstance<String>()?.filter {
                it in WidgetActionLease.actions
            }
        if (allowed != null) row.put("onDemandActions", JSONArray(allowed))
    }

    /**
     * Read-only settlement preserves request identity and patches only sanitized preview/status.
     */
    suspend fun applyIdleStatus(
        context: Context,
        ticket: Map<String, Any?>,
        preview: String? = null,
    ): Boolean {
        val before = WidgetFeed.persistence?.string("oc.widgetSessions") ?: return false
        val current = runCatching { JSONObject(before) }.getOrNull()
        val rows =
            current
                ?.takeIf {
                    it.optString("profileID") == ticket["profileID"] &&
                        it.optString("scopeToken") == ticket["scopeToken"]
                }
                ?.optJSONArray("sessions")
        val row = rows?.let { settledRow(it, ticket) }
        if (current == null || rows == null || row == null) return false
        row.put("busy", false)
        if (!preview.isNullOrBlank()) row.put("preview", preview)
        row.remove("startedAt")
        row.remove("step")
        row.put("onDemandActions", JSONArray())
        return save(context, current, rows, before)
    }

    private fun settledRow(rows: JSONArray, ticket: Map<String, Any?>): JSONObject? =
        (0 until rows.length())
            .mapNotNull { rows.optJSONObject(it) }
            .firstOrNull {
                sameRun(it, ticket) && it.optString("requestID").isEmpty()
            }

    private fun sameRun(row: JSONObject, ticket: Map<String, Any?>): Boolean =
        row.optString("id") == ticket["sessionID"] &&
            row.optString("runMarker") == ticket["runMarker"] &&
            row.optLong("startedAt") == ticket["startedAt"]

    private suspend fun save(
        context: Context,
        current: JSONObject,
        rows: JSONArray,
        before: String,
    ): Boolean {
        current.put(
            "connected",
            BackgroundConnectionService.active && current.optBoolean("connected"),
        )
        current.put(
            "runningCount",
            (0 until rows.length()).count {
                rows.optJSONObject(it)?.optBoolean("busy") == true
            },
        )
        current.put(
            "pendingCount",
            (0 until rows.length()).count {
                !rows.optJSONObject(it)?.optString("requestID").isNullOrEmpty()
            },
        )
        return WidgetFeed.persistence?.cas(
            current.optString("profileID"),
            "oc.widgetSessions",
            before,
            current.toString(),
        ) == true
    }
}
