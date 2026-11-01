package io.github.eslamasabry.oc.core.testing

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestDispatcher

/** Every dispatcher is the test dispatcher, serial() included. */
public class TestDispatchers(private val dispatcher: TestDispatcher) : AppDispatchers {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher

    override fun serial(name: String): CoroutineDispatcher = dispatcher
}

/** Records every call; scripted replies. */
public class FakeServerGateway(sessions: List<Session> = emptyList()) : ServerGateway {
    public val calls: MutableList<String> = mutableListOf()
    public var sessionList: ImmutableList<Session> = sessions.toImmutableList()
    public var closed: Boolean = false
        private set

    override val capabilities: StateFlow<ServerCapabilities> = MutableStateFlow(ServerCapabilities.Unknown)

    override suspend fun health(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "health" }

    override suspend fun sessions(): Outcome<ImmutableList<Session>> = Outcome.Ok(sessionList).also { calls += "sessions" }

    override suspend fun session(id: SessionId): Outcome<Session> =
        Outcome.Ok(sessionList.first { it.id == id }).also { calls += "session ${id.raw}" }

    override suspend fun close() {
        closed = true
        calls += "close"
    }
}

public class FakeOperationsGateway : ServerOperationsGateway

public class FakeGatewayFactory(
    override val backend: BackendKind = BackendKind.OpenCode2,
    public val gateway: FakeServerGateway = FakeServerGateway(),
) : GatewayFactory {
    public val connected: MutableList<ServerProfile> = mutableListOf()

    override suspend fun connect(profile: ServerProfile): Outcome<GatewayPair> {
        connected += profile
        return Outcome.Ok(GatewayPair(gateway, FakeOperationsGateway()))
    }
}

public val noSessions: ImmutableList<Session> = persistentListOf()
