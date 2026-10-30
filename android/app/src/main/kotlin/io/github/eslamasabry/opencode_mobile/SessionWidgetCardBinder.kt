package io.github.eslamasabry.opencode_mobile

import android.util.TypedValue
import android.widget.RemoteViews
import org.json.JSONObject

private const val CARD_SIDE_PADDING_DP = 36f
private const val NARROW_WIDTH_DP = 220f
private const val TITLE_SP = 22f
private const val NARROW_TITLE_SP = 18f
private const val COMMAND_SP = 13f
private const val COMMAND_MAX_LINES = 2

/**
 * The adaptive card (widget_card.xml), every size from 2x2 up. It shows the one thing that
 * matters now, in this order: a request that needs the person (the question, the exact command,
 * Allow once where the widget can answer it, else Open); a working turn (its title and one live
 * line); else the last conversation and a quiet New conversation field. Taller sizes add the
 * other conversations as rows; nothing else changes with the size.
 */
internal class WidgetCardBinder(private val env: WidgetRowEnv, private val text: WidgetRowText) {
    private val g = env.g

    fun bind(frame: SessionWidgetFrame): RemoteViews {
        val v = RemoteViews(env.context.packageName, R.layout.widget_card)
        paintChrome(v)
        val sessions = env.state.sessions
        val hero = sessions.firstOrNull()?.let { WidgetRowModel(env, it) }
        if (hero == null) {
            empty(v)
            return v
        }
        // Narrow in text widths: large text on a wide card makes the same choices as a 2x2.
        val fontScale = env.context.resources.configuration.fontScale.coerceAtLeast(1f)
        val narrow = frame.widthDp / fontScale < NARROW_WIDTH_DP
        val inner = frame.widthDp - CARD_SIDE_PADDING_DP
        v.setOnClickPendingIntent(android.R.id.background, env.open(hero.session))
        header(v, hero, narrow)
        val needs = hero.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU
        val ask = if (needs) env.copy.ask(hero.ask, hero.question) else null
        val title = ask?.question ?: hero.title
        val titleSp = if (narrow) NARROW_TITLE_SP else TITLE_SP
        val commandNeed = ask?.command?.let {
            WidgetLayoutPolicy.estimateLines(it, inner, COMMAND_SP, false, COMMAND_MAX_LINES + 1)
        } ?: 0
        val live = hero.lead == WidgetLayoutPolicy.Lead.WORKING
        val metaText = meta(hero, narrow)
        val plan = WidgetLayoutPolicy.cardPlan(
            frame.effectiveHeightDp,
            narrow,
            WidgetLayoutPolicy.CardNeeds(
                WidgetLayoutPolicy.estimateLines(title, inner, titleSp, true, 2),
                // A command longer than two lines cannot be read whole on the card: Open only.
                if (commandNeed > COMMAND_MAX_LINES) 0 else commandNeed,
                live,
                metaText.isNotBlank(),
                sessions.size - 1,
            ),
        )
        v.setTextViewText(R.id.wc_title, title)
        v.setTextColor(R.id.wc_title, g.text)
        v.setTextViewTextSize(R.id.wc_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
        v.setInt(R.id.wc_title, "setMaxLines", plan.title)
        command(v, ask?.command.orEmpty(), plan.command)
        liveLine(v, hero, plan.live)
        v.show(R.id.wc_meta, plan.meta)
        v.setTextViewText(R.id.wc_meta, metaText)
        v.setTextColor(R.id.wc_meta, g.muted)
        WidgetCardRows(env, text).bind(v, sessions.drop(1).take(plan.rows))
        // A request may be allowed here only when its command is on the card, or it has none.
        val commandShown = ask == null || ask.command.isBlank() || plan.command > 0
        bottom(v, hero, needs, commandShown, narrow)
        return v
    }

    /** The ground, the header tile and the field follow the app's dark or light Graphite. */
    private fun paintChrome(v: RemoteViews) {
        g.paintGround(v, WidgetV2Style.backgroundMode(env.context, env.id, env.snapshot))
        v.setInt(R.id.wc_tile, "setBackgroundResource", g.tileBg)
        v.setInt(R.id.wc_mark, "setColorFilter", g.text)
        v.setTextColor(R.id.wc_app, g.subtle)
        v.setInt(R.id.wc_field, "setBackgroundResource", g.fieldBg)
        v.setInt(R.id.wc_mic_icon, "setColorFilter", g.subtle)
        v.setInt(R.id.wc_allow, "setBackgroundResource", g.mintBg)
        v.setTextColor(R.id.wc_allow, g.onPrimary)
        v.setInt(R.id.wc_open, "setBackgroundResource", g.outlineBg)
        v.setTextColor(R.id.wc_open_text, g.text)
        v.setInt(R.id.wc_open_arrow, "setColorFilter", g.text)
    }

    /** One status in the header, only when it is news: needs you (amber dot and word), sending,
     * or an action that did not land. Working and ready say themselves in the body. */
    private fun header(v: RemoteViews, m: WidgetRowModel, narrow: Boolean) {
        val notice = text.notice()
        val (word, color) = when {
            env.connection == WidgetV2Policy.Connection.PENDING && m.request ->
                env.str(R.string.widget_v2_pending) to g.muted
            notice != null -> notice to g.muted
            m.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU -> env.copy.needsYou() to g.amber
            else -> "" to g.muted
        }
        val amber = color == g.amber
        v.show(R.id.wc_dot, amber)
        if (amber) env.dot(v, R.id.wc_dot, g.amber)
        v.show(R.id.wc_status, word.isNotBlank())
        // A narrow card has room for the mark and the status, not the name as well.
        v.show(R.id.wc_app, !(narrow && word.isNotBlank()))
        v.setTextViewText(R.id.wc_status, word)
        v.setTextColor(R.id.wc_status, color)
    }

    /** Where it is: "This phone · neon-breaker"; a finished one adds when ("· 26 min ago"); offline
     * says so instead. A narrow card leaves the server out. */
    private fun meta(m: WidgetRowModel, narrow: Boolean): String {
        val server = if (narrow) "" else m.server
        return when (m.lead) {
            WidgetLayoutPolicy.Lead.OFFLINE -> env.copy.offline(env.lastSeen)
            WidgetLayoutPolicy.Lead.READY -> env.copy.join(server, m.project, env.copy.ago(m.updatedAt))
            else -> env.copy.join(server, m.project)
        }
    }

    private fun command(v: RemoteViews, command: String, lines: Int) {
        v.show(R.id.wc_cmd, lines > 0)
        v.setTextViewText(R.id.wc_cmd, command)
        v.setTextColor(R.id.wc_cmd, g.subtle)
        v.setInt(R.id.wc_cmd, "setMaxLines", lines.coerceAtLeast(1))
    }

    /** "Editing files · 2:13": a mint dot, the activity and a clock that counts in the launcher. */
    private fun liveLine(v: RemoteViews, m: WidgetRowModel, shown: Boolean) {
        v.show(R.id.wc_live_row, shown)
        text.tintClock(v, R.id.wc_live, g.subtle)
        env.dot(v, R.id.wc_live_dot, if (m.ticking) g.green else g.idle)
        text.clock(v, R.id.wc_live, m, text.liveWords(m), if (shown) text.liveStatic(m) else "")
    }

    private fun bottom(v: RemoteViews, m: WidgetRowModel, needs: Boolean, commandShown: Boolean, narrow: Boolean) {
        val team = m.teamAction.isNotBlank() && m.can(m.teamAction)
        // A turn the widget cannot see live cannot be steered from it: Open alone.
        val stale = m.lead == WidgetLayoutPolicy.Lead.WORKING && !m.ticking && !env.settings
        v.show(R.id.wc_buttons, needs || team || stale)
        v.show(R.id.wc_field, !(needs || team || stale))
        if (needs || team || stale) answers(v, m, needs && commandShown && m.can("allowOnce"), team, narrow)
        else quietField(v, m)
    }

    /** Allow once (or the AI Team step) in mint, Open beside it; Open alone when the widget cannot answer. */
    private fun answers(v: RemoteViews, m: WidgetRowModel, allow: Boolean, team: Boolean, narrow: Boolean) {
        v.show(R.id.wc_allow, allow || team)
        if (allow) {
            v.setTextViewText(R.id.wc_allow,
                env.str(if (narrow) R.string.widget_v2_allow_short else R.string.widget_v2_allow))
            v.setContentDescription(R.id.wc_allow, env.str(R.string.widget_allow_for, m.title))
            v.setOnClickPendingIntent(R.id.wc_allow, env.action(m, "allowOnce"))
        } else if (team) {
            v.setTextViewText(R.id.wc_allow, env.str(
                if (m.teamAction == "approvePlan") R.string.widget_v2_approve else R.string.widget_v2_resume))
            v.setOnClickPendingIntent(R.id.wc_allow, env.action(m, m.teamAction))
        }
        // Open names its target to a screen reader; the arrow says it on narrow cards too.
        v.setContentDescription(R.id.wc_open, env.str(R.string.widget_v2_open_chat))
        v.show(R.id.wc_open_arrow, !(narrow && (allow || team)))
        v.setOnClickPendingIntent(R.id.wc_open, env.open(m.session))
    }

    /** The quiet field: steer a working turn, else start a new conversation. The mic shows only
     * where dictation can reach the agent now. */
    private fun quietField(v: RemoteViews, m: WidgetRowModel) {
        val working = m.lead == WidgetLayoutPolicy.Lead.WORKING
        val (words, intent) = when {
            env.settings -> env.str(R.string.widget_v2_background_settings) to
                SessionWidgetIntents.backgroundSettingsIntent(env.context, env.id)
            working -> env.str(R.string.widget_v2_steer_hint) to env.micIntent(m)
            m.project.isBlank() -> env.str(R.string.widget_v2_new) to env.newConversationIntent()
            else -> env.str(R.string.widget_v2_new_in, m.project) to env.newConversationIntent()
        }
        val live = env.connection == WidgetV2Policy.Connection.CONNECTED && env.overlays
        val mic = !env.settings && live &&
            (if (working) m.steerable else env.snapshot.optString("lastProjectID").isNotBlank())
        v.setTextViewText(R.id.wc_field_text, words)
        v.setTextColor(R.id.wc_field_text, g.muted)
        v.setOnClickPendingIntent(R.id.wc_field, intent)
        v.show(R.id.wc_mic, mic)
        if (mic) {
            v.setContentDescription(R.id.wc_mic, env.str(R.string.widget_v2_voice))
            v.setOnClickPendingIntent(R.id.wc_mic, intent)
        }
    }

    /** Nothing published yet (never connected, or the server was removed): one plain sentence. */
    private fun empty(v: RemoteViews) {
        v.show(R.id.wc_dot, false)
        v.show(R.id.wc_status, false)
        v.setOnClickPendingIntent(android.R.id.background, env.open(null))
        v.setTextViewText(R.id.wc_title, env.str(R.string.widget_v2_empty))
        v.setTextColor(R.id.wc_title, g.subtle)
        v.setTextViewTextSize(R.id.wc_title, TypedValue.COMPLEX_UNIT_SP, NARROW_TITLE_SP)
        v.setInt(R.id.wc_title, "setMaxLines", 2)
        for (id in listOf(R.id.wc_cmd, R.id.wc_live_row, R.id.wc_meta, R.id.wc_rows, R.id.wc_buttons, R.id.wc_field)) {
            v.show(id, false)
        }
        v.show(R.id.wc_gap, true)
        v.show(R.id.wc_gap_bottom, true)
        v.show(R.id.wc_gap_rows, false)
    }
}

/** The other conversations on a tall card: quiet hairline rows, a title and one state line each. */
internal class WidgetCardRows(private val env: WidgetRowEnv, private val text: WidgetRowText) {
    private val g = env.g

    fun bind(v: RemoteViews, others: List<JSONObject>) {
        v.removeAllViews(R.id.wc_rows)
        v.show(R.id.wc_rows, others.isNotEmpty())
        // With rows, they take the spare height; without, it is shared around the story.
        v.show(R.id.wc_gap, others.isEmpty())
        v.show(R.id.wc_gap_bottom, others.isEmpty())
        v.show(R.id.wc_gap_rows, others.isNotEmpty())
        others.forEach { v.addView(R.id.wc_rows, row(WidgetRowModel(env, it))) }
    }

    private fun row(m: WidgetRowModel): RemoteViews {
        val r = RemoteViews(env.context.packageName, R.layout.widget_card_row)
        r.setOnClickPendingIntent(R.id.wc_row, env.open(m.session))
        r.setInt(R.id.wc_divider, "setBackgroundColor", g.divider)
        val needs = m.lead == WidgetLayoutPolicy.Lead.NEEDS_YOU
        r.setTextViewText(R.id.wc_row_title, if (needs) env.copy.ask(m.ask, m.question).question else m.title)
        r.setTextColor(R.id.wc_row_title, g.text)
        val dot = when (m.lead) {
            WidgetLayoutPolicy.Lead.NEEDS_YOU -> g.amber
            WidgetLayoutPolicy.Lead.WORKING -> if (m.ticking) g.green else null
            else -> null
        }
        r.show(R.id.wc_row_dot, dot != null)
        if (dot != null) env.dot(r, R.id.wc_row_dot, dot)
        text.tintClock(r, R.id.wc_row_state, if (needs) g.amber else g.muted)
        val still = when (m.lead) {
            WidgetLayoutPolicy.Lead.NEEDS_YOU -> env.copy.join(env.copy.needsYou(), m.project)
            WidgetLayoutPolicy.Lead.WORKING -> text.liveStatic(m)
            WidgetLayoutPolicy.Lead.OFFLINE -> env.copy.join(m.project, env.copy.ago(m.updatedAt))
            WidgetLayoutPolicy.Lead.READY -> env.copy.join(m.project, env.copy.ago(m.updatedAt))
        }
        text.clock(r, R.id.wc_row_state, m, text.liveWords(m), still)
        return r
    }
}
