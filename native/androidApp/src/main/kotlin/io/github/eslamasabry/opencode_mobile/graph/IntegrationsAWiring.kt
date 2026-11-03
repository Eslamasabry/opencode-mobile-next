package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import io.github.eslamasabry.oc.engine.connectors.catalogue.KeyValueCatalogueCache
import io.github.eslamasabry.oc.engine.connectors.registry.PublicRegistryClient
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryTransport
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueAbilities
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueSource
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsSource
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2Connectors
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Unit BUILD-INTEGRATIONS-A: the Android side of the Connectors page (Settings › Agent › Tools). It reads the live
 * connection graphs the preview runtime opened (shared with Search/Usage) and talks OC2's MCP wire through
 * Oc2Connectors; any other server says it cannot list connectors (Failure.Unavailable), never a guess.
 */

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ConnectorsSource>())
@Inject
class AndroidConnectorsSource(private val shared: SearchUsageServers, appScope: AppCoroutineScope) : ConnectorsSource {
    private val scope = CoroutineScope(appScope.coroutineContext)

    override val servers: StateFlow<ImmutableList<ConnectorServer>> =
        shared.live
            .map { list -> list.map { (id, name) -> ConnectorServer(id, name) }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = shared.active

    override suspend fun list(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.servers(gateway) ?: unavailable()
    }

    override suspend fun connect(profile: ProfileId, name: String): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.connect(gateway, name) ?: unavailable()
    }

    override suspend fun disconnect(profile: ProfileId, name: String): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.disconnect(gateway, name) ?: unavailable()
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun unavailable(): Outcome<Nothing> = Failure.Unavailable(FeatureId("serverCatalog")).failed()
}

/*
 * The MCP catalogue (Settings › Agent › Tools › Browse the catalogue). The public registry is read with its own
 * unauthenticated client (never a server's), and the per-profile consent and saved list live in
 * `oc.setupRegistry.<profileId>` (the Flutter key, swept with the profile's other `oc.<what>.<profileId>` keys).
 */

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CatalogueSource>())
@Inject
class AndroidCatalogueSource(
    private val shared: SearchUsageServers,
    private val stores: PreviewStores,
    http: HttpClientFactory,
    private val redactor: Redactor,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : CatalogueSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val registry by lazy { PublicRegistryClient(NetRegistryTransport(http.create(REGISTRY_SPEC)), redactor) }

    override val servers: StateFlow<ImmutableList<ConnectorServer>> =
        shared.live
            .map { list -> list.map { (id, name) -> ConnectorServer(id, name) }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = shared.active

    /** OC2 lists, adds for the current location until restart, and removes; nothing else is claimed. */
    override fun abilities(profile: ProfileId): CatalogueAbilities {
        val gateway = shared.gateway(profile) ?: return CatalogueAbilities.None
        val oc2 = Oc2Connectors.handles(gateway)
        return CatalogueAbilities(canList = oc2, canAdd = oc2, canRemove = oc2)
    }

    override suspend fun installed(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.servers(gateway) ?: unavailable()
    }

    override suspend fun add(profile: ProfileId, draft: McpServerDraft): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.add(gateway, draft) ?: unavailable()
    }

    override suspend fun remove(profile: ProfileId, name: String): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.remove(gateway, name) ?: unavailable()
    }

    override fun catalogue(profile: ProfileId): CatalogueStore =
        CatalogueStore(
            cache = KeyValueCatalogueCache(stores.prefs) { id -> stores.lanes.forProfile(ProfileId(id)) },
            profileId = profile.raw,
            client = registry,
            nowMs = { clock.now().toEpochMilliseconds() },
            redactor = redactor,
        )

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun unavailable(): Outcome<Nothing> = Failure.Unavailable(FeatureId("serverCatalog")).failed()

    private companion object {
        val REGISTRY_SPEC =
            TransportSpec(
                ServerOrigin("https", "registry.modelcontextprotocol.io", 443),
                HostClass.Public,
                cleartextPermitted = false,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 15_000,
            )
    }
}

/**
 * [RegistryTransport] over the app's HTTP stack: one GET of [RegistryTransport.ENDPOINT] with `limit`, `version=latest`
 * and the optional `search`; no redirects (the client never follows them), a 30 s overall deadline, only 200 accepted,
 * and the body read up to [RegistryTransport.MAX_RESPONSE_BYTES] (more is a failure). No credentials are ever sent.
 */
private class NetRegistryTransport(private val net: NetClient) : RegistryTransport {
    override suspend fun get(search: String?): Outcome<String> {
        val reply =
            net.exchange({
                method = HttpMethod.Get
                url(RegistryTransport.ENDPOINT)
                url.parameters.append("limit", RegistryTransport.MAX_ENTRIES.toString())
                url.parameters.append("version", "latest")
                if (search != null) url.parameters.append("search", search)
                header(HttpHeaders.Accept, "application/json")
                timeout { requestTimeoutMillis = 30_000 }
            }) { response ->
                if (response.status.value != 200) return@exchange null
                val channel = response.bodyAsChannel()
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > RegistryTransport.MAX_RESPONSE_BYTES) return@exchange null
                }
                out.toByteArray().decodeToString()
            }
        return when (reply) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> reply.value?.ok() ?: Failure.UnreadableReply().failed()
        }
    }
}
