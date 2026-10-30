package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Home-screen "Usage" widget: what is left on each provider limit window and
 * when it resets. It renders only the display numbers the Flutter quota
 * monitor persists under `flutter.oc.widgetUsage.<profileId>` and never makes
 * a network call. The monitor runs while the app process lives, so numbers
 * older than [UsageWidgetPlans.STALE_AFTER_MS] (or past a reset) are labelled "Updated 2h ago"
 * instead of being shown as current. A tap opens Usage -> Remaining.
 */
open class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                id,
                responsiveViews(context, id, appWidgetManager.getAppWidgetOptions(id))
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, responsiveViews(context, appWidgetId, newOptions))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetV2Bridge.deleteWidgets(context, ids)
    }

    companion object {
        const val LAUNCH_ACTION_USAGE = "usage"
        const val LAUNCH_ACTION_USAGE_SETTINGS = "usage_settings"
        private const val COMPACT_HEIGHT_DP = 110
        private const val SHORT_HEIGHT_DP = 180
        private const val MEDIUM_HEIGHT_DP = 280
        private const val TALL_HEIGHT_DP = 400

        /** Redraws every placed widget; called after a snapshot write. */
        fun refreshAll(context: Context) {
            // The Status widget shows the plan limit closest to running out from the same rows.
            StatusWidgetProvider.refreshAll(context)
            val manager = AppWidgetManager.getInstance(context)
            // Every picker entry (2x2 and wide) is its own receiver: redraw all of them.
            val ids = WidgetProviders.usage.flatMap {
                manager.getAppWidgetIds(ComponentName(context, it)).toList()
            }
            if (ids.isEmpty()) return
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    responsiveViews(context, id, manager.getAppWidgetOptions(id))
                )
            }
        }

        /** The widget's current height in dp: the portrait height in portrait, else the landscape one. */
        private fun heightDp(context: Context, options: Bundle?): Int {
            if (options == null) return 0
            val portrait = context.resources.configuration.orientation !=
                Configuration.ORIENTATION_LANDSCAPE
            val key = if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            return options.getInt(key, 0)
        }

        private fun responsiveViews(context: Context, widgetId: Int, options: Bundle): RemoteViews =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sizedViews(context, widgetId, options)
            } else {
                legacyViews(context, widgetId, options)
            }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun sizedViews(context: Context, widgetId: Int, options: Bundle): RemoteViews {
            @Suppress("DEPRECATION")
            val launcherSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val now = WidgetSizePolicy.currentSize(
                WidgetSizePolicy.launcherSizes(launcherSizes.orEmpty().map { it.width to it.height }),
                context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE,
                WidgetSizePolicy.Bounds(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)))
            // One render for the size it has now (see SessionsWidgetProvider.buildViews).
            if (now != null) return buildViews(context, widgetId, now.first, now.second)
            val variants = linkedMapOf<SizeF, RemoteViews>()
            for (width in listOf(UsageWidgetFit.MIN_WIDTH_DP, UsageWidgetFit.TWO_COLUMNS_WIDTH_DP)) {
                for (height in listOf(COMPACT_HEIGHT_DP, SHORT_HEIGHT_DP, MEDIUM_HEIGHT_DP, TALL_HEIGHT_DP)) {
                    variants[SizeF(width.toFloat(), height.toFloat())] =
                        buildViews(context, widgetId, width, height)
                }
            }
            return RemoteViews(variants)
        }

        private fun legacyViews(context: Context, widgetId: Int, options: Bundle): RemoteViews {
            val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val widthKey = if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            return buildViews(
                context, widgetId, options.getInt(widthKey, UsageWidgetFit.MIN_WIDTH_DP),
                heightDp(context, options)
            )
        }

        private fun buildViews(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews =
            UsageWidgetRenderer(context, widgetId).buildViews(widthDp, heightDp)
    }
}
