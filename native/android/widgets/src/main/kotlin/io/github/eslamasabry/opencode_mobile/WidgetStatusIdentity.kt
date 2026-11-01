package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** The identity of a running turn as the widget snapshot and the saved target currently describe it. */
internal object WidgetStatusIdentity {
    fun current(context: Context, profile: String, session: String): Map<String, Any?>? {
        val prefs = context.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("flutter.oc.activeProfile", null) != profile) return null
        val snapshot = WidgetV2Bridge.snapshot(context)?.takeIf { it.optString("profileID") == profile }
        return snapshot?.let { identity(prefs, it, profile, session) }
    }

    private fun identity(
        prefs: SharedPreferences,
        snapshot: JSONObject,
        profile: String,
        session: String,
    ): Map<String, Any?>? {
        val row = sessionRow(snapshot, session)
        val fingerprint = snapshot.optJSONObject("onDemand")?.optString("fingerprint").orEmpty()
        val scope = snapshot.optString("scopeToken")
        val trusted = row != null && fingerprint.isNotBlank() && targetMatches(prefs, profile, fingerprint, scope)
        return if (!trusted || row == null) null else mapOf(
            "profileID" to profile, "sessionID" to session,
            "scopeToken" to scope, "fingerprint" to fingerprint,
            "runMarker" to row.optString("runMarker"), "startedAt" to row.optLong("startedAt"),
            "busy" to row.optBoolean("busy"), "requestID" to row.optString("requestID"),
        )
    }

    private fun sessionRow(snapshot: JSONObject, session: String): JSONObject? {
        val rows = snapshot.optJSONArray("sessions") ?: return null
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .firstOrNull { it.optString("id") == session }
    }

    private fun targetMatches(prefs: SharedPreferences, profile: String, fingerprint: String, scope: String): Boolean {
        val target = runCatching { JSONObject(prefs.getString("flutter.oc.widgetTarget.$profile", "") ?: "") }
            .getOrNull()
        return target != null && target.optString("fingerprint") == fingerprint &&
            target.optString("scopeToken") == scope
    }
}
