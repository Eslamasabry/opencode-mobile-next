package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.requests.ask.AskTarget
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.AskView
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.composer.presentation.SendFieldRules
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderRules
import io.github.eslamasabry.oc.feature.chat.presentation.WorkLineRules
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardRules
import io.github.eslamasabry.oc.feature.chat.ui.FoldAnchorRules
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.ModelNames
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The S8 chat polish rules (auditor sweep of a2e7d3fa5), each from its finding. */
class S8ChatPolishRulesTest {
    // ---- P-003: a normal send shows no "may already have been sent" error ----

    @Test
    fun `P-003 a send still on its way blocks Send but shows no already-sent error`() {
        val inFlight = DraftFacts(text = "hi", sendBlocked = true, sendInFlight = true)
        assertFalse(SendFieldRules.showsAlreadySent(inFlight))
        val uncertain = DraftFacts(text = "hi", sendBlocked = true, sendInFlight = false)
        assertTrue(SendFieldRules.showsAlreadySent(uncertain))
        assertFalse(SendFieldRules.showsAlreadySent(DraftFacts(text = "hi")))
    }

    @Test
    fun `P-003 a send that did not go puts the words back unless the person typed`() {
        assertTrue(SendFieldRules.restoreAfterFailedSend(field = "", serialAtTap = 4, serialNow = 4))
        assertFalse(SendFieldRules.restoreAfterFailedSend(field = "", serialAtTap = 4, serialNow = 5))
        assertFalse(SendFieldRules.restoreAfterFailedSend(field = "new words", serialAtTap = 4, serialNow = 4))
    }

    @Test
    fun `P-003 an admitted send waits, then restores when it ends uncertain or failed, and stops once cleared`() {
        val sending = DraftFacts(text = "hi", sendBlocked = true, sendInFlight = true)
        assertEquals(SendFieldRules.AfterSend.Wait, SendFieldRules.afterSend("hi", true, sending, userEdited = false))
        // Before the draft reported the send on its way, an unblocked draft is just a lagging state: wait.
        assertEquals(
            SendFieldRules.AfterSend.Wait,
            SendFieldRules.afterSend("hi", false, DraftFacts(text = "hi"), userEdited = false),
        )
        val uncertain = DraftFacts(text = "hi", sendBlocked = true)
        assertEquals(SendFieldRules.AfterSend.Restore, SendFieldRules.afterSend("hi", false, uncertain, false))
        // A definite failure: the bubble is Failed, the draft no longer blocked, and the words still in it.
        assertEquals(
            SendFieldRules.AfterSend.Restore,
            SendFieldRules.afterSend("hi", true, DraftFacts(text = "hi"), userEdited = false),
        )
        // Acknowledged: the draft owner cleared it.
        assertEquals(
            SendFieldRules.AfterSend.Done,
            SendFieldRules.afterSend("hi", true, DraftFacts(text = ""), userEdited = false),
        )
        assertEquals(SendFieldRules.AfterSend.Done, SendFieldRules.afterSend("hi", true, uncertain, userEdited = true))
    }

    // ---- P-012: past tense only for finished steps ----

    private fun step(id: String, tool: String, state: StepState, path: String? = null): TurnStep {
        val input = path?.let { JsonObject(mapOf("filePath" to JsonPrimitive(it))) } ?: JsonObject(emptyMap())
        val status =
            when (state) {
                StepState.Pending -> ToolStatus.Pending
                StepState.Running -> ToolStatus.Running
                else -> ToolStatus.Completed
            }
        val tool0 = ToolState(status, input = input)
        return TurnStep(id, toolFacts(tool, tool0), state, null, null, toolOutputView(tool0, tool))
    }

    @Test
    fun `P-012 steps waiting for approval are not counted as edited or ran`() {
        val counts = WorkCounts(read = 1, edited = 1, ran = 1, steps = 1)
        val work =
            TurnWork(
                "t",
                listOf(
                    step("r", "read", StepState.Done, "a.kt"),
                    step("w", "write", StepState.Pending, "b.kt"),
                    step("b", "bash", StepState.Running),
                ),
                counts,
                WorkLineState.WaitingForYou,
                null,
            )
        val split = WorkLineRules.split(work)
        assertEquals(WorkCounts(read = 1, steps = 1), split.done)
        assertEquals(1, split.waitingEdit)
        assertEquals(1, split.waitingRun)
        assertEquals(0, split.waitingOther)
    }

    @Test
    fun `B-CHAT-063 a step that simply runs is counted with the rest, never as waiting to run`() {
        val work =
            TurnWork(
                "t",
                listOf(step("a", "bash", StepState.Done), step("b", "bash", StepState.Running)),
                WorkCounts(ran = 2, steps = 1),
                WorkLineState.Running,
                null,
            )
        val split = WorkLineRules.split(work)
        assertEquals(2, split.done.ran)
        assertEquals(0, split.waiting)
    }

    @Test
    fun `P-012 with every step finished the engine's counts are used unchanged`() {
        val counts = WorkCounts(edited = 2, ran = 1, steps = 3)
        val work =
            TurnWork(
                "t",
                listOf(step("e", "edit", StepState.Done, "a.kt"), step("b", "bash", StepState.Failed)),
                counts,
                WorkLineState.Done,
                null,
            )
        val split = WorkLineRules.split(work)
        assertEquals(counts, split.done)
        assertEquals(0, split.waiting)
    }

    // ---- P-013: the approval card says each thing once ----

    @Test
    fun `P-013 a file change has no technical tool line and no copy-to-run button`() {
        val edit = AskView(AskVerb.Edit, name = "game.js", targets = persistentListOf(AskTarget("src/game.js", 3, 1, file = true)))
        assertFalse(RequestCardRules.showsToolLine(edit))
        assertFalse(RequestCardRules.copiesTargets(edit))
        val run = AskView(AskVerb.Run, name = "npm test", targets = persistentListOf(AskTarget("npm test")))
        assertTrue(RequestCardRules.copiesTargets(run))
        assertFalse(RequestCardRules.showsToolLine(run))
        assertTrue(RequestCardRules.showsToolLine(AskView(AskVerb.Generic)))
    }

    @Test
    fun `P-013 a file named by its one target line is not named again in the question`() {
        val one = AskView(AskVerb.Edit, name = "game.js", targets = persistentListOf(AskTarget("src/game.js", file = true)))
        assertTrue(RequestCardRules.questionOmitsName(one))
        val same = AskView(AskVerb.Create, name = "notes.md", targets = persistentListOf(AskTarget("notes.md", file = true)))
        // Review 2026-11-13 #1: a creation says "Create notes.md?", never "Create 1 file?".
        assertFalse(RequestCardRules.questionOmitsName(same))
        val other = AskView(AskVerb.Edit, name = "game.js", targets = persistentListOf(AskTarget("src/other.js")))
        assertFalse(RequestCardRules.questionOmitsName(other))
        val run = AskView(AskVerb.Run, name = "npm test", targets = persistentListOf(AskTarget("npm test")))
        assertFalse(RequestCardRules.questionOmitsName(run))
        assertFalse(RequestCardRules.questionOmitsName(AskView(AskVerb.Edit, name = "a.kt")))
    }

    // ---- P-018: one "Continue elsewhere" page ----

    @Test
    fun `P-018 the Continue actions are offered together when there are two or more`() {
        val menu = HeaderRules.menu(ServerCapabilities(cliSessionResume = true), shared = false)
        val group = HeaderRules.continueGroup(menu)
        assertTrue(HeaderAction.ContinueOnComputer in group && HeaderAction.ContinueOnPhone in group, "$group")
        assertTrue(group.all { it in menu })
        assertEquals(emptyList(), HeaderRules.continueGroup(listOf(HeaderAction.Find, HeaderAction.ContinueOnPhone)))
    }

    // ---- P-010: an opened row stays under the finger ----

    @Test
    fun `P-010 a held row's growth is paid back with an equal scroll, nothing else is`() {
        assertEquals(390f, FoldAnchorRules.scrollFor(held = true, before = 100, after = 490))
        assertEquals(-390f, FoldAnchorRules.scrollFor(held = true, before = 490, after = 100))
        assertEquals(0f, FoldAnchorRules.scrollFor(held = false, before = 100, after = 490))
        assertEquals(0f, FoldAnchorRules.scrollFor(held = true, before = -1, after = 490))
    }

    // ---- P-005: never the raw model id ----

    @Test
    fun `P-005 a model named before the catalog loads reads as words, not its id`() {
        assertEquals("Crouton Alpha Free", ModelNames.fromId("crouton-alpha-free"))
        // Changed on purpose (c38e81af3b pinned this copy's own capitals): the chip now uses the app's one rule,
        // Flutter's modelNameFromId, as the picker and the reply footer do.
        assertEquals("GPT-5", ModelNames.fromId("openai/gpt-5"))
        assertEquals("GLM-4.6", ModelNames.fromId("GLM-4.6"))
        assertEquals("Opus 4.6 · 1M context", ModelNames.fromId("claude-opus-4-6[1m]"))
    }
}
