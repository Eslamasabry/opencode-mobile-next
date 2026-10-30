package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/** Cosmetic per-widget settings do not require a live conversation scope. */
internal class WidgetOverlayStyle(
    private val activity: Activity,
    launch: Map<String, Any?>,
    private val widgetId: Int,
) {
    // A widget may be placed before any profile exists.
    private val owner = (launch["profileID"] as? String)?.takeIf { it.isNotBlank() } ?: WidgetV2Bridge.DEVICE_STYLE_KEY
    private val key = "flutter.oc.widgetStyle.$owner"

    fun read(): Map<String, String> {
        val prefs = activity.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
        val saved = runCatching {
            JSONObject(prefs.getString(key, "{}") ?: "{}").optJSONObject(widgetId.toString())
        }.getOrNull()
        return mapOf(
            "background" to (saved?.optString("background", "solid") ?: "solid"),
            "accent" to (saved?.optString("accent", "wallpaper") ?: "wallpaper"),
        )
    }

    /** The project this widget lists ("" for all projects). */
    fun project(): String = saved()?.optString("project").orEmpty()

    /** The projects it can be scoped to, as the app last published them. */
    fun projects(): List<String> {
        val list = WidgetV2Bridge.snapshot(activity)?.optJSONArray("projects") ?: return emptyList()
        return (0 until list.length()).map { list.optString(it) }.filter { it.isNotBlank() }
    }

    /** Scopes the Conversations widget to one project, or to all with "". */
    fun saveProject(arguments: Any?, result: MethodChannel.Result) {
        val project = (arguments as? Map<*, *>)?.get("project") as? String
        if (project == null || (project.isNotEmpty() && project !in projects())) {
            result.error("invalidProject", null, null)
            return
        }
        val prefs = activity.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
        val styles = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val entry = styles.optJSONObject(widgetId.toString()) ?: JSONObject()
        styles.put(widgetId.toString(), entry.put("project", project))
        finishSaved(prefs.edit().putString(key, styles.toString()).commit(), result)
    }

    private fun saved(): JSONObject? = runCatching {
        JSONObject(activity.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
            .getString(key, "{}") ?: "{}").optJSONObject(widgetId.toString())
    }.getOrNull()

    private fun finishSaved(saved: Boolean, result: MethodChannel.Result) {
        if (!saved) {
            result.error("unavailable", null, null)
            return
        }
        SessionsWidgetProvider.refreshAll(activity)
        UsageWidgetProvider.refreshAll(activity)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        result.success(mapOf("saved" to true))
        activity.finish()
    }

    fun save(arguments: Any?, result: MethodChannel.Result) {
        val args = arguments as? Map<*, *>
        val background = args?.get("background") as? String
        val accent = args?.get("accent") as? String
        if (background !in setOf("solid", "tinted", "transparent") ||
            accent !in setOf("wallpaper", "app", "green", "amber", "blue", "teal")
        ) {
            result.error("invalidStyle", null, null)
        } else {
            val prefs = activity.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
            val styles = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }
            val entry = styles.optJSONObject(widgetId.toString()) ?: JSONObject()
            styles.put(widgetId.toString(), entry.put("background", background).put("accent", accent))
            finishSaved(prefs.edit().putString(key, styles.toString()).commit(), result)
        }
    }
}
