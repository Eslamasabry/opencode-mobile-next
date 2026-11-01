package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.PromptDelivery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 09 §4.3: steer, never queue. */
class SteerTest {
    private val steering = ServerCapabilities(promptSteering = true, inbox = true)

    @Test
    fun `B-PROTO-025 the steer table, decided from capabilities and the mid-turn facet`() {
        val plain = ServerCapabilities()
        assertEquals(SendRoute.Plain, SteerPolicy.route(true, running = false, steering, false, null))
        assertEquals(SendRoute.Steer, SteerPolicy.route(true, running = true, steering, false, null))
        assertEquals(SendRoute.JoinTurn, SteerPolicy.route(true, true, plain, hasMidTurnFacet = true, midTurnPrompt = MidTurnPrompt.JoinsTurn))
        assertEquals(SendRoute.RestartTurn, SteerPolicy.route(true, true, plain, true, MidTurnPrompt.RestartsTurn))
        assertEquals(SendRoute.Blocked, SteerPolicy.route(true, true, plain, true, null), "agent not loaded yet: no promise")
        assertEquals(SendRoute.AfterReply, SteerPolicy.route(true, true, plain, false, null), "no steering and no inbox")
        assertEquals(SendRoute.Offline, SteerPolicy.route(false, true, steering, false, null))
        assertEquals(PromptDelivery.Steer, SendRoute.Steer.wireDelivery)
        assertNull(SendRoute.Plain.wireDelivery, "the default omits the field")
    }

    @Test
    fun `NoQueueChoiceTest no situation ever selects queue delivery`() {
        for (online in listOf(true, false)) for (running in listOf(true, false)) for (steer in listOf(true, false))
            for (inbox in listOf(true, false)) for (facet in listOf(true, false))
                for (mid in listOf(null, MidTurnPrompt.JoinsTurn, MidTurnPrompt.RestartsTurn)) {
                    val route = SteerPolicy.route(online, running, ServerCapabilities(promptSteering = steer, inbox = inbox), facet, mid)
                    assertTrue(route.wireDelivery != PromptDelivery.Queue, "$route")
                    if (running && steer && online) assertEquals(SendRoute.Steer, route, "where steering exists it steers")
                }
    }

    @Test
    fun `B-PROTO-025 a send while a steering turn runs goes out as a steer, transport first, without a marker`() = runTest {
        val w = World(Disk(), this, link = FakeLink(capabilities = steering))
        val gate = CompletableDeferred<Unit>()
        w.link.transport = { gate.await(); Outcome.Ok(Unit) }
        val facts = Facts().apply { running += REF }
        val d = w.delivery(facts = facts)
        val sending = async { d.send(req("also check the tests")) }
        testScheduler.runCurrent()
        assertTrue(d.pending.value.isEmpty(), "transport first: no bubble before the transport")
        gate.complete(Unit)
        assertIs<Outcome.Ok<LocalSendId>>(sending.await())
        testScheduler.runCurrent()
        assertEquals(PromptDelivery.Steer, w.link.delivered.single().prompt.delivery)
        assertTrue(w.queue.entries().isEmpty() && w.disk.queueBlob() == null, "steers never write a queue marker")
        assertEquals(SendRoute.Steer, (d.outcomes.value.single() as SendOutcome.Accepted).route)
    }

    @Test
    fun `B-PROTO-025 an idle send omits the delivery, a joining agent gets no delivery, a restarting one is stopped first`() = runTest {
        val w = World(Disk(), this)
        val facts = Facts()
        val d = w.delivery(facts = facts)
        d.send(req("idle"))
        testScheduler.runCurrent()
        assertNull(w.link.delivered.single().prompt.delivery)

        val join = World(Disk(), this, link = FakeLink().apply { hasMidTurnFacet = true; midTurn = MidTurnPrompt.JoinsTurn })
        val jf = Facts().apply { running += REF }
        join.delivery(facts = jf).send(req("join"))
        testScheduler.runCurrent()
        assertNull(join.link.delivered.single().prompt.delivery)
        assertTrue(join.link.stops.isEmpty())

        val restart = World(Disk(), this, link = FakeLink().apply { hasMidTurnFacet = true; midTurn = MidTurnPrompt.RestartsTurn })
        restart.delivery(facts = jf).send(req("restart"))
        testScheduler.runCurrent()
        assertEquals(listOf(S1), restart.link.stops, "Stop and send: stopped first")
        assertEquals(listOf("restart"), restart.link.deliveredTexts())

        val unknown = World(Disk(), this, link = FakeLink().apply { hasMidTurnFacet = true; midTurn = null })
        assertIs<Outcome.Failed>(unknown.delivery(facts = jf).send(req("not yet")))
        testScheduler.runCurrent()
        assertTrue(unknown.link.attempts.isEmpty() && unknown.link.stops.isEmpty(), "no promise of either: the text stays")
    }

    @Test
    fun `B-PROTO-025 routeFor gives the composer its words`() = runTest {
        val w = World(Disk(), this, link = FakeLink(capabilities = steering))
        val facts = Facts()
        val d = w.delivery(facts = facts)
        assertEquals(SendRoute.Plain, d.routeFor(REF))
        facts.running += REF
        assertEquals(SendRoute.Steer, d.routeFor(REF))
        w.link.connected = false
        assertEquals(SendRoute.Offline, d.routeFor(REF))
    }
}
