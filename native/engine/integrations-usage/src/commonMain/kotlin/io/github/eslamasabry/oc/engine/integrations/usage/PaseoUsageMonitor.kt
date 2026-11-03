package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

public enum class PaseoUsageStatus { Off, Paused, Waiting, HostStopped, Checking, Current, Unavailable }

/** A saved Paseo server, as the monitor needs it. */
public data class PaseoUsageProfile(val id: String, val name: String)

/** Where the monitor reads from. Every read is an [Outcome]: a failure is a plain status, never text. */
public interface PaseoUsagePorts {
    /** The saved Paseo servers. */
    public fun paseoProfiles(): List<PaseoUsageProfile>

    /** The server's capability says its daemon can list provider usage. */
    public fun isReadable(profileId: String): Boolean

    public suspend fun readServer(profileId: String): Outcome<List<ProviderUsageReading>>

    /** This phone's agent host can be read at all (a supported phone with the host installed). */
    public fun phoneSupported(): Boolean = false

    /** This phone's agent host; Ok(null) when the host is stopped. */
    public suspend fun readPhone(): Outcome<List<ProviderUsageReading>?> = Outcome.Ok(null)
}

/**
 * Reads provider usage from Paseo servers and this phone's agent host for Usage and the widget (Flutter
 * `PaseoUsageMonitor`). The opt-in is `oc.paseoUsage.<profileId>` (and `oc.paseoUsage.phone`), swept with the profile.
 * Timers belong to the caller: [refresh] reads once, only while [setRuntime] allows it.
 */
public class PaseoUsageMonitor(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val ports: PaseoUsagePorts,
    private val widget: UsageWidgetFeed?,
    private val clock: () -> Instant,
    private val autoRead: Boolean = false,
) {
    public companion object {
        public const val PHONE_ID: String = "phone"

        public fun key(profileId: String): String = "oc.paseoUsage.$profileId"
    }

    private val status = HashMap<String, PaseoUsageStatus>()
    private val blocked = HashSet<String>()
    private val readAt = HashMap<String, Instant>()
    private val last = HashMap<String, List<ProviderUsageReading>>()
    private val refreshing = Mutex()
    private var foreground = true
    private var backgroundAllowed = false
    private var disposed = false
    private var epoch = 0

    public val runningAllowed: Boolean get() = !disposed && (foreground || backgroundAllowed)

    private fun profile(id: String): PaseoUsageProfile? = ports.paseoProfiles().firstOrNull { it.id == id }

    private fun readable(id: String): Boolean =
        if (id == PHONE_ID) {
            !disposed && id !in blocked && ports.phoneSupported()
        } else {
            profile(id) != null && id !in blocked && ports.isReadable(id)
        }

    public fun enabled(id: String): Boolean = readable(id) && (autoRead || store.bool(key(id)) == true)

    public fun optedIn(id: String): Boolean = readable(id) && store.bool(key(id)) == true

    private fun targets(): List<String> =
        buildList {
            if (enabled(PHONE_ID)) add(PHONE_ID)
            ports.paseoProfiles().filter { enabled(it.id) }.forEach { add(it.id) }
        }

    public fun lastReadAt(id: String): Instant? = readAt[id]

    public fun readingsFor(id: String): List<ProviderUsageReading>? = if (enabled(id)) last[id] else null

    public fun statusFor(id: String): PaseoUsageStatus =
        when {
            !enabled(id) -> PaseoUsageStatus.Off
            !runningAllowed -> PaseoUsageStatus.Paused
            else -> status[id] ?: PaseoUsageStatus.Waiting
        }

    /** Turns reading on or off for [id]. Off deletes the opt-in and the widget snapshot (B-SEC-116). */
    public suspend fun setEnabled(id: String, value: Boolean): Boolean {
        if (disposed || id in blocked) return false
        if (id == PHONE_ID) {
            if (!ports.phoneSupported()) return false
        } else if (profile(id) == null || !ports.isReadable(id)) {
            return false
        }
        if (value) {
            if (store.commit(lane) { putBool(key(id), true) } !is Outcome.Ok) return false
            refresh()
        } else {
            if (store.commit(lane) { remove(key(id)) } !is Outcome.Ok) return false
            status.remove(id)
            readAt.remove(id)
            last.remove(id)
            widget?.clearForProfile(id)
        }
        return true
    }

    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (disposed) return
        if (this.foreground == foreground && this.backgroundAllowed == backgroundAllowed) return
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        epoch++
    }

    /** Reads every enabled source once. Nothing is read while runtime gating forbids it. */
    public suspend fun refresh() {
        if (!runningAllowed || targets().isEmpty()) return
        if (!refreshing.tryLock()) {
            refreshing.withLock {}
            return
        }
        try {
            for (id in targets()) {
                if (!runningAllowed) break
                read(id)
            }
        } finally {
            refreshing.unlock()
        }
    }

    private suspend fun read(id: String) {
        val start = epoch
        fun current() = !disposed && start == epoch && enabled(id)
        val isPhone = id == PHONE_ID
        val profile = if (isPhone) null else profile(id) ?: return
        status[id] = PaseoUsageStatus.Checking
        val readings: List<ProviderUsageReading>
        if (isPhone) {
            val outcome = ports.readPhone()
            if (outcome !is Outcome.Ok) {
                if (current()) status[id] = PaseoUsageStatus.Unavailable
                return
            }
            readings = outcome.value ?: run {
                if (current()) status[id] = PaseoUsageStatus.HostStopped
                return
            }
        } else {
            readings = ports.readServer(id).getOrNull() ?: run {
                if (current()) status[id] = PaseoUsageStatus.Unavailable
                return
            }
        }
        if (!current()) return
        status[id] = PaseoUsageStatus.Current
        readAt[id] = clock()
        last[id] = readings
        widget?.recordReadings(id, profile?.name ?: "", readings, thisPhone = isPhone)
        if (!current()) widget?.clearForProfile(id)
    }

    /** A saved server is being removed: stop reading it and clear its widget rows; the sweep removes the opt-in. */
    public suspend fun removeProfile(id: String) {
        blocked += id
        epoch++
        status.remove(id)
        last.remove(id)
        widget?.clearForProfile(id)
    }

    public fun cancelDeletion(id: String) {
        if (!disposed) blocked.remove(id)
    }

    public fun dispose() {
        disposed = true
        epoch++
    }
}
