package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.WireJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentService
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceRun
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * B-LINUX-061..063. Flutter's version-one service configuration and ownership receipts, under its
 * existing profile key. Output is never persisted. Corrupt or oversized saved values are preserved,
 * and every write compares the complete prior envelope on the profile's deletion-fenced writer
 * lane.
 */
internal class StoredDevelopmentServices(
    private val stores: PreviewStores,
    private val profile: ProfileId,
) : DevelopmentServiceStore {
    override val key: String = DevelopmentServiceStore.keyFor(profile.raw)
    private val mutex = Mutex()

    override fun load(): Outcome<List<DevelopmentService>> {
        if (stores.profiles.profile(profile) == null) return deleted()
        return when (val read = snapshot()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> Outcome.Ok(read.value.services)
        }
    }

    override suspend fun save(service: DevelopmentService): Outcome<Unit> = change { items ->
        if (!valid(service)) return@change refused()
        val at = items.indexOfFirst { it.id == service.id }
        if (at < 0) {
            if (items.size >= DevelopmentServiceStore.MAX_SERVICES) return@change refused()
            items.add(service)
        } else {
            items[at] = service
        }
        Outcome.Ok(Unit)
    }

    override suspend fun remove(id: String): Outcome<Unit> = change { items ->
        items.removeAll { it.id == id }
        Outcome.Ok(Unit)
    }

    override suspend fun updateRun(
        id: String,
        run: DevelopmentServiceRun?,
        expectedOwner: String?,
    ): Outcome<Unit> = change { items ->
        if (run != null && !validRun(run)) return@change refused()
        val at = items.indexOfFirst { it.id == id }
        if (at < 0 || (expectedOwner != null && items[at].run?.ownerToken != expectedOwner)) {
            return@change Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        items[at] = items[at].withRun(run)
        Outcome.Ok(Unit)
    }

    private data class Snapshot(val raw: String?, val services: List<DevelopmentService>)

    private fun snapshot(): Outcome<Snapshot> =
        try {
            val value = stores.prefs.get(key)
            if (value != null && value !is PrefValue.Str) {
                refused()
            } else {
                val raw = (value as? PrefValue.Str)?.value
                if (raw == null) Outcome.Ok(Snapshot(null, emptyList()))
                else Outcome.Ok(Snapshot(raw, decode(raw)))
            }
        } catch (_: Exception) {
            refused()
        }

    private suspend fun change(
        edit: (MutableList<DevelopmentService>) -> Outcome<Unit>
    ): Outcome<Unit> = mutex.withLock {
        repeat(MAX_CAS_ATTEMPTS) {
            if (stores.profiles.profile(profile) == null) return@withLock deleted()
            val snapshot =
                when (val read = snapshot()) {
                    is Outcome.Failed -> return@withLock read
                    is Outcome.Ok -> read.value
                }
            val next = snapshot.services.toMutableList()
            val edited = edit(next)
            if (edited is Outcome.Failed) return@withLock edited
            val encoded = encode(next)
            if (encoded.length > MAX_ENVELOPE) return@withLock refused()
            if (stores.profiles.profile(profile) == null) return@withLock deleted()
            when (
                val saved =
                    stores.prefs.compareAndSet(
                        stores.lanes.forProfile(profile),
                        key,
                        snapshot.raw,
                        encoded,
                    )
            ) {
                is Outcome.Failed -> return@withLock saved
                is Outcome.Ok -> if (saved.value) return@withLock Outcome.Ok(Unit)
            }
        }
        Outcome.Failed(Failure.Conflict())
    }

    private fun decode(raw: String): List<DevelopmentService> {
        require(raw.length <= MAX_ENVELOPE)
        val root = WireJson.parseToJsonElement(raw) as? JsonObject ?: error("invalid envelope")
        val version = root["version"] as? JsonPrimitive
        require(version != null && !version.isString && version.intOrNull == 1)
        val items = root["services"] as? JsonArray ?: error("invalid services")
        require(items.size <= DevelopmentServiceStore.MAX_SERVICES)
        val services = items.map { element ->
            val item = element as? JsonObject ?: error("invalid service")
            val run =
                item["run"]
                    ?.takeUnless { it == JsonNull }
                    ?.let { value ->
                        val saved = value as? JsonObject ?: error("invalid receipt")
                        val started =
                            saved["startedAt"]
                                ?.takeUnless { it == JsonNull }
                                ?.let {
                                    val number = it as? JsonPrimitive ?: error("invalid start")
                                    require(!number.isString)
                                    number.longOrNull ?: error("invalid start")
                                }
                        val stopped =
                            saved["stopped"]?.let {
                                val boolean = it as? JsonPrimitive ?: error("invalid stopped")
                                require(!boolean.isString)
                                boolean.booleanOrNull ?: error("invalid stopped")
                            } ?: false
                        DevelopmentServiceRun(
                                ownerToken = saved.requiredString("owner", 100),
                                shellId = saved.optionalString("shellID"),
                                startedAt = started,
                                stopped = stopped,
                            )
                            .also { require(validRun(it)) }
                    }
            DevelopmentService(
                id = item.requiredString("id", 100),
                name = item.requiredString("name", 80),
                command = item.requiredString("command", 4096),
                directory = item.requiredString("directory", 4096),
                url = item.optionalString("url").orEmpty(),
                workspace = item.optionalString("workspace"),
                run = run,
            )
        }
        require(services.map { it.id }.toSet().size == services.size)
        return services
    }

    private fun encode(services: List<DevelopmentService>): String = buildJsonObject {
        put("version", 1)
        put(
            "services",
            JsonArray(
                services.map { service ->
                    buildJsonObject {
                        put("id", service.id)
                        put("name", service.name)
                        put("command", service.command)
                        put("directory", service.directory)
                        put("workspace", service.workspace?.let(::JsonPrimitive) ?: JsonNull)
                        put("url", service.url)
                        put(
                            "run",
                            service.run?.let { run ->
                                buildJsonObject {
                                    put("owner", run.ownerToken)
                                    put("shellID", run.shellId?.let(::JsonPrimitive) ?: JsonNull)
                                    put(
                                        "startedAt",
                                        run.startedAt?.let(::JsonPrimitive) ?: JsonNull,
                                    )
                                    put("stopped", run.stopped)
                                }
                            } ?: JsonNull,
                        )
                    }
                }
            ),
        )
    }
        .toString()

    private fun JsonObject.requiredString(name: String, max: Int): String =
        optionalString(name)?.also { require(it.isNotBlank() && it.length <= max) }
            ?: error("missing string")

    private fun JsonObject.optionalString(name: String): String? {
        val value: JsonElement = get(name) ?: return null
        if (value == JsonNull) return null
        val primitive = value as? JsonPrimitive ?: error("invalid string")
        require(primitive.isString)
        return primitive.content
    }

    private fun valid(service: DevelopmentService): Boolean =
        service.id.isNotBlank() &&
            service.id.length <= 100 &&
            service.name.isNotBlank() &&
            service.name.length <= 80 &&
            service.command.isNotBlank() &&
            service.command.length <= 4096 &&
            service.directory.isNotBlank() &&
            service.directory.length <= 4096 &&
            service.url.length <= MAX_ENVELOPE &&
            (service.workspace?.length ?: 0) <= MAX_ENVELOPE &&
            (service.run?.let(::validRun) != false)

    private fun validRun(run: DevelopmentServiceRun): Boolean =
        run.ownerToken.isNotBlank() &&
            run.ownerToken.length <= 100 &&
            (run.shellId?.let { it.isNotBlank() && it.length <= 256 } != false) &&
            (run.startedAt?.let { it >= 0 } != false)

    private fun refused(): Outcome.Failed = Outcome.Failed(Failure.StorageRefused())

    private fun deleted(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))

    private companion object {
        const val MAX_ENVELOPE = 256 * 1024
        const val MAX_CAS_ATTEMPTS = 4
    }
}
