package io.github.eslamasabry.oc.feature.chat.relations

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.feature.chat.relations.presentation.RelationStatus
import io.github.eslamasabry.oc.feature.chat.relations.presentation.SessionRelationsPins
import io.github.eslamasabry.oc.feature.chat.relations.presentation.SessionRelationsViewModel
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SessionRelationsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The Subagents page (Go to › Subagents) over a server that lists children, as OpenCode 2 now does: it used to fail
 * with Unavailable("subagentSessions") because the flag was off and nothing answered `sessionChildren`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRelationsSubAgentsTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val parent = SessionId("ses_parent")
    private val ref = ConversationRef(ProfileId("p"), parent)
    private val profile =
        ServerProfile(
            ref.profileId,
            "Fixture",
            BackendKind.OpenCode2,
            ServerOrigin("https", "example.invalid", 443),
            ProfileTrust(null, null),
            false,
            null,
        )

    private val children =
        listOf(
            Session(
                SessionId("ses_explore"),
                "Look around (@explore subagent)",
                parentId = parent,
                agent = "explore",
                time = SessionTime(createdMs = 2),
            ),
            Session(SessionId("ses_review"), "Review the diff", parentId = parent, time = SessionTime(createdMs = 1)),
        )

    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) = Unit

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private inner class Fixture(val test: TestScope, subagentSessions: Boolean) {
        val gateway = FakeServerGateway(listOf(Session(parent, "Parent")) + children)
        val listed = mutableListOf<SessionId>()
        val operations =
            object : FakeOperationsGateway() {
                override suspend fun sessionChildren(id: SessionId): Outcome<ImmutableList<Session>> {
                    if (!subagentSessions) return super.sessionChildren(id)
                    listed += id
                    return Outcome.Ok(children.filter { it.parentId == id }.toImmutableList())
                }
            }
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val app = AppCoroutineScope(test.backgroundScope.coroutineContext)
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                dispatcher,
            )
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = profile.backend

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec) = Outcome.Ok(GatewayPair(gateway, operations))
                    }
                ),
                { p, pair, scope ->
                    object : ConnectionGraph, ChatGraphAccessors {
                        override val profile = p
                        override val scope = scope
                        override val session = ConnectionSession(p, pair.live, scope)
                        override val chatDelivery =
                            ConnectionDelivery(
                                pair.live,
                                p,
                                scope,
                                clock,
                                setOf(DeliveryStorage(kv, DefaultWriterLanes(), ProfileDirectory { true })),
                                dispatchers,
                            )
                        override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                        override val chatGateway = pair.live
                        override val chatOperations = pair.operations
                    }
                },
                app,
                dispatchers,
            )

        init {
            gateway.capabilities.value =
                gateway.capabilities.value.copy(projectManagement = true, subagentSessions = subagentSessions)
            gateway.statuses = persistentMapOf(SessionId("ses_explore") to SessionStatus.Busy)
        }

        suspend fun page(): SessionRelationsViewModel {
            gateway.events.connect()
            registry.open(profile)
            test.runCurrent()
            val pins = SessionRelationsPins(emptySet())
            val vm = SessionRelationsViewModel(SessionRelationsRoute(ref), registry, Nav, pins, dispatchers, clock)
            test.runCurrent()
            return vm
        }
    }

    @Test
    fun `S30-CHAT-003 the Subagents page lists the children where the server lists them, never an error`() = runTest {
        val f = Fixture(this, subagentSessions = true)
        val vm = f.page()
        val state = vm.state.value
        assertNull(state.failure, "the page must not fail: $state")
        assertEquals(parent, state.parent?.id)
        assertEquals(listOf(SessionId("ses_explore"), SessionId("ses_review")), state.children.map { it.id })
        assertEquals(RelationStatus.Working, state.children.first().status)
        assertEquals(RelationStatus.Done, state.children.last().status)
        assertEquals(listOf(parent), f.listed)
    }

    @Test
    fun `S30-CHAT-003 a server without subagentSessions still answers the named Unavailable, read nothing`() = runTest {
        val f = Fixture(this, subagentSessions = false)
        val vm = f.page()
        assertEquals(Failure.Unavailable(FeatureId("subagentSessions")), vm.state.value.failure)
        assertEquals(emptyList(), f.listed)
    }
}
