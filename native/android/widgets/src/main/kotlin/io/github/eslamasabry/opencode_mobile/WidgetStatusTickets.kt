package io.github.eslamasabry.opencode_mobile

import android.content.Context
import org.json.JSONObject

/** Durable opaque tickets live in the profile deletion namespace, not WorkData. */
internal object WidgetStatusTickets {
    private fun key(profile: String) = "flutter.oc.widgetFollowup.$profile"
    private fun prefs(context: Context) = context.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
    private fun tickets(context: Context, profile: String) = runCatching {
        JSONObject(prefs(context).getString(key(profile), "{}") ?: "{}")
    }.getOrElse { JSONObject() }

    @Synchronized
    fun save(context: Context, profile: String, session: String, ticket: JSONObject): Boolean {
        val saved = tickets(context, profile).put(session, ticket)
        return prefs(context).edit().putString(key(profile), saved.toString()).commit()
    }

    @Synchronized
    fun read(context: Context, profile: String, session: String, token: String): Map<String, Any?>? {
        val ticket = tickets(context, profile).optJSONObject(session)
            ?.takeIf { it.optString("token") == token }
        val current = ticket?.let { WidgetStatusIdentity.current(context, profile, session) }
        if (ticket == null || current == null) return null
        val values = ticket.keys().asSequence().associateWith {
            if (it == "startedAt") ticket.optLong(it) else ticket.opt(it)
        }
        return values.takeIf { WidgetStatusPolicy.matches(it, current) }
    }

    /** Compare token before removing so an older job cannot cancel a newer tap. */
    @Synchronized
    fun remove(context: Context, profile: String, session: String, token: String) {
        val saved = tickets(context, profile)
        if (saved.optJSONObject(session)?.optString("token") != token) return
        saved.remove(session)
        val edit = prefs(context).edit()
        if (saved.length() == 0) edit.remove(key(profile)) else edit.putString(key(profile), saved.toString())
        edit.commit()
    }

    /** Drops this session's ticket whatever its token is. */
    @Synchronized
    fun discard(context: Context, profile: String, session: String) {
        val token = tickets(context, profile).optJSONObject(session)?.optString("token") ?: return
        remove(context, profile, session, token)
    }

    @Synchronized
    fun clear(context: Context, profile: String) {
        prefs(context).edit().remove(key(profile)).commit()
    }
}
