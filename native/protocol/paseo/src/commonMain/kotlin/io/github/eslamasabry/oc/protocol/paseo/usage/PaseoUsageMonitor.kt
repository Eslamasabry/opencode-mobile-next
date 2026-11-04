package io.github.eslamasabry.oc.protocol.paseo.usage

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/*
 * The Paseo usage monitor's logic (port of lib/state/paseo_usage_monitor.dart and the row shape of
 * lib/background/usage_widget_snapshot.dart, B-AGENT-191), without timers: the cadence (5 min foreground, 15 min
 * background when allowed) belongs to the engine that owns it (engine/usage), which calls [PaseoUsageMonitor.refresh].
 * Opt-in is per server, off by default, stored as `oc.paseoUsage.<profileId>` so profile deletion sweeps it.
 */

public enum class PaseoUsageStatus { Off, Paused, Waiting, Checking, Current, Unavailable }

/** A saved server as the monitor needs it; only Paseo servers can be read. */
public data class UsageProfile(val id: String, val name: String, val paseo: Boolean)

/** One widget window: display numbers only. */
public data class UsageWidgetWindow(val seconds: Int?, val left: Int, val resetsAtMs: Long?)

/** One provider's widget row: its cleaned name and numbers, never plan, source or error text. */
public data class UsageWidgetRow(val name: String, val windows: List<UsageWidgetWindow>, val needsSignIn: Boolean = false)

/** The widget document of one server: its saved name and the rows by provider id (exactly the last answer's). */
public data class UsageWidgetDoc(val name: String, val rows: Map<String, UsageWidgetRow>)

/** Where the opt-in lives (`oc.paseoUsage.<profileId>`, a per-profile key the deletion sweep finds). */
public interface UsageOptInStore {
    public suspend fun get(key: String): Boolean?

    /** False when storage refused. */
    public suspend fun set(key: String, value: Boolean): Boolean

    public suspend fun remove(key: String): Boolean
}

/** The home-screen widget's storage (`oc.usageWidget.<profileId>`) and redraw. */
public interface UsageWidgetSink {
    public suspend fun record(profileId: String, doc: UsageWidgetDoc)

    public suspend fun clear(profileId: String)
}

/** The widget rows of [readings]: names and numbers only. */
public fun usageWidgetDoc(profileName: String, readings: List<ProviderUsageReading>): UsageWidgetDoc =
    UsageWidgetDoc(
        name = profileName,
        rows = readings.associate { r ->
            r.providerId to UsageWidgetRow(
                name = r.displayName,
                windows = r.windows.map { UsageWidgetWindow(it.durationSeconds, it.remainingPercent, it.resetsAtMs) },
                needsSignIn = r.needsSignIn,
            )
        },
    )

public class PaseoUsageMonitor(
    private val profiles: () -> List<UsageProfile>,
    private val store: UsageOptInStore,
    private val widget: UsageWidgetSink?,
    /** A reader for one server; the monitor closes it after each read. */
    private val createReader: (UsageProfile) -> ProviderUsageReader,
    /** Whether the server's credentials can be read now (not being deleted, unlocked). */
    private val isReadable: (String) -> Boolean = { true },
    private val clock: Clock,
) {
    private val lock = Mutex()
    private val optIn = HashMap<String, Boolean>()
    private val status = HashMap<String, PaseoUsageStatus>()
    private val readAt = HashMap<String, Long>()
    private val shown = HashMap<String, Int>()
    private val last = HashMap<String, List<ProviderUsageReading>>()
    private val blocked = HashSet<String>()
    private var foreground = true
    private var backgroundAllowed = false
    private var epoch = 0

    public companion object {
        public fun key(profileId: String): String = "oc.paseoUsage.$profileId"
    }

    public val runningAllowed: Boolean get() = foreground || backgroundAllowed

    /** Saved Paseo servers, the only ones this can read. */
    public val paseoProfiles: List<UsageProfile> get() = profiles().filter { it.paseo }

    private fun profile(id: String) = profiles().firstOrNull { it.id == id }

    private fun readable(id: String): Boolean {
        val p = profile(id) ?: return false
        return p.paseo && id !in blocked && isReadable(id)
    }

    /** Loads the stored opt-ins (call once at start). */
    public suspend fun load() {
        for (p in paseoProfiles) optIn[p.id] = store.get(key(p.id)) == true
    }

    public fun enabled(id: String): Boolean = readable(id) && optIn[id] == true

    public fun statusFor(id: String): PaseoUsageStatus =
        when {
            !enabled(id) -> PaseoUsageStatus.Off
            !runningAllowed -> PaseoUsageStatus.Paused
            else -> status[id] ?: PaseoUsageStatus.Waiting
        }

    public fun lastReadAtMs(id: String): Long? = readAt[id]

    /** How many providers reported plan numbers on the last answer (zero: signed out, or no limits). */
    public fun shownProviders(id: String): Int = shown[id] ?: 0

    public fun readingsFor(id: String): List<ProviderUsageReading>? = if (enabled(id)) last[id] else null

    /** Only Paseo servers can be switched on; false when refused (nothing stored). Switching on reads at once. */
    public suspend fun setEnabled(id: String, value: Boolean): Boolean {
        if (!readable(id)) return false
        if (value) {
            if (!store.set(key(id), true)) return false
            optIn[id] = true
            refresh()
        } else {
            if (!store.remove(key(id))) return false
            optIn.remove(id)
            status.remove(id)
            readAt.remove(id)
            shown.remove(id)
            last.remove(id)
            widget?.clear(id)
        }
        return true
    }

    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (this.foreground == foreground && this.backgroundAllowed == backgroundAllowed) return
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        epoch++
    }

    /** Reads every opted-in server once (one refresh at a time); nothing when paused or nothing is on. */
    public suspend fun refresh() {
        if (!runningAllowed) return
        lock.withLock {
            for (p in paseoProfiles) {
                if (!runningAllowed) break
                if (enabled(p.id)) read(p)
            }
        }
    }

    private suspend fun read(profile: UsageProfile) {
        val id = profile.id
        val started = epoch
        fun current() = started == epoch && enabled(id)
        status[id] = PaseoUsageStatus.Checking
        val reader = createReader(profile)
        try {
            when (val outcome = reader.read()) {
                is Outcome.Failed -> if (current()) status[id] = PaseoUsageStatus.Unavailable
                is Outcome.Ok -> {
                    if (!current()) return
                    val readings = outcome.value
                    status[id] = PaseoUsageStatus.Current
                    readAt[id] = clock.now().toEpochMilliseconds()
                    shown[id] = readings.count { it.windows.isNotEmpty() }
                    last[id] = readings
                    widget?.record(id, usageWidgetDoc(profile.name, readings))
                    if (!current()) widget?.clear(id)
                }
            }
        } finally {
            reader.close()
        }
    }

    /** Deletion started: stop reading and drop the widget's numbers. */
    public suspend fun removeProfile(id: String) {
        blocked += id
        epoch++
        status.remove(id)
        last.remove(id)
        optIn.remove(id)
        widget?.clear(id)
    }
}
