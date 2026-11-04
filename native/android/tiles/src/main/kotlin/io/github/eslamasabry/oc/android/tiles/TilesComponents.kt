// Native-authored seam (not a twin). The launcher activity is a :androidApp shell, so intents here
// name it by its
// frozen FQCN with setClassName instead of a class literal. The shared preferences file is this
// module's own cache.
package io.github.eslamasabry.oc.android.tiles

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.service.quicksettings.TileService

/** Frozen component names and the one preferences file this module owns. */
object TilesComponents {
    /** The launcher activity (`:androidApp`), unchanged from the Flutter app. */
    const val MAIN_ACTIVITY: String = "io.github.eslamasabry.opencode_mobile.MainActivity"

    const val ATTENTION_TILE: String = "io.github.eslamasabry.opencode_mobile.AttentionTileService"
    const val PAUSE_TILE: String = "io.github.eslamasabry.opencode_mobile.PauseTileService"

    /**
     * Tile caches and the pinned-shortcut records. Not the Flutter "FlutterSharedPreferences"
     * store.
     */
    const val PREFS: String = "oc_tiles"

    internal fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Redraws only this app's installed home widgets after their shared activity count changes. */
    internal fun requestWidgetRedraw(context: Context) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            for (provider in manager.installedProviders) {
                if (provider.provider.packageName != context.packageName) continue
                val ids = manager.getAppWidgetIds(provider.provider)
                if (ids.isEmpty()) continue
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(provider.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        } catch (_: Exception) {
            // Missing launcher support leaves the durable count available for its next normal
            // redraw.
        }
    }

    /**
     * Asks the system to re-bind [component] so it redraws. Never throws: a missing or disabled
     * tile is fine.
     */
    internal fun requestRedraw(context: Context, component: String) {
        try {
            TileService.requestListeningState(context, ComponentName(context, component))
        } catch (_: Exception) {
            // The tile is not added, the component is disabled, or the platform refused: nothing to
            // redraw.
        }
    }
}
