package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the app remembers about one tip or tour. Replays from Help › Tips are not counted. */
public data class TipRecord(val count: Int = 0, val lastShown: Instant? = null, val done: Boolean = false)

/** Why a tip may not show right now, as the host sees its own screen. */
public data class TipMoment(
    /** A question or approval card waits for the person. */
    val questionWaiting: Boolean = false,
    /** The keyboard is up over the control the tip points at. */
    val keyboardOverAnchor: Boolean = false,
    /** An onboarding step is on screen (or the first reply has not arrived). */
    val onboarding: Boolean = false,
) {
    public val blocked: Boolean get() = questionWaiting || keyboardOverAnchor || onboarding
}

/** One screen visit: at most one NEW tip per visit. Create one per visit (its ViewModel or entry), never share. */
public class TipVisit {
    internal val hadTip = AtomicBoolean(false)
}

/** The tip or tour on screen now. */
public data class ActiveTip(val id: TipId, val replay: Boolean, val priority: Int)

/**
 * The teaching system's rules in one place (plan 06 §7.2, docs/ux-system/teaching.md; B-NAV-038, B-STATE-142):
 * - a tip shows at most `maxCount` times (default 2) and never again once used ([markUsed]);
 * - one tip at a time app-wide (a higher priority replaces a lower one; a refusal consumes nothing), with a quiet
 *   [COOLDOWN] between tips, and at most one new tip per [TipVisit];
 * - never while a question waits, the keyboard covers the anchor or onboarding runs ([TipMoment]);
 * - stored app-wide as `oc.tips` = `{"<id>":{"count":n,"last":"<ISO-8601 UTC>","done":true}}` (Flutter format,
 *   read unchanged); unreadable state means nothing was shown; [reset] is "Show tips again".
 *
 * Decisions are synchronous and in memory; the record is persisted in [scope] through the app lane (a failed write
 * can at worst repeat a tip after a restart).
 */
public class TipController(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private class State(
        val records: PersistentMap<String, TipRecord>,
        val active: ActiveTip?,
        val lastShownAny: Instant?,
    )

    private val state = AtomicReference<State?>(null)
    private val mutableActive = MutableStateFlow<ActiveTip?>(null)
    private val writes = Mutex()
    private val log = AppLog.tagged("Tips")

    /** The tip or tour on screen now, or null. */
    public val active: StateFlow<ActiveTip?> = mutableActive.asStateFlow()

    public fun isActive(id: TipId): Boolean = mutableActive.value?.id == id

    public fun record(id: TipId): TipRecord = loaded().records[id.raw] ?: TipRecord()

    /** Whether [id] could still show at a future moment. */
    public fun due(id: TipId, maxCount: Int = DEFAULT_MAX_COUNT): Boolean {
        val record = record(id)
        return !record.done && record.count < maxCount
    }

    /**
     * Shows [id] when the rules allow it (`shouldShow` + `recordShown` of plan 03 §11.2 as ONE atomic step, so two
     * screens can never both win). Returns whether it is on screen now.
     */
    public fun offer(
        id: TipId,
        visit: TipVisit? = null,
        maxCount: Int = DEFAULT_MAX_COUNT,
        priority: Int = 0,
        moment: TipMoment = TipMoment(),
    ): Boolean {
        while (true) {
            val s = loaded()
            val current = s.active
            // Asked again while it shows (a rebuild): still showing. A replay is not its moment.
            if (current?.id == id) return !current.replay
            if (moment.blocked) return false
            val record = s.records[id.raw] ?: TipRecord()
            if (record.done || record.count >= maxCount) return false
            if (visit != null && visit.hadTip.load()) return false
            if (current != null && (current.replay || priority <= current.priority)) return false
            val now = clock.now()
            val last = s.lastShownAny
            if (current == null && last != null && now - last < COOLDOWN) return false
            val next = State(
                records = s.records.putting(id.raw, record.copy(count = record.count + 1, lastShown = now)),
                active = ActiveTip(id, replay = false, priority = priority),
                lastShownAny = now,
            )
            if (!state.compareAndSet(s, next)) continue
            if (visit != null && !visit.hadTip.compareAndSet(expectedValue = false, newValue = true)) {
                // Another tip won this visit in the meantime: give the slot back, the count stands (it was decided).
                release(id)
                return false
            }
            mutableActive.value = next.active
            persist()
            return true
        }
    }

    /** "Show me" in Help › Tips: shows [id] now whatever the rules say, and counts nothing. */
    public fun replay(id: TipId) {
        change { State(it.records, ActiveTip(id, replay = true, priority = Int.MAX_VALUE), it.lastShownAny) }
    }

    /** The tip left the screen (faded, touched away, its place closed). It still counts as shown. */
    public fun dismiss(id: TipId) {
        release(id)
    }

    /** The person used what [id] teaches (or finished or left its tour): never again, and it leaves the screen. */
    public fun markUsed(id: TipId) {
        var wrote = false
        change { s ->
            val record = s.records[id.raw] ?: TipRecord()
            wrote = !record.done
            State(
                records = if (record.done) s.records else s.records.putting(id.raw, record.copy(done = true)),
                active = if (s.active?.id == id) null else s.active,
                lastShownAny = s.lastShownAny,
            )
        }
        if (wrote) persist()
    }

    /** "Show tips again": every tip and tour may show again at its moment. */
    public suspend fun reset(): Outcome<Unit> {
        change { State(persistentMapOf(), null, null) }
        return writes.withLock { store.commit(lanes.app) { remove(STORAGE_KEY) } }
    }

    private fun release(id: TipId) {
        change { s -> if (s.active?.id != id) s else State(s.records, null, s.lastShownAny) }
    }

    private inline fun change(next: (State) -> State) {
        while (true) {
            val s = loaded()
            val updated = next(s)
            if (state.compareAndSet(s, updated)) {
                mutableActive.value = updated.active
                return
            }
        }
    }

    /** Writes the latest records (never an older snapshot: each write reads the state when it runs). */
    private fun persist() {
        scope.launch {
            writes.withLock {
                val records = loaded().records
                // Nothing recorded is stored as no key (a write queued before a reset cannot leave `{}` behind).
                val written = store.commit(lanes.app) { if (records.isEmpty()) remove(STORAGE_KEY) else putString(STORAGE_KEY, encode(records)) }
                if (written is Outcome.Failed) log.w { "tip record not saved; a tip may repeat after a restart" }
            }
        }
    }

    private fun loaded(): State {
        state.load()?.let { return it }
        state.compareAndSet(null, State(decode(store.string(STORAGE_KEY)), null, null))
        return state.load()!!
    }

    public companion object {
        public const val STORAGE_KEY: String = "oc.tips"
        public const val DEFAULT_MAX_COUNT: Int = 2

        /** The quiet gap after one tip before another may show. */
        public val COOLDOWN: Duration = 2.minutes

        internal fun decode(text: String?): PersistentMap<String, TipRecord> {
            val value = text?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return persistentMapOf()
            var records = persistentMapOf<String, TipRecord>()
            for ((key, data) in value) records = records.putting(key.toString(), recordOf(data))
            return records
        }

        private fun recordOf(value: Any?): TipRecord {
            if (value !is Map<*, *>) return TipRecord()
            val count = (value["count"] as? Long)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
            return TipRecord(count, (value["last"] as? String)?.let(Instant::parseOrNull), value["done"] == true)
        }

        internal fun encode(records: Map<String, TipRecord>): String =
            DartJson.encode(
                records.entries.associateTo(LinkedHashMap()) { (id, r) ->
                    id to LinkedHashMap<String, Any>().apply {
                        put("count", r.count.toLong())
                        r.lastShown?.let { put("last", DartJson.iso8601Utc(it.epochSeconds * 1_000_000 + it.nanosecondsOfSecond / 1_000)) }
                        if (r.done) put("done", true)
                    }
                },
            )
    }
}
