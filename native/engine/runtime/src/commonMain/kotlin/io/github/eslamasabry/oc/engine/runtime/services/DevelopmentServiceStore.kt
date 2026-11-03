package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The key-value store the services list lives in: the migrated preferences store (Flutter's `oc.*` keys are adopted
 * unchanged). [read] answers null for a missing value.
 */
public interface DevelopmentServiceSettings {
    public suspend fun read(key: String): String?

    public suspend fun write(key: String, value: String): Outcome<Unit>

    public suspend fun remove(key: String): Outcome<Unit>
}

/**
 * Configurations and ownership receipts only (port of lib/state/development_service_store.dart). Log output and
 * credentials are never saved. Key `oc.developmentServices.<profileId>`: per-profile, so the integrator registers
 * [KEY_PREFIX] with `ProfileScope` and the profile sweep removes it (native AGENTS rule 9).
 *
 * A saved value this store cannot read is never overwritten: every change reads it first and refuses
 * (`Failure.Runtime(StorageUnavailable)`) instead of replacing it. Writes run one at a time per store; the binding
 * keeps one store per profile. [canWrite] turns false when the profile is being deleted: a write that was already
 * under way removes what it wrote, so a pending start never recreates a swept profile's data.
 */
public class DevelopmentServiceStore(
    private val settings: DevelopmentServiceSettings,
    public val profile: ProfileId,
    private val canWrite: () -> Boolean,
) {
    public val key: String get() = keyFor(profile)
    private val lane = Mutex()

    /** What is saved, or `Failure.Runtime(StorageUnavailable)` when it cannot be read. Missing reads as empty. */
    public suspend fun load(): Outcome<List<DevelopmentService>> {
        val raw = settings.read(key) ?: return emptyList<DevelopmentService>().ok()
        val unreadable = Failure.Runtime(RuntimeProblem.StorageUnavailable).failed()
        if (raw.length > MAX_BYTES) return unreadable
        val parsed =
            try {
                Json.parseToJsonElement(raw)
            } catch (_: IllegalArgumentException) {
                return unreadable
            }
        val json = parsed as? JsonObject ?: return unreadable
        if ((json["version"] as? JsonPrimitive)?.intOrNull != 1) return unreadable
        val items = json["services"] as? JsonArray ?: return unreadable
        if (items.size > MAX_SERVICES) return unreadable
        val services = items.map { DevelopmentServiceJson.decode(it) ?: return unreadable }
        if (services.map { it.id }.toSet().size != services.size) return unreadable
        return services.ok()
    }

    public suspend fun save(service: DevelopmentService): Outcome<Unit> = change { items ->
        val index = items.indexOfFirst { it.id == service.id }
        when {
            index >= 0 -> items[index] = service
            items.size >= MAX_SERVICES -> return@change Failure.StorageRefused()
            else -> items += service
        }
        null
    }

    public suspend fun remove(id: String): Outcome<Unit> = change { items ->
        items.removeAll { it.id == id }
        null
    }

    /**
     * Replaces [id]'s receipt. With [expectedOwner], refuses (`Refused(StaleScope)`) unless the saved receipt still
     * carries that nonce, so a late answer never overwrites a newer start.
     */
    public suspend fun updateRun(id: String, run: DevelopmentServiceRun?, expectedOwner: String? = null): Outcome<Unit> =
        change { items ->
            val index = items.indexOfFirst { it.id == id }
            if (index < 0 || (expectedOwner != null && items[index].run?.ownerToken != expectedOwner)) {
                return@change Failure.Refused(RefusalReason.StaleScope)
            }
            items[index] = items[index].withRun(run)
            null
        }

    private suspend fun change(edit: (MutableList<DevelopmentService>) -> Failure?): Outcome<Unit> = lane.withLock {
        if (!canWrite()) return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val items = when (val loaded = load()) {
            is Outcome.Failed -> return@withLock loaded
            is Outcome.Ok -> loaded.value.toMutableList()
        }
        edit(items)?.let { return@withLock it.failed() }
        val saved = settings.write(key, DevelopmentServiceJson.encodeAll(items).toString())
        // A pending write must not recreate profile data after the deletion sweep.
        if (!canWrite()) {
            settings.remove(key)
            return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        }
        saved
    }

    public companion object {
        public const val KEY_PREFIX: String = "oc.developmentServices."
        public const val MAX_SERVICES: Int = 20
        private const val MAX_BYTES = 256 * 1024

        public fun keyFor(profile: ProfileId): String = KEY_PREFIX + profile.raw
    }
}
