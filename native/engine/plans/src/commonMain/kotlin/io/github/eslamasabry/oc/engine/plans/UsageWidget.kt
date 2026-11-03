package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.math.roundToLong

/** Which windows each widget entry shows. */
public enum class UsageWidgetWindows(public val wire: String) { Both("both"), FiveHour("fiveHour"), Weekly("weekly") }

/** The person's widget choices (D7): which subscriptions, which windows, and whether rows that need setup appear. */
public data class UsageWidgetSettings(
    val hiddenKeys: Set<String> = emptySet(),
    val showSetupRows: Boolean = true,
    val windows: UsageWidgetWindows = UsageWidgetWindows.Both,
) {
    public fun toJson(): String =
        DartJson.encode(
            mapOf("hidden" to hiddenKeys.sorted(), "setup" to showSetupRows, "windows" to windows.wire),
        )

    public companion object {
        /** Stored in this shared key. */
        public const val KEY: String = "oc.usageWidget.v1"

        public fun load(prefs: PlansPrefs): UsageWidgetSettings {
            val raw = prefs.getString(KEY) ?: return UsageWidgetSettings()
            val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return UsageWidgetSettings()
            val hidden = data["hidden"] as? List<*> ?: emptyList<Any?>()
            return UsageWidgetSettings(
                hiddenKeys = hidden.filterIsInstance<String>().filter { it.length <= 160 }.toSet(),
                showSetupRows = data["setup"] != false,
                windows =
                    UsageWidgetWindows.entries.firstOrNull { it.wire == data["windows"] } ?: UsageWidgetWindows.Both,
            )
        }
    }
}

/** Whether the home-screen widget is drawn on this platform, and the nudge that redraws it. */
public interface UsageWidgetHost {
    public val supported: Boolean

    public suspend fun refreshNative()
}

/**
 * Persists the display numbers of the Android "Usage" home-screen widget (port of UsageWidgetSnapshot). The widget is
 * native and may be drawn while the app process is gone, so it reads the last stored numbers. Only what the widget
 * prints is written: provider, the server's saved name, each window's length, remaining percent and reset time, and
 * when the numbers were measured. Account references, tokens, addresses, plan names and raw errors are never stored.
 */
public class UsageWidgetSnapshot(
    private val prefs: PlansPrefs,
    private val host: UsageWidgetHost,
    private val clock: PlansClock,
) {
    public companion object {
        public const val KEY_PREFIX: String = "oc.widgetUsage"

        public fun key(profileId: String): String = "$KEY_PREFIX.$profileId"

        /** The single feed the widget prefers. */
        public const val SUBSCRIPTIONS_KEY: String = "oc.widgetUsageSubs"

        /** The rows a single widget can show; extra windows are dropped. */
        public const val MAX_WINDOWS_PER_PROVIDER: Int = 3

        public const val SIGN_IN: String = "sign_in"
        public const val ADD_KEY: String = "add_key"
        public const val SET_UP: String = "set_up"

        /** GLM and MiniMax take a key, the rest sign in. */
        public fun actionFor(providerId: String): String =
            if (providerId == "glm" || providerId == "minimax") ADD_KEY else SIGN_IN

        private val providerIdPattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

        public fun validProviderId(id: String): Boolean = providerIdPattern.matches(id)

        /** The widget entry for [s], or null when it has nothing to say there. */
        public fun widgetEntryFor(s: UsageSubscription, settings: UsageWidgetSettings): Map<String, Any?>? {
            val setup =
                s.state == SubscriptionState.CantReadYet || s.state == SubscriptionState.NotSignedIn ||
                    s.state == SubscriptionState.SignInExpired
            if (setup && !settings.showSetupRows) return null
            if (s.state == SubscriptionState.NoLimits || s.state == SubscriptionState.ApiKeyOnly) return null
            val five = s.fiveHour
            val week = s.weekly
            val chosen =
                when (settings.windows) {
                    UsageWidgetWindows.Both -> listOfNotNull(five, week).ifEmpty { s.windows.take(2) }
                    UsageWidgetWindows.FiveHour -> listOfNotNull(five ?: s.windows.firstOrNull())
                    UsageWidgetWindows.Weekly -> listOfNotNull(week ?: s.windows.firstOrNull())
                }
            if (!setup && chosen.isEmpty() && s.state != SubscriptionState.ReaderBroken) return null
            val state =
                when (s.state) {
                    SubscriptionState.Ok -> "ok"
                    SubscriptionState.NearLimit -> "near"
                    SubscriptionState.Exhausted -> "out"
                    SubscriptionState.OfflineStale -> if (s.resetSinceLastReading) "reset" else "stale"
                    SubscriptionState.ReaderBroken -> "broken"
                    SubscriptionState.CantReadYet -> "setup"
                    SubscriptionState.NotSignedIn, SubscriptionState.SignInExpired -> "signin"
                    else -> "ok"
                }
            return linkedMapOf(
                "key" to s.key,
                "provider" to s.providerId,
                "name" to s.displayName,
                "state" to state,
                "fetchedAt" to (s.fetchedAt ?: 0L),
                "reset" to s.resetSinceLastReading,
                "windows" to chosen.map { w -> windowJson(w.durationSeconds, w.label, w.leftPercent, w.resetsAt) },
                "action" to
                    when (s.fix) {
                        UsageFix.SignIn -> SIGN_IN
                        UsageFix.AddKey -> ADD_KEY
                        UsageFix.SetUp -> SET_UP
                        else -> ""
                    },
                "profileId" to (s.fixConnection?.id ?: ""),
            )
        }

        private fun windowJson(seconds: Int?, label: String, left: Double, resetsAt: Long?): Map<String, Any?> =
            linkedMapOf<String, Any?>().apply {
                if (seconds != null) put("seconds", seconds) else put("label", label)
                put("left", left.roundToLong().coerceIn(0, 100))
                put("resetsAt", resetsAt)
            }
    }

    /**
     * The single feed: one entry per subscription, in the order of the Usage page, each with its 5-hour and weekly
     * window when the provider reports both. Rows that need setup are included unless [settings] hide them.
     */
    public suspend fun publishSubscriptions(snapshot: UsageSnapshot, settings: UsageWidgetSettings) {
        if (!host.supported) return
        val entries =
            snapshot.subscriptions.filter { it.key !in settings.hiddenKeys }.mapNotNull { widgetEntryFor(it, settings) }
        val raw = prefs.getString(SUBSCRIPTIONS_KEY)
        if (entries.isEmpty() && !snapshot.loaded) return
        val payload = DartJson.encode(mapOf("v" to 1, "entries" to entries))
        if (payload == raw) return
        prefs.setString(SUBSCRIPTIONS_KEY, payload)
        host.refreshNative()
    }

    /** Records a measurement the monitor accepted as fresh. */
    public suspend fun record(profileId: String, profileName: String, snapshot: ProviderQuotaSnapshot) {
        if (!host.supported || profileId.isEmpty()) return
        if (!quotaCollectionAvailable(snapshot.provider) || !snapshot.canShowWindows) return
        val windows =
            snapshot.windows
                .filter { it.remainingPercent != null && it.durationSeconds != null }
                .map {
                    linkedMapOf<String, Any?>(
                        "seconds" to it.durationSeconds,
                        "left" to it.remainingPercent!!.coerceIn(0.0, 100.0).roundToLong(),
                        "resetsAt" to it.resetsAt,
                    )
                }.take(MAX_WINDOWS_PER_PROVIDER)
        if (windows.isEmpty()) return
        mutate(profileId, profileName) { rows ->
            rows[snapshot.provider.id] =
                linkedMapOf("state" to "ok", "fetchedAt" to snapshot.fetchedAt, "windows" to windows)
        }
    }

    /** The collector says the stored sign-in no longer works: keep the last numbers and let the widget say so. */
    public suspend fun markAuthNeeded(profileId: String, profileName: String, provider: QuotaProvider) {
        if (!host.supported || profileId.isEmpty() || !quotaCollectionAvailable(provider)) return
        mutate(profileId, profileName) { rows ->
            val row = rows.getOrPut(provider.id) { emptyRow() }
            row["state"] = "auth"
            row["action"] = actionFor(provider.id)
        }
    }

    /** Not signed in where the app reads it. An existing row keeps its windows; numbers are never invented. */
    public suspend fun markNotSignedIn(
        profileId: String,
        profileName: String,
        providerId: String,
        displayName: String = "",
        thisPhone: Boolean = false,
    ) {
        if (!host.supported || profileId.isEmpty() || !validProviderId(providerId)) return
        mutate(profileId, profileName, local = thisPhone) { rows ->
            val row = rows.getOrPut(providerId) { emptyRow() }
            val windows = row["windows"]
            if (windows is List<*> && windows.isNotEmpty()) return@mutate
            row["state"] = "signin"
            row["action"] = actionFor(providerId)
            if (displayName.isNotEmpty()) row["name"] = displayName
        }
    }

    private fun emptyRow(): MutableMap<String, Any?> =
        linkedMapOf("fetchedAt" to clock.nowMs(), "windows" to emptyList<Any?>())

    /** Monitoring of this provider ended (consent withdrawn, source changed). */
    public suspend fun removeProvider(profileId: String, provider: QuotaProvider) {
        if (!host.supported || profileId.isEmpty()) return
        mutate(profileId, "") { it.remove(provider.id) }
    }

    /** Drops everything stored for [profileId]; true when nothing is left. */
    public suspend fun clearForProfile(profileId: String): Boolean {
        if (profileId.isEmpty() || prefs.getString(key(profileId)) == null) return true
        if (!prefs.remove(key(profileId))) return false
        if (host.supported) host.refreshNative()
        return true
    }

    private suspend fun mutate(
        profileId: String,
        profileName: String,
        local: Boolean = false,
        change: (MutableMap<String, MutableMap<String, Any?>>) -> Unit,
    ) {
        val rows = LinkedHashMap<String, MutableMap<String, Any?>>()
        var name = profileName
        val raw = prefs.getString(key(profileId))
        if (raw != null) {
            val decoded = DartJson.decodeOrNull(raw) as? Map<*, *>
            if (decoded != null) {
                if (name.isEmpty()) name = decoded["name"]?.toString() ?: ""
                (decoded["rows"] as? Map<*, *>)?.forEach { (k, v) ->
                    if (v is Map<*, *>) {
                        rows[k.toString()] =
                            LinkedHashMap<String, Any?>().apply { v.forEach { (a, b) -> put(a.toString(), b) } }
                    }
                }
            }
        }
        change(rows)
        if (rows.isEmpty()) {
            if (raw == null) return
            prefs.remove(key(profileId))
        } else {
            val payload =
                DartJson.encode(
                    linkedMapOf<String, Any?>("name" to name, "rows" to rows).apply { if (local) put("local", true) },
                )
            if (payload == raw) return
            prefs.setString(key(profileId), payload)
        }
        host.refreshNative()
    }
}
