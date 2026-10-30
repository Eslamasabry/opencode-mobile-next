package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * The Conversations widget (widget_conversations.xml, the 4x2 entry). The top item is the one
 * that matters now: a request that needs you (its ask, the exact command, Allow / Reply / Open),
 * else a working turn (title, "Working · project · step · 3 min"), else the last conversation.
 * The next conversations follow as one-line rows, as many as the height holds. Allow keeps the
 * card's rule: only a command shown whole may be allowed from the home screen, and the tap goes
 * through the same action policy, lease and confirm step as before.
 */
internal class ConversationsWidgetBinder(private val env: WidgetRowEnv, private val text: WidgetRowText) {
    private val g = env.g

    fun bind(frame: SessionWidgetFrame): RemoteViews {
        val v = RemoteViews(env.context.packageName, R.layout.widget_conversations)
        paintChrome(v)
        // Edit scopes the widget to one project; "" lists them all.
        val project = WidgetV2Style.project(env.context, env.id, env.snapshot)
        val sessions = env.state.sessions.filter { project.isBlank() || it.optString("project") == project }
        if (project.isNotBlank()) {
            v.setTextViewText(R.id.cv_label, env.copy.join(env.str(R.string.widget_conversations_title), project))
        }
        val hero = sessions.firstOrNull()?.let { WidgetRowModel(env, it) }
        v.setOnClickPendingIntent(android.R.id.background, env.open(null))
        if (hero == null) {
            empty(v, project)
            return v
        }
        val inner = frame.widthDp - SIDE_PADDING_DP
        v.setOnClickPendingIntent(R.id.cv_hero, env.open(hero.session))
        val heroHeight = when (hero.lead) {
            WidgetLayoutPolicy.Lead.NEEDS_YOU -> needs(v, hero, inner)
            WidgetLayoutPolicy.Lead.WORKING -> working(v, hero)
            else -> last(v, hero)
        }
        val rows = ConversationsWidgetPlan.rows(frame.effectiveHeightDp, heroHeight, sessions.size - 1)
        ConversationsWidgetRows(env).bind(v, sessions.drop(1).take(rows))
        return v
    }

    private fun paintChrome(v: RemoteViews) {
        g.paintGround(v, WidgetV2Style.backgroundMode(env.context, env.id, env.snapshot))
        v.setInt(R.id.cv_tile, "setBackgroundResource", g.tileBg)
        v.setInt(R.id.cv_mark, "setColorFilter", g.text)
        v.setTextColor(R.id.cv_label, g.subtle)
        v.setTextViewText(R.id.cv_label, env.str(R.string.widget_conversations_title))
        v.setInt(R.id.cv_new_pill, "setBackgroundResource", g.pillBg)
        v.setInt(R.id.cv_plus, "setColorFilter", g.text)
        v.setContentDescription(R.id.cv_new, env.str(R.string.widget_v2_new))
        v.setOnClickPendingIntent(R.id.cv_new, ConversationsWidgetRows.newConversation(env))
        v.setTextColor(R.id.cv_title, g.text)
        v.setTextColor(R.id.cv_meta, g.muted)
        v.setInt(R.id.cv_ask, "setBackgroundResource", g.codeBg)
        v.setTextColor(R.id.cv_ask, g.text)
        v.setInt(R.id.cv_allow, "setBackgroundResource", g.amberBg)
        v.setTextColor(R.id.cv_allow, g.onAmber)
        for (id in listOf(R.id.cv_reply, R.id.cv_open)) {
            v.setInt(id, "setBackgroundResource", g.pillBg)
            v.setTextColor(id, g.text)
        }
        v.setInt(R.id.cv_divider, "setBackgroundColor", g.divider)
    }

    /** "● Needs you · project · Run this command?", the command in mono, then the answers. A
     * question without a command reads as the title instead. Returns the hero's height in dp. */
    private fun needs(v: RemoteViews, m: WidgetRowModel, innerDp: Float): Int {
        val (word, color) = needsWord(m)
        val question = m.ask?.optString("q").orEmpty().ifBlank { m.title }
        val command = m.ask?.optString("file").orEmpty()
        val commandLines = if (command.isBlank()) 0
            else ConversationsWidgetPlan.commandLines(command, innerDp - CODE_PADDING_DP, CODE_SP)
        var height = META_DP
        if (command.isBlank()) {
            title(v, question, 2)
            height += ConversationsWidgetPlan.titleHeight(
                WidgetLayoutPolicy.estimateLines(question, innerDp, TITLE_SP, true, 2))
            meta(v, if (color == g.amber) g.amber else null, word, color, m.project)
        } else {
            v.show(R.id.cv_title, false)
            meta(v, if (color == g.amber) g.amber else null, word, color, m.project, question)
        }
        v.show(R.id.cv_ask, command.isNotBlank())
        if (command.isNotBlank()) {
            v.setTextViewText(R.id.cv_ask, command)
            v.setInt(R.id.cv_ask, "setMaxLines", MAX_COMMAND_LINES)
            height += ConversationsWidgetPlan.askHeight(commandLines.coerceAtMost(MAX_COMMAND_LINES))
        }
        answers(v, m, command.isBlank() || commandLines <= MAX_COMMAND_LINES)
        return height + BUTTONS_DP
    }

    /** Allow (only what can be read whole here; a longer command is answered in the app) or the
     * AI Team step, then Reply and Open. */
    private fun answers(v: RemoteViews, m: WidgetRowModel, readable: Boolean) {
        val allow = m.request && readable && m.can("allowOnce")
        val team = m.teamAction.isNotBlank() && m.can(m.teamAction)
        v.show(R.id.cv_buttons, true)
        v.show(R.id.cv_allow_hit, allow || team)
        // The touch area is the 48dp frame around each pill; its spoken name says which
        // conversation it acts on.
        if (allow) {
            v.setTextViewText(R.id.cv_allow, env.str(R.string.widget_v2_allow_short))
            v.setContentDescription(R.id.cv_allow_hit, env.str(R.string.widget_allow_for, m.title))
            v.setOnClickPendingIntent(R.id.cv_allow_hit, env.action(m, "allowOnce"))
        } else if (team) {
            val step = env.str(
                if (m.teamAction == "approvePlan") R.string.widget_v2_approve else R.string.widget_v2_resume)
            v.setTextViewText(R.id.cv_allow, step)
            v.setContentDescription(R.id.cv_allow_hit,
                NativeStrings.get(env.context, R.string.widget_step_for, step, m.title))
            v.setOnClickPendingIntent(R.id.cv_allow_hit, env.action(m, m.teamAction))
        }
        // Reply opens the conversation ready to type (composer focused, keyboard up), where the
        // whole ask and its context are; Open just opens it.
        val reply = m.session.optJSONObject("team") == null
        v.show(R.id.cv_reply_hit, reply)
        v.setTextViewText(R.id.cv_reply, env.str(R.string.widget_reply))
        if (reply) {
            v.setContentDescription(R.id.cv_reply_hit, env.str(R.string.widget_reply_for, m.title))
            v.setOnClickPendingIntent(R.id.cv_reply_hit, env.reply(m.session))
        }
        v.setTextViewText(R.id.cv_open, env.str(R.string.widget_v2_open))
        v.setContentDescription(R.id.cv_open_hit, env.str(R.string.widget_v2_open_chat))
        v.setOnClickPendingIntent(R.id.cv_open_hit, env.open(m.session))
    }

    /** Sending, an action that did not land, else Needs you in amber. */
    private fun needsWord(m: WidgetRowModel): Pair<String, Int> {
        val notice = text.notice()
        return when {
            env.connection == WidgetV2Policy.Connection.PENDING && m.request ->
                env.str(R.string.widget_v2_pending) to g.muted
            notice != null -> notice to g.muted
            else -> env.copy.needsYou() to g.amber
        }
    }

    /** "Fix the TV focus ring" over "● Working · IPTV_King · git add -A · 3 min". */
    private fun working(v: RemoteViews, m: WidgetRowModel): Int {
        title(v, m.title, 1)
        v.show(R.id.cv_ask, false)
        v.show(R.id.cv_buttons, false)
        val since = WidgetElapsedPolicy.sinceUnit(System.currentTimeMillis(), m.startedAt)
        val span = when {
            since == null -> ""
            since.first == "min" -> env.str(R.string.widget_v2_span_min, since.second)
            else -> env.str(R.string.widget_v2_span_hour, since.second)
        }
        val step = m.step.trim().removeSuffix("…").removeSuffix("...").trim()
        val dot = if (m.ticking) g.green else g.idle
        meta(v, dot, env.copy.working(), if (m.ticking) g.green else g.muted, m.project, step, span)
        return ConversationsWidgetPlan.titleHeight(1) + META_DP
    }

    /** The last conversation: its title over "project · 16 min ago"; offline says so instead. */
    private fun last(v: RemoteViews, m: WidgetRowModel): Int {
        title(v, m.title, 1)
        v.show(R.id.cv_ask, false)
        v.show(R.id.cv_buttons, false)
        if (m.lead == WidgetLayoutPolicy.Lead.OFFLINE) {
            meta(v, null, "", g.muted, env.copy.offline(env.lastSeen))
        } else {
            meta(v, null, "", g.muted, m.project, env.copy.ago(m.updatedAt))
        }
        return ConversationsWidgetPlan.titleHeight(1) + META_DP
    }

    private fun title(v: RemoteViews, words: String, lines: Int) {
        v.show(R.id.cv_title, true)
        v.setTextViewText(R.id.cv_title, words)
        v.setInt(R.id.cv_title, "setMaxLines", lines)
    }

    /** The state line: an optional dot, the status word in its colour and weight, then the rest. */
    private fun meta(v: RemoteViews, dot: Int?, word: String, color: Int, vararg rest: String) {
        v.show(R.id.cv_dot, dot != null)
        if (dot != null) env.dot(v, R.id.cv_dot, dot)
        val line = SpannableStringBuilder()
        if (word.isNotBlank()) {
            line.append(word)
            line.setSpan(ForegroundColorSpan(color), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            line.setSpan(StyleSpan(Typeface.BOLD), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val tail = env.copy.join(*rest)
        if (tail.isNotBlank()) {
            if (line.isNotEmpty()) line.append(" · ")
            line.append(tail)
        }
        v.show(R.id.cv_meta_row, line.isNotEmpty())
        v.setTextViewText(R.id.cv_meta, line)
    }

    /** Nothing to show yet: one plain sentence; the + still starts a conversation. */
    private fun empty(v: RemoteViews, project: String) {
        val never = env.snapshot.optString("profileID").isBlank()
        title(v, when {
            never -> env.str(R.string.widget_v2_empty)
            project.isNotBlank() -> env.str(R.string.widget_conversations_empty_in, project)
            else -> env.str(R.string.widget_conversations_empty)
        }, 2)
        v.setTextColor(R.id.cv_title, g.subtle)
        v.show(R.id.cv_meta_row, false)
        v.show(R.id.cv_ask, false)
        v.show(R.id.cv_buttons, false)
        v.show(R.id.cv_rows, false)
        v.show(R.id.cv_divider, false)
        v.setOnClickPendingIntent(R.id.cv_hero, env.open(null))
    }

    private companion object {
        const val SIDE_PADDING_DP = 32f
        const val CODE_PADDING_DP = 18f
        const val TITLE_SP = 18f
        const val CODE_SP = 13f
        const val MAX_COMMAND_LINES = 2
        const val META_DP = 20
        const val BUTTONS_DP = 51
    }
}

/** The next conversations under the top item, one line each, and the header's New conversation. */
internal class ConversationsWidgetRows(private val env: WidgetRowEnv) {
    private val g = env.g

    fun bind(v: RemoteViews, others: List<JSONObject>) {
        v.removeAllViews(R.id.cv_rows)
        v.show(R.id.cv_rows, others.isNotEmpty())
        v.show(R.id.cv_divider, others.isNotEmpty())
        for (session in others) v.addView(R.id.cv_rows, row(WidgetRowModel(env, session)))
    }

    /** "Say hi            settings-demo · 16 min"; a row that needs you or works says so first. */
    private fun row(m: WidgetRowModel): RemoteViews {
        val r = RemoteViews(env.context.packageName, R.layout.widget_conversations_row)
        r.setOnClickPendingIntent(R.id.cv_row, env.open(m.session))
        r.setTextViewText(R.id.cv_row_title, m.title)
        r.setTextColor(R.id.cv_row_title, g.text)
        val (word, color) = when (m.lead) {
            WidgetLayoutPolicy.Lead.NEEDS_YOU -> env.copy.needsYou() to g.amber
            WidgetLayoutPolicy.Lead.WORKING -> if (m.ticking) env.copy.working() to g.green else "" to g.muted
            else -> "" to g.muted
        }
        val age = if (word.isBlank()) ConversationsWidgetCopy.age(env, m.updatedAt) else ""
        val line = SpannableStringBuilder()
        if (word.isNotBlank()) {
            line.append(word)
            line.setSpan(ForegroundColorSpan(color), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val tail = env.copy.join(m.project, age)
        if (tail.isNotBlank()) {
            if (line.isNotEmpty()) line.append(" · ")
            line.append(tail)
        }
        r.setTextViewText(R.id.cv_row_meta, line)
        r.setTextColor(R.id.cv_row_meta, g.muted)
        return r
    }

    companion object {
        private const val NEW_TASK_ACTION = "new_task"

        /** The + opens a new, empty conversation in the app. */
        fun newConversation(env: WidgetRowEnv): PendingIntent = PendingIntent.getActivity(env.context, env.id,
            Intent(env.context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("opencode-widget://new-conversation/${env.id}")
                putExtra(MainActivity.EXTRA_LAUNCH_ACTION, NEW_TASK_ACTION)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** How the Conversations widget spends its height, in dp at font scale 1. */
internal object ConversationsWidgetPlan {
    /** Paddings (5 + 10), the header with its 48dp New conversation target (48) and the gap under it (2). */
    private const val FIXED_DP = 5 + 10 + 48 + 2
    /** The hairline and the air above it. */
    private const val DIVIDER_DP = 11
    const val ROW_DP = 48
    private const val MAX_ROWS = 4
    private const val TITLE_LINE_DP = 24
    private const val ASK_GAP_DP = 8
    private const val ASK_PADDING_DP = 10
    private const val ASK_LINE_DP = 17
    private const val MONO_GLYPH_EM = 0.6f

    fun titleHeight(lines: Int): Int = lines.coerceAtLeast(1) * TITLE_LINE_DP

    fun askHeight(lines: Int): Int = ASK_GAP_DP + ASK_PADDING_DP + lines.coerceAtLeast(1) * ASK_LINE_DP

    /** Whole lines a command takes in monospace at [textSp] across [widthDp]. */
    fun commandLines(command: String, widthDp: Float, textSp: Float): Int {
        if (command.isBlank()) return 0
        val perLine = (widthDp / (textSp * MONO_GLYPH_EM)).toInt().coerceAtLeast(1)
        return (command.length + perLine - 1) / perLine
    }

    /** Whole rows under the top item: none when they would be cut. */
    fun rows(effectiveHeightDp: Int, heroDp: Int, available: Int): Int {
        val left = effectiveHeightDp - FIXED_DP - heroDp - DIVIDER_DP
        if (available <= 0 || left < ROW_DP) return 0
        return minOf(available, MAX_ROWS, left / ROW_DP)
    }
}

/** Short ages for a row: "16 min", "1 h", "yesterday", "Mon", "3 Oct". */
internal object ConversationsWidgetCopy {
    fun age(env: WidgetRowEnv, thenMs: Long): String {
        if (thenMs <= 0) return ""
        val e = WidgetElapsedPolicy.elapsed(System.currentTimeMillis(), thenMs)
        return when (e.kind) {
            WidgetLayoutPolicy.Ago.NOW -> env.str(R.string.widget_v2_ago_now)
            WidgetLayoutPolicy.Ago.MINUTES -> env.str(R.string.widget_v2_span_min, e.count)
            WidgetLayoutPolicy.Ago.HOURS -> env.str(R.string.widget_v2_span_hour, e.count)
            WidgetLayoutPolicy.Ago.YESTERDAY -> env.str(R.string.widget_v2_ago_yesterday)
            else -> e.label
        }
    }
}
