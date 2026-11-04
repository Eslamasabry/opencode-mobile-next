package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.platform.AndroidNetworkStatus
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.SetupInspectionGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.setupinspection.*
import io.github.eslamasabry.oc.feature.servers.setup.presentation.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

/**
 * One process-owned network observer; registry browsing never assumes a live loopback server means
 * Internet.
 */
@SingleIn(AppScope::class)
@Inject
class SetupNetwork(application: Application) {
    val monitor = AndroidNetworkStatus(application).also { it.start() }
}

/**
 * Setup reads borrow the live gateway's authentication but capture its current location at opening.
 */
@ContributesBinding(AppScope::class)
@Inject
class NativeSetupAccess(
    private val stores: PreviewStores,
    private val connections: ConnectionRegistry,
    private val http: HttpClientFactory,
    private val redactor: Redactor,
    private val network: SetupNetwork,
) : SetupAccess {
    override suspend fun open(profile: ProfileId): Outcome<SetupContext> {
        val warmed = stores.warm()
        if (warmed is Outcome.Failed) return warmed
        val saved =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph = connections.live.value[profile]
        val gateway = graph?.session?.server
        val inspector =
            gateway
                ?.takeIf {
                    it.capabilities.value.setupConfigRead && it.capabilities.value.setupMcpInventory
                }
                ?.facet<SetupInspectionGateway>()
                ?.setupInspection(gateway.location.value)
        val available = {
            stores.profiles.profile(profile) == saved &&
                (graph == null || connections.live.value[profile] === graph)
        }
        val cache = SetupRegistryCache(stores, profile, available, redactor)
        val registry = SetupRegistry(cache, PublicRegistryFetcher(http, redactor))
        return SetupContext(
                inspector,
                registry,
                network.monitor.state.map { it.online != NetworkState.Online.No },
                available,
            )
            .ok()
    }
}

private const val REGISTRY_MAX_BYTES = 512 * 1024

/**
 * Profile lane and liveness fence make a pending refresh unable to recreate a removed profile's
 * cache.
 */
private class SetupRegistryCache(
    private val stores: PreviewStores,
    private val profile: ProfileId,
    private val available: () -> Boolean,
    private val redactor: Redactor,
) : RegistryCache {
    private val key = "oc.setupRegistry.${profile.raw}"

    override fun exists(): Boolean = available() && stores.prefs.get(key) != null

    override suspend fun load(): Outcome<RegistrySnapshot?> {
        val raw = stores.prefs.string(key) ?: return null.ok()
        if (raw.length > REGISTRY_MAX_BYTES) return Failure.UnreadableReply().failed()
        return attempt({ Failure.UnreadableReply() }) {
            val data = Json.parseToJsonElement(raw) as? JsonObject ?: error("invalid cache")
            require((data["version"] as? JsonPrimitive)?.intOrNull == 1)
            val entries =
                (data["entries"] as? JsonArray).orEmpty().take(100).mapNotNull {
                    safeListing(it, redactor)
                }
            RegistrySnapshot(
                (data["optedIn"] as? JsonPrimitive)?.booleanOrNull == true,
                entries = entries,
            )
        }
    }

    override suspend fun save(snapshot: RegistrySnapshot): Outcome<Unit> {
        if (!available()) return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val body = buildJsonObject {
            put("version", 1)
            put("optedIn", snapshot.optedIn)
            put(
                "entries",
                JsonArray(
                    snapshot.entries.take(100).map { listing ->
                        buildJsonObject {
                            put("name", listing.name)
                            put("version", listing.version)
                            put("title", listing.title)
                            put("description", listing.description)
                        }
                    }
                ),
            )
        }
            .toString()
        val before = stores.prefs.string(key)
        return when (
            val result =
                stores.prefs.compareAndSet(stores.lanes.forProfile(profile), key, before, body)
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                if (result.value && available()) Unit.ok()
                else Failure.Refused(RefusalReason.StaleScope).failed()
        }
    }
}

/**
 * No profile client, authentication, redirects or dynamic origin can reach this public metadata
 * request.
 */
private class PublicRegistryFetcher(
    private val http: HttpClientFactory,
    private val redactor: Redactor,
) : RegistryFetcher {
    override suspend fun fetch(search: String?): Outcome<List<RegistryListing>> {
        val origin = ServerOrigin("https", "registry.modelcontextprotocol.io", 443)
        val net =
            http.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    false,
                    readTimeoutMs = 15_000,
                )
            )
        return try {
            val reply =
                attempt({ Failure.Network() }) {
                    net.http
                        .prepareGet("https://registry.modelcontextprotocol.io/v0.1/servers") {
                            parameter("limit", "100")
                            parameter("version", "latest")
                            val term = search?.trim()
                            if (
                                !term.isNullOrEmpty() &&
                                    term.length <= 100 &&
                                    !redactor.containsSecret(term)
                            )
                                parameter("search", term)
                            timeout { requestTimeoutMillis = 20_000 }
                        }
                        .execute { response ->
                            if (response.status.value != 200)
                                return@execute Failure.Rejected(response.status.value).failed()
                            val declared =
                                response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                            if (declared != null && declared > REGISTRY_MAX_BYTES)
                                return@execute Failure.UnreadableReply().failed()
                            val channel = response.bodyAsChannel()
                            val bytes = ArrayList<Byte>()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = channel.readAvailable(buffer, 0, buffer.size)
                                if (count < 0) break
                                if (bytes.size + count > REGISTRY_MAX_BYTES)
                                    return@execute Failure.UnreadableReply().failed()
                                for (i in 0 until count) bytes += buffer[i]
                            }
                            attempt({ Failure.UnreadableReply() }) {
                                val objectValue =
                                    Json.parseToJsonElement(
                                        bytes
                                            .toByteArray()
                                            .decodeToString(throwOnInvalidSequence = true)
                                    ) as? JsonObject ?: error("invalid registry")
                                val rows =
                                    objectValue["servers"] as? JsonArray
                                        ?: error("invalid registry")
                                rows.take(100).mapNotNull { item ->
                                    val row = item as? JsonObject ?: return@mapNotNull null
                                    val official =
                                        ((row["_meta"] as? JsonObject)?.get(
                                            "io.modelcontextprotocol.registry/official"
                                        ) as? JsonObject)
                                    val status =
                                        (official?.get("status") as? JsonPrimitive)?.content
                                    if (status == "deleted" || status == "deprecated") null
                                    else safeListing(row["server"], redactor)
                                }
                            }
                        }
                }
            when (reply) {
                is Outcome.Failed -> reply
                is Outcome.Ok -> reply.value
            }
        } finally {
            net.close()
        }
    }
}

private fun safeListing(value: JsonElement?, redactor: Redactor): RegistryListing? {
    val item = value as? JsonObject ?: return null
    fun identifier(key: String): String? {
        val text = (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return text.takeIf {
            it.length in 1..256 &&
                Regex("^[A-Za-z0-9@._/+\\-]+$").matches(it) &&
                !redactor.containsSecret(it)
        }
    }
    fun plain(key: String, limit: Int): String =
        redactor
            .redact((item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty())
            .replace(Regex("[\\x00-\\x1f]"), " ")
            .take(limit)
    return RegistryListing(
        identifier("name") ?: return null,
        identifier("version") ?: return null,
        plain("title", 120),
        plain("description", 1000),
    )
}
