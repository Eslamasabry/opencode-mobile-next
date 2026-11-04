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
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
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
}

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

    /** The conversation's own backend. Null = that connection is not live. */
    public fun graphFor(ref: ConversationRef): ConnectionGraph? = live.value[ref.profileId]

    /**
     * Retires only the captured generation; an edited-source reconnect must never close a
     * replacement.
     */
    public suspend fun closeIfCurrent(profile: ProfileId, expected: ConnectionGraph): Boolean =
        admission.withLock {
            if (live.value[profile] !== expected) return@withLock false
            mutableLive.update { it.removing(profile) }
            (expected.scope.coroutineContext[Job])?.cancelAndJoin()
            expected.session.close()
            true
        }

    /**
     * Stop admission → cancel streams (and join) → release (ADR §9.1). Writer-lane drain joins in
     * slice 1.
     */
    public suspend fun close(profile: ProfileId) {
        admission.withLock {
            val graph = live.value[profile] ?: return@withLock
            mutableLive.update { it.removing(profile) }
            (graph.scope.coroutineContext[Job])?.cancelAndJoin()
            graph.session.close()
        }
    }
}
