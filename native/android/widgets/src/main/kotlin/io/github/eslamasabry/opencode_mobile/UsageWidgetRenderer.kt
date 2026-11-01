package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import io.github.eslamasabry.oc.android.widgets.R
import io.github.eslamasabry.oc.android.widgets.MainActivity
import io.github.eslamasabry.oc.android.widgets.NativeStrings

internal class UsageWidgetRenderer(private val context: Context, private val widgetId: Int) {
    private val views = RemoteViews(context.packageName, R.layout.widget_usage)
    private val snapshot = try {
        JSONObject(context.getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)
            .getString("flutter.oc.widgetSessions", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }
    private val palette = WidgetV2Style.palette(context, widgetId, snapshot)
    private val open by lazy { launchIntent(OPEN_REQUEST_CODE, UsageWidgetProvider.LAUNCH_ACTION_USAGE) }

    fun buildViews(widthDp: Int, heightDp: Int): RemoteViews {
        bindHeader()
        val now = System.currentTimeMillis()
        val plans = UsageWidgetPlans.groupPlans(UsageWidgetRows.readRows(context))
        val shown = UsageWidgetFit.pick(
            plans.map { UsageWidgetPlans.planFit(it, now) },
            heightDp,
            rowIDs.size
        )
        val multiProfile = plans.flatten().map { it.profileName }.distinct().size > 1
        views.setViewVisibility(R.id.usage_empty, if (plans.isEmpty()) View.VISIBLE else View.GONE)
        for (index in rowIDs.indices) {
            val pick = shown.getOrNull(index)
            if (pick == null) {
                views.setViewVisibility(rowIDs[index], View.GONE)
            } else {
                val plan = plans[pick.index]
                bindRow(index, plan, widthDp)
                val detail = if (pick.showDetail) UsageWidgetText.detailText(context, plan, now, multiProfile) else ""
                views.setTextViewText(detailIDs[index], detail)
                views.setViewVisibility(detailIDs[index], if (detail.isEmpty()) View.GONE else View.VISIBLE)
            }
        }
        val lone = shown.size == 1 && heightDp >= SPACIOUS_HEIGHT_DP &&
            !plans[shown[0].index].first().signInOnly
        sizeFirstRow(lone)
        return views
    }

    /** One plan in a tall widget gets larger type; the rows block is centred in the remaining
     * height by the layout instead of a card that is half empty. Sizes are set on every render
     * so the large type never outlives the lone plan. */
    private fun sizeFirstRow(lone: Boolean) {
        val (title, column, detail) = WidgetUsageStyle.usageTextSizes(lone)
        val sp = android.util.TypedValue.COMPLEX_UNIT_SP
        views.setTextViewTextSize(titleIDs[0], sp, title)
        views.setTextViewTextSize(c1TextIDs[0], sp, column)
        views.setTextViewTextSize(c2TextIDs[0], sp, column)
        views.setTextViewTextSize(detailIDs[0], sp, detail)
    }

    private fun bindHeader() {
        WidgetV2Style.apply(context, views, widgetId, snapshot)
        views.setTextColor(R.id.usage_heading, palette.text)
        views.setTextColor(R.id.usage_empty, palette.muted)
        views.setTextViewText(R.id.usage_heading, NativeStrings.get(context, R.string.usage_widget_title))
        views.setTextViewText(R.id.usage_empty, NativeStrings.get(context, R.string.usage_widget_empty))
        views.setOnClickPendingIntent(R.id.usage_root, open)
        views.setOnClickPendingIntent(R.id.usage_header, open)
        // No measured limits yet: open Usage to read or connect a source.
        views.setOnClickPendingIntent(
            R.id.usage_empty,
            open
        )
    }

    private fun bindRow(index: Int, plan: List<UsageWidgetRow>, widthDp: Int) {
        val first = plan.first()
        views.setViewVisibility(rowIDs[index], View.VISIBLE)
        views.setOnClickPendingIntent(
            rowIDs[index],
            if (first.fixAction.isEmpty()) open else launchIntent(
                FIX_REQUEST_CODE + index,
                "usage_target:${first.fixAction}:${first.provider}:${first.profileId}"
            )
        )
        views.setTextViewText(titleIDs[index], UsageWidgetText.providerName(context, first.provider, first.displayName))
        views.setTextColor(titleIDs[index], palette.text)
        views.setTextColor(leftIDs[index], palette.attention)
        views.setTextColor(detailIDs[index], palette.muted)
        val setupOnly = first.signInOnly
        views.setTextViewText(leftIDs[index], if (setupOnly) setupLabel(first) else "")
        views.setViewVisibility(colsIDs[index], if (setupOnly) View.GONE else View.VISIBLE)
        if (!setupOnly) bindColumns(index, plan, widthDp)
    }

    private fun setupLabel(row: UsageWidgetRow): String = NativeStrings.get(
        context,
        when (row.fixAction) {
            "add_key" -> R.string.usage_widget_add_key
            "set_up" -> R.string.usage_widget_set_up
            else -> R.string.usage_widget_sign_in
        }
    )

    private fun bindColumns(index: Int, plan: List<UsageWidgetRow>, widthDp: Int) {
        val columns = UsageWidgetPlans.columnRows(plan, widthDp)
        bindColumn(columns[0], c1TextIDs[index], c1BarIDs[index])
        val second = columns.getOrNull(1)
        views.setViewVisibility(c2IDs[index], if (second == null) View.GONE else View.VISIBLE)
        if (second != null) bindColumn(second, c2TextIDs[index], c2BarIDs[index])
    }

    private fun bindColumn(row: UsageWidgetRow, textId: Int, barId: Int) {
        views.setTextViewText(
            textId,
            NativeStrings.get(
                context,
                R.string.usage_widget_column,
                UsageWidgetText.shortWindowName(context, row.seconds, row.label),
                row.left
            )
        )
        views.setProgressBar(barId, FULL_PERCENT, row.left, false)
        val stale = row.forceStale || row.authNeeded
        val (fill, track) = WidgetUsageStyle.usageBar(palette.text, palette.muted, row.left, stale)
        views.setTextColor(textId, if (stale) palette.muted else palette.text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(barId, "setProgressTintList", ColorStateList.valueOf(fill))
            views.setColorStateList(barId, "setProgressBackgroundTintList", ColorStateList.valueOf(track))
        }
    }

    private fun launchIntent(requestCode: Int, launchAction: String): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent().setClassName(context, MainActivity.COMPONENT).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_LAUNCH_ACTION, launchAction)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val FLUTTER_PREFS = "FlutterSharedPreferences"
        private const val OPEN_REQUEST_CODE = 200
        private const val FIX_REQUEST_CODE = 300
        private const val FULL_PERCENT = 100
        private const val SPACIOUS_HEIGHT_DP = 150

        private val rowIDs = intArrayOf(
            R.id.usage_row_0, R.id.usage_row_1, R.id.usage_row_2,
            R.id.usage_row_3, R.id.usage_row_4, R.id.usage_row_5
        )
        private val titleIDs = intArrayOf(
            R.id.usage_title_0, R.id.usage_title_1, R.id.usage_title_2,
            R.id.usage_title_3, R.id.usage_title_4, R.id.usage_title_5
        )
        private val leftIDs = intArrayOf(
            R.id.usage_left_0, R.id.usage_left_1, R.id.usage_left_2,
            R.id.usage_left_3, R.id.usage_left_4, R.id.usage_left_5
        )
        private val colsIDs = intArrayOf(
            R.id.usage_cols_0, R.id.usage_cols_1, R.id.usage_cols_2,
            R.id.usage_cols_3, R.id.usage_cols_4, R.id.usage_cols_5
        )
        private val c1TextIDs = intArrayOf(
            R.id.usage_c1_text_0, R.id.usage_c1_text_1, R.id.usage_c1_text_2,
            R.id.usage_c1_text_3, R.id.usage_c1_text_4, R.id.usage_c1_text_5
        )
        private val c1BarIDs = intArrayOf(
            R.id.usage_c1_bar_0, R.id.usage_c1_bar_1, R.id.usage_c1_bar_2,
            R.id.usage_c1_bar_3, R.id.usage_c1_bar_4, R.id.usage_c1_bar_5
        )
        private val c2IDs = intArrayOf(
            R.id.usage_c2_0, R.id.usage_c2_1, R.id.usage_c2_2,
            R.id.usage_c2_3, R.id.usage_c2_4, R.id.usage_c2_5
        )
        private val c2TextIDs = intArrayOf(
            R.id.usage_c2_text_0, R.id.usage_c2_text_1, R.id.usage_c2_text_2,
            R.id.usage_c2_text_3, R.id.usage_c2_text_4, R.id.usage_c2_text_5
        )
        private val c2BarIDs = intArrayOf(
            R.id.usage_c2_bar_0, R.id.usage_c2_bar_1, R.id.usage_c2_bar_2,
            R.id.usage_c2_bar_3, R.id.usage_c2_bar_4, R.id.usage_c2_bar_5
        )
        private val detailIDs = intArrayOf(
            R.id.usage_detail_0, R.id.usage_detail_1, R.id.usage_detail_2,
            R.id.usage_detail_3, R.id.usage_detail_4, R.id.usage_detail_5
        )

    }
}
