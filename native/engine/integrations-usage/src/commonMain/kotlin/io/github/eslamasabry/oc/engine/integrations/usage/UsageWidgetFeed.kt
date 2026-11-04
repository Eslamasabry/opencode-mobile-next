package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * The provider ids whose collector numbers may be collected at all; Claude stays off (Flutter
 * `quotaCollectionAvailable`).
 */
public fun quotaCollectionAvailable(providerId: String): Boolean =
    providerId == "codex" || providerId == "minimax" || providerId == "glm"

/** One collector window as the widget needs it: display numbers only. */
public data class QuotaWidgetWindow(
    val remainingPercent: Double?,
    val durationSeconds: Long?,
    val resetsAt: Instant?,
)

/**
 * The part of a collector quota snapshot the widget records. [canShowWindows] is the snapshot's own
 * rule (status ok and a matched or source-bound account); account refs, plan names, URLs and errors
 * are not part of this type.
 */
public data class QuotaWidgetInput(
    val providerId: String,
    val canShowWindows: Boolean,
    val fetchedAt: Instant,
    val windows: List<QuotaWidgetWindow>,
)

/** Which windows each widget entry shows. */
public enum class UsageWidgetWindows(public val wire: String) {
    Both("both"),
    FiveHour("fiveHour"),
    Weekly("weekly"),
}

/** The person's widget choices (D7), stored app-wide in `oc.usageWidget.v1`. */
public data class UsageWidgetSettings(
    val hiddenKeys: Set<String> = emptySet(),
    val showSetupRows: Boolean = true,
    val windows: UsageWidgetWindows = UsageWidgetWindows.Both,
) {
    public fun toJson(): String =
        DartJson.encode(
            mapOf(
                "hidden" to hiddenKeys.sorted(),
                "setup" to showSetupRows,
                "windows" to windows.wire,
            )
        )

    public companion object {
        public const val KEY: String = "oc.usageWidget.v1"

        public fun load(store: KeyValueStore): UsageWidgetSettings {
            val raw = store.string(KEY) ?: return UsageWidgetSettings()
            val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return UsageWidgetSettings()
            return UsageWidgetSettings(
                hiddenKeys =
                    (data["hidden"] as? List<*>)
                        .orEmpty()
                        .filterIsInstance<String>()
                        .filter { it.length <= 160 }
                        .toSet(),
                showSetupRows = data["setup"] != false,
                windows =
                    UsageWidgetWindows.entries.firstOrNull { it.wire == data["windows"] }
                        ?: UsageWidgetWindows.Both,
            )
        }
    }
}

/**
 * The display numbers of the Android "Usage" home-screen widget (Flutter `UsageWidgetSnapshot`).
 * The widget is drawn while the app process may be gone, so it reads the last stored numbers and
 * never calls the network. Only what the widget prints is written: provider, saved server name,
 * each window's length, remaining percent and reset time, and when the numbers were measured.
 * Account references, tokens, addresses, plan names and raw errors are never stored, and Claude
 * collector numbers can never appear.
 *
 * One preference per server, `oc.widgetUsage.<profileId>`, so the profile sweep removes it; the
 * subscriptions feed is the app-wide `oc.widgetUsageSubs`. Inert when [isAndroid] is false.
 */
public class UsageWidgetFeed(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val isAndroid: Boolean,
    private val refreshNative: suspend () -> Unit = {},
    private val clock: () -> Instant,
) {
    public companion object {
        public const val KEY_PREFIX: String = "oc.widgetUsage"
        public const val SUBSCRIPTIONS_KEY: String = "oc.widgetUsageSubs"
        public const val MAX_WINDOWS_PER_PROVIDER: Int = 3
        public const val SIGN_IN: String = "sign_in"
        public const val ADD_KEY: String = "add_key"
        public const val SET_UP: String = "set_up"

        public fun key(profileId: String): String = "$KEY_PREFIX.$profileId"

        /** GLM and MiniMax take a key, the rest sign in. */
        public fun actionFor(providerId: String): String =
            if (providerId == "glm" || providerId == "minimax") ADD_KEY else SIGN_IN

        private val providerIdPattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

        public fun validProviderId(id: String): Boolean = providerIdPattern.matches(id)

        /** The widget entry for [s], or null when it has nothing to say there. */
        public fun widgetEntryFor(
            s: UsageSubscription,
            settings: UsageWidgetSettings,
        ): Map<String, Any?>? {
            val setup =
                s.state == SubscriptionState.CantReadYet ||
                    s.state == SubscriptionState.NotSignedIn ||
                    s.state == SubscriptionState.SignInExpired
            if (setup && !settings.showSetupRows) return null
            if (s.state == SubscriptionState.NoLimits || s.state == SubscriptionState.ApiKeyOnly)
                return null
            val five = s.fiveHour
            val week = s.weekly
            val chosen = ArrayList<UsageWindow>()
            when (settings.windows) {
                UsageWidgetWindows.Both -> {
                    five?.let { chosen += it }
                    week?.let { chosen += it }
                    if (chosen.isEmpty()) chosen += s.windows.take(2)
                }
                UsageWidgetWindows.FiveHour ->
                    (five ?: s.windows.firstOrNull())?.let { chosen += it }
                UsageWidgetWindows.Weekly -> (week ?: s.windows.firstOrNull())?.let { chosen += it }
            }
            if (!setup && chosen.isEmpty() && s.state != SubscriptionState.ReaderBroken) return null
            val state =
                when (s.state) {
                    SubscriptionState.Ok -> "ok"
                    SubscriptionState.NearLimit -> "near"
                    SubscriptionState.Exhausted -> "out"
                    SubscriptionState.OfflineStale ->
                        if (s.resetSinceLastReading) "reset" else "stale"
                    SubscriptionState.ReaderBroken -> "broken"
                    SubscriptionState.CantReadYet -> "setup"
                    SubscriptionState.NotSignedIn,
                    SubscriptionState.SignInExpired -> "signin"
                    else -> "ok"
                }
            return linkedMapOf(
                "key" to s.key,
                "provider" to s.providerId,
                "name" to s.displayName,
                "state" to state,
                "fetchedAt" to (s.fetchedAt?.toEpochMilliseconds() ?: 0L),
                "reset" to s.resetSinceLastReading,
                "windows" to
                    chosen.map { w ->
                        windowJson(
                            w.durationSeconds,
                            w.label,
                            w.leftPercent.roundToLong().coerceIn(0, 100),
                            w.resetsAt,
                        )
                    },
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

        private fun windowJson(
            seconds: Long?,
            label: String,
            left: Long,
            resetsAt: Instant?,
        ): Map<String, Any?> = buildMap {
            if (seconds != null) put("seconds", seconds) else put("label", label)
            put("left", left)
            put("resetsAt", resetsAt?.toEpochMilliseconds())
        }
    }

    /** Records a collector measurement the monitor accepted as fresh. */
    public suspend fun record(profileId: String, profileName: String, snapshot: QuotaWidgetInput) {
        if (!isAndroid || profileId.isEmpty()) return
        if (!quotaCollectionAvailable(snapshot.providerId) || !snapshot.canShowWindows) return
        val windows =
            snapshot.windows
                .filter { it.remainingPercent != null && it.durationSeconds != null }
                .map {
                    linkedMapOf<String, Any?>(
                        "seconds" to it.durationSeconds,
                        "left" to it.remainingPercent!!.coerceIn(0.0, 100.0).roundToLong(),
                        "resetsAt" to it.resetsAt?.toEpochMilliseconds(),
                    )
                }
                .take(MAX_WINDOWS_PER_PROVIDER)
        if (windows.isEmpty()) return
        mutate(profileId, profileName) { rows ->
            rows[snapshot.providerId] =
                linkedMapOf(
                    "state" to "ok",
                    "fetchedAt" to snapshot.fetchedAt.toEpochMilliseconds(),
                    "windows" to windows,
                )
        }
    }

    /**
     * Replaces everything stored for a Paseo server (or this phone, [thisPhone]) with what its
     * daemon just reported. Providers the daemon no longer reports disappear. A signed-out provider
     * becomes a row with the way in and no numbers until numbers replace it (B-STATE-148).
     */
    public suspend fun recordReadings(
        profileId: String,
        profileName: String,
        readings: List<ProviderUsageReading>,
        thisPhone: Boolean = false,
    ) {
        if (!isAndroid || profileId.isEmpty()) return
        mutate(profileId, profileName, local = thisPhone) { rows ->
            rows.clear()
            for (reading in readings) {
                if (reading.needsSignIn && reading.windows.isEmpty()) {
                    rows[reading.providerId] =
                        linkedMapOf(
                            "state" to "signin",
                            "action" to actionFor(reading.providerId),
                            "name" to reading.displayName,
                            "fetchedAt" to reading.fetchedAt.toEpochMilliseconds(),
                            "windows" to emptyList<Any?>(),
                        )
                    continue
                }
                if (reading.windows.isEmpty()) continue
                rows[reading.providerId] =
                    linkedMapOf(
                        "state" to "ok",
                        "name" to reading.displayName,
                        "fetchedAt" to reading.fetchedAt.toEpochMilliseconds(),
                        "windows" to
                            reading.windows.take(MAX_WINDOWS_PER_PROVIDER).map {
                                windowJson(
                                    it.durationSeconds,
                                    it.label,
                                    it.remainingPercent.toLong().coerceIn(0, 100),
                                    it.resetsAt,
                                )
                            },
                    )
            }
        }
    }

    /**
     * The collector says the stored sign-in no longer works: keep the last numbers and flag the
     * row.
     */
    public suspend fun markAuthNeeded(profileId: String, profileName: String, providerId: String) {
        if (!isAndroid || profileId.isEmpty()) return
        if (!quotaCollectionAvailable(providerId)) return
        mutate(profileId, profileName) { rows ->
            val row =
                rows.getOrPut(providerId) {
                    linkedMapOf(
                        "fetchedAt" to clock().toEpochMilliseconds(),
                        "windows" to emptyList<Any?>(),
                    )
                }
            row["state"] = "auth"
            row["action"] = actionFor(providerId)
        }
    }

    /**
     * The source says this provider is not signed in: an existing row keeps its numbers; otherwise
     * a row with the way in.
     */
    public suspend fun markNotSignedIn(
        profileId: String,
        profileName: String,
        providerId: String,
        displayName: String = "",
        thisPhone: Boolean = false,
    ) {
        if (!isAndroid || profileId.isEmpty() || !validProviderId(providerId)) return
        mutate(profileId, profileName, local = thisPhone) { rows ->
            val row =
                rows.getOrPut(providerId) {
                    linkedMapOf(
                        "fetchedAt" to clock().toEpochMilliseconds(),
                        "windows" to emptyList<Any?>(),
                    )
                }
            val windows = row["windows"]
            if (windows is List<*> && windows.isNotEmpty()) return@mutate
            row["state"] = "signin"
            row["action"] = actionFor(providerId)
            if (displayName.isNotEmpty()) row["name"] = displayName
        }
    }

    /** Monitoring of this provider ended (consent withdrawn, source changed). */
    public suspend fun removeProvider(profileId: String, providerId: String) {
        if (!isAndroid || profileId.isEmpty()) return
        mutate(profileId, "") { rows -> rows.remove(providerId) }
    }

    /**
     * The single feed the widget prefers: one entry per subscription in Usage order (contract §5).
     */
    public suspend fun publishSubscriptions(
        snapshot: UsageSnapshot,
        settings: UsageWidgetSettings,
    ): Boolean {
        if (!isAndroid) return true
        val entries =
            snapshot.subscriptions
                .filter { it.key !in settings.hiddenKeys }
                .mapNotNull { widgetEntryFor(it, settings) }
        val raw = store.string(SUBSCRIPTIONS_KEY)
        if (entries.isEmpty() && !snapshot.loaded) return true
        val payload = DartJson.encode(linkedMapOf("v" to 1, "entries" to entries))
        if (payload == raw) return true
        if (store.commit(lane) { putString(SUBSCRIPTIONS_KEY, payload) } !is Outcome.Ok)
            return false
        refreshNative()
        return true
    }

    public suspend fun clearSubscriptions() {
        if (store.string(SUBSCRIPTIONS_KEY) == null) return
        if (store.commit(lane) { remove(SUBSCRIPTIONS_KEY) } !is Outcome.Ok) return
        if (isAndroid) refreshNative()
    }

    /** Drops everything stored for [profileId]; true when nothing is left. */
    public suspend fun clearForProfile(profileId: String): Boolean {
        if (profileId.isEmpty()) return true
        if (store.string(key(profileId)) == null) return true
        if (store.commit(lane) { remove(key(profileId)) } !is Outcome.Ok) return false
        if (isAndroid) refreshNative()
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
        val raw = store.string(key(profileId))
        if (raw != null) {
            // Unreadable payload: start over rather than keep unknown content.
            val decoded = DartJson.decodeOrNull(raw) as? Map<*, *>
            if (decoded != null) {
                if (name.isEmpty()) name = decoded["name"]?.toString() ?: ""
                (decoded["rows"] as? Map<*, *>)?.forEach { (k, v) ->
                    if (v is Map<*, *>)
                        rows[k.toString()] =
                            v.entries.associateTo(LinkedHashMap()) { it.key.toString() to it.value }
                }
            }
        }
        change(rows)
        val ok =
            if (rows.isEmpty()) {
                if (raw == null) return
                store.commit(lane) { remove(key(profileId)) }
            } else {
                val doc = linkedMapOf<String, Any?>("name" to name, "rows" to rows)
                if (local) doc["local"] = true
                val payload = DartJson.encode(doc)
                if (payload == raw) return
                store.commit(lane) { putString(key(profileId), payload) }
            }
        if (ok is Outcome.Ok) refreshNative()
    }
}
