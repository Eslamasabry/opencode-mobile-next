package io.github.eslamasabry.oc.core.data.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.concurrent.atomics.AtomicReference

/**
 * [ProfileScope] over the live [KeyValueStore] and [SecretStore]. Sweeps run in [sweepLane] (the app lane: the
 * profile's own lane is closed by then).
 */
public class DefaultProfileScope(
    private val store: KeyValueStore,
    private val secrets: SecretStore,
    private val sweepLane: WriterLane,
) : ProfileScope {

    private data class Registry(
        val shapes: PersistentList<KeyShape> = persistentListOf(),
        val secretPrefixes: PersistentList<Pair<String, String>> = persistentListOf(),
        val tables: PersistentList<String> = persistentListOf(),
    )

    private val registry = AtomicReference(Registry())

    override fun register(shape: KeyShape) {
        require(shape.prefix.startsWith("oc.") || shape.scope == KeyScope.Other) { "keys start with oc. (plan 05 §3.1)" }
        update { it.copy(shapes = it.shapes.adding(shape)) }
    }

    override fun registerSecret(prefix: String, ownerModule: String) {
        update { it.copy(secretPrefixes = it.secretPrefixes.adding(prefix to ownerModule)) }
    }

    override fun registerTable(table: String, profileColumn: String) {
        update { it.copy(tables = it.tables.adding("$table.$profileColumn")) }
    }

    override fun shapes(): List<KeyShape> = registry.load().shapes

    override fun scopedKeys(profileId: ProfileId): Set<String> =
        ProfileKeyRule.scopedKeys(profileId.raw, store.keys("oc."), savedProfileIds())

    override suspend fun ownedData(profileId: ProfileId): Outcome<OwnedData> {
        val keys = scopedKeys(profileId)
        val names = LinkedHashSet<String>()
        for ((prefix, _) in registry.load().secretPrefixes) {
            when (val found = secrets.names(prefix + profileId.raw)) {
                is Outcome.Failed -> return found
                is Outcome.Ok -> found.value.filterTo(names) { it == prefix + profileId.raw || it.startsWith(prefix + profileId.raw + ".") }
            }
        }
        return Outcome.Ok(OwnedData(keys, names, registry.load().tables))
    }

    override fun reservedSegments(): Set<String> {
        val segments = LinkedHashSet<String>()
        for (shape in registry.load().shapes) {
            if (shape.scope != KeyScope.App && shape.scope != KeyScope.Shared) continue
            shape.prefix.split('.').filterTo(segments) { it.isNotEmpty() && it != "oc" }
        }
        segments += "app" // oc.draft.<target>.app, the app-wide draft (plan 05 §3.1 rule 3)
        segments += ProfileKeyRule.AGENT_BACKEND_SUFFIX.removePrefix(".")
        return segments
    }

    override suspend fun sweep(profileId: ProfileId, retained: Set<String>): Outcome<SweepReceipt> {
        // Computed late, at the moment of the commit, so keys written by Kotlin surfaces after the lanes drained are
        // still found (plan 05 §3.4).
        val doomed = scopedKeys(profileId)
        val removed = doomed - retained
        if (removed.isEmpty()) return Outcome.Ok(SweepReceipt(0, (doomed intersect retained).size, emptyMap()))
        val shapes = registry.load().shapes.filter { it.scope == KeyScope.Profile || it.scope == KeyScope.Owner }
        val byOwner = removed.groupingBy { key ->
            shapes.filter { key.startsWith(it.prefix) }.maxByOrNull { it.prefix.length }?.ownerModule ?: UNREGISTERED
        }.eachCount()
        return store.commit(sweepLane) { removed.forEach(::remove) }
            .map { SweepReceipt(removed.size, (doomed intersect retained).size, byOwner) }
    }

    /** Every id in `oc.profiles` (lenient, like Dart: a bad blob protects nothing extra). */
    private fun savedProfileIds(): Set<String> {
        val rows = store.string(PROFILES_KEY)?.let(DartJson::decodeOrNull) as? List<*> ?: return emptySet()
        return rows.mapNotNullTo(LinkedHashSet()) { (it as? Map<*, *>)?.get("id") as? String }
    }

    private inline fun update(change: (Registry) -> Registry) {
        while (true) {
            val current = registry.load()
            if (registry.compareAndSet(current, change(current))) return
        }
    }

    public companion object {
        public const val PROFILES_KEY: String = "oc.profiles"
        public const val UNREGISTERED: String = "unregistered"
    }
}
