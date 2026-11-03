package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** One window a Paseo daemon reports (Flutter `ProviderUsageWindowReading`); display numbers only. */
public data class ProviderUsageWindowReading(
    val durationSeconds: Long?,
    val label: String,
    val remainingPercent: Int,
    val resetsAtMs: Long?,
)

/** One provider a Paseo daemon reports (Flutter `ProviderUsageReading`). [needsSignIn]: reported as not signed in. */
public data class ProviderUsageReading(
    val providerId: String,
    val displayName: String,
    val fetchedAtMs: Long,
    val windows: List<ProviderUsageWindowReading>,
    val needsSignIn: Boolean = false,
)

/** PORT. One bounded `provider.usage.list` read on a saved Paseo server. */
public interface ProviderUsageGateway {
    public suspend fun read(): Outcome<List<ProviderUsageReading>>

    public suspend fun close()
}

public enum class PaseoUsageStatus {
    Off, Paused, Waiting,

    /** This phone's agent host is not running; usage is never read by starting it. */
    HostStopped,
    Checking, Current, Unavailable,
}

/**
 * Reads plan usage from opted-in Paseo servers and this phone's agent host (Flutter `PaseoUsageMonitor`, reading
 * part). Opt-in is per server in `oc.paseoUsage.<profileId>`; with [autoRead] no switch is needed. The phone host is
 * read only through [phoneRead], which returns null when the host is not running and must never start it.
 *
 * Not ported here: the monitor's own 5/15-minute timer and the widget write (the integrator drives [refresh] from the
 * app's pacer and the widget module).
 */
public class PaseoUsageMonitor(
    private val switches: UsageSwitches,
    private val servers: () -> List<UsageServer>,
    private val createGateway: (UsageServer) -> ProviderUsageGateway,
    private val isReadable: (String) -> Boolean,
    private val clock: () -> Long,
    private val phoneSupported: () -> Boolean = { false },
    private val phoneRead: (suspend () -> List<ProviderUsageReading>?)? = null,
    private val autoRead: Boolean = false,
    private val timeoutMs: Long = 12_000L,
) {
    private val status = HashMap<String, PaseoUsageStatus>()
    private val blocked = HashSet<String>()
    private val readAt = HashMap<String, Long>()
    private val last = HashMap<String, List<ProviderUsageReading>>()
    private var refreshing: CompletableDeferred<Unit>? = null
    private var foreground = true
    private var backgroundAllowed = false
    private var epoch = 0

    public val runningAllowed: Boolean get() = foreground || backgroundAllowed

    private fun server(id: String): UsageServer? = servers().firstOrNull { it.id == id }

    private fun readable(id: String): Boolean {
        if (id == PHONE_ID) return id !in blocked && phoneSupported() && phoneRead != null
        val s = server(id) ?: return false
        return s.backend == BackendKind.Paseo && id !in blocked && isReadable(id)
    }

    /** Whether [id] is read: always when it can be with [autoRead], else only after the per-server opt-in. */
    public fun enabled(id: String): Boolean = readable(id) && (autoRead || switches.bool(key(id)) == true)

    private val targets: List<String>
        get() = buildList {
            if (enabled(PHONE_ID)) add(PHONE_ID)
            servers().filter { it.backend == BackendKind.Paseo && enabled(it.id) }.forEach { add(it.id) }
        }

    /** What [id] reported on its last answer, or null when it has not answered or is not read. */
    public fun readingsFor(id: String): List<ProviderUsageReading>? = if (enabled(id)) last[id] else null

    public fun lastReadAtMs(id: String): Long? = readAt[id]

    public fun statusFor(id: String): PaseoUsageStatus = when {
        !enabled(id) -> PaseoUsageStatus.Off
        !runningAllowed -> PaseoUsageStatus.Paused
        else -> status[id] ?: PaseoUsageStatus.Waiting
    }

    public suspend fun setEnabled(id: String, value: Boolean): Boolean {
        if (id == PHONE_ID) {
            if (id in blocked || !phoneSupported()) return false
        } else {
            val s = server(id)
            if (s == null || s.backend != BackendKind.Paseo || id in blocked || !isReadable(id)) return false
        }
        if (value) {
            if (!switches.putBool(key(id), true)) return false
        } else {
            if (!switches.remove(key(id))) return false
            status.remove(id)
            readAt.remove(id)
            last.remove(id)
        }
        return true
    }

    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (this.foreground == foreground && this.backgroundAllowed == backgroundAllowed) return
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        epoch++
    }

    /** Reads every target once; a refresh already running is joined, not repeated. */
    public suspend fun refresh() {
        if (!runningAllowed || targets.isEmpty()) return
        refreshing?.let { return it.await() }
        val done = CompletableDeferred<Unit>()
        refreshing = done
        try {
            for (id in targets) {
                if (!runningAllowed) break
                read(id)
            }
        } finally {
            refreshing = null
            done.complete(Unit)
        }
    }

    private suspend fun read(id: String) {
        val started = epoch
        fun current() = started == epoch && enabled(id)
        val isPhone = id == PHONE_ID
        val server = if (isPhone) null else server(id) ?: return
        status[id] = PaseoUsageStatus.Checking
        var gateway: ProviderUsageGateway? = null
        val outcome: Outcome<List<ProviderUsageReading>?> = attempt({ Failure.Unexpected() }) {
            withTimeoutOrNull(timeoutMs) {
                if (isPhone) {
                    Outcome.Ok(phoneRead!!.invoke())
                } else {
                    val opened = createGateway(server!!)
                    gateway = opened
                    opened.read()
                }
            } ?: Outcome.Failed(Failure.TimedOut())
        }.let { call ->
            when (call) {
                is Outcome.Ok -> call.value
                is Outcome.Failed -> call
            }
        }
        gateway?.let { opened -> attempt({ Failure.Unexpected() }) { opened.close() } }
        when (outcome) {
            is Outcome.Failed -> if (current()) status[id] = PaseoUsageStatus.Unavailable
            is Outcome.Ok -> {
                val readings = outcome.value
                if (readings == null) {
                    if (current()) status[id] = PaseoUsageStatus.HostStopped
                    return
                }
                if (!current()) return
                status[id] = PaseoUsageStatus.Current
                readAt[id] = clock()
                last[id] = readings
            }
        }
    }

    /** Deletion started: stop reading [id] and drop its numbers. */
    public fun removeProfile(id: String) {
        blocked += id
        epoch++
        status.remove(id)
        last.remove(id)
    }

    /** Reopens admission after an aborted removal. */
    public fun cancelDeletion(id: String) {
        blocked -= id
    }

    public companion object {
        /** The source id of this phone's built-in agent host (not a saved profile id). */
        public const val PHONE_ID: String = "phone"

        public fun key(profileId: String): String = "oc.paseoUsage.$profileId"
    }
}

/** Paseo usage as readings (Flutter `PaseoUsageSource`): this phone's agent host and the Paseo servers. */
public class PaseoUsageSource(
    private val monitor: PaseoUsageMonitor,
    private val paseoProfileIds: () -> List<String>,
) : UsageSource {
    override suspend fun read(context: UsageReadContext): UsageSourceResult {
        monitor.refresh()
        val readings = ArrayList<UsageReading>()
        val unreached = LinkedHashSet<String>()
        for (id in listOf(PaseoUsageMonitor.PHONE_ID) + paseoProfileIds()) {
            if (!monitor.enabled(id)) continue
            val status = monitor.statusFor(id)
            val list = monitor.readingsFor(id)
            // A host that did not answer keeps its cached numbers, marked offline.
            if (list == null || status == PaseoUsageStatus.HostStopped || status == PaseoUsageStatus.Unavailable) {
                unreached += id
                continue
            }
            for (reading in list) {
                if (reading.needsSignIn && reading.windows.isEmpty()) {
                    readings += UsageReading(
                        providerId = reading.providerId,
                        displayName = reading.displayName,
                        connectionId = id,
                        status = UsageReadingStatus.NotSignedIn,
                        fetchedAtMs = reading.fetchedAtMs,
                        method = SignInMethod.Plan,
                    )
                    continue
                }
                readings += UsageReading(
                    providerId = reading.providerId,
                    displayName = reading.displayName,
                    connectionId = id,
                    status = if (reading.windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
                    fetchedAtMs = reading.fetchedAtMs,
                    method = SignInMethod.Plan,
                    windows = reading.windows.map { w ->
                        UsageWindow(
                            id = when (w.durationSeconds) {
                                18_000L -> "5h"
                                604_800L -> "weekly"
                                else -> w.label.ifEmpty { "window" }
                            },
                            usedPercent = 100.0 - w.remainingPercent,
                            durationSeconds = w.durationSeconds,
                            resetsAtMs = w.resetsAtMs,
                            label = w.label,
                        )
                    },
                )
            }
        }
        return UsageSourceResult(readings = readings, unreached = unreached)
    }
}
