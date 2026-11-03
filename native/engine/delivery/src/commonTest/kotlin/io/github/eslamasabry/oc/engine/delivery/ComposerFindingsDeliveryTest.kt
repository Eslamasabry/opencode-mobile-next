package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/**
 * Regressions for the composer review findings (RUNBOOK §7, items 1, 2 and 8): each was red against
 * the GLM worker patch before its fix.
 */
class ComposerFindingsDeliveryTest {
    private val x = ModelSelection(ModelRef("prov", "x"))
    private val y = ModelSelection(ModelRef("prov", "y"))
    private val file = PromptAttachment("text/plain", "a.txt", "data:text/plain;base64,YQ==")

    private fun withModel(text: String, model: ModelSelection) =
        SendRequest(REF, text, model = model, typedText = text)

    @Test
    fun `B-STATE-119 finding 1 a second send's model never lands between another send's selection and its prompt`() =
        runTest {
            val w = World(Disk(), this)
            val log = mutableListOf<String>()
            val firstPrompt = CompletableDeferred<Unit>()
            w.link.onSelection = {
                log += "select ${w.link.selections.last().first?.model?.modelId}"
            }
            w.link.answer = { sent ->
                log += "prompt ${sent.prompt.text}"
                if (sent.prompt.text == "one") firstPrompt.await()
                FakeLink.Answer.Confirmed
            }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(withModel("one", x)))
            testScheduler.runCurrent()
            assertIs<Outcome.Ok<*>>(d.send(withModel("two", y)))
            testScheduler.runCurrent()
            // While "one" is on the wire, "two" must not move the session to another model.
            assertEquals(listOf("select x", "prompt one"), log)
            firstPrompt.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(listOf("select x", "prompt one", "select y", "prompt two"), log)
            assertEquals(2, w.link.delivered.size)
        }

    @Test
    fun `09-10_3 finding 2 a failed transport never mutates the session model`() = runTest {
        val w = World(Disk(), this)
        w.link.transport = { Failure.Network(NetworkWhy.Offline).failed() }
        val d = w.delivery()
        assertIs<Outcome.Ok<*>>(d.send(withModel("hold", x)))
        testScheduler.runCurrent()
        assertTrue(w.link.selections.isEmpty(), "preflight checks transport before the model")
        assertTrue(w.link.attempts.isEmpty())
        assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
    }

    @Test
    fun `B-STATE-119 finding 2 a changed epoch before the selection mutates neither server`() =
        runTest {
            val w = World(Disk(), this)
            val gate = CompletableDeferred<Unit>()
            w.link.transport = {
                gate.await()
                Outcome.Ok(Unit)
            }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(withModel("stale", x)))
            testScheduler.runCurrent()
            val other = FakeLink()
            w.connection.link.value = other
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(w.link.selections.isEmpty() && other.selections.isEmpty())
            assertTrue(w.link.attempts.isEmpty() && other.attempts.isEmpty())
        }

    @Test
    fun `B-PROTO-043 finding 2 slash or shell syntax with files goes out as a prompt that keeps its files`() =
        runTest {
            val w = World(Disk(), this)
            val d = w.delivery()
            for (typed in listOf("/review now", "!ls")) {
                val sent =
                    d.send(
                        SendRequest(
                            REF,
                            typed,
                            attachments = persistentListOf(file),
                            typedText = typed,
                        )
                    )
                assertIs<Outcome.Ok<*>>(sent, typed)
                testScheduler.runCurrent()
            }
            assertTrue(w.link.commands.isEmpty(), "no command path drops the files")
            assertEquals(listOf("/review now", "!ls"), w.link.deliveredTexts())
            assertTrue(w.link.delivered.all { it.prompt.attachments == listOf(file) })
        }

    private class Server(val fake: FakeServerGateway = FakeServerGateway(listOf(Session(S1)))) :
        ServerGateway by fake, SessionSelectionGateway {
        val variants = mutableListOf<String>()

        override suspend fun setSessionModel(
            session: SessionId,
            model: ModelRef,
            variant: String,
        ): Outcome<Unit> {
            variants += variant
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            fake.createSession(io.github.eslamasabry.oc.core.model.NewSession())
    }

    @Test
    fun `B-PROTO-018 finding 8 a requested variant named default is the no-variant readback`() =
        runTest {
            val g = Server()
            g.fake.sessionList =
                persistentListOf(
                    Session(
                        S1,
                        selection = SessionSelection(ModelRef("p", "m"), "", null, true, true),
                    )
                )
            val link = GatewaySendLink(P1, "http://h", g)
            assertEquals(
                Outcome.Ok(Unit),
                link.applySelection(S1, ModelSelection(ModelRef("p", "m"), "default"), null),
            )
            assertEquals(listOf(""), g.variants, "the sentinel is never sent as a variant")
        }
}
