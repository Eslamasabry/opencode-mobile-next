package io.github.eslamasabry.oc.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.label
import io.github.eslamasabry.oc.core.common.ids.ProfileId
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

/** Why a panel save did not happen. Logged by name; the person gets [Words] from the window. */
public enum class WidgetPanelSave {
    Saved,
    NotOwned,
    Invalid,
    /** The snapshot's scope token is not the one the setup was opened with. */
    ScopeChanged,
    /** The chosen project is no longer one the widget lists. */
    ProjectGone,
    NoStorage,
    Unreadable,
    /** The store refused the write (a closed lane, a clash). */
    WriteRefused,
    /** Another writer changed the stored styles between the read and the write. */
    ChangedUnderneath,
}

public object WidgetPanels {
    private val log = AppLog.tagged("WidgetPanel")
    public val backgrounds: List<String> = listOf("solid", "tinted", "transparent")
    public val accents: List<String> = listOf("wallpaper", "app", "green", "teal", "blue", "amber")

    /** Why a save must not go ahead, from what the setup was opened with and what is stored now. */
    internal fun refusal(opened: WidgetPanelPreferences, current: WidgetPanelPreferences): WidgetPanelSave? =
        when {
            opened.scope != current.scope -> WidgetPanelSave.ScopeChanged
            opened.project.isNotEmpty() && opened.project !in current.projects -> WidgetPanelSave.ProjectGone
            else -> null
        }

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
    ): WidgetPanelSave {
        val result = saveChecked(context, widgetId, preferences)
        // Ids and the reason only: never a project name, a title or a profile name.
        if (result != WidgetPanelSave.Saved) log.w { "panel save refused: $result (widget $widgetId)" }
        return result
    }

    private fun beforeWrite(context: Context, widgetId: Int, preferences: WidgetPanelPreferences): WidgetPanelSave? {
        val refused =
            when {
                !owns(context, widgetId) -> WidgetPanelSave.NotOwned
                preferences.background !in backgrounds || preferences.accent !in accents -> WidgetPanelSave.Invalid
                else -> null
            }
        if (refused != null) return refused
        val current = read(context, widgetId)
        val result = refusal(preferences, current)
        // Which comparison refused, as yes/no facts only: never a token, a project name or a profile id.
        if (result != null) {
            log.w {
                "panel check: $result scopeEqual=${preferences.scope == current.scope} " +
                    "openedScopeBlank=${preferences.scope.isEmpty()} currentScopeBlank=${current.scope.isEmpty()} " +
                    "projectListed=${preferences.project.isEmpty() || preferences.project in current.projects}"
            }
        }
        return result
    }

    private suspend fun saveChecked(
        context: Context,
        widgetId: Int,
        preferences: WidgetPanelPreferences,
    ): WidgetPanelSave {
        val refused = beforeWrite(context, widgetId, preferences)
        val storage = WidgetFeed.persistence
        val result =
            when {
                refused != null -> refused
                storage == null -> WidgetPanelSave.NoStorage
                else -> store(context, storage, widgetId, preferences)
            }
        if (result == WidgetPanelSave.Saved) {
            SessionsWidgetProvider.refreshAll(context)
            UsageWidgetProvider.refreshAll(context)
        }
        return result
    }

    private suspend fun store(
        context: Context,
        storage: WidgetPersistence,
        widgetId: Int,
        preferences: WidgetPanelPreferences,
    ): WidgetPanelSave {
        val profile = WidgetV2Bridge.snapshot(context)?.optString("profileID").orEmpty()
        val key = "oc.widgetStyle.${profile.ifBlank { WidgetV2Bridge.DEVICE_STYLE_KEY }}"
        val before = storage.string(key)
        val styles = parseStyles(before) ?: return WidgetPanelSave.Unreadable
        val next = styles.put(widgetId.toString(), style(preferences)).toString()
        return write(storage, profile, key, before, next)
    }

    private fun parseStyles(raw: String?): JSONObject? =
        try {
            JSONObject(raw ?: "{}")
        } catch (_: Exception) {
            null
        }

    private fun style(preferences: WidgetPanelPreferences): JSONObject =
        JSONObject()
            .put("background", preferences.background)
            .put("accent", preferences.accent)
            .put("project", preferences.project)

    private suspend fun write(
        storage: WidgetPersistence,
        profile: String,
        key: String,
        before: String?,
        next: String,
    ): WidgetPanelSave {
        val lane = if (profile.isBlank()) storage.lanes.app else storage.lanes.forProfile(ProfileId(profile))
        return when (val done = storage.store.compareAndSet(lane, key, before, next)) {
            is Outcome.Failed -> {
                log.w { "panel write: lane refused (${done.failure.label()}) profileBlank=${profile.isBlank()}" }
                WidgetPanelSave.WriteRefused
            }
            is Outcome.Ok -> if (done.value) WidgetPanelSave.Saved else WidgetPanelSave.ChangedUnderneath
        }
    }
}
