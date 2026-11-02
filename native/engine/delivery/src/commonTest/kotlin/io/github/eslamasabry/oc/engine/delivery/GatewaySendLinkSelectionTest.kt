package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The production [GatewaySendLink] selection contract: mutate once, then a strict readback (full-composer contract). */
class GatewaySendLinkSelectionTest {
    private class Server(val fake: FakeServerGateway = FakeServerGateway(listOf(Session(S1)))) :
        ServerGateway by fake, SessionSelectionGateway {
        val modelMutations = mutableListOf<Triple<ModelRef, String, SessionId>>()
        val agentMutations = mutableListOf<Pair<String, SessionId>>()
        var modelAnswer: Outcome<Unit> = Outcome.Ok(Unit)
        var agentAnswer: Outcome<Unit> = Outcome.Ok(Unit)

        fun readback(selection: SessionSelection?) {
            fake.sessionList = persistentListOf(Session(S1, selection = selection))
        }

        override suspend fun setSessionModel(session: SessionId, model: ModelRef, variant: String): Outcome<Unit> {
            modelMutations += Triple(model, variant, session)
            return modelAnswer
        }

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> {
            agentMutations += agent to session
            return agentAnswer
        }

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            fake.createSession(io.github.eslamasabry.oc.core.model.NewSession())
    }

    private fun sel(provider: String = "p", id: String = "m", variant: String? = "Think") =
        ModelSelection(ModelRef(provider, id), variant)

    private fun known(model: ModelRef?, variant: String) =
        SessionSelection(model, variant, null, modelKnown = model != null, agentKnown = true)

    @Test
    fun `B-STATE-119 DLV-12 the mutation happens once and the readback must match provider, id and normalized variant`() =
        runTest {
            val g = Server()
            g.readback(known(ModelRef("p", "m"), "Think"))
            val link = GatewaySendLink(P1, "http://h", g)
            assertEquals(Outcome.Ok(Unit), link.applySelection(S1, sel(), "build"))
            assertEquals(listOf(Triple(ModelRef("p", "m"), "Think", S1)), g.modelMutations)
            assertEquals(listOf("build" to S1), g.agentMutations)
            assertTrue(g.fake.calls.contains("session ses_1"), "the session was read back")
            // A null variant mutates with "" and matches an empty readback; whitespace normalizes away.
            g.readback(known(ModelRef("p", "m"), ""))
            assertEquals(Outcome.Ok(Unit), link.applySelection(S1, sel(variant = null), null))
            g.readback(known(ModelRef("p", "m"), "Think"))
            assertEquals(Outcome.Ok(Unit), link.applySelection(S1, sel(variant = " Think "), null))
        }

    @Test
    fun `B-STATE-119 DLV-12 a readback naming another provider, id or variant fails closed after one mutation`() =
        runTest {
            val g = Server()
            g.readback(known(ModelRef("p", "other"), "Think"))
            val link = GatewaySendLink(P1, "http://h", g)
            val failed = link.applySelection(S1, sel(), null)
            assertIs<Outcome.Failed>(failed)
            val reason = assertIs<Failure.Refused>(failed.failure)
            assertEquals(RefusalReason.StaleScope, reason.reason)
            g.readback(known(ModelRef("other", "m"), "Think"))
            assertIs<Outcome.Failed>(link.applySelection(S1, sel(), null))
            g.readback(known(ModelRef("p", "m"), "beta"))
            assertIs<Outcome.Failed>(link.applySelection(S1, sel(), null))
            assertEquals(3, g.modelMutations.size, "one mutation attempt per call, never a retry")
        }

    @Test
    fun `B-STATE-119 DLV-12 a missing or unknown readback fails closed`() = runTest {
        val g = Server()
        g.readback(null)
        val link = GatewaySendLink(P1, "http://h", g)
        assertIs<Outcome.Failed>(link.applySelection(S1, sel(), null))
        g.readback(known(ModelRef("p", "m"), "Think").copy(modelKnown = false))
        val unknown = link.applySelection(S1, sel(), null)
        assertIs<Outcome.Failed>(unknown)
        assertIs<Failure.Refused>(unknown.failure)
        // A readback that itself fails returns as-is: still no prompt upstream.
        g.readback(known(ModelRef("p", "m"), "Think"))
        g.fake.failNext = Failure.Server(status = 503)
        assertEquals(Failure.Server(status = 503).failed(), link.applySelection(S1, sel(), null))
        assertEquals(3, g.modelMutations.size)
    }

    @Test
    fun `B-STATE-119 DLV-12 without the facet an advertised switching capability fails closed, inline semantics stay`() =
        runTest {
            val fake = FakeServerGateway(listOf(Session(S1)))
            val link = GatewaySendLink(P1, "http://h", fake)
            val refused = link.applySelection(S1, sel(), null)
            assertIs<Outcome.Failed>(refused)
            val unavailable = assertIs<Failure.Unavailable>(refused.failure)
            assertEquals(FeatureId("sessionModelProviderSwitching"), unavailable.feature)
            assertFalse(fake.calls.contains("session ses_1"), "no readback without a facet")
            fake.capabilities.value = ServerCapabilities(sessionModelProviderSwitching = false)
            assertEquals(Outcome.Ok(Unit), link.applySelection(S1, sel(), "build"), "the prompt keeps the inline model")
            assertEquals(Outcome.Ok(Unit), link.applySelection(S1, null, null), "nothing to apply")
            assertTrue(fake.calls.isEmpty(), "no server call at all")
        }

    @Test
    fun `B-STATE-119 DLV-12 an agent-only choice mutates the agent without a model readback`() = runTest {
        val g = Server()
        val link = GatewaySendLink(P1, "http://h", g)
        assertEquals(Outcome.Ok(Unit), link.applySelection(S1, null, "build"))
        assertEquals(listOf("build" to S1), g.agentMutations)
        assertTrue(g.modelMutations.isEmpty())
        assertFalse(g.fake.calls.contains("session ses_1"), "the model readback belongs to a model choice")
    }

    @Test
    fun `B-STATE-119 DLV-12 a failed mutation returns as-is, with no readback and no retry`() = runTest {
        val g = Server()
        g.modelAnswer = Failure.Server(status = 500).failed()
        g.agentAnswer = Failure.Busy().failed()
        val link = GatewaySendLink(P1, "http://h", g)
        assertEquals(Failure.Server(status = 500).failed(), link.applySelection(S1, sel(), null))
        assertFalse(g.fake.calls.contains("session ses_1"), "a failed mutation is never read back")
        assertEquals(1, g.modelMutations.size)
        assertEquals(Failure.Busy().failed(), link.applySelection(S1, null, "build"))
        assertEquals(1, g.agentMutations.size)
    }
}
