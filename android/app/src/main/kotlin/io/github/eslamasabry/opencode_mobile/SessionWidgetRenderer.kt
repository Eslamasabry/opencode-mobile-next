package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.widget.RemoteViews
import org.json.JSONObject

/** Renders the widget for one size from the cached snapshot: the adaptive card from 2x2 up,
 * the strip at 4x1 and the tiny line at 2x1 (see [WidgetV2Policy.Size]); the Conversations
 * entry has its own layout ([renderConversations]). */
internal class SessionWidgetRenderer(
    private val context: Context,
    private val id: Int,
    private val snapshot: JSONObject,
    /** Whether the live background connection runs; a parameter so renders can be checked off-device. */
    private val serviceActive: Boolean = BackgroundConnectionService.active,
) {
    fun render(width: Int, height: Int): RemoteViews {
        val size = WidgetSizePolicy.size(width, height)
        val state = SessionWidgetSnapshot(snapshot, serviceActive)
        val graphite = WidgetGraphite.of(context, snapshot)
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val frame = SessionWidgetFrame(width.toFloat(), (height / fontScale).toInt())
        val env = WidgetRowEnv(context, id, snapshot, state, graphite)
        val text = WidgetRowText(env)
        return when (size) {
            WidgetV2Policy.Size.TINY, WidgetV2Policy.Size.STRIP -> {
                val tiny = size == WidgetV2Policy.Size.TINY
                val views = RemoteViews(context.packageName,
                    if (tiny) R.layout.widget_sessions_compact else R.layout.widget_sessions_strip)
                graphite.paintGround(views, WidgetV2Style.backgroundMode(context, id, snapshot))
                val strip = WidgetStripBinder(env, text)
                if (tiny) strip.bindTiny(views, state.sessions.firstOrNull(), frame)
                else strip.bindStrip(views, state.sessions.firstOrNull(), frame)
                views
            }
            // 2x2, 4x2 and taller: one card; the height decides how many rows it adds.
            else -> WidgetCardBinder(env, text).bind(frame)
        }
    }

    /** The Conversations widget (the 4x2 picker entry): the same snapshot, its own layout, at
     * whatever size the launcher gives it. */
    fun renderConversations(width: Int, height: Int): RemoteViews {
        val state = SessionWidgetSnapshot(snapshot, serviceActive)
        val graphite = WidgetGraphite.of(context, snapshot)
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val env = WidgetRowEnv(context, id, snapshot, state, graphite)
        return ConversationsWidgetBinder(env, WidgetRowText(env))
            .bind(SessionWidgetFrame(width.toFloat(), (height / fontScale).toInt()))
    }
}

/** The usable size at font scale 1: the width in dp and the height already divided by the font scale. */
internal class SessionWidgetFrame(val widthDp: Float, val effectiveHeightDp: Int)

internal class SessionWidgetSnapshot(
    private val snapshot: JSONObject,
    private val serviceActive: Boolean = BackgroundConnectionService.active,
) {
    val sessions: List<JSONObject> = visibleSessions()
    /** Working turns that are not already counted as needing you. */
    val running = WidgetStatusRules.workingCount(
        snapshot.optInt("runningCount", sessions.count { it.optBoolean("busy") }),
        sessions.count { it.optBoolean("busy") && it.optString("requestID").isNotBlank() })
    val needs = snapshot.optInt("pendingCount").coerceAtLeast(0)
    val connection: WidgetV2Policy.Connection = connection()

    private fun visibleSessions(): List<JSONObject> {
        val array = snapshot.optJSONArray("sessions")
        val rows = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .filter { it.optString("id").isNotBlank() }.take(MAX_SESSIONS)
        val chats = rows.any { it.optJSONObject("team") == null }
        return rows.filter {
            WidgetStatusRules.showRow(it.optJSONObject("team") != null,
                it.optJSONObject("team")?.optString("action").orEmpty(), it.optString("requestID"), chats)
        }.sortedBy {
            WidgetStatusRules.rank(
                it.optString("requestID").isNotBlank() || it.optBoolean("needsYou"), it.optBoolean("busy"))
        }
    }

    private fun connection(): WidgetV2Policy.Connection {
        val receipt = snapshot.optJSONObject("actionResult")
        val onDemandAvailable = WidgetV2Policy.onDemandAvailable(
            snapshot.optInt("version"),
            snapshot.optJSONObject("onDemand")?.optString("fingerprint")?.isNotBlank() == true)
        return WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(snapshot.optInt("version"),
            snapshot.optBoolean("connected"), serviceActive,
            onDemandAvailable, BackgroundConnectionService.widgetActionPending,
            WidgetV2Policy.needsBackgroundSettings(receipt?.optString("status"), receipt?.optString("error"))))
    }

    private companion object {
        const val MAX_SESSIONS = 12
    }
}
