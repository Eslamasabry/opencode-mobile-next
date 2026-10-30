package io.github.eslamasabry.opencode_mobile

import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

private const val STRIP_SIDE_DP = 150f
private const val STRIP_MIN_TEXT_DP = 80f
private const val STRIP_TITLE_SP = 15f
private const val TINY_TWO_LINE_HEIGHT_DP = 56

/** The 2x1 and 4x1 sizes: the same card's one thing on one row. */
internal class WidgetStripBinder(private val env: WidgetRowEnv, private val text: WidgetRowText) {
    private val g = env.g

    fun bindTiny(views: RemoteViews, session: JSONObject?, frame: SessionWidgetFrame) {
        val m = session?.let { WidgetRowModel(env, it) }
        views.setOnClickPendingIntent(android.R.id.background, env.open(session))
        views.setInt(R.id.wc_title, "setMaxLines", if (frame.effectiveHeightDp >= TINY_TWO_LINE_HEIGHT_DP) 2 else 1)
        if (m == null) {
            views.setViewVisibility(R.id.wc_dot, View.GONE)
            views.setTextViewText(R.id.wc_title, env.str(R.string.widget_v2_empty))
            views.setTextColor(R.id.wc_title, g.subtle)
            return
        }
        // A dot only for a state: amber needs you, mint working; a finished one has none.
        val dot = dotColor(m)
        views.setViewVisibility(R.id.wc_dot, if (dot != null) View.VISIBLE else View.GONE)
        if (dot != null) env.dot(views, R.id.wc_dot, dot)
        val needs = m.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU
        views.setTextViewText(R.id.wc_title, if (needs) env.copy.ask(m.ask, m.question).question else m.title)
        views.setTextColor(R.id.wc_title, g.text)
    }

    fun bindStrip(views: RemoteViews, session: JSONObject?, frame: SessionWidgetFrame) {
        val m = session?.let { WidgetRowModel(env, it) }
        views.setOnClickPendingIntent(android.R.id.background, env.open(session))
        views.setInt(R.id.wc_tile, "setBackgroundResource", g.tileBg)
        views.setInt(R.id.wc_mark, "setColorFilter", g.text)
        views.setInt(R.id.wc_mic, "setBackgroundResource", g.fieldBg)
        views.setInt(R.id.wc_mic_icon, "setColorFilter", g.subtle)
        views.setInt(R.id.wc_allow, "setBackgroundResource", g.mintBg)
        views.setTextColor(R.id.wc_allow, g.onPrimary)
        views.setViewVisibility(R.id.wc_allow, View.GONE)
        views.setViewVisibility(R.id.wc_mic, View.GONE)
        views.setTextColor(R.id.wc_title, g.text)
        if (m == null) {
            views.setTextViewText(R.id.wc_title, env.str(R.string.widget_v2_empty))
            views.setTextColor(R.id.wc_title, g.subtle)
            views.setViewVisibility(R.id.wc_dot, View.GONE)
            views.setViewVisibility(R.id.wc_sub, View.GONE)
            views.setViewVisibility(R.id.wc_sub_still, View.GONE)
            return
        }
        val needs = m.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU
        val ask = env.copy.ask(m.ask, m.question)
        // The short question carries its command; a long command does not fit a strip, so the
        // strip asks in plain words and offers Open (a tap) instead of Allow.
        val stripText = (frame.widthDp - STRIP_SIDE_DP).coerceAtLeast(STRIP_MIN_TEXT_DP)
        val readable = ask.command.isBlank() &&
            WidgetLayoutPolicy.estimateLines(ask.question, stripText, STRIP_TITLE_SP, true, 2) <= 1
        views.setTextViewText(R.id.wc_title, if (needs) ask.question else m.title)
        stripState(views, m)
        if (needs && readable && m.can("allowOnce")) {
            views.setViewVisibility(R.id.wc_allow, View.VISIBLE)
            views.setTextViewText(R.id.wc_allow, env.str(R.string.widget_v2_allow_short))
            views.setOnClickPendingIntent(R.id.wc_allow, env.action(m, "allowOnce"))
        } else if (!needs && !env.settings && m.lead != WidgetLayoutPolicy.Lead.OFFLINE) {
            views.setViewVisibility(R.id.wc_mic, View.VISIBLE)
            views.setContentDescription(R.id.wc_mic, env.str(R.string.widget_v2_voice))
            val working = m.lead == WidgetLayoutPolicy.Lead.WORKING
            views.setOnClickPendingIntent(R.id.wc_mic, if (working) env.micIntent(m) else env.newConversationIntent())
        }
    }

    /** The dot and the one line under the title: amber needs you, the live line, or when. */
    private fun stripState(views: RemoteViews, m: WidgetRowModel) {
        val dot = dotColor(m)
        views.show(R.id.wc_dot, dot != null)
        if (dot != null) env.dot(views, R.id.wc_dot, dot)
        views.setViewVisibility(R.id.wc_sub, View.VISIBLE)
        text.tintClock(views, R.id.wc_sub, if (m.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU) g.amber else g.muted)
        val still = when (m.lead) {
            WidgetLayoutPolicy.Lead.NEEDS_YOU -> env.copy.join(text.statusText(m, true), m.project)
            WidgetLayoutPolicy.Lead.WORKING -> text.liveStatic(m)
            WidgetLayoutPolicy.Lead.OFFLINE -> env.copy.offline(env.lastSeen)
            WidgetLayoutPolicy.Lead.READY -> env.copy.join(m.project, env.copy.ago(m.updatedAt))
        }
        text.clock(views, R.id.wc_sub, m, text.liveWords(m), still)
    }

    /** Amber needs you, mint working; a finished or offline one has no dot. */
    private fun dotColor(m: WidgetRowModel): Int? = when (m.lead) {
        WidgetLayoutPolicy.Lead.NEEDS_YOU -> g.amber
        WidgetLayoutPolicy.Lead.WORKING -> if (m.ticking) g.green else null
        else -> null
    }
}
