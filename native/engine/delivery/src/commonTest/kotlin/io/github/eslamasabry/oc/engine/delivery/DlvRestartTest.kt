package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.StopResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DlvRestartTest {
    @Test
    fun `B-PROTO-025 DLV-8 restart sends only after a confirmed stop or nothing running`() = runTest {
        for (result in listOf(Failure.TimedOut().failed(), StopResult.Uncertain.ok(), StopResult.Stopped.ok(), StopResult.NothingRunning.ok())) {
            val w = World(Disk(), this, link = FakeLink().apply { hasMidTurnFacet = true; midTurn = MidTurnPrompt.RestartsTurn; stopAnswer = { result } })
            val d = w.delivery(facts = Facts().apply { running += REF })
            val sent = d.send(req("restart"))
            testScheduler.runCurrent()
            val confirmed = result is Outcome.Ok && result.value != StopResult.Uncertain
            if (confirmed) {
                assertIs<Outcome.Ok<LocalSendId>>(sent)
                assertEquals(listOf("restart"), w.link.deliveredTexts())
            } else {
                assertIs<Outcome.Failed>(sent)
                assertTrue(w.link.attempts.isEmpty() && w.queue.entries().isEmpty() && d.pending.value.isEmpty())
            }
        }
    }
}
