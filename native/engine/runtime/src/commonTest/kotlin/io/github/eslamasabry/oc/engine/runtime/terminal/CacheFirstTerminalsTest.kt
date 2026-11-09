package io.github.eslamasabry.oc.engine.runtime.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class CacheFirstTerminalsTest {
    private val ref = ConversationRef(ProfileId("phone"), SessionId("conversation"))
    private val row = TerminalProcess("t1", "build", "bash", persistentListOf("-c", "secret"), "/p", true, 7, null)

    private class Shelf(var value: KeptTerminals? = null) : TerminalLastKnown {
        override fun peek(ref: ConversationRef) = value
        override suspend fun read(ref: ConversationRef) = value
        override suspend fun keep(ref: ConversationRef, kept: KeptTerminals) {
            value = Json.decodeFromString(KeptTerminals.serializer(), Json.encodeToString(KeptTerminals.serializer(), kept))
        }
    }

    private fun source(list: () -> Outcome<ImmutableList<TerminalProcess>>) =
        RemoteTerminalSource {
            Outcome.Ok(
                RemoteTerminalTarget(
                    object : TerminalGateway {
                        override suspend fun listTerminals() = list()
                    }
                ) { true }
            )
        }

    private fun TestScope.owner(src: RemoteTerminalSource, shelf: TerminalLastKnown?) =
        RemoteTerminals(ref, src, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), shelf)

    @Test
    fun `CF-43 the first frame with kept terminals has the live rows' ids and titles, and no arguments`() = runTest {
        val shelf = Shelf()
        val live = owner(source { Outcome.Ok(persistentListOf(row)) }, shelf)
        live.refresh()
        val reopened = owner(source { Outcome.Ok(persistentListOf(row)) }, shelf)
        val first = reopened.state.value
        assertEquals(false, first.loading)
        assertEquals(live.state.value.terminals.map { it.id to it.title }, first.terminals.map { it.id to it.title })
        assertTrue(first.terminals.single().arguments.isEmpty())
    }

    @Test
    fun `CF-44 a failed list keeps the kept rows`() = runTest {
        val shelf = Shelf(KeptTerminals.of(listOf(row)))
        val o = owner(source { Outcome.Failed(Failure.Network()) }, shelf)
        o.refresh()
        assertNotNull(o.state.value.failure)
        assertEquals(listOf("t1"), o.state.value.terminals.map { it.id })
    }

    @Test
    fun `CF-45 with nothing kept the first-ever load is loading`() = runTest {
        assertTrue(owner(source { Outcome.Ok(persistentListOf()) }, Shelf()).state.value.loading)
    }
}
