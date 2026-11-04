package io.github.eslamasabry.oc.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import org.json.JSONObject

/**
 * Presentation preferences only. Endpoints, project routing and credentials never enter a panel.
 */
public data class WidgetPanelPreferences(
    val background: String = "solid",
    val accent: String = "wallpaper",
    val projects: List<String> = emptyList(),
    val project: String = "",
    val scope: String = "",
)

public object WidgetPanels {
    public val backgrounds: List<String> = listOf("solid", "tinted", "transparent")
    public val accents: List<String> = listOf("wallpaper", "app", "green", "teal", "blue", "amber")

    public fun owns(context: Context, widgetId: Int): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.packageName ==
            context.packageName

    public fun read(context: Context, widgetId: Int): WidgetPanelPreferences {
        val snapshot = WidgetV2Bridge.snapshot(context)
        val profile = snapshot?.optString("profileID").orEmpty()
        val storage = WidgetFeed.persistence
        fun saved(owner: String): JSONObject? =
            try {
                JSONObject(storage?.string("oc.widgetStyle.$owner") ?: "{}")
                    .optJSONObject(widgetId.toString())
            } catch (_: Exception) {
                null
            }
        val style = saved(profile) ?: saved(WidgetV2Bridge.DEVICE_STYLE_KEY) ?: JSONObject()
        val rows = snapshot?.optJSONArray("sessions")
        val projects =
            if (rows == null) emptyList()
            else
                (0 until rows.length())
                    .mapNotNull {
                        rows.optJSONObject(it)?.optString("project")?.takeIf(String::isNotBlank)
                    }
                    .distinct()
                    .sorted()
        return WidgetPanelPreferences(
            background = style.optString("background").takeIf { it in backgrounds } ?: "solid",
            accent = style.optString("accent").takeIf { it in accents } ?: "wallpaper",
            projects = projects,
            project = style.optString("project").takeIf { it in projects }.orEmpty(),
            scope = snapshot?.optString("scopeToken").orEmpty(),
        )
    }

    /**
     * Compare-and-set and the profile lane fence prevent deletion from resurrecting saved choices.
     */
    public suspend fun save(
        context: Context,
        widgetId: Int,
        preferences: WidgetPanelPreferences,
    ): Boolean {
        if (
            !owns(context, widgetId) ||
                preferences.background !in backgrounds ||
                preferences.accent !in accents
        )
            return false
        val current = read(context, widgetId)
        if (preferences.scope != current.scope) return false
        if (preferences.project.isNotEmpty() && preferences.project !in current.projects)
            return false
        val storage = WidgetFeed.persistence ?: return false
        val profile = WidgetV2Bridge.snapshot(context)?.optString("profileID").orEmpty()
        val owner = profile.ifBlank { WidgetV2Bridge.DEVICE_STYLE_KEY }
        val key = "oc.widgetStyle.$owner"
        val before = storage.string(key)
        val styles =
            try {
                JSONObject(before ?: "{}")
            } catch (_: Exception) {
                return false
            }
        styles.put(
            widgetId.toString(),
            JSONObject()
                .put("background", preferences.background)
                .put("accent", preferences.accent)
                .put("project", preferences.project),
        )
        val saved =
            if (profile.isBlank())
                (storage.store.compareAndSet(storage.lanes.app, key, before, styles.toString())
                        as? Outcome.Ok)
                    ?.value == true
            else storage.cas(profile, key, before, styles.toString())
        if (saved) {
            SessionsWidgetProvider.refreshAll(context)
            UsageWidgetProvider.refreshAll(context)
        }
        return saved
    }
}
