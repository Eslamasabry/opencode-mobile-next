package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** [ConnectionDelivery.send] hands a rich request to the durable pipeline unchanged (full-composer contract). */
class ConnectionDeliveryRichTest {
    private class RichServer(val fake: FakeServerGateway = FakeServerGateway(listOf(Session(S1)))) :
        ServerGateway by fake, SessionSelectionGateway {
        var applied: Triple<String, String, String>? = null

        override suspend fun setSessionModel(session: SessionId, model: ModelRef, variant: String): Outcome<Unit> {
            applied = Triple(model.providerId, model.modelId, variant)
            fake.sessionList =
                persistentListOf(
                    Session(S1, selection = SessionSelection(model, variant, null, modelKnown = true, agentKnown = true)),
                )
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            fake.createSession(io.github.eslamasabry.oc.core.model.NewSession())
    }

    private fun delivery(test: TestScope, server: RichServer): ConnectionDelivery =
        ConnectionDelivery(
            gateway = server,
            profile =
                ServerProfile(
                    P1,
                    "local",
                    BackendKind.OpenCode2,
                    ServerOrigin("http", "127.0.0.1", 4097),
                    ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = null,
                ),
            scope = ConnectionCoroutineScope(test.backgroundScope.coroutineContext),
            clock = TestClock(Instant.fromEpochMilliseconds(NOW_MS)),
            stores = setOf(DeliveryStorage(kv(Disk(), test), DefaultWriterLanes(), ProfileDirectory { true })),
            dispatchers = TestDispatchers(StandardTestDispatcher(test.testScheduler)),
        )

    @Test
    fun `B-STATE-042 DLV-13 a rich send applies the choice before the prompt and delivers the exact files once`() =
        runTest {
            val server = RichServer()
            server.fake.events.connect()
            val files = persistentListOf(PromptAttachment("text/plain", "notes.txt", "file:///cache/notes.txt"))
            val choice = ModelSelection(ModelRef("anthropic", "claude-sonnet-4-6"), "Think")
            val d = delivery(this, server)
            val sent = d.send(REF, "summarize this", files, choice)
            assertIs<Outcome.Ok<*>>(sent)
            testScheduler.runCurrent()
            assertEquals(Triple("anthropic", "claude-sonnet-4-6", "Think"), server.applied, "the choice reached the model API first")
            val (session, prompt) = server.fake.sentPrompts.single()
            assertEquals(S1, session)
            assertEquals(choice, prompt.model)
            assertEquals(files, prompt.attachments)
            assertEquals("summarize this", prompt.text)
            assertIs<SendOutcome.Accepted>(d.engine!!.outcomes.value.single())
        }

    @Test
    fun `B-STATE-042 DLV-13 the old two-argument send keeps its meaning and applies no selection`() = runTest {
        val server = RichServer()
        server.fake.events.connect()
        val d = delivery(this, server)
        assertIs<Outcome.Ok<*>>(d.send(REF, "plain words"))
        testScheduler.runCurrent()
        assertNull(server.applied, "no model mutation for a plain send")
        val prompt = server.fake.sentPrompts.single().second
        assertTrue(prompt.attachments.isEmpty())
        assertNull(prompt.model)
        assertIs<SendOutcome.Accepted>(d.engine!!.outcomes.value.single())
    }
}
