package io.github.eslamasabry.oc.core.data.kv

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
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
import kotlin.concurrent.atomics.AtomicReference

/** `commit()` answered false. Only its class name reaches `Failure.details` (plan 03 §16.1 `StorageFailures`). */
internal class CommitRefused : Exception()

/**
 * [KeyValueStore] over a [PrefsBackend] holding Flutter's physical layout (`flutter.` prefix, Flutter encodings).
 * On Android the backend is the adopted `FlutterSharedPreferences` file (plan 05 §2.2).
 *
 * The store keeps its own immutable snapshot of ACKNOWLEDGED values. `SharedPreferencesImpl` updates its memory before
 * the disk write, so reading through it could show a refused value for a moment; reads here never do.
 */
public class PrefsKeyValueStore(
    private val backend: PrefsBackend,
    private val codec: FlutterPrefsCodec,
    private val gate: MigrationGate,
    private val io: CoroutineDispatcher,
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

    override fun get(key: String): PrefValue? = loaded().values[key]

    override fun keys(prefix: String): Set<String> = loaded().values.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }

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
            withContext(io + NonCancellable) {
                fileLock.withLock {
                    val current = loaded().values[key]
                    val matches = if (expected == null) current == null else (current as? PrefValue.Str)?.value == expected
                    if (!matches) {
                        Outcome.Ok(false)
                    } else {
                        applyLocked(mapOf(FlutterPrefsCodec.physicalKey(key) to encoded)).map { true }
                    }
                }
            }.also { if (it is Outcome.Ok && it.value) mutableChanges.emit(setOf(key)) }
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
        val result = withContext(io + NonCancellable) {
            fileLock.withLock { applyLocked(logical.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }) }
        }
        if (result is Outcome.Ok) withContext(NonCancellable) { mutableChanges.emit(logical.keys) }
        return result
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
