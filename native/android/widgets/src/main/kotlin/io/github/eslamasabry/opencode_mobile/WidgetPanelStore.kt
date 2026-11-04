package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Only opaque identity leaves the native widget layer; cosmetic writes share its deletion-aware
 * store and lane.
 */
public class WidgetPanelStore(
    private val context: Context,
    public val widgetId: Int,
    public val mode: String,
    private val launch: Map<String, Any?>,
) {
    public data class Data(
        val background: String,
        val accent: String,
        val project: String,
        val projects: List<String>,
        val steerMode: String,
    )

    public fun ownsWidget(): Boolean {
        val provider =
            AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider
                ?: return false
        return provider.packageName == context.packageName &&
            provider.className in WidgetProviders.allNames
    }

    public fun currentScope(): Boolean {
        val snapshot = WidgetV2Bridge.snapshot(context) ?: return false
        return !(launch["profileID"] as? String).isNullOrBlank() &&
            snapshot.optString("profileID") == launch["profileID"] &&
            snapshot.optString("scopeToken") == launch["scopeToken"]
    }

    public suspend fun load(): Outcome<Data> =
        try {
            check(ownsWidget())
            val storage = checkNotNull(WidgetFeed.persistence)
            val owner = owner()
            val all = JSONObject(storage.string("oc.widgetStyle.$owner") ?: "{}")
            val saved =
                all.optJSONObject(widgetId.toString())
                    ?: JSONObject(
                            storage.string("oc.widgetStyle.${WidgetV2Bridge.DEVICE_STYLE_KEY}")
                                ?: "{}"
                        )
                        .optJSONObject(widgetId.toString())
                    ?: JSONObject()
            Outcome.Ok(
                Data(
                    saved.optString("background", "solid"),
                    saved.optString("accent", "wallpaper"),
                    saved.optString("project"),
                    projects(),
                    launch["steerMode"] as? String ?: "",
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Outcome.Failed(Failure.StorageRefused())
        }

    public suspend fun saveStyle(background: String, accent: String): Outcome<Unit> {
        if (
            mode != "configure" ||
                background !in setOf("solid", "tinted", "transparent") ||
                accent !in setOf("wallpaper", "app", "green", "teal", "blue", "amber")
        )
            return Outcome.Failed(Failure.Device())
        return save { it.put("background", background).put("accent", accent) }
    }

    public suspend fun saveProject(project: String): Outcome<Unit> {
        if (mode != "project" || (project.isNotEmpty() && project !in projects()))
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return save { it.put("project", project) }
    }

    private suspend fun save(edit: (JSONObject) -> Unit): Outcome<Unit> {
        if (!ownsWidget()) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val storage = WidgetFeed.persistence ?: return Outcome.Failed(Failure.StorageRefused())
        return storage.publications.withLock {
            // A live scope may change while the panel is open. Never save under its replacement.
            if (owner() != WidgetV2Bridge.DEVICE_STYLE_KEY && !currentScope())
                return@withLock Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            val key = "oc.widgetStyle.${owner()}"
            val before = storage.string(key)
            val decoded: Outcome<JSONObject> =
                try {
                    Outcome.Ok(JSONObject(before ?: "{}"))
                } catch (_: Exception) {
                    Outcome.Failed(Failure.StorageRefused())
                }
            if (decoded is Outcome.Failed) return@withLock decoded
            val styles = (decoded as Outcome.Ok).value
            val entry = styles.optJSONObject(widgetId.toString()) ?: JSONObject()
            edit(entry)
            styles.put(widgetId.toString(), entry)
            val saved =
                if (owner() == WidgetV2Bridge.DEVICE_STYLE_KEY) {
                    storage.store
                        .compareAndSet(storage.lanes.app, key, before, styles.toString())
                        .getOrNull() == true
                } else storage.cas(owner(), key, before, styles.toString())
            if (!saved) return@withLock Outcome.Failed(Failure.StorageRefused())
            SessionsWidgetProvider.refreshAll(context)
            UsageWidgetProvider.refreshAll(context)
            StatusWidgetProvider.refreshAll(context)
            Outcome.Ok(Unit)
        }
    }

    /**
     * A single fixed submission id survives recreation; uncertain acknowledgement is never
     * replayed.
     */
    public suspend fun submit(text: String, submissionId: String): Map<String, Any?> {
        if (mode !in setOf("steer", "voice") || !ownsWidget())
            return mapOf("status" to "rejected", "error" to "unsupported")
        val payload =
            WidgetActionBridge.overlayPayload(launch, mode, text) + mapOf("id" to submissionId)
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { continuation ->
                WidgetActionBridge.submit(context, payload) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        } ?: mapOf("status" to "uncertain", "error" to "uncertainDelivery")
    }

    private fun owner(): String =
        (launch["profileID"] as? String)?.takeIf(String::isNotBlank)
            ?: WidgetV2Bridge.DEVICE_STYLE_KEY

    private fun projects(): List<String> {
        val snapshot = WidgetV2Bridge.snapshot(context) ?: return emptyList()
        if (owner() != WidgetV2Bridge.DEVICE_STYLE_KEY && !currentScope()) return emptyList()
        val projects = snapshot.optJSONArray("projects") ?: return emptyList()
        return (0 until projects.length())
            .map { projects.optString(it) }
            .filter(String::isNotBlank)
            .distinct()
    }

    public companion object {
        public const val EXTRA_PAYLOAD: String = "oc.widgetPayload"
        public const val EXTRA_MODE: String = "oc.widgetMode"

        /**
         * External configure accepts a widget id only; private routes must bind the URI token to
         * the payload.
         */
        public fun fromIntent(
            context: Context,
            intent: Intent,
            configureOnly: Boolean,
        ): WidgetPanelStore? {
            val id =
                intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            val provider =
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: return null
            if (
                provider.packageName != context.packageName ||
                    provider.className !in WidgetProviders.allNames
            )
                return null
            val mode =
                if (configureOnly) {
                    if (provider.className == SessionsWideWidgetProvider::class.java.name) "project"
                    else "configure"
                } else intent.getStringExtra(EXTRA_MODE).orEmpty()
            if (mode !in setOf("configure", "project", "steer", "voice")) return null
            val launch =
                if (configureOnly) {
                    WidgetV2Bridge.snapshot(context)
                        ?.let { WidgetV2Bridge.identity(it, null) }
                        .orEmpty()
                } else
                    WidgetV2Bridge.parsePayload(intent.getStringExtra(EXTRA_PAYLOAD)) ?: return null
            if (!configureOnly && intent.data != null) {
                val uri = intent.data ?: return null
                if (
                    uri.scheme != "opencode-widget" ||
                        uri.host != mode ||
                        uri.pathSegments.size != 2 ||
                        uri.pathSegments[0] != id.toString() ||
                        uri.pathSegments[1] != WidgetActionPolicy.token(launch)
                )
                    return null
            }
            return WidgetPanelStore(context, id, mode, launch)
        }

        public fun configurePayload(context: Context): String =
            JSONObject(
                    WidgetV2Bridge.snapshot(context)
                        ?.let { WidgetV2Bridge.identity(it, null) }
                        .orEmpty()
                )
                .toString()
    }
}
