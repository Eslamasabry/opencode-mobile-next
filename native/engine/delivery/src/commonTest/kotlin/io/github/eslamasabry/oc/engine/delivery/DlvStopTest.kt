package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DlvStopTest {
    @Test
    fun `B-AGENT-243 DLV-6 each on-wire send retains its own Stop obligation`() = runTest {
        for (firstFails in listOf(false, true)) {
            val w = World(Disk(), this)
            val answers = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
            w.link.answer = { sent ->
                val index = if (sent.prompt.text == "first") 0 else 1
                answers[index].await()
                if (index == 1 && firstFails) FakeLink.Answer.Refused(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("prompt")))
                else FakeLink.Answer.Confirmed
            }
            val d = w.delivery()
            d.send(req("first"))
            d.send(req("second"))
            testScheduler.runCurrent()
            assertEquals(2, w.link.attempts.size)
            w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
            d.stop(REF)
            answers[1].complete(Unit) // reverse completion order
            testScheduler.runCurrent()
            answers[0].complete(Unit)
            testScheduler.runCurrent()
            assertEquals(if (firstFails) 2 else 3, w.link.stops.size)
        }
    }
}
