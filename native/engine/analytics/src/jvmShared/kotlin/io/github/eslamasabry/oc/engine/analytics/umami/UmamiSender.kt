package io.github.eslamasabry.oc.engine.analytics.umami

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable

/** A prepared request can be cancelled before execute, including when consent is withdrawn. */
public interface UmamiCall {
    public suspend fun execute(): Outcome<Set<Int>>
    public fun cancel()
}

public fun interface UmamiTransport {
    public fun prepare(events: List<UmamiEvent>): UmamiCall
}

/** All calls run on the sender's worker, never on the caller (Android's main thread). */
public interface UmamiStore {
    public suspend fun read(): Outcome<String?>
    public suspend fun replace(value: String?): Outcome<Unit>
}

@Serializable
internal data class StoredEvent(val ordinal: Long, val atMs: Long, val event: UmamiEvent) {
    @kotlinx.serialization.Transient
    val wireBytes: Int = UmamiCodec.json.encodeToString(event).toByteArray(Charsets.UTF_8).size
}

@Serializable
internal data class StoredOutbox(
    val events: List<StoredEvent> = emptyList(),
    val retryAtMs: Long = 0,
    val failures: Int = 0,
    val format: Int = 1,
)

/**
 * One bounded outbox, with one coalesced worker task. No alarms, jobs, retry timer or network observer.
 * Only normal app use (event / consent) wakes it. Disk and HTTP stay off the caller. Consent changes cancel an
 * in-flight call and invalidate every older snapshot. A revoked generation is never restored or retried.
 */
public class UmamiSender(
    private val website: String,
    private val build: UmamiBuild,
    private val device: () -> UmamiDevice,
    private val now: () -> Long,
    private val store: UmamiStore,
    private val transport: UmamiTransport?,
    private val maxBytes: Int = MAX_BYTES,
    private val executor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "oc-usage").apply { isDaemon = true }
    },
) {
    init { require(maxBytes in 1..MAX_BYTES) }

    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val lock = Any()
    private var enabled = false
    private var generation = 0L
    private var restore = true
    private var dirty = false
    private var worker = false
    private var active: UmamiCall? = null
    private var retryAtMs = 0L
    private var failures = 0
    private var nextOrdinal = 0L
    private var opened = false
    private var page = umamiPages.getValue("conversations")
    private val queue = ArrayDeque<StoredEvent>()

    public val queued: Int get() = synchronized(lock) { queue.size }
    public val queuedBytes: Int get() = synchronized(lock) { bytes(queue.toList()) }

    public fun setUsage(on: Boolean) {
        synchronized(lock) {
            enabled = on && transport != null && website.isNotBlank()
            if (!enabled) {
                generation++
                restore = false
                queue.clear()
                retryAtMs = 0
                failures = 0
                opened = false
                page = umamiPages.getValue("conversations")
                active?.cancel()
            }
            dirty = true
            schedule()
        }
    }

    public fun event(name: String, props: Map<String, String>) {
        synchronized(lock) {
            if (!enabled) return
            val at = now()
            val details = device()
            val source = UmamiSource(website, build, details)
            val mapped = umamiEvent(name, props, page, source, at) ?: return
            if (name == "screen_view") page = umamiPages.getValue(mapped.payload.data.getValue("screen"))
            // First consent may happen after coldStart. Establish its session without replaying pre-consent events.
            if (!opened) {
                opened = true
                if (name != "app_open") {
                    append(umamiEvent("app_open", mapOf("kind" to "cold"), page, source, at)!!, at)
                }
            }
            append(mapped, at)
            dirty = true
            schedule()
        }
    }

    private fun append(event: UmamiEvent, at: Long) {
        queue.addLast(StoredEvent(nextOrdinal++, at, event))
        trim(now())
    }

    private fun trim(at: Long) {
        var lostOpening = false
        val kept = queue.filter { item ->
            val expired = at - item.atMs > MAX_AGE_MS || item.atMs > at + CLOCK_SKEW_MS
            if (item.event.payload.name == "app_open") lostOpening = expired
            !expired && !lostOpening
        }
        queue.clear()
        queue.addAll(kept)
        while (queue.size > MAX_EVENTS || bytes(queue.toList()) > maxBytes) {
            // Never leave the tail of an unsent session after discarding its opening. Prefer dropping a whole old
            // session; for one oversized session preserve its opening and discard the oldest remaining event.
            if (queue.first().event.payload.name == "app_open") {
                val next = queue.withIndex().firstOrNull { it.index > 0 && it.value.event.payload.name == "app_open" }?.index
                if (next != null) repeat(next) { queue.removeFirst() }
                else if (queue.size > 1) queue.removeAt(1)
                else queue.removeFirst()
            } else queue.removeFirst()
        }
        if (lostOpening && queue.isEmpty()) opened = false
    }

    private fun schedule() {
        if (worker) return
        worker = true
        scope.launch { drain() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun drain() {
        try {
            restoreOnce()
            while (true) {
                val snapshot: StoredOutbox
                val epoch: Long
                val sending: Boolean
                synchronized(lock) {
                    if (!dirty) { worker = false; return }
                    dirty = false
                    trim(now())
                    epoch = generation
                    snapshot = StoredOutbox(queue.toList(), retryAtMs, failures)
                    sending = enabled
                }
                if (store.replace(if (sending) UmamiCodec.json.encodeToString(snapshot) else null) is Outcome.Failed) {
                    synchronized(lock) { worker = false }
                    return
                }
                val ready = synchronized(lock) {
                    enabled && epoch == generation && snapshot.events.isNotEmpty() && now() >= retryAtMs
                }
                if (!ready) continue
                // Batch JSON is encoded on the worker without holding the producer lock.
                val call = transport?.prepare(snapshot.events.map { it.event }) ?: continue
                val allowed = synchronized(lock) {
                    if (!enabled || epoch != generation) {
                        call.cancel()
                        false
                    } else {
                        active = call
                        true
                    }
                }
                if (!allowed) continue
                val accepted = call.execute().getOrNull()
                synchronized(lock) {
                    if (active === call) active = null
                    if (!enabled || epoch != generation) return@synchronized
                    // A batch can partly fail with HTTP 200. Never replay the accepted items.
                    val sent = accepted.orEmpty().mapNotNull { snapshot.events.getOrNull(it)?.ordinal }.toSet()
                    queue.removeAll { it.ordinal in sent }
                    if (accepted?.size == snapshot.events.size) {
                        failures = 0
                        retryAtMs = now() + MIN_SEND_GAP_MS
                    } else {
                        failures = (failures + 1).coerceAtMost(MAX_FAILURES)
                        retryAtMs = now() + (FIRST_BACKOFF_MS * (1L shl (failures - 1))).coerceAtMost(MAX_BACKOFF_MS)
                    }
                    dirty = true // persist success/backoff; the next pass cannot send until new normal use after retryAt.
                }
            }
        } catch (cancelled: CancellationException) {
            synchronized(lock) { active?.cancel(); active = null; worker = false }
            throw cancelled
        } catch (_: Exception) {
            // Disk/codec problems do not escape into the app or schedule a retry wake-up.
            synchronized(lock) { worker = false }
        }
    }

    private suspend fun restoreOnce() {
        val epoch = synchronized(lock) {
            if (!restore) return
            restore = false
            generation
        }
        val old = attempt({ Failure.UnreadableReply() }) {
            store.read().getOrNull()?.takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_STORE_BYTES }
                ?.let { UmamiCodec.json.decodeFromString<StoredOutbox>(it) }
        }.getOrNull() ?: return
        if (old.format != 1) return
        // Parsing, schema checks and per-record JSON sizing happen entirely on the worker, outside the lock.
        val restored = ArrayList<StoredEvent>()
        for ((index, item) in old.events.takeLast(MAX_EVENTS).withIndex()) {
            val p = item.event.payload
            val knownPage = umamiPages.values.firstOrNull { it.url == p.url } ?: continue
            val oldBuild = try {
                UmamiBuild(p.data["version"] ?: continue, p.data["build"] ?: continue, p.tag)
            } catch (_: IllegalArgumentException) { continue }
            val ownKeys = setOf("version", "build") + umamiDeviceKeys
            // The envelope an event was queued with stays with it; only listed keys and words survive the restore.
            val queuedFacts = UmamiField.clean(p.data.filterKeys { it in umamiDeviceKeys })
            val queuedWith = device().copy(language = p.language, facts = queuedFacts)
            val mapped = umamiEvent(p.name ?: "screen_view", p.data - ownKeys, knownPage,
                UmamiSource(website, oldBuild, queuedWith), item.atMs) ?: continue
            // Restored ordinals are negative; new producer ordinals are nonnegative. Nothing is sent from this field.
            restored += StoredEvent(-(index.toLong() + 1), item.atMs, mapped)
        }
        synchronized(lock) {
            if (!enabled || epoch != generation) return
            val pending = queue.toList()
            queue.clear()
            queue.addAll(restored)
            queue.addAll(pending)
            trim(now())
            failures = old.failures.coerceIn(0, MAX_FAILURES)
            retryAtMs = old.retryAtMs.coerceAtMost(now() + MAX_BACKOFF_MS)
            dirty = true
        }
    }

    private fun bytes(events: List<StoredEvent>): Int = 2 + events.sumOf { it.wireBytes + 1 }

    public companion object {
        public const val MAX_EVENTS: Int = 256
        /** Encoded wire JSON bytes; disk framing is separately bounded to 300 KiB. */
        public const val MAX_BYTES: Int = 256 * 1024
        public const val MAX_STORE_BYTES: Int = 300 * 1024
        public const val MAX_AGE_MS: Long = 3 * 24 * 60 * 60_000L
        public const val FIRST_BACKOFF_MS: Long = 30_000L
        public const val MAX_BACKOFF_MS: Long = 6 * 60 * 60_000L
        public const val MIN_SEND_GAP_MS: Long = 5_000L
        private const val MAX_FAILURES: Int = 16
        private const val CLOCK_SKEW_MS: Long = 60_000L
    }
}

/** One fan-out at the existing vendor boundary; primary alone receives crashes, reports, traces and properties. */
public class UsageTelemetryVendor(
    private val primary: TelemetryVendor,
    private val sender: UmamiSender?,
    private val build: UmamiBuild,
) : TelemetryVendor by primary {
    private var buildSet = false
    private var usageOn = false
    private var sessionOpened = false
    private var syntheticOpening = false

    @Synchronized
    override fun setCollection(usage: Boolean, crashes: Boolean) {
        primary.setCollection(usage, crashes)
        usageOn = usage
        if (!usage) { sessionOpened = false; syntheticOpening = false }
        sender?.setUsage(usage)
        if (usage && !buildSet) {
            primary.setUserProperties(mapOf("app_build" to build.build))
            buildSet = true
        }
        if (!usage) buildSet = false // primary clears app_build together with its analytics data.
    }

    @Synchronized
    override fun event(name: String, props: Map<String, String>) {
        if (!usageOn) return
        if (syntheticOpening && name == "app_open" && props["kind"] == "cold") {
            syntheticOpening = false
            return
        }
        if (name == "app_open") syntheticOpening = false
        if (!sessionOpened) {
            sessionOpened = true
            if (name != "app_open") {
                syntheticOpening = true
                sendEvent("app_open", mapOf("kind" to "cold"))
            }
        }
        sendEvent(name, props)
    }

    private fun sendEvent(name: String, props: Map<String, String>) {
        primary.event(name, props)
        sender?.event(name, props)
    }
}
