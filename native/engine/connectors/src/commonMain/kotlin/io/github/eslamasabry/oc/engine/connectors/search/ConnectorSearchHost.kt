package io.github.eslamasabry.oc.engine.connectors.search

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.bridge.ConnectorSearchBridge
import io.github.eslamasabry.oc.engine.connectors.bridge.LoopbackServerPort
import io.github.eslamasabry.oc.engine.connectors.bridge.SecureRandomBytes
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueCache
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PORT: hands an agent's helper the bridge descriptor (Dart `GenUiSearchPublisher`). True when the helper now knows
 * [endpoint] and [bearer]. The bearer must never be logged or shown.
 */
public fun interface ConnectorSearchPublisher {
    public suspend fun publish(profileId: String, agentId: String, endpoint: String, bearer: String): Boolean
}

/**
 * Owns the connector-search bridges for the phone's connection (Dart `_connectorSearchSync`; B-AGENT-132/133). Call
 * [sync] whenever the connection changes: while Agent cards are on and connected, each qualified agent gets one bridge
 * and its descriptor; any change of owner, generation, location or agents closes the old bridges (they stop serving
 * at once). A failed publish closes its bridge and is retried no earlier than five seconds later.
 */
public class ConnectorSearchHost(
    private val environment: ConnectorSearchEnvironment,
    private val service: ConnectorSearchService,
    private val server: LoopbackServerPort,
    private val random: SecureRandomBytes,
    private val publisher: ConnectorSearchPublisher,
    private val work: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val lane = Mutex()
    private var key: Any? = null
    private var epoch = 0
    private var retryAfterMs: Long? = null
    private var disposed = false
    private val bridges = ArrayList<ConnectorSearchBridge>()

    /** The open bridges (tests and diagnostics; never print the bearer). */
    public val openBridges: List<ConnectorSearchBridge> get() = bridges.toList()

    public suspend fun sync() {
        lane.withLock { syncLocked() }
    }

    private suspend fun syncLocked() {
        val owner = environment.context()?.takeUnless { disposed }
        val agents = owner?.agents?.filter { it.cardsQualified }.orEmpty()
        val available =
            owner != null && owner.cardsEnabled && owner.connected && !owner.deleting && agents.isNotEmpty()
        val next =
            if (available && owner != null) {
                listOf(
                    owner.profileId, owner.baseUrl, owner.username, owner.generation,
                    listOf(owner.directory, owner.workspace, owner.locationRevision), agents.map { it.id },
                )
            } else {
                null
            }
        if (key == next) return
        val retry = retryAfterMs
        if (next != null && retry != null && retry > nowMs()) return
        retryAfterMs = null
        key = next
        val myEpoch = ++epoch
        closeAll()
        if (owner == null || next == null) return
        fun current(agent: SearchAgent): Boolean {
            val now = environment.context() ?: return false
            return !disposed && myEpoch == epoch && key == next && now.generation == owner.generation &&
                now.directory == owner.directory && now.workspace == owner.workspace &&
                now.locationRevision == owner.locationRevision && now.activeProfileId == owner.profileId &&
                now.profileId == owner.profileId && now.baseUrl == owner.baseUrl && now.username == owner.username &&
                now.cardsEnabled && now.connected && !now.deleting && agent in now.agents
        }
        for (agent in agents) {
            if (!current(agent)) return
            val started =
                ConnectorSearchBridge.start(server, random, work, { current(agent) }) { request ->
                    service.find(owner.profileId, agent, request)
                }
            val bridge = (started as? Outcome.Ok)?.value
            if (bridge == null) {
                if (current(agent)) backOff()
                continue
            }
            if (!current(agent)) {
                bridge.close()
                return
            }
            val published = publisher.publish(owner.profileId, agent.id, bridge.endpoint, bridge.bearer)
            if (published && current(agent)) {
                bridges += bridge
            } else {
                if (current(agent)) backOff()
                bridge.close()
            }
        }
    }

    private fun backOff() {
        key = null
        retryAfterMs = nowMs() + RETRY_AFTER_MS
    }

    private suspend fun closeAll() {
        val old = bridges.toList()
        bridges.clear()
        old.forEach { it.close() }
    }

    public suspend fun dispose() {
        lane.withLock {
            disposed = true
            key = null
            epoch++
            closeAll()
        }
    }

    public companion object {
        public const val RETRY_AFTER_MS: Long = 5_000
    }
}

/**
 * Turns the catalogue on from a connector search step's "Turn on" (Dart `enableConnectorCatalogue`): saves consent,
 * then loads the list within 30 s. True when consent is saved; a failed load still leaves it on.
 */
public suspend fun enableConnectorCatalogue(
    profileId: String?,
    cache: CatalogueCache,
    registry: RegistryClient,
    nowMs: () -> Long,
    redactor: Redactor,
): Boolean {
    if (profileId == null) return false
    val store = CatalogueStore(cache, profileId, registry, nowMs, redactor)
    try {
        store.load()
        if (store.setOptIn(true) is Outcome.Failed) return false
        withTimeoutOrNull(30_000) { store.refresh() }
        return store.snapshot.value.optedIn
    } finally {
        store.dispose()
    }
}
