package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DlvScopeTest {
    @Test
    fun `B-PROTO-043 DLV-3 a prompt retains its original location through the transport wait`() = runTest {
        val w = World(Disk(), this)
        val d = w.delivery()
        w.link.transport = {
            w.link.scope = DispatchScope(P1, w.link.scope.baseUrl, "/changed", "workspace")
            Unit.ok()
        }
        d.send(req("old location"))
        testScheduler.runCurrent()
        assertTrue(w.link.attempts.isEmpty())
        assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
        assertTrue(w.queue.entries().isEmpty())
    }

    @Test
    fun `B-PROTO-043 DLV-3 commands refuse a changed location or connection after waking`() = runTest {
        for (text in listOf("/review", "!pwd")) for (replace in listOf(false, true)) {
            val w = World(Disk(), this)
            val replacement = FakeLink()
            w.link.transport = {
                if (replace) w.connection.link.value = replacement
                else w.link.scope = DispatchScope(P1, w.link.scope.baseUrl, "/changed", null)
                Unit.ok()
            }
            assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.StaleScope)), w.delivery().send(req(text)))
            assertTrue(w.link.commands.isEmpty() && replacement.commands.isEmpty())
        }
    }
}
