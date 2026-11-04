package io.github.eslamasabry.oc.feature.computer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class ComputerPresenterTest {
    private val opened = mutableListOf<ConversationRef>()
    private var backs = 0

    private fun TestScope.page(tools: FakeComputerTools): ComputerPresenter =
        ComputerPresenter(this, COMPUTER, tools, onBack = { backs++ }, openChat = { opened += it }).also { advanceUntilIdle() }

    @Test
    fun `B-AGENT-167 updating asks first then shows ordered phases without ending the request`() = runTest {
        val tools = FakeComputerTools()
        val p = page(tools)
        assertEquals("1.4.0", p.state.value.version)
        p.askUpdate()
        assertTrue(p.state.value.confirming)
        assertTrue("update" !in tools.calls, "asking alone sends nothing")
        p.cancelUpdate()
        assertFalse(p.state.value.confirming)
        assertTrue("update" !in tools.calls)
    }

    @Test
    fun `B-AGENT-167 progress phases arrive in order and the answer reads the new version`() = runTest {
        val tools = FakeComputerTools()
        val p = page(tools)
        tools.steps = listOf(UpdateStep.Starting, UpdateStep.Downloading, UpdateStep.Installing, UpdateStep.Finishing)
        p.askUpdate()
        p.confirmUpdate()
        assertTrue(p.state.value.updating)
        assertEquals(UpdateStep.Starting, p.state.value.step)
        advanceUntilIdle()
        assertEquals(tools.steps, tools.seenSteps)
        assertFalse(p.state.value.updating)
        assertNull(p.state.value.step)
        assertEquals(UpdateLine.Done("1.5.0"), p.state.value.update)
        assertEquals("1.5.0", p.state.value.version)
        assertEquals(1, tools.calls.count { it == "update" })
    }

    @Test
    fun `B-AGENT-167 matching versions report already up to date`() = runTest {
        val tools = FakeComputerTools().apply { updateAnswer = Outcome.Ok(UpdateAnswer.Current) }
        val p = page(tools)
        p.askUpdate(); p.confirmUpdate(); advanceUntilIdle()
        assertEquals(UpdateLine.Current, p.state.value.update)
    }

    @Test
    fun `B-AGENT-167 a refused update is a failure line with no thrown error and the computer's words stay out`() = runTest {
        val tools = FakeComputerTools().apply { updateAnswer = Outcome.Ok(UpdateAnswer.Refused) }
        val p = page(tools)
        p.askUpdate(); p.confirmUpdate(); advanceUntilIdle()
        assertEquals(UpdateLine.Failed(null), p.state.value.update)
        assertFalse(p.state.value.updating)
    }

    @Test
    fun `B-AGENT-167 a dropped link during the restart says it reconnects, other failures keep Details`() = runTest {
        val tools = FakeComputerTools().apply { updateAnswer = Outcome.Failed(Failure.Network(NetworkWhy.Reset)) }
        val p = page(tools)
        p.askUpdate(); p.confirmUpdate(); advanceUntilIdle()
        assertEquals(UpdateLine.Dropped, p.state.value.update)
        val refused = Failure.Refused(RefusalReason.StaleScope)
        tools.updateAnswer = Outcome.Failed(refused)
        p.askUpdate(); p.confirmUpdate(); advanceUntilIdle()
        assertEquals(UpdateLine.Failed(refused), p.state.value.update)
    }

    @Test
    fun `B-AGENT-174 the import list loads on open and importing opens the conversation it made`() = runTest {
        val tools = FakeComputerTools()
        val p = page(tools)
        val ready = assertIs<ImportPhase.Ready>(p.state.value.imports)
        assertEquals(listOf(NEWER, OLDER), ready.list.items, "newest first, as the computer listed them")
        assertEquals(2, ready.list.alreadyHere)
        p.import(NEWER.key)
        advanceUntilIdle()
        assertTrue("import:${NEWER.key}" in tools.calls)
        assertEquals(listOf(ConversationRef(COMPUTER, io.github.eslamasabry.oc.core.common.ids.SessionId("agent-9"))), opened)
        assertNull(p.state.value.importing)
    }

    @Test
    fun `B-AGENT-174 a refused import is a failure and opens nothing`() = runTest {
        val refused = Failure.Refused(RefusalReason.StaleScope)
        val tools = FakeComputerTools().apply { importAnswer = Outcome.Failed(refused) }
        val p = page(tools)
        p.import(OLDER.key)
        advanceUntilIdle()
        assertTrue(opened.isEmpty())
        assertEquals(refused, p.state.value.importFailed)
    }

    @Test
    fun `B-AGENT-174 a list that did not load offers Try again`() = runTest {
        val down = Failure.Network(NetworkWhy.Reset)
        val tools = FakeComputerTools().apply { importableAnswer = Outcome.Failed(down) }
        val p = page(tools)
        assertEquals(ImportPhase.Failed(down), p.state.value.imports)
        tools.importableAnswer = FakeComputerTools().importableAnswer
        p.reloadImports()
        advanceUntilIdle()
        assertIs<ImportPhase.Ready>(p.state.value.imports)
        assertEquals(2, tools.calls.count { it == "importable" })
    }
}
