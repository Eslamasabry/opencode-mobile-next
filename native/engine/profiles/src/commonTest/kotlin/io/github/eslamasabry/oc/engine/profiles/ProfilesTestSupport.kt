package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineDispatcher

/** A prefs file that refuses any commit touching one of [refuse] (logical keys), like Flutter's `_RefusingStore`. */
internal class RefusingBackend : InMemoryPrefsBackend() {
    val refuse = mutableSetOf<String>()

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        if (changes.keys.any { it.removePrefix("flutter.") in refuse }) return false
        return super.commit(changes)
    }
}

/** Secure storage with failure switches. Values never leave it except through [read]. */
internal class FakeSecrets(initial: Map<String, String> = emptyMap()) : SecretStore {
    val values = LinkedHashMap(initial)
    val deleted = mutableListOf<String>()
    var failAllReads = false
    val failReads = mutableSetOf<String>()
    var failWrites = false
    var failAllDeletes = false
    val failDeletes = mutableSetOf<String>()

    /** Delete answers Ok but keeps the entry (an unconfirmed delete). */
    val retainDeletes = mutableSetOf<String>()

    /** The Nth (1-based) enumeration call fails. */
    var failEnumerationAt: Int? = null
    private var enumerations = 0

    private fun refused(): Outcome<Nothing> = Failure.Storage(StorageProblem.KeystoreUnavailable).failed()

    override suspend fun read(name: String): Outcome<String?> =
        if (failAllReads || name in failReads) refused() else values[name].ok()

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        if (failWrites) return refused()
        values[name] = value
        return Unit.ok()
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        if (failAllDeletes || name in failDeletes) return refused()
        deleted += name
        if (name !in retainDeletes) values.remove(name)
        return Unit.ok()
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> {
        enumerations++
        if (failEnumerationAt == enumerations) return refused()
        return values.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }.ok()
    }
}

/** One profiles engine over in-memory storage, wired like the app graph. */
internal class Harness(
    io: CoroutineDispatcher,
    val secrets: FakeSecrets = FakeSecrets(),
    val backend: RefusingBackend = RefusingBackend(),
    platform: SecretPlatform = SecretPlatform.Android,
) {
    val kv = PrefsKeyValueStore(backend, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), io)
    val lanes = DefaultWriterLanes()
    val redactor = Redactor()
    val scope = DefaultProfileScope(kv, secrets, lanes.app).also {
        ProfileKeys.register(it)
        DeferredFeatureData.register(it)
    }
    val store = ProfileStore(kv, secrets, lanes, redactor, platform)

    fun deletion(
        closers: Set<ProfileCloser> = emptySet(),
        cleaners: Set<ProfileResourceCleaner> = emptySet(),
    ): ProfileDeletion = ProfileDeletion(store, scope, lanes, closers, cleaners)

    /** The live-file cleaners for the Flutter embedded records (steps 3–5), as the app registers them today. */
    fun embeddedCleaners(): Set<ProfileResourceCleaner> = setOf(
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.OfflineQueue, DeletionStep.QUEUE),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.SessionDrafts, DeletionStep.DRAFTS),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.PendingPhoto, DeletionStep.DEVICE_SURFACES),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.WidgetSnapshot, DeletionStep.DEVICE_SURFACES + 1),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.PinnedShortcuts, DeletionStep.DEVICE_SURFACES + 2),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.AttentionTile, DeletionStep.DEVICE_SURFACES + 3),
        EmbeddedRecordCleaner(kv, lanes.app, FlutterEraEdits.BuiltinServerOwner, DeletionStep.QUEUE + 1),
    )

    suspend fun seed(vararg pairs: Pair<String, Any>) {
        val result = kv.commit(lanes.app) {
            for ((key, value) in pairs) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBool(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putLong(key, value.toLong())
                    is Double -> putDouble(key, value)
                    else -> putString(key, DartJson.encode(value))
                }
            }
        }
        check(result is Outcome.Ok) { "seed refused" }
    }

    fun string(key: String): String? = (kv.get(key) as? PrefValue.Str)?.value

    /** What a fresh process would read: the persisted file, all of it, as text. */
    fun persistedText(): String = backend.persisted().entries.joinToString("\n") { (k, v) ->
        "$k=" + when (v) {
            is RawPref.Str -> v.value
            is RawPref.StrSet -> v.value.joinToString(",")
            else -> v.toString()
        }
    }
}

internal fun id(raw: String): ProfileId = ProfileId(raw)

internal fun json(value: Any?): String = DartJson.encode(value)

/** A retained copy held in memory; [bytes] is its whole content as text, for the canary grep. */
internal class MapCopy(
    override val label: String,
    override val kind: RetainedCopyKind,
    initial: Map<String, PrefValue>,
) : RetainedCopy {
    var entries: Map<String, PrefValue> = LinkedHashMap(initial)
    var refuse = false
    var rewrites = 0

    override suspend fun read(): Outcome<Map<String, PrefValue>?> = entries.ok()

    override suspend fun rewrite(entries: Map<String, PrefValue>): Outcome<Unit> {
        if (refuse) return Failure.Storage(StorageProblem.WriteRefused).failed()
        this.entries = LinkedHashMap(entries)
        rewrites++
        return Unit.ok()
    }

    fun bytes(): String = entries.entries.joinToString("\n") { (k, v) ->
        val text = when (v) {
            is PrefValue.Str -> v.value
            is PrefValue.StrList -> v.value.joinToString(",")
            is PrefValue.Bool -> v.value.toString()
            is PrefValue.Int64 -> v.value.toString()
            is PrefValue.Dbl -> v.value.toString()
            is PrefValue.Undecodable -> "?"
        }
        "$k=$text"
    }
}
