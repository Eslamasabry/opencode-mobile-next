package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock

// ---- The route probe (no Android types: :android:platform implements it over ConnectivityManager's default network)

public enum class RouteStatus { Unknown, Offline, Online }

/** A VPN or any route the platform cannot name reads [Unknown]; never a mobile signal. */
public enum class RouteTransport { Unknown, Wifi, Mobile, Other }

/** The app's default network now, not the reachability of one server (Flutter `NetworkReading`). */
public data class NetworkRoute(
    val status: RouteStatus = RouteStatus.Unknown,
    val transport: RouteTransport = RouteTransport.Unknown,
    val metered: Boolean? = null,
    val connected: Boolean? = null,
) {
    public val hasNoNetwork: Boolean get() = connected == false

    public companion object {
        public val Unknown: NetworkRoute = NetworkRoute()
    }
}

/** Read fresh at every request: a cached UI snapshot must never authorise a retry. */
public fun interface NetworkRouteProbe {
    public suspend fun current(): Outcome<NetworkRoute>
}

// ---- Download sizes with provenance (Flutter `DownloadSize`)

public enum class DownloadSizeKind { Unknown, Exact, LowerBound, Estimate }

public data class DownloadSize(val kind: DownloadSizeKind, val bytes: Long?) {
    public companion object {
        public val Unknown: DownloadSize = DownloadSize(DownloadSizeKind.Unknown, null)
        public fun exact(bytes: Long): DownloadSize = DownloadSize(DownloadSizeKind.Exact, bytes)
        public fun lowerBound(bytes: Long): DownloadSize = DownloadSize(DownloadSizeKind.LowerBound, bytes)
        public fun estimate(bytes: Long): DownloadSize = DownloadSize(DownloadSizeKind.Estimate, bytes)
    }
}

public enum class MobileDownloadDecisionKind { Allowed, NeedsConsent, Blocked, Unavailable }

public enum class MobileDownloadReason {
    NonMobile, BelowThreshold, Accepted, FirstOffer, Offline, Declined, Unfinished, NetworkUnknown, SizeUnknown,
    StorageUnavailable, Closed,
}

public data class MobileDownloadDecision(val kind: MobileDownloadDecisionKind, val reason: MobileDownloadReason) {
    public val allowed: Boolean get() = kind == MobileDownloadDecisionKind.Allowed
}

/** [value] is null when the operation was not started. */
public data class MobileDownloadResult<out T>(val decision: MobileDownloadDecision, val value: Outcome<T>?)

/**
 * Large downloads on mobile data (B-STATE-030/031, B-SEC-013), `oc.mobileDownloadConsent.<id>` =
 * `{"version":1,"choice":"<name>"}`. One instance serves every saved profile.
 *
 * A start gate only: an automatic offer is claimed on disk before it is returned, so overlapping callers and restarts
 * never repeat it. Unknown routes, VPNs, failed probes and sizes without proof never run and never claim. Callers
 * cancel or pause a running transfer themselves when the policy changes.
 */
public class MobileDownloadConsentStore(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val savedProfiles: SavedProfiles = SavedProfiles.from(store),
) : ConsentStore {

    private class Entry(val choice: ConsentChoice, val writable: Boolean)

    private val entries = AtomicReference<PersistentMap<String, Entry>>(persistentHashMapOf())
    private val closed = AtomicReference<PersistentSet<String>>(persistentHashSetOf())
    private data class Request(val generation: Long, val revoked: Boolean)
    private val requests = AtomicReference<Map<String, Request>>(emptyMap())
    private val mutexes = ProfileMutexes()
    private val changes = Revision()
    private val log = AppLog.tagged("MobileDownloadConsent")

    override val topics: Set<ConsentTopic> = setOf(ConsentTopic.MobileDownload)
    override val revision: StateFlow<Long> = changes.flow

    override fun read(profileId: ProfileId, topic: ConsentTopic): ConsentRead =
        when {
            topic != ConsentTopic.MobileDownload -> ConsentRead.Unknown
            requests.load()[profileId.raw]?.revoked == true -> ConsentRead.Refused
            !storageAvailable(profileId) -> ConsentRead.Unknown
            else -> choice(profileId)?.read ?: ConsentRead.Unknown
        }

    /** The stored answer; null while the profile is closed, absent, or its record unreadable. */
    public fun choice(profileId: ProfileId): ConsentChoice? = if (!present(profileId)) null else entry(profileId)?.choice

    /** False after a refused write, for a corrupt record, and for a closed or removed profile. */
    public fun storageAvailable(profileId: ProfileId): Boolean =
        present(profileId) && entry(profileId)?.writable == true

    /** Claims the automatic offer once, before the UI shows it. Pass the live probe, never a cached snapshot. */
    public suspend fun request(profileId: ProfileId, size: DownloadSize, probe: NetworkRouteProbe): MobileDownloadDecision =
        mutexes.of(profileId).withLock { decide(profileId, size, probe) }

    /**
     * Runs [operation] only after a fresh authorisation. After a prompt, await [answer], then call this again:
     * accepting never starts work. The operation starts while the profile's queue is held (so a queued revocation
     * precedes it) but the queue is not held for the transfer.
     */
    public suspend fun <T> run(
        profileId: ProfileId,
        size: DownloadSize,
        probe: NetworkRouteProbe,
        operation: suspend () -> Outcome<T>,
    ): MobileDownloadResult<T> = coroutineScope {
        val started: Pair<MobileDownloadDecision, Deferred<Outcome<T>>?> = mutexes.of(profileId).withLock {
            val decision = decide(profileId, size, probe)
            when {
                !decision.allowed -> decision to null
                !present(profileId) -> unavailable(MobileDownloadReason.Closed) to null
                else -> decision to async(start = CoroutineStart.UNDISPATCHED) { operation() }
            }
        }
        MobileDownloadResult(started.first, started.second?.await())
    }

    /** The person's answer to the claimed offer. Refused with `AlreadyAnswered` when no offer is waiting. */
    public suspend fun answer(profileId: ProfileId, allow: Boolean): Outcome<Unit> =
        mutexes.of(profileId).withLock {
            val entry = writableEntry(profileId) ?: return@withLock Outcome.Failed(unwritable(profileId))
            if (entry.choice != ConsentChoice.Offered) return@withLock Outcome.Failed(Failure.Refused(RefusalReason.AlreadyAnswered))
            save(profileId, if (allow) ConsentChoice.Accepted else ConsentChoice.Denied)
        }

    /** Settings may revise any answer. */
    public suspend fun changeFromSettings(profileId: ProfileId, allow: Boolean): Outcome<Unit> {
        var generation = 0L
        update(requests) { current ->
            generation = (current[profileId.raw]?.generation ?: 0) + 1
            current + (profileId.raw to Request(generation, !allow || current[profileId.raw]?.revoked == true))
        }
        // B-SEC-013 / SI-22: withdraw the grant before waiting for the writer or storage.
        changes.bump()
        return mutexes.of(profileId).withLock {
            writableEntry(profileId) ?: return@withLock Outcome.Failed(unwritable(profileId))
            val written = save(profileId, if (allow) ConsentChoice.Accepted else ConsentChoice.Denied)
            if (written is Outcome.Ok) {
                update(requests) { current ->
                    if (current[profileId.raw]?.generation == generation) current + (profileId.raw to Request(generation, !allow)) else current
                }
                changes.bump()
            }
            written
        }
    }

    override fun closeProfile(profileId: ProfileId) {
        update(closed) { it.adding(profileId.raw) }
        update(entries) { it.removing(profileId.raw) }
        changes.bump()
    }

    /**
     * Only after a deletion that ABORTED with the profile kept: reopens with a fresh snapshot of what storage holds.
     * Returns false (and stays closed) when the profile is no longer saved or its list is unreadable.
     */
    public fun reopenProfile(profileId: ProfileId): Boolean {
        if (!savedProfiles.contains(profileId)) return false
        update(entries) { it.removing(profileId.raw) }
        update(closed) { it.removing(profileId.raw) }
        changes.bump()
        return true
    }

    override suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit> {
        closeProfile(profileId)
        // Drain: every command admitted before the close finishes (a pending probe returns Closed); a transfer is not waited for.
        return mutexes.of(profileId).withLock {
            val key = ConsentKeys.mobileDownload(profileId)
            if (store.get(key) == null) Outcome.Ok(Unit) else store.commit(lanes.app) { remove(key) }
        }
    }

    // ---- internals (all run under the profile's mutex except the reads)

    private suspend fun decide(profileId: ProfileId, size: DownloadSize, probe: NetworkRouteProbe): MobileDownloadDecision {
        if (!present(profileId)) return unavailable(MobileDownloadReason.Closed)
        val entry = entry(profileId)
        if (entry == null || !entry.writable) return unavailable(MobileDownloadReason.StorageUnavailable)
        val route = when (val read = attempt({ Failure.Device() }) { probe.current() }) {
            is Outcome.Failed -> return unavailable(MobileDownloadReason.NetworkUnknown)
            is Outcome.Ok -> when (val inner = read.value) {
                is Outcome.Failed -> return unavailable(MobileDownloadReason.NetworkUnknown)
                is Outcome.Ok -> inner.value
            }
        }
        if (!present(profileId)) return unavailable(MobileDownloadReason.Closed)
        if (route.hasNoNetwork || route.status == RouteStatus.Offline) {
            return MobileDownloadDecision(MobileDownloadDecisionKind.Blocked, MobileDownloadReason.Offline)
        }
        if (route.status != RouteStatus.Online || route.transport == RouteTransport.Unknown) {
            return unavailable(MobileDownloadReason.NetworkUnknown)
        }
        if (route.transport != RouteTransport.Mobile) {
            return MobileDownloadDecision(MobileDownloadDecisionKind.Allowed, MobileDownloadReason.NonMobile)
        }
        val bytes = size.bytes
        if (bytes == null || bytes < 0 || size.kind == DownloadSizeKind.Unknown || size.kind == DownloadSizeKind.Estimate ||
            (size.kind == DownloadSizeKind.LowerBound && bytes <= THRESHOLD_BYTES)
        ) {
            return unavailable(MobileDownloadReason.SizeUnknown)
        }
        if (bytes <= THRESHOLD_BYTES) {
            return MobileDownloadDecision(MobileDownloadDecisionKind.Allowed, MobileDownloadReason.BelowThreshold)
        }
        if (!storageAvailable(profileId)) return unavailable(MobileDownloadReason.StorageUnavailable)
        if (requests.load()[profileId.raw]?.revoked == true) {
            return MobileDownloadDecision(MobileDownloadDecisionKind.Blocked, MobileDownloadReason.Declined)
        }
        return when (entry(profileId)?.choice) {
            ConsentChoice.Accepted -> MobileDownloadDecision(MobileDownloadDecisionKind.Allowed, MobileDownloadReason.Accepted)
            ConsentChoice.Denied -> MobileDownloadDecision(MobileDownloadDecisionKind.Blocked, MobileDownloadReason.Declined)
            ConsentChoice.Offered -> MobileDownloadDecision(MobileDownloadDecisionKind.Blocked, MobileDownloadReason.Unfinished)
            ConsentChoice.Unseen -> when (save(profileId, ConsentChoice.Offered)) {
                is Outcome.Failed -> unavailable(
                    if (present(profileId)) MobileDownloadReason.StorageUnavailable else MobileDownloadReason.Closed,
                )
                is Outcome.Ok ->
                    if (!present(profileId)) {
                        unavailable(MobileDownloadReason.Closed)
                    } else {
                        MobileDownloadDecision(MobileDownloadDecisionKind.NeedsConsent, MobileDownloadReason.FirstOffer)
                    }
            }
            null -> unavailable(MobileDownloadReason.Closed)
        }
    }

    private suspend fun save(profileId: ProfileId, choice: ConsentChoice): Outcome<Unit> {
        if (!present(profileId)) return Outcome.Failed(closing())
        val encoded = DartJson.encode(linkedMapOf("version" to 1L, "choice" to choice.wire))
        val written = store.commit(lanes.forProfile(profileId)) { putString(ConsentKeys.mobileDownload(profileId), encoded) }
        when (written) {
            is Outcome.Ok -> if (!isClosed(profileId)) update(entries) { it.putting(profileId.raw, Entry(choice, writable = true)) }
            is Outcome.Failed -> {
                log.w { "consent write refused; storage marked unavailable" }
                if (!isClosed(profileId)) update(entries) { it.putting(profileId.raw, Entry(it[profileId.raw]?.choice ?: ConsentChoice.Unseen, false)) }
            }
        }
        changes.bump()
        return written
    }

    private fun writableEntry(profileId: ProfileId): Entry? =
        if (!present(profileId)) null else entry(profileId)?.takeIf { it.writable }

    private fun unwritable(profileId: ProfileId): Failure = if (!present(profileId)) closing() else refusedWrite()

    private fun isClosed(profileId: ProfileId): Boolean = profileId.raw in closed.load()

    /** Open, opaque and still in `oc.profiles`. */
    private fun present(profileId: ProfileId): Boolean =
        !isClosed(profileId) && profileId.isOpaque() && savedProfiles.contains(profileId)

    /** The cached entry, loaded on first use. Null for a corrupt record (not cached: the next read tries again). */
    private fun entry(profileId: ProfileId): Entry? {
        entries.load()[profileId.raw]?.let { return it }
        val choice = load(profileId) ?: return null
        update(entries) { if (it.containsKey(profileId.raw) || isClosed(profileId)) it else it.putting(profileId.raw, Entry(choice, true)) }
        return entries.load()[profileId.raw] ?: Entry(choice, true)
    }

    private fun load(profileId: ProfileId): ConsentChoice? {
        val raw = store.get(ConsentKeys.mobileDownload(profileId)) ?: return ConsentChoice.Unseen
        val text = (raw as? PrefValue.Str)?.value ?: return null
        if (text.length > MAX_RECORD) return null
        val value = DartJson.decodeOrNull(text) as? Map<*, *> ?: return null
        if (!isVersionOne(value["version"])) return null
        return ConsentChoice.fromWire(value["choice"])
    }

    private fun unavailable(reason: MobileDownloadReason) = MobileDownloadDecision(MobileDownloadDecisionKind.Unavailable, reason)

    public companion object {
        /** Strictly more than this many bytes asks on mobile data. */
        public const val THRESHOLD_BYTES: Long = 50_000_000
        private const val MAX_RECORD = 512
    }
}

internal inline fun <T> update(ref: AtomicReference<T>, change: (T) -> T) {
    while (true) {
        val current = ref.load()
        if (ref.compareAndSet(current, change(current))) return
    }
}
