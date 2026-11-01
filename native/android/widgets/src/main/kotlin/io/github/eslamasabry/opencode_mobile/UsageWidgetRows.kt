package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.NativeStrings
import io.github.eslamasabry.oc.android.widgets.R
import org.json.JSONArray
import org.json.JSONObject

/** Reads display-only snapshots, preserving feed order and the per-profile fallback. */
internal object UsageWidgetRows {
    private const val PREFIX = "flutter.oc.widgetUsage."
    private const val SUBS_KEY = "flutter.oc.widgetUsageSubs"
    private const val FLUTTER_PREFS = "FlutterSharedPreferences"
    private const val FULL_PERCENT = 100

    /** Claude first, then Codex, then every other provider A to Z. */
    private fun providerRank(provider: String): Int =
        when (provider) {
            "claude" -> 0
            "codex" -> 1
            else -> 2
        }

    /** One row per provider window, grouped by server then provider order. */
    fun readRows(context: Context): List<UsageWidgetRow> {
        val prefs =
            io.github.eslamasabry.oc.android.widgets.WidgetFeed.persistence ?: return emptyList()
        val subscriptions = readSubscriptionRows(prefs.string(SUBS_KEY.removePrefix("flutter.")))
        val hasSelection = prefs.store.get("oc.usageWidget.v1") != null
        if (hasSelection || subscriptions?.any { !it.signInOnly } == true) {
            return subscriptions.orEmpty()
        }
        val rows = mutableListOf<UsageWidgetRow>()
        for (key in prefs.store.keys(PREFIX.removePrefix("flutter.")).sorted()) {
            val raw = prefs.string(key) ?: continue
            try {
                appendProfile(
                    context,
                    JSONObject(raw),
                    key.removePrefix(PREFIX.removePrefix("flutter.")),
                    rows,
                )
            } catch (_: Exception) {
                // Keep any rows already read before this profile failed.
            }
        }
        // Stable: servers keep their order within one provider.
        val profiles = rows.sortedWith(compareBy({ providerRank(it.provider) }, { it.provider }))
        return UsageWidgetPlans.chooseFeed(subscriptions, profiles, hasSelection)
    }

    /** Null means the subscription feed is absent or unreadable, so use the profile feed. */
    private fun readSubscriptionRows(raw: String?): List<UsageWidgetRow>? {
        if (raw == null) return null
        return try {
            JSONObject(raw).optJSONArray("entries")?.let { subscriptionRows(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun subscriptionRows(entries: JSONArray): List<UsageWidgetRow> {
        val rows = mutableListOf<UsageWidgetRow>()
        for (i in 0 until entries.length()) {
            entries.optJSONObject(i)?.let { appendSubscription(it, rows) }
        }
        return rows
    }

    private fun appendSubscription(entry: JSONObject, rows: MutableList<UsageWidgetRow>) {
        val provider = entry.optString("provider")
        if (provider.isEmpty()) return
        val state = entry.optString("state")
        val action =
            entry
                .optString("action")
                .takeIf {
                    it == "sign_in" || it == "add_key" || it == "set_up"
                }
                .orEmpty()
        val windows = entry.optJSONArray("windows")
        val setup = state == "setup" || state == "signin"
        val row =
            UsageWidgetRow(
                provider = provider,
                displayName = entry.optString("name"),
                profileName = "",
                seconds = 0L,
                label = "",
                left = 0,
                resetsAt = 0L,
                fetchedAt = entry.optLong("fetchedAt", 0L),
                authNeeded = false,
                profileId = entry.optString("profileId"),
            )
        if (setup || windows == null || windows.length() == 0) {
            if (action.isNotEmpty()) rows += row.copy(fixAction = action, signInOnly = true)
        } else {
            val stale = state == "stale" || state == "reset" || state == "broken"
            appendWindows(windows, row.copy(forceStale = stale), rows)
        }
    }

    private fun appendProfile(
        context: Context,
        doc: JSONObject,
        profileId: String,
        rows: MutableList<UsageWidgetRow>,
    ) {
        val name =
            if (doc.optBoolean("local")) {
                NativeStrings.get(context, R.string.usage_widget_this_phone)
            } else {
                doc.optString("name")
            }
        val byProvider = doc.optJSONObject("rows") ?: return
        val providers =
            byProvider
                .keys()
                .asSequence()
                .toList()
                .sortedWith(compareBy({ providerRank(it) }, { it }))
        for (provider in providers) {
            val entry = byProvider.optJSONObject(provider) ?: continue
            val state = entry.optString("state")
            val fix =
                entry
                    .optString("action")
                    .takeIf {
                        (state == "auth" || state == "signin") &&
                            (it == "sign_in" || it == "add_key")
                    }
                    .orEmpty()
            val row =
                UsageWidgetRow(
                    provider = provider,
                    displayName = entry.optString("name"),
                    profileName = name,
                    seconds = 0L,
                    label = "",
                    left = 0,
                    resetsAt = 0L,
                    fetchedAt = entry.optLong("fetchedAt", 0L),
                    authNeeded = state == "auth",
                    fixAction = fix,
                    profileId = profileId,
                )
            appendProvider(entry, row, rows)
        }
    }

    private fun appendProvider(
        entry: JSONObject,
        row: UsageWidgetRow,
        rows: MutableList<UsageWidgetRow>,
    ) {
        val windows = entry.optJSONArray("windows") ?: return
        if (windows.length() == 0 && row.fixAction.isNotEmpty()) {
            rows += row.copy(signInOnly = true)
        } else {
            appendWindows(windows, row, rows)
        }
    }

    private fun appendWindows(
        windows: JSONArray,
        row: UsageWidgetRow,
        rows: MutableList<UsageWidgetRow>,
    ) {
        for (w in 0 until windows.length()) {
            val window = windows.optJSONObject(w) ?: continue
            rows +=
                row.copy(
                    seconds = window.optLong("seconds", 0L),
                    label = window.optString("label"),
                    left = window.optInt("left", 0).coerceIn(0, FULL_PERCENT),
                    resetsAt = window.optLong("resetsAt", 0L),
                )
        }
    }
}
