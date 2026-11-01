package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.FormAnswer
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainTest {
    @Test
    fun `operations default to Unavailable naming the capability`() = runTest {
        val ops = FakeOperationsGateway()
        assertEquals(Outcome.Failed(Failure.Unavailable(FeatureId("sessionFork"))), ops.forkSession(SessionId("s")))
        assertEquals(Outcome.Failed(Failure.Unavailable(FeatureId("terminal"))), ops.listTerminals())
    }

    @Test
    fun `forms and inbox are inert by default - empty reads, Unavailable mutations`() = runTest {
        val gateway = FakeServerGateway()
        assertEquals(0, (gateway.pendingForms() as Outcome.Ok).value.size)
        assertEquals(0, (gateway.inbox(SessionId("s")) as Outcome.Ok).value.size)
        assertEquals(
            Failure.Unavailable(FeatureId("forms")),
            (gateway.replyForm(SessionId("s"), FormId("f"), FormAnswer(persistentMapOf())) as Outcome.Failed).failure,
        )
        assertEquals(
            Failure.Unavailable(FeatureId("inbox")),
            (gateway.setInboxDelivery(SessionId("s"), InboxItemId("i"), InboxDelivery.Steer) as Outcome.Failed).failure,
        )
    }

    @Test
    fun `optional facets are found only on gateways that implement them`() {
        val plain: ServerGateway = FakeServerGateway()
        assertNull(plain.facet<StagedRevertGateway>())
    }

    @Test
    fun `the project view keeps the current directory and location-less events, all keeps everything`() = runTest {
        val gateway = FakeServerGateway()
        gateway.setLocation(io.github.eslamasabry.oc.core.model.ServerLocation("/work/a"))
        val project = mutableListOf<GatewayEvent>()
        val all = mutableListOf<GatewayEvent>()
        backgroundScope.launch { gateway.events.project.collect { project += it.event } }
        backgroundScope.launch { gateway.events.all.collect { all += it.event } }
        runCurrent()
        gateway.events.connect()
        gateway.events.emit(GatewayEvent.RunStarted(SessionId("a")), directory = "/work/a")
        gateway.events.emit(GatewayEvent.RunStarted(SessionId("b")), directory = "/work/b")
        runCurrent()
        assertEquals(listOf(GatewayEvent.Connected, GatewayEvent.RunStarted(SessionId("a"))), project)
        assertEquals(3, all.size)
        assertIs<StreamStatus.Connected>(gateway.events.status.value)
        assertEquals(1, gateway.events.connects.first())
    }
}
