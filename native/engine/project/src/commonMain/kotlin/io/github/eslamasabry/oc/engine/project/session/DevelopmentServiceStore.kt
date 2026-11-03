package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Port of lib/state/development_service_store.dart and the value types of lib/domain/development_service.dart.

/** Who started a run (written before the create request, so a lost response never authorizes a duplicate start). */
public data class DevelopmentServiceRun(
    val ownerToken: String,
    val shellId: String? = null,
    val startedAtMs: Long? = null,
    val stopped: Boolean = false,
)

/** One registered development service; never its log output or credentials. */
public data class DevelopmentService(
    val id: String,
    val name: String,
    val command: String,
    val directory: String,
    val url: String,
    val workspace: String? = null,
    val run: DevelopmentServiceRun? = null,
)

/**
 * Configurations and ownership receipts under `oc.developmentServices.<profileId>` (swept with the profile). Every
 * change is checked against [canWrite] before AND after its commit: a receipt that lands after the profile was deleted
 * removes what it wrote instead of recreating the profile's preferences.
 */
public class DevelopmentServiceStore(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    public val profile: ProfileId,
    private val canWrite: () -> Boolean,
) {
    private val writes = Mutex()

    public val key: String get() = keyFor(profile)

    /** Unreadable data is a Failure, never silently replaced. */
    public fun load(): Outcome<List<DevelopmentService>> {
        val raw = store.string(key) ?: return Outcome.Ok(emptyList())
        if (raw.length > MAX_CHARS) return Outcome.Failed(Failure.UnreadableReply())
        return decode(raw)?.let { Outcome.Ok(it) } ?: Outcome.Failed(Failure.UnreadableReply())
    }

    public suspend fun save(service: DevelopmentService): Outcome<Unit> =
        change { items ->
            val index = items.indexOfFirst { it.id == service.id }
            when {
                index >= 0 -> items[index] = service
                items.size >= MAX_SERVICES -> return@change false
                else -> items += service
            }
            true
        }

    public suspend fun remove(id: String): Outcome<Unit> = change { items -> items.removeAll { it.id == id }; true }

    /** Records (or clears) a run receipt; refused when the service or its expected owner changed. */
    public suspend fun updateRun(id: String, run: DevelopmentServiceRun?, expectedOwner: String? = null): Outcome<Unit> =
        change { items ->
            val index = items.indexOfFirst { it.id == id }
            if (index < 0 || (expectedOwner != null && items[index].run?.ownerToken != expectedOwner)) return@change false
            items[index] = items[index].copy(run = run)
            true
        }

    private suspend fun change(edit: (MutableList<DevelopmentService>) -> Boolean): Outcome<Unit> =
        writes.withLock {
            if (!canWrite()) return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            val items = when (val loaded = load()) {
                is Outcome.Failed -> return@withLock loaded
                is Outcome.Ok -> loaded.value.toMutableList()
            }
            if (!edit(items)) return@withLock Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            val saved = store.commit(lane) { putString(key, encode(items)) }
            // A write that was already on its way must not recreate profile data after the deletion sweep.
            if (!canWrite()) {
                store.commit(lane) { remove(key) }
                return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            }
            saved
        }

    public companion object {
        public const val MAX_SERVICES: Int = 20
        private const val MAX_CHARS = 256 * 1024

        public fun keyFor(profile: ProfileId): String = "oc.developmentServices.${profile.raw}"

        public val SHAPE: KeyShape =
            KeyShape("oc.developmentServices.", KeyScope.Profile, PrefType.Str, ":engine:project", "development services")

        internal fun encode(items: List<DevelopmentService>): String =
            DartJson.encode(
                mapOf(
                    "version" to 1L,
                    "services" to items.map { s ->
                        linkedMapOf(
                            "id" to s.id, "name" to s.name, "command" to s.command, "directory" to s.directory,
                            "workspace" to s.workspace, "url" to s.url,
                            "run" to s.run?.let { r ->
                                linkedMapOf("owner" to r.ownerToken, "shellID" to r.shellId, "startedAt" to r.startedAtMs, "stopped" to r.stopped)
                            },
                        )
                    },
                ),
            )

        internal fun decode(raw: String): List<DevelopmentService>? {
            val json = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
            if (json["version"] != 1L) return null
            val rows = json["services"] as? List<*> ?: return null
            if (rows.size > MAX_SERVICES) return null
            val services = rows.map { row -> service(row as? Map<*, *> ?: return null) ?: return null }
            return if (services.map { it.id }.toSet().size == services.size) services else null
        }

        private fun service(row: Map<*, *>): DevelopmentService? {
            fun text(key: String, max: Int): String? = (row[key] as? String)?.takeIf { it.isNotBlank() && it.length <= max }
            val run = row["run"]?.let { raw ->
                val r = raw as? Map<*, *> ?: return null
                val owner = (r["owner"] as? String)?.takeIf { it.isNotEmpty() && it.length <= 100 } ?: return null
                DevelopmentServiceRun(owner, r["shellID"] as? String, r["startedAt"] as? Long, r["stopped"] == true)
            }
            return DevelopmentService(
                id = text("id", 100) ?: return null,
                name = text("name", 80) ?: return null,
                command = text("command", 4096) ?: return null,
                directory = text("directory", 4096) ?: return null,
                url = row["url"] as? String ?: "",
                workspace = row["workspace"] as? String,
                run = run,
            )
        }
    }
}
