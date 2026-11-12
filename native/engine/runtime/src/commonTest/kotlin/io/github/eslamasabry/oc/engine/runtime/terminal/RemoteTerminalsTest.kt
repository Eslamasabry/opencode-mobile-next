package io.github.eslamasabry.oc.engine.runtime.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class RemoteTerminalsTest {
    private val ref = ConversationRef(ProfileId("phone"), SessionId("conversation"))

    @Test
    fun `B-AGENT-159 opening a conversation terminal selects its project only on page entry`() =
        runTest {
            val source = Source()
            val owner =
                RemoteTerminals(
                    ref,
                    source,
                    backgroundScope,
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                )

            owner.refresh()
            assertEquals(1, source.activations)
            assertEquals(1, source.lists)
            assertNull(owner.state.value.failure)

            source.current = false
            owner.refresh()
            owner.create()
            assertEquals(1, source.activations)
            assertEquals(1, source.lists)
            assertEquals(0, source.creates)
            assertEquals(Failure.Refused(RefusalReason.StaleScope), owner.state.value.failure)
        }

    @Test
    fun `B-AGENT-159 a refused terminal entry never switches project on retry or create`() =
        runTest {
            val source = Source().apply { current = false }
            val owner =
                RemoteTerminals(
                    ref,
                    source,
                    backgroundScope,
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                )

            owner.refresh()
            owner.refresh()
            owner.create()

            assertEquals(1, source.activations)
            assertEquals(0, source.lists)
            assertEquals(0, source.creates)
            assertEquals(Failure.Refused(RefusalReason.StaleScope), owner.state.value.failure)
        }

    @Test
    fun `W4 a new terminal is created with the name the screen numbered it with`() =
        runTest {
            val source = Source()
            val owner =
                RemoteTerminals(ref, source, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)))
            owner.refresh()
            owner.create("Terminal 2")
            assertEquals("Terminal 2", source.lastTitle)
        }

    private class Source : RemoteTerminalSource {
        var lastTitle: String? = null
        var current = true
        var activations = 0
        var lists = 0
        var creates = 0
        private val gateway =
            object : TerminalGateway {
                override suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> {
                    lists++
                    return Outcome.Ok(persistentListOf())
                }

                override suspend fun createTerminal(title: String?): Outcome<TerminalProcess> {
                    creates++
                    lastTitle = title
                    return Outcome.Failed(Failure.Unexpected())
                }
            }

        override suspend fun activate(ref: ConversationRef): Outcome<RemoteTerminalTarget> {
            activations++
            return resolve(ref)
        }

        override suspend fun resolve(ref: ConversationRef): Outcome<RemoteTerminalTarget> =
            if (current) Outcome.Ok(RemoteTerminalTarget(gateway) { current })
            else Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    }
}
