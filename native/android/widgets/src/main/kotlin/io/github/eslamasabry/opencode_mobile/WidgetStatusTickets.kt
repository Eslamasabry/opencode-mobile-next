package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import org.json.JSONObject

/** Durable opaque tickets live in the profile deletion namespace, not WorkData. */
internal object WidgetStatusTickets {
    private fun key(profile: String) = "oc.widgetFollowup.$profile"

    private fun tickets(context: Context, profile: String) = runCatching {
        JSONObject(WidgetFeed.persistence?.string(key(profile)) ?: "{}")
    }
        .getOrElse { JSONObject() }

    suspend fun save(
        context: Context,
        profile: String,
        session: String,
        ticket: JSONObject,
    ): Boolean {
        val storage = WidgetFeed.persistence ?: return false
        val before = storage.string(key(profile))
        val saved = tickets(context, profile).put(session, ticket)
        return storage.cas(profile, key(profile), before, saved.toString())
    }

    fun read(
        context: Context,
        profile: String,
        session: String,
        token: String,
    ): Map<String, Any?>? {
        val ticket =
            tickets(context, profile).optJSONObject(session)?.takeIf {
                it.optString("token") == token
            }
        val current = ticket?.let { WidgetStatusIdentity.current(context, profile, session) }
        if (ticket == null || current == null) return null
        val values =
            ticket.keys().asSequence().associateWith {
                if (it == "startedAt") ticket.optLong(it) else ticket.opt(it)
            }
        return values.takeIf { WidgetStatusPolicy.matches(it, current) }
    }

    /** Compare token before removing so an older job cannot cancel a newer tap. */
    suspend fun remove(context: Context, profile: String, session: String, token: String) {
        val storage = WidgetFeed.persistence ?: return
        val before = storage.string(key(profile)) ?: return
        val saved = runCatching { JSONObject(before) }.getOrNull() ?: return
        if (saved.optJSONObject(session)?.optString("token") != token) return
        saved.remove(session)
        storage.cas(
            profile,
            key(profile),
            before,
            if (saved.length() == 0) null else saved.toString(),
        )
    }

    /** Drops this session's ticket whatever its token is. */
    suspend fun discard(context: Context, profile: String, session: String) {
        val token = tickets(context, profile).optJSONObject(session)?.optString("token") ?: return
        remove(context, profile, session, token)
    }

    suspend fun clear(context: Context, profile: String) {
        val storage = WidgetFeed.persistence ?: return
        val before = storage.string(key(profile)) ?: return
        storage.store.compareAndSet(storage.lanes.app, key(profile), before, null)
    }
}
