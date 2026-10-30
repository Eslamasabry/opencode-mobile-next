package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/** What every card, strip, tiny and list binder shares: the snapshot, its connection and the common paints. */
internal class WidgetRowEnv(
    val context: Context,
    val id: Int,
    val snapshot: JSONObject,
    val state: SessionWidgetSnapshot,
    val g: WidgetGraphite
) {
    val copy = WidgetCopy(context)
    val connection = state.connection
    val offline = connection == WidgetV2Policy.Connection.OFFLINE ||
        connection == WidgetV2Policy.Connection.BACKGROUND_SETTINGS
    val settings = connection == WidgetV2Policy.Connection.BACKGROUND_SETTINGS
    val overlays = WidgetV2Bridge.overlayReady(context)
    val lastSeen: Long = listOf(
        snapshot.optLong("lastSeenAt"),
        snapshot.optLong("pausedAt"),
        state.sessions.firstOrNull()?.optLong("updatedAt") ?: 0L
    ).firstOrNull { it > 0 } ?: 0L

    fun str(resId: Int): String = NativeStrings.get(context, resId)

    fun str(resId: Int, arg: Any): String = NativeStrings.get(context, resId, arg)

    fun open(session: JSONObject?) = SessionWidgetIntents.chatIntent(context, id, snapshot, session)

    /** The conversation, opened to answer: composer focused, keyboard up. */
    fun reply(session: JSONObject) = SessionWidgetIntents.chatIntent(context, id, snapshot, session, reply = true)

    /** The mic: dictate into the working turn when that can be done live, else open the conversation. */
    fun micIntent(m: WidgetRowModel?): PendingIntent {
        val live = connection == WidgetV2Policy.Connection.CONNECTED && overlays
        if (m != null && live && m.steerable) {
            return WidgetV2Bridge.routeIntent(context, id, snapshot, m.session, "steer")
        }
        return open(m?.session)
    }

    fun newConversationIntent(): PendingIntent {
        val voiceReady = overlays &&
            connection == WidgetV2Policy.Connection.CONNECTED &&
            snapshot.optString("lastProjectID").isNotBlank()
        return when {
            settings -> SessionWidgetIntents.backgroundSettingsIntent(context, id)
            voiceReady -> WidgetV2Bridge.routeIntent(context, id, snapshot, null, "voice")
            else -> open(null)
        }
    }

    fun action(m: WidgetRowModel, name: String): PendingIntent =
        WidgetV2Bridge.actionIntent(context, id, snapshot, m.session, name)

    fun dot(v: RemoteViews, viewId: Int, color: Int) = v.setInt(viewId, "setColorFilter", color)
}

/** Shows or hides [id]; a launcher re-applies updates onto old views, so every update says which. */
internal fun RemoteViews.show(id: Int, shown: Boolean) = setViewVisibility(id, if (shown) View.VISIBLE else View.GONE)

/** What one conversation is, read once. */
internal class WidgetRowModel(private val env: WidgetRowEnv, val session: JSONObject) {
    val title = session.optString("title")
    val project = session.optString("project")
    /** The server's display name ("This phone", the name it was saved under); never an address. */
    val server = session.optString("server")
    val request = session.optString("requestID").isNotBlank()
    /** Waiting on the person: an answerable request, or a question or another server's ask that
     * only the app can answer. */
    val needs = request || session.optBoolean("needsYou")
    val busy = session.optBoolean("busy")
    val ask: JSONObject? = session.optJSONObject("ask")

    /** What it is doing right now, while busy; never the previous reply. */
    val step = if (busy) session.optString("step") else ""
    val updatedAt = session.optLong("updatedAt")
    val startedAt = session.optLong("startedAt")
    val teamAction = session.optJSONObject("team")?.optString("action").orEmpty()
    val lead = WidgetStatusRules.lead(needs, busy, env.offline)
    val valid = SessionWidgetActions.validActions(env.snapshot, session)
    val advertised =
        if (env.snapshot.optJSONObject("onDemand")?.optString("fingerprint")?.isNotBlank() == true) {
            SessionWidgetActions.advertisedActions(session)
        } else {
            emptySet()
        }
    val question = ask?.optString("q").orEmpty().ifBlank { title }
    val ticking = !needs && WidgetStatusRules.timerTicks(
        busy,
        startedAt,
        env.connection == WidgetV2Policy.Connection.CONNECTED
    )

    /** A working turn that accepts steering while the widget is live. */
    val steerable: Boolean
        get() = busy && !needs && session.optString("steerMode") in STEER_MODES

    fun can(name: String) = WidgetV2Policy.actionEnabled(env.connection, name, advertised, name in valid)

    private companion object {
        val STEER_MODES = setOf("steer", "afterReply")
    }
}

/** The words and dot colours a conversation shows. */
internal class WidgetRowText(private val env: WidgetRowEnv) {
    fun notice(): String? =
        when (WidgetV2Policy.actionResultNotice(env.snapshot.optJSONObject("actionResult")?.optString("status"))) {
            "rejected" -> env.str(R.string.widget_v2_failed)
            "uncertain" -> env.str(R.string.widget_v2_uncertain)
            else -> null
        }

    /** One status phrase per conversation: the sending state or a failed action outranks the ordinary words. */
    fun statusText(m: WidgetRowModel, top: Boolean): String {
        val notice = if (top) notice() else null
        return when {
            env.connection == WidgetV2Policy.Connection.PENDING && m.request -> env.str(R.string.widget_v2_pending)
            notice != null -> notice
            else -> leadText(m)
        }
    }

    private fun leadText(m: WidgetRowModel): String = when (m.lead) {
        WidgetLayoutPolicy.Lead.NEEDS_YOU -> env.copy.needsYou()
        WidgetLayoutPolicy.Lead.OFFLINE -> env.copy.offline(env.lastSeen)
        WidgetLayoutPolicy.Lead.WORKING -> env.copy.working()
        WidgetLayoutPolicy.Lead.READY -> env.copy.ready()
    }

    /** The live line's words without the clock: the activity ("Editing files"), else "Working".
     * The app's activity sentences end in an ellipsis; the clock follows here instead. */
    fun liveWords(m: WidgetRowModel): String =
        m.step.trim().removeSuffix("…").removeSuffix("...").trim().ifBlank { env.copy.working() }

    /** The live line when the clock cannot count. Not connected, a cached turn cannot be shown as
     * still running: "Offline · last seen 5 min ago". Connected but without a start time:
     * "Editing files · 3 min", or the words alone. */
    fun liveStatic(m: WidgetRowModel): String {
        if (env.connection != WidgetV2Policy.Connection.CONNECTED) return env.copy.offline(env.lastSeen)
        val since = WidgetElapsedPolicy.sinceUnit(System.currentTimeMillis(), m.startedAt)
        val span = when {
            since == null -> ""
            since.first == "min" -> env.str(R.string.widget_v2_span_min, since.second)
            else -> env.str(R.string.widget_v2_span_hour, since.second)
        }
        return env.copy.join(liveWords(m), span)
    }

    /** Starts [viewId] (a Chronometer) counting up from the turn's start after [words], or stops it
     * and shows [still]. A launcher re-applies updates onto running views, so it is always set. */
    fun clock(v: RemoteViews, viewId: Int, m: WidgetRowModel, words: String, still: String) {
        // A Chronometer speaks its own elapsed time (TalkBack read "3 min ago" as "211 hours"), and
        // setContentDescription does not override that. So a stopped clock is a plain TextView twin.
        val twin = stillId(viewId)
        val base = WidgetStatusRules.chronometerBase(
            m.startedAt, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()
        )
        if (m.ticking) {
            v.setChronometer(viewId, base, words.replace("%", "%%") + " · %s", true)
            v.setViewVisibility(viewId, android.view.View.VISIBLE)
            v.setViewVisibility(twin, android.view.View.GONE)
        } else {
            v.setChronometer(viewId, base, null, false)
            v.setViewVisibility(viewId, android.view.View.GONE)
            v.setTextViewText(twin, still)
            v.setViewVisibility(twin, android.view.View.VISIBLE)
        }
    }

    /** The plain TextView that stands in for the Chronometer [viewId] while it is stopped. */
    fun stillId(viewId: Int): Int = when (viewId) {
        R.id.wc_live -> R.id.wc_live_still
        R.id.wc_row_state -> R.id.wc_row_state_still
        R.id.wc_sub -> R.id.wc_sub_still
        else -> error("no still twin for view $viewId")
    }

    /** Colors the clock and its twin alike. */
    fun tintClock(v: RemoteViews, viewId: Int, color: Int) {
        v.setTextColor(viewId, color)
        v.setTextColor(stillId(viewId), color)
    }
}
