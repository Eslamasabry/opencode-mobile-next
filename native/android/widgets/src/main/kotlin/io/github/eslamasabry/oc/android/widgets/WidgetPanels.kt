package io.github.eslamasabry.oc.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.AppLog
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
    /** The server (profile id) the setup was opened for; blank before the app ever published a snapshot. */
    val profile: String = "",
)

/** Why a panel save did not happen. Logged by name; the person gets [Words] from the window. */
public enum class WidgetPanelSave {
    Saved,
    NotOwned,
    Invalid,
    /** The setup was opened for another server than the one the widget now follows. */
    SetupChanged,
    /** The chosen project is no longer one the widget lists. */
    ProjectGone,
    NoStorage,
    Unreadable,
    NotWritten,
}

public object WidgetPanels {
    private val log = AppLog.tagged("WidgetPanel")

    /**
     * A setup opened for a server stays valid while the widget follows that server. A reconnect changes the
     * snapshot's scope token, never the server, so it is no reason to refuse the save.
     */
    internal fun refusal(opened: WidgetPanelPreferences, current: WidgetPanelPreferences): WidgetPanelSave? =
        when {
            opened.profile.isNotEmpty() && opened.profile != current.profile -> WidgetPanelSave.SetupChanged
            opened.project.isNotEmpty() && opened.project !in current.projects -> WidgetPanelSave.ProjectGone
            else -> null
        }

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
            profile = profile,
        )
    }

    /**
     * Compare-and-set and the profile lane fence prevent deletion from resurrecting saved choices.
     */
    public suspend fun save(
        context: Context,
        widgetId: Int,
        preferences: WidgetPanelPreferences,
    ): WidgetPanelSave {
        val result = saveChecked(context, widgetId, preferences)
        // Ids and the reason only: never a project name, a title or a profile name.
        if (result != WidgetPanelSave.Saved) log.w { "panel save refused: $result (widget $widgetId)" }
        return result
    }

    private suspend fun saveChecked(
        context: Context,
        widgetId: Int,
        preferences: WidgetPanelPreferences,
    ): WidgetPanelSave {
        if (!owns(context, widgetId)) return WidgetPanelSave.NotOwned
        if (preferences.background !in backgrounds || preferences.accent !in accents)
            return WidgetPanelSave.Invalid
        refusal(preferences, read(context, widgetId))?.let { return it }
        val storage = WidgetFeed.persistence ?: return WidgetPanelSave.NoStorage
        val profile = WidgetV2Bridge.snapshot(context)?.optString("profileID").orEmpty()
        val owner = profile.ifBlank { WidgetV2Bridge.DEVICE_STYLE_KEY }
        val key = "oc.widgetStyle.$owner"
        val before = storage.string(key)
        val styles =
            try {
                JSONObject(before ?: "{}")
            } catch (_: Exception) {
                return WidgetPanelSave.Unreadable
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
        if (!saved) return WidgetPanelSave.NotWritten
        SessionsWidgetProvider.refreshAll(context)
        UsageWidgetProvider.refreshAll(context)
        return WidgetPanelSave.Saved
    }
}
