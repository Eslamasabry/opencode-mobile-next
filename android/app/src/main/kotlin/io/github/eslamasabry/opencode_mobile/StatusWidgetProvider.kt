package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import java.io.File
import org.json.JSONObject

/**
 * Home-screen "Status" widget (2x2): each server online or offline, this phone's Linux, the
 * running tasks and the plan limit closest to running out. It reads only what the app already
 * stores for the other widgets (the conversations snapshot and the Usage rows) and this
 * process's own Linux state, and never opens a network connection. A tap on a line opens that
 * thing's screen in the app.
 */
open class StatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context, it, manager.getAppWidgetOptions(it))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, buildViews(context, id, options))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetV2Bridge.deleteWidgets(context, ids)
    }

    companion object {
        private const val SQUARE_DP = 170
        private const val HEIGHT_DP = 180

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            WidgetProviders.status.forEach { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
                    manager.updateAppWidget(it, buildViews(context, it, manager.getAppWidgetOptions(it)))
                }
            }
        }

        private fun buildViews(context: Context, id: Int, options: Bundle): RemoteViews {
            val size = SessionsWidgetProvider.currentSize(context, options) ?: (SQUARE_DP to HEIGHT_DP)
            val snapshot = WidgetV2Bridge.snapshot(context) ?: JSONObject()
            return StatusWidgetRenderer(context, id, snapshot, linuxState(context),
                UsageWidgetRows.readRows(context)).render(size.second)
        }

        /** Installed, and running in this process. Never starts or creates the Linux runtime. */
        private fun linuxState(context: Context): StatusWidgetRenderer.Linux {
            val installed = File(File(context.filesDir, "linux"), "ubuntu.ready").isFile
            return StatusWidgetRenderer.Linux(installed, installed && (BuiltinLinux.serverRunningIfStarted() ||
                BuiltinServerService.isForegroundRunning))
        }
    }
}

/** Draws the Status widget for one size. Every input is a parameter, so it renders off-device. */
internal class StatusWidgetRenderer(
    private val context: Context,
    private val id: Int,
    private val snapshot: JSONObject,
    private val linux: Linux,
    private val usage: List<UsageWidgetRow>,
    private val now: Long = System.currentTimeMillis(),
) {
    private val g = WidgetGraphite.of(context, snapshot)
    private val copy = WidgetCopy(context, now)

    /** This phone's Linux: set up at all, and running now. */
    data class Linux(val installed: Boolean, val running: Boolean)

    /** Draws for [heightDp]; the width only changes where the words end. */
    fun render(heightDp: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_status)
        g.paintGround(v, WidgetV2Style.backgroundMode(context, id, snapshot))
        v.setInt(R.id.st_tile, "setBackgroundResource", g.tileBg)
        v.setInt(R.id.st_mark, "setColorFilter", g.text)
        v.setTextColor(R.id.st_label, g.subtle)
        v.setTextViewText(R.id.st_label, s(R.string.widget_status_title))
        // Not watching now: the lines are as the app last saw them, this long ago.
        val watched = StatusWidgetModel.watchedAt(snapshot)
        v.show(R.id.st_age, watched != null)
        v.setTextViewText(R.id.st_age, watched?.let { copy.ago(it) }.orEmpty())
        v.setTextColor(R.id.st_age, g.muted)
        v.setOnClickPendingIntent(android.R.id.background, launch(OPEN_APP, ""))
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val height = (heightDp / fontScale).toInt()
        val lines = StatusWidgetModel.lines(snapshot, linux.installed, linux.running)
        val plan = StatusWidgetModel.plan(usage, now)
        val count = StatusWidgetModel.lineCount(height, plan != null, lines.size)
        // A few lines sit in the middle of the cell; once they fill it they start at the top.
        v.show(R.id.st_gap_top, count < StatusWidgetModel.lineCount(height, plan != null, Int.MAX_VALUE))
        v.removeAllViews(R.id.st_lines)
        lines.take(count).forEach { v.addView(R.id.st_lines, line(it)) }
        val empty = lines.isEmpty() && plan == null
        v.show(R.id.st_empty, empty)
        if (empty) {
            v.setTextViewText(R.id.st_empty, s(R.string.widget_v2_empty))
            v.setTextColor(R.id.st_empty, g.subtle)
        }
        bindPlan(v, plan)
        return v
    }

    private fun line(line: StatusWidgetModel.Line): RemoteViews {
        val r = RemoteViews(context.packageName, R.layout.widget_status_line)
        val tone = color(line.tone)
        r.setInt(R.id.st_line_dot, "setColorFilter", if (line.tone == StatusWidgetModel.Tone.IDLE) g.idle else tone)
        val problem = line.tone == StatusWidgetModel.Tone.BAD || line.tone == StatusWidgetModel.Tone.WARN
        r.setTextViewText(R.id.st_line_name, name(line))
        r.setTextColor(R.id.st_line_name, if (problem) tone else g.text)
        r.setTextViewText(R.id.st_line_value, value(line))
        r.setTextColor(R.id.st_line_value, if (problem) tone else g.muted)
        r.setOnClickPendingIntent(R.id.st_line, launch(
            when (line.kind) {
                StatusWidgetModel.Kind.SERVER -> "connect"
                StatusWidgetModel.Kind.LINUX -> "linux"
                StatusWidgetModel.Kind.TASKS -> "activity"
            }, line.kind.name))
        return r
    }

    private fun name(line: StatusWidgetModel.Line): String = when (line.kind) {
        StatusWidgetModel.Kind.SERVER -> line.name.ifBlank { s(R.string.widget_status_server) }
        StatusWidgetModel.Kind.LINUX -> s(R.string.widget_status_linux)
        StatusWidgetModel.Kind.TASKS -> s(R.string.widget_status_tasks)
    }

    private fun value(line: StatusWidgetModel.Line): String = when (line.state) {
        "online" -> s(R.string.widget_status_online)
        "connecting" -> s(R.string.widget_status_connecting)
        "offline" -> span(line.since)?.let { s(R.string.widget_status_offline_for, it) }
            ?: s(R.string.widget_status_offline)
        "running" -> if (line.kind == StatusWidgetModel.Kind.TASKS) {
            s(R.string.widget_status_tasks_running, line.count)
        } else s(R.string.widget_status_running)
        "stopped" -> s(R.string.widget_status_stopped)
        else -> s(R.string.widget_status_tasks_none)
    }

    /** "12 min", "3 h", "2 d" since [sinceMs]; null under a minute or unknown. */
    private fun span(sinceMs: Long): String? {
        if (sinceMs <= 0) return null
        val minutes = (now - sinceMs).coerceAtLeast(0) / MINUTE_MS
        return when {
            minutes < 1 -> null
            minutes < MINUTES_PER_HOUR -> s(R.string.widget_v2_span_min, minutes.toInt())
            minutes < MINUTES_PER_DAY -> s(R.string.widget_v2_span_hour, (minutes / MINUTES_PER_HOUR).toInt())
            else -> s(R.string.widget_status_span_day, (minutes / MINUTES_PER_DAY).toInt())
        }
    }

    private fun bindPlan(v: RemoteViews, plan: StatusWidgetModel.Plan?) {
        v.show(R.id.st_plan, plan != null)
        if (plan == null) return
        val problem = plan.tone == StatusWidgetModel.Tone.BAD || plan.tone == StatusWidgetModel.Tone.WARN
        val tone = color(plan.tone)
        v.setTextViewText(R.id.st_plan_name, s(R.string.widget_status_plan,
            UsageWidgetText.providerName(context, plan.provider, plan.displayName),
            window(plan)))
        v.setTextColor(R.id.st_plan_name, if (problem) tone else g.text)
        v.setTextViewText(R.id.st_plan_left, s(R.string.widget_status_left, plan.left))
        v.setTextColor(R.id.st_plan_left, if (problem) tone else g.muted)
        v.setProgressBar(R.id.st_plan_bar, FULL, plan.left.coerceIn(0, FULL), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val fill = if (plan.tone == StatusWidgetModel.Tone.IDLE) g.muted else tone
            v.setColorStateList(R.id.st_plan_bar, "setProgressTintList", ColorStateList.valueOf(fill))
            v.setColorStateList(R.id.st_plan_bar, "setProgressBackgroundTintList",
                ColorStateList.valueOf(g.divider))
        }
        v.setOnClickPendingIntent(R.id.st_plan, launch("usage", "plan"))
    }

    /** "5 h" for an hours window, else the Usage widget's short name ("Week"). */
    private fun window(plan: StatusWidgetModel.Plan): String {
        val hours = plan.seconds / SECONDS_PER_HOUR
        return if (plan.seconds > 0 && plan.seconds % SECONDS_PER_HOUR == 0L && hours in 1 until HOURS_WINDOW_MAX) {
            s(R.string.widget_v2_span_hour, hours.toInt())
        } else UsageWidgetText.shortWindowName(context, plan.seconds, plan.label)
    }

    private fun color(tone: StatusWidgetModel.Tone): Int = when (tone) {
        StatusWidgetModel.Tone.OK -> g.green
        StatusWidgetModel.Tone.WARN -> g.amber
        StatusWidgetModel.Tone.BAD -> g.bad
        StatusWidgetModel.Tone.IDLE -> g.muted
    }

    private fun s(resId: Int, vararg args: Any) = NativeStrings.get(context, resId, *args)

    /** Opens the app at [action] (a known launch action), or just the app. Each line has its own
     * data so the intents stay apart. */
    private fun launch(action: String, tag: String): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("opencode-widget://status/$id/${tag.lowercase()}")
            if (action != OPEN_APP) putExtra(MainActivity.EXTRA_LAUNCH_ACTION, action)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val OPEN_APP = ""
        const val FULL = 100
        const val MINUTE_MS = 60_000L
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 1_440L
        const val SECONDS_PER_HOUR = 3_600L
        const val HOURS_WINDOW_MAX = 48L
    }
}
