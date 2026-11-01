package io.github.eslamasabry.oc.core.data.kv

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.GateState
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** `commit()` answered false. Only its class name reaches `Failure.details` (plan 03 §16.1 `StorageFailures`). */
internal class CommitRefused : Exception()

/**
 * [KeyValueStore] over a [PrefsBackend] holding Flutter's physical layout (`flutter.` prefix, Flutter encodings).
 * On Android the backend is the adopted `FlutterSharedPreferences` file (plan 05 §2.2).
 *
 * The store keeps its own immutable snapshot of ACKNOWLEDGED values. `SharedPreferencesImpl` updates its memory before
 * the disk write, so reading through it could show a refused value for a moment; reads here never do.
 *
 * Reads before the [gate] is Open go to [earlyReads] (plan 05 §2.2; review w1-data-oc2 MAJOR 5): debug, qa and test
 * builds throw [GateNotOpen], release counts the read in diagnostics and answers. The migrator, the crash-loop guard and
 * the receivers that must read earlier use [migrationReader].
 *
 * An admitted write finishes its platform commit, its snapshot update AND its [changes] publication in one
 * non-cancellable step, so a caller cancelled mid-commit cannot leave a persisted change unannounced (review
 * w1-data-oc2 MAJOR 3).
 */
public class PrefsKeyValueStore(
    private val backend: PrefsBackend,
    private val codec: FlutterPrefsCodec,
    private val gate: MigrationGate,
    private val io: CoroutineDispatcher,
    private val earlyReads: EarlyReadPolicy = EarlyReadPolicy.Throw,
) : KeyValueStore {

    private class Snapshot(val raw: PersistentMap<String, RawPref>, val values: PersistentMap<String, PrefValue>)

    private val snapshot = AtomicReference<Snapshot?>(null)

    /** The file-wide serialisation (audit codex-04+05 MAJOR 8): every mutation and its compensation hold it. */
    private val fileLock = Mutex()

    // B-STATE-004: listeners only see acknowledged values. Buffered so a writer is not held by a slow collector for the
    // first 64 change sets; after that the writer waits (never drops a change).
    private val mutableChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)
    override val changes: SharedFlow<Set<String>> = mutableChanges

    /** Plan 05 §7.1 step 7: load the file on IO before the first read needs it. */
    public suspend fun warm() {
        withContext(io) { loaded() }
    }

    override fun get(key: String): PrefValue? {
        checkOpen()
        return loaded().values[key]
    }

    override fun keys(prefix: String): Set<String> {
        checkOpen()
        return keysOf(prefix)
    }

    private fun keysOf(prefix: String): Set<String> = loaded().values.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }

    private fun checkOpen() {
        if (gate.state.value !is GateState.Open) earlyReads.onEarlyRead()
    }

    /**
     * Plan 05 §2.2 `MigrationReader`: the only reads allowed before the gate is Open (the migrator, the crash-loop guard,
     * the locale read, the restore receiver and the alarm re-arm; Konsist allowlist). The same acknowledged snapshot.
     */
    @MigrationOnly
    public fun migrationReader(): MigrationReader =
        object : MigrationReader {
            override fun get(key: String): PrefValue? = loaded().values[key]

            override fun keys(prefix: String): Set<String> = keysOf(prefix)
        }

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val ops = Recorder(codec).apply(edit)
        ops.refusal?.let { return Outcome.Failed(it) }
        gate.awaitWritable()
        return lane.write { apply(ops.changes) }
    }

    override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> {
        val encoded: RawPref? = next?.let { value ->
            if (FlutterPrefsCodec.clashes(value)) return Outcome.Failed(clash())
            codec.encodeString(value)
        }
        gate.awaitWritable()
        return lane.write {
            // B-STATE-004 / audit MAJOR 8: the comparison and the commit happen under the same file lock as commit().
            // Review w1-data-oc2 MAJOR 3: the publication is inside the same non-cancellable step as the commit.
            withContext(io + NonCancellable) {
                val result = fileLock.withLock {
                    val current = loaded().values[key]
                    val matches = if (expected == null) current == null else (current as? PrefValue.Str)?.value == expected
                    if (!matches) {
                        Outcome.Ok(false)
                    } else {
                        applyLocked(mapOf(FlutterPrefsCodec.physicalKey(key) to encoded)).map { true }
                    }
                }
                if (result is Outcome.Ok && result.value) mutableChanges.emit(setOf(key))
                result
            }
        }
    }

    override suspend fun compareAndCommit(lane: WriterLane, expected: Map<String, PrefValue?>, edit: PrefEdit.() -> Unit): Outcome<Boolean> {
        val ops = Recorder(codec).apply(edit)
        ops.refusal?.let { return Outcome.Failed(it) }
        gate.awaitWritable()
        return lane.write {
            // B-STATE-063 eng-4: compare, commit and publish are one admitted, non-cancellable operation.
            withContext(io + NonCancellable) {
                val result = fileLock.withLock {
                    if (expected.any { (key, value) -> loaded().values[key] != value }) Outcome.Ok(false)
                    else applyLocked(ops.changes.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }).map { true }
                }
                if (result is Outcome.Ok && result.value && ops.changes.isNotEmpty()) mutableChanges.emit(ops.changes.keys)
                result
            }
        }
    }

    /**
     * The migrator's write capability (plan 05 §2.2): it passes the gate (which it drives) and uses no lane, but holds
     * the same file lock as every other writer.
     */
    @MigrationOnly
    public fun migrationWriter(): MigrationWriter = MigrationWriter { edit ->
        val ops = Recorder(codec).apply(edit)
        ops.refusal?.let { Outcome.Failed(it) } ?: apply(ops.changes)
    }

    public fun interface MigrationWriter {
        public suspend fun commit(edit: PrefEdit.() -> Unit): Outcome<Unit>
    }

    private suspend fun apply(logical: Map<String, RawPref?>): Outcome<Unit> {
        if (logical.isEmpty()) return Outcome.Ok(Unit)
        // Plan 03 §17.5 rule 2 (B-STATE-004): an admitted write finishes its platform commit and publishes its change.
        // Commit, snapshot and publication are one non-cancellable step (review w1-data-oc2 MAJOR 3): returning from a
        // withContext to a cancelled caller throws, so nothing may be left for after it.
        return withContext(io + NonCancellable) {
            val result = fileLock.withLock { applyLocked(logical.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }) }
            if (result is Outcome.Ok) mutableChanges.emit(logical.keys)
            result
        }
    }

    /** Caller holds [fileLock]. */
    private suspend fun applyLocked(physical: Map<String, RawPref?>): Outcome<Unit> {
        val before = loaded()
        val previous = physical.keys.associateWith { before.raw[it] }
        val acknowledged = attempt(::storageFailure) { if (!backend.commit(physical)) throw CommitRefused() }
        if (acknowledged is Outcome.Failed) {
            // B-STATE-004/005: put the touched keys back (the platform's memory may already hold the refused values).
            // The snapshot never changed, so readers keep seeing the acknowledged values even if this fails too.
            attempt(::storageFailure) { backend.commit(previous) }
            return acknowledged
        }
        var raw = before.raw
        var values = before.values
        for ((physicalKey, value) in physical) {
            val logicalKey = FlutterPrefsCodec.logicalKey(physicalKey) ?: continue
            if (value == null) {
                raw = raw.removing(physicalKey)
                values = values.removing(logicalKey)
            } else {
                raw = raw.putting(physicalKey, value)
                val decoded = codec.decode(value).value
                values = if (decoded == null) values.removing(logicalKey) else values.putting(logicalKey, decoded)
            }
        }
        snapshot.store(Snapshot(raw, values))
        return Outcome.Ok(Unit)
    }

    private fun loaded(): Snapshot {
        snapshot.load()?.let { return it }
        val raw = backend.load().toPersistentHashMap()
        var values = persistentHashMapOf<String, PrefValue>()
        for ((physicalKey, value) in raw) {
            val logicalKey = FlutterPrefsCodec.logicalKey(physicalKey) ?: continue // not visible to Dart; never exposed
            val decoded = codec.decode(value).value ?: continue // <null>: Dart's getAll skips it
            values = values.putting(logicalKey, decoded)
        }
        val fresh = Snapshot(raw, values)
        return if (snapshot.compareAndSet(null, fresh)) fresh else snapshot.load()!!
    }

    private class Recorder(private val codec: FlutterPrefsCodec) : PrefEdit {
        val changes = LinkedHashMap<String, RawPref?>()
        var refusal: Failure? = null

        override fun putString(key: String, value: String) {
            if (FlutterPrefsCodec.clashes(value)) refusal = clash() else changes[checked(key)] = codec.encodeString(value)
        }

        override fun putBool(key: String, value: Boolean) {
            changes[checked(key)] = codec.encodeBool(value)
        }

        override fun putLong(key: String, value: Long) {
            changes[checked(key)] = codec.encodeLong(value)
        }

        override fun putStringList(key: String, value: List<String>) {
            changes[checked(key)] = codec.encodeStringList(value)
        }

        override fun putDouble(key: String, value: Double) {
            changes[checked(key)] = codec.encodeDouble(value)
        }

        override fun remove(key: String) {
            changes[checked(key)] = null
        }

        private fun checked(key: String): String {
            require(key.isNotEmpty() && !key.startsWith(FlutterPrefsCodec.KEY_PREFIX)) { "logical keys only" }
            return key
        }
    }

    private companion object {
        fun storageFailure(e: Throwable): Failure = Failure.StorageRefused(Redacted.shape(e))

        /**
         * Plan 05 §2.4 calls this `InvalidValue`; plan 03 §10.1's closed list has no such subtype, so a value the store
         * must not write is a refused write (INTEGRATE-W1). Details name the class only.
         */
        fun clash(): Failure = Failure.StorageRefused(Redacted.shape(PrefixClash()))
    }
}
