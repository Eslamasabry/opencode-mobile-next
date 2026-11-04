package io.github.eslamasabry.oc.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.opencode_mobile.SessionsWideWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetProviders
import io.github.eslamasabry.opencode_mobile.WidgetV2Style
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

public data class WidgetPanelStyle(
    val widgetId: Int,
    val owner: String,
    val scope: String,
    val background: String,
    val accent: String,
    val project: String,
    val projects: List<String>,
    val revision: String?,
)

/** The standalone cosmetic editor uses the graph's acknowledged store and the widget publication lane. */
public class WidgetPanelStore(context: Context, private val persistence: WidgetPersistence) {
    private val app = context.applicationContext

    public fun palette(background: String, accent: String, appTheme: Map<String, Int>? = null): WidgetV2Style.Palette {
        val snapshot = parse(persistence.string(WidgetPersistence.SNAPSHOT)) ?: JSONObject()
        if (appTheme != null) snapshot.put("appTheme", JSONObject(appTheme))
        return WidgetV2Style.previewPalette(app, background, accent, snapshot)
    }

    public fun owns(widgetId: Int): Boolean {
        val provider = AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId)?.provider ?: return false
        return provider.packageName == app.packageName && provider.className in WidgetProviders.allNames
    }

    public fun choosesProject(widgetId: Int): Boolean =
        AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId)?.provider?.className == SessionsWideWidgetProvider::class.java.name

    public suspend fun load(widgetId: Int): Outcome<WidgetPanelStyle> = persistence.publications.withLock {
        if (!owns(widgetId)) return@withLock stale()
        val raw = persistence.string(WidgetPersistence.SNAPSHOT)
        val snapshot = parse(raw) ?: if (raw != null) return@withLock unavailable() else JSONObject()
        val owner = owner(snapshot)
        val revision = persistence.string("oc.widgetStyle.$owner")
        val envelope = parse(revision) ?: if (revision != null) return@withLock unavailable() else JSONObject()
        val saved = envelope.optJSONObject(widgetId.toString())
            ?: if (owner != DEVICE) parse(persistence.string("oc.widgetStyle.$DEVICE"))?.optJSONObject(widgetId.toString()) else null
        Outcome.Ok(
            WidgetPanelStyle(
                widgetId, owner, snapshot.optString("scopeToken"),
                saved?.optString("background")?.takeIf { it in BACKGROUNDS } ?: "solid",
                saved?.optString("accent")?.takeIf { it in ACCENTS } ?: "wallpaper",
                saved?.optString("project").orEmpty(), projects(snapshot), revision,
            )
        )
    }

    public suspend fun saveStyle(old: WidgetPanelStyle, background: String, accent: String): Outcome<Unit> {
        if (background !in BACKGROUNDS || accent !in ACCENTS) return unavailable()
        return save(old, null) { it.put("background", background).put("accent", accent) }
    }

    public suspend fun saveProject(old: WidgetPanelStyle, project: String): Outcome<Unit> =
        save(old, project) { it.put("project", project) }

    private suspend fun save(old: WidgetPanelStyle, project: String?, edit: (JSONObject) -> JSONObject): Outcome<Unit> =
        persistence.publications.withLock {
            if (!owns(old.widgetId)) return@withLock stale()
            val snapshotRaw = persistence.string(WidgetPersistence.SNAPSHOT)
            val snapshot = parse(snapshotRaw) ?: if (snapshotRaw != null) return@withLock unavailable() else JSONObject()
            if (owner(snapshot) != old.owner || snapshot.optString("scopeToken") != old.scope) return@withLock stale()
            if (project != null && (project.isNotEmpty() && project !in projects(snapshot))) return@withLock stale()
            val key = "oc.widgetStyle.${old.owner}"
            if (persistence.string(key) != old.revision) return@withLock stale()
            val styles = parse(old.revision) ?: if (old.revision != null) return@withLock unavailable() else JSONObject()
            val entry = styles.optJSONObject(old.widgetId.toString())
                ?: if (old.owner != DEVICE) parse(persistence.string("oc.widgetStyle.$DEVICE"))?.optJSONObject(old.widgetId.toString()) else null
            styles.put(old.widgetId.toString(), edit(entry ?: JSONObject()))
            val lane = if (old.owner == DEVICE) persistence.lanes.app else persistence.lanes.forProfile(ProfileId(old.owner))
            when (val saved = persistence.store.compareAndCommit(
                lane,
                mapOf(key to old.revision?.let { PrefValue.Str(it) }, WidgetPersistence.SNAPSHOT to snapshotRaw?.let { PrefValue.Str(it) }),
            ) { putString(key, styles.toString()) }) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> if (!saved.value) stale() else {
                    AndroidWidgetSurface.redrawEveryWidget(app)
                    Outcome.Ok(Unit)
                }
            }
        }

    private fun projects(snapshot: JSONObject): List<String> {
        val result = linkedSetOf<String>()
        snapshot.optJSONArray("projects")?.let { rows ->
            for (index in 0 until rows.length()) rows.optString(index).takeIf(String::isNotBlank)?.let(result::add)
        }
        // The native merged attention feed carries project labels on its session rows.
        snapshot.optJSONArray("sessions")?.let { rows ->
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.optString("project")?.takeIf(String::isNotBlank)?.let(result::add)
        }
        return result.toList()
    }

    private fun owner(snapshot: JSONObject): String = snapshot.optString("profileID").ifBlank { DEVICE }
    private fun parse(raw: String?): JSONObject? = if (raw == null) null else try { JSONObject(raw) } catch (_: Exception) { null }
    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    private fun unavailable(): Outcome.Failed = Outcome.Failed(Failure.StorageRefused())

    private companion object {
        const val DEVICE = "_device"
        val BACKGROUNDS = setOf("solid", "tinted", "transparent")
        val ACCENTS = setOf("wallpaper", "app", "green", "amber", "blue", "teal")
    }
}
