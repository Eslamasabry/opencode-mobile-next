package io.github.eslamasabry.oc.engine.connection

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerProfile
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zero backends is legal in slice 0 (no :protocol:* yet); the consumer declares the multibinding
 * (plan 03 §13.5).
 */
@ContributesTo(AppScope::class)
public interface ConnectionMultibindings {
    @Multibinds(allowEmpty = true) public val gatewayFactories: Set<GatewayFactory>
    @Multibinds(allowEmpty = true) public val credentialSources: Set<ConnectionCredentialSource>
    @Multibinds(allowEmpty = true) public val transientSources: Set<TransientConnectionSource>
}

/**
 * A trusted engine resolves only its own runtime profiles; it never inserts them into the profile
 * store.
 */
public fun interface TransientConnectionSource {
    public suspend fun resolve(ref: ConversationRef): Outcome<TransientConnection?>
}

/**
 * Independent native owner reader, available to gateway factories without a registry dependency
 * cycle.
 */
@SingleIn(AppScope::class)
@Inject
public class TransientOwners {
    private val owners = mutableMapOf<ProfileId, TransientConnection>()

    @Synchronized
    public fun ownerOf(alias: ProfileId): ProfileId? =
        owners[alias]?.takeIf { it.currentOwner() }?.ownerProfileId

    @Synchronized
    internal fun admit(spec: TransientConnection) {
        owners[spec.profile.id] = spec
    }

    @Synchronized
    internal fun retire(alias: ProfileId) {
        owners.remove(alias)
    }
}

/**
 * The owner supplies the native admission proof; the registry checks it before and after dialing.
 */
public class TransientConnection(
    public val profile: ServerProfile,
    public val credentials: ConnectionCredentials,
    public val ownerProfileId: ProfileId,
    public val location: ServerLocation,
    public val currentOwner: () -> Boolean,
)

/**
 * Owns every live [ConnectionGraph] (plan 03 §13.4). App-scoped code reaches connection state only
 * through here.
 */
@SingleIn(AppScope::class)
@Inject
public class ConnectionRegistry(
    private val factories: Set<GatewayFactory>,
    private val graphFactory: ConnectionGraph.Factory,
    public val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val credentialSources: Set<ConnectionCredentialSource> = emptySet(),
    private val transientSources: Set<TransientConnectionSource> = emptySet(),
    private val transientOwners: TransientOwners = TransientOwners(),
) {
    init {
        check(factories.groupBy { it.backend }.all { it.value.size == 1 }) {
            "two GatewayFactory for one backend"
        }
        check(credentialSources.size <= 1) { "multiple connection credential sources" }
    }

    public val mainContext: kotlin.coroutines.CoroutineContext
        get() = dispatchers.main

    private val mutableLive =
        MutableStateFlow<PersistentMap<ProfileId, ConnectionGraph>>(persistentMapOf())
    public val live: StateFlow<ImmutableMap<ProfileId, ConnectionGraph>> = mutableLive.asStateFlow()

    // Whole open/close operations are serialised (audit MAJOR 6: a serial dispatcher alone is not a
    // mutex).
    private val admission = Mutex()
    private val transient = mutableMapOf<ProfileId, TransientConnection>()

    /**
     * Idempotent: returns the live graph when one exists. Creates the graph's scope, connects the
     * gateways in it (plan 04 §11.1: `GatewayFactory.connect(spec)`), then creates the extension. A
     * failed connect cancels the scope, so nothing it started outlives the failure.
     */
    public suspend fun open(profile: ServerProfile): Outcome<ConnectionGraph> = admission.withLock {
        live.value[profile.id]?.let {
            return@withLock Outcome.Ok(it)
        }
        val factory =
            factories.firstOrNull { it.backend == profile.backend }
                ?: return@withLock Outcome.Failed(
                    Failure.Unavailable(FeatureId("backend.${profile.backend}"))
                )
        val scope =
            ConnectionCoroutineScope(
                supervisedContext(
                    appScope.coroutineContext.job,
                    dispatchers.default,
                    "conn-${profile.id.raw}",
                )
            )
        val credentials =
            when (val read = credentialSources.singleOrNull()?.resolve(profile)) {
                null ->
                    ConnectionCredentials
                        .None // isolated fixtures and unauthenticated slice-0 graphs
                is Outcome.Ok -> read.value
                is Outcome.Failed -> {
                    (scope.coroutineContext[Job])?.cancelAndJoin()
                    return@withLock read
                }
            }
        val spec = GatewaySpec(profile = profile, credentials = credentials, scope = scope)
        when (val pair = factory.connect(spec)) {
            is Outcome.Failed -> {
                (scope.coroutineContext[Job])?.cancelAndJoin()
                pair
            }
            is Outcome.Ok -> {
                val graph = graphFactory.create(profile, pair.value, scope)
                graph.session.markConnected()
                mutableLive.update { it.putting(profile.id, graph) }
                Outcome.Ok(graph)
            }
        }
    }

    /**
     * Opens only the phone helper's exact owner alias, under that saved owner's native lifetime.
     */
    public suspend fun openTransient(
        profile: ServerProfile,
        credentials: ConnectionCredentials,
        ownerProfileId: ProfileId,
        currentOwner: () -> Boolean,
        location: ServerLocation = ServerLocation.Default,
    ): Outcome<ConnectionGraph> = admission.withLock {
        fun current(): Boolean =
            profile.id.raw == "${ownerProfileId.raw}.agents" &&
                profile.backend == BackendKind.Paseo &&
                profile.managedOnPhone &&
                profile.origin.host in setOf("127.0.0.1", "localhost", "::1") &&
                profile.origin.port == 4099 &&
                !credentials.secret.isNullOrEmpty() &&
                currentOwner()
        if (!current()) return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val previous = transient[profile.id]
        live.value[profile.id]?.let { graph ->
            if (previous == null || previous.ownerProfileId != ownerProfileId)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            if (
                previous.profile == profile &&
                    previous.credentials == credentials &&
                    previous.currentOwner() &&
                    graph.session.server.location.value == location
            ) {
                return@withLock Outcome.Ok(graph)
            }
            closeLocked(profile.id)
        }
        if (!current()) return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val factory =
            factories.singleOrNull { it.backend == profile.backend }
                ?: return@withLock Outcome.Failed(
                    Failure.Unavailable(FeatureId("backend.${profile.backend}"))
                )
        val scope =
            ConnectionCoroutineScope(
                supervisedContext(
                    appScope.coroutineContext.job,
                    dispatchers.default,
                    "conn-${profile.id.raw}",
                )
            )
        val proof =
            TransientConnection(profile, credentials, ownerProfileId, location, currentOwner)
        transientOwners.admit(proof)
        val spec = GatewaySpec(profile, credentials, scope, location = location)
        val connected =
            try {
                factory.connect(spec)
            } catch (error: Throwable) {
                transientOwners.retire(profile.id)
                scope.coroutineContext.job.cancel()
                throw error
            }
        when (connected) {
            is Outcome.Failed -> {
                transientOwners.retire(profile.id)
                scope.coroutineContext.job.cancelAndJoin()
                connected
            }
            is Outcome.Ok -> {
                val healthy =
                    try {
                        connected.value.live.health()
                    } catch (error: Throwable) {
                        transientOwners.retire(profile.id)
                        scope.coroutineContext.job.cancel()
                        throw error
                    }
                if (healthy is Outcome.Failed || !current()) {
                    transientOwners.retire(profile.id)
                    scope.coroutineContext.job.cancelAndJoin()
                    connected.value.live.close()
                    return@withLock if (healthy is Outcome.Failed) healthy
                    else Failure.Refused(RefusalReason.StaleScope).failed()
                }
                val graph = graphFactory.create(profile, connected.value, scope)
                graph.session.markConnected()
                transient[profile.id] = proof
                mutableLive.update { it.putting(profile.id, graph) }
                Outcome.Ok(graph)
            }
        }
    }

    public fun ownerOfTransient(alias: ProfileId): ProfileId? =
        if (live.value[alias] != null) transientOwners.ownerOf(alias) else null

    /**
     * Cold chat recovery resolves a durable id and project through its owning engine; it never
     * creates an agent.
     */
    public suspend fun recoverTransient(ref: ConversationRef): Outcome<ConnectionGraph?> {
        if (!ref.profileId.raw.endsWith(".agents")) return Outcome.Ok(null)
        for (source in transientSources) {
            when (val read = source.resolve(ref)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> {
                    val spec = read.value ?: continue
                    if (spec.profile.id != ref.profileId)
                        return Failure.Refused(RefusalReason.StaleScope).failed()
                    return openTransient(
                        spec.profile,
                        spec.credentials,
                        spec.ownerProfileId,
                        spec.currentOwner,
                        spec.location,
                    )
                }
            }
        }
        return Outcome.Ok(null)
    }

    /** The conversation's own backend. Null = that connection is not live. */
    public fun graphFor(ref: ConversationRef): ConnectionGraph? = live.value[ref.profileId]

    private suspend fun closeLocked(profile: ProfileId) {
        transient.remove(profile)
        transientOwners.retire(profile)
        val graph = live.value[profile] ?: return
        mutableLive.update { it.removing(profile) }
        graph.scope.coroutineContext.job.cancelAndJoin()
        graph.session.close()
    }

    /** Stop owner and every owned helper alias under one admission lock, then join all scopes. */
    public suspend fun close(profile: ProfileId) {
        admission.withLock {
            val aliases = transient.filterValues { it.ownerProfileId == profile }.keys.toList()
            for (alias in aliases) closeLocked(alias)
            closeLocked(profile)
        }
    }
}
