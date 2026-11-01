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
import org.json.JSONObject

/** Event-driven, privacy-safe snapshot renderer. Never opens a network connection. */
open class SessionsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context, it, manager.getAppWidgetOptions(it), javaClass)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, buildViews(context, id, options, javaClass))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetV2Bridge.deleteWidgets(context, ids)
    }

    companion object {
        // Fallback cell sizes when the launcher reports none: 2x1, 4x1, 2x2, 4x2, 4x4.
        private const val NARROW = 110
        private const val WIDE = 250
        private const val ONE_ROW = 70
        private const val TWO_ROWS = 180
        private const val FOUR_ROWS = 360

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            // Every entry (Conversations and the retired 2x2, list and strip) is its own receiver:
            // redraw all of them, and the Status widget, which counts the same running turns.
            WidgetProviders.sessions.forEach { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
                    manager.updateAppWidget(it, buildViews(context, it, manager.getAppWidgetOptions(it), provider))
                }
            }
            StatusWidgetProvider.refreshAll(context)
        }

        private fun readSnapshot(context: Context): JSONObject = try {
            JSONObject(context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.oc.widgetSessions", "{}") ?: "{}")
        } catch (_: Exception) { JSONObject() }

        private fun buildViews(context: Context, id: Int, options: Bundle, provider: Class<*>): RemoteViews {
            val renderer = SessionWidgetRenderer(context, id, readSnapshot(context))
            if (provider == SessionsWideWidgetProvider::class.java) {
                // The Conversations widget: one render for the size it has now, else its 4x2.
                val size = currentSize(context, options) ?: (WIDE to TWO_ROWS)
                return renderer.renderConversations(size.first, size.second)
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                buildResponsive(context, renderer, options)
            } else {
                renderer.render(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, NARROW),
                    WidgetSizePolicy.legacyHeight(
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, TWO_ROWS)
                    )
                )
            }
        }

        /** The size the widget has now (see [WidgetSizePolicy.currentSize]), or null. */
        internal fun currentSize(context: Context, options: Bundle): Pair<Int, Int>? {
            val launcher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().map { it.width to it.height }
            } else emptyList()
            return WidgetSizePolicy.currentSize(
                WidgetSizePolicy.launcherSizes(launcher),
                context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE,
                WidgetSizePolicy.Bounds(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                )
            )
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun buildResponsive(context: Context, renderer: SessionWidgetRenderer, options: Bundle): RemoteViews {
            val now = currentSize(context, options)
            // One render for the size it has now: a size map is resolved by the host, and
            // some hosts pick the landscape entry (a strip inside a 4x2 box). The launcher
            // sends new options on every resize, which renders again.
            if (now != null) return renderer.render(now.first, now.second)
            fun variant(width: Int, height: Int) =
                SizeF(width.toFloat(), height.toFloat()) to renderer.render(width, height)
            return RemoteViews(
                linkedMapOf(
                    variant(NARROW, ONE_ROW),
                    variant(WIDE, ONE_ROW),
                    variant(NARROW, TWO_ROWS),
                    variant(WIDE, TWO_ROWS),
                    variant(WIDE, FOUR_ROWS)
                )
            )
        }
    }
}
