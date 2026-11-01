package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.Ev.info
import io.github.eslamasabry.oc.engine.transcript.Ev.msg
import io.github.eslamasabry.oc.engine.transcript.Ev.textPart
import io.github.eslamasabry.oc.engine.transcript.Ev.toolPart
import io.github.eslamasabry.oc.engine.transcript.internal.passes.COMPACTION_PASSED
import io.github.eslamasabry.oc.engine.transcript.internal.passes.CopyProbe
import io.github.eslamasabry.oc.engine.transcript.internal.passes.Display
import io.github.eslamasabry.oc.engine.transcript.internal.passes.deriveTurns
import io.github.eslamasabry.oc.engine.transcript.internal.passes.isPassingWords
import io.github.eslamasabry.oc.engine.transcript.internal.passes.markFoldedWork
import io.github.eslamasabry.oc.engine.transcript.internal.passes.mergeTextParts
import io.github.eslamasabry.oc.engine.transcript.internal.passes.settleStaleLive
import io.github.eslamasabry.oc.engine.transcript.internal.passes.timelineDisplay
import io.github.eslamasabry.oc.engine.transcript.internal.passes.turnActionOwners
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ports of `test/transcript_settle_test.dart` (B-CHAT-116, B-CHAT-117): the settle pass on its own. */
class SettleTest {
    private fun user(id: String, text: String = "go") = msg(info(id, Role.User), textPart(id, "$id-t", text))

    private fun compaction(id: String, status: String) =
        msg(info(id, Role.User), Part("c", id, Ev.S, PartType.Compaction, rawType = "v2:compaction", toolName = status))

    private fun assistant(id: String, vararg parts: Part) = msg(info(id, Role.Assistant), *parts)

    private fun running(id: String, metadata: JsonObject? = null) =
        Part("t", id, Ev.S, PartType.Tool, rawType = "tool", toolName = "bash", tool = ToolState(ToolStatus.Running, metadata = metadata))

    private fun hasRunningCompaction(m: List<MessageWithParts>) =
        m.any { it.parts.any { p -> p.type == PartType.Compaction && p.toolName == "running" } }

    private fun toolStatus(m: List<MessageWithParts>) = m.flatMap { it.parts }.first { it.type == PartType.Tool }.tool!!.status

    // The Paseo shape of transcript_settle_test.dart `real`: prompt, compaction loading, compaction done, reply.
    private val real =
        listOf(
            user("u1"),
            compaction("k1", "running"),
            compaction("k2", "completed"),
            assistant("a1", textPart("a1", "t", "continuing")),
        )

    @Test
    fun `B-CHAT-116 the compaction line ends once the compaction notice and later work arrive`() {
        assertTrue(hasRunningCompaction(real))
        val settled = settleStaleLive(real, busy = true)
        assertFalse(hasRunningCompaction(settled))
        // One message per message: rows, parts and turn owners read by the same index (43fabb514).
        assertEquals(real.size, settled.size)
        assertTrue(settled.any { m -> m.parts.any { it.toolName == COMPACTION_PASSED } })
        assertTrue(settled.any { m -> m.parts.any { it.toolName == "completed" } })
    }

    @Test
    fun `B-CHAT-116 the compaction line stays while it is the newest thing and the agent is busy`() {
        assertTrue(hasRunningCompaction(settleStaleLive(real.subList(0, 2), busy = true)))
    }

    @Test
    fun `B-CHAT-116 the compaction line ends when the turn has ended`() {
        assertFalse(hasRunningCompaction(settleStaleLive(real.subList(0, 2), busy = false)))
    }

    @Test
    fun `B-CHAT-117 a tool under later words of its turn is finished`() {
        val messages = listOf(user("u1"), assistant("a0", toolPart("a0", "c1", ToolStatus.Running)), assistant("a1", textPart("a1", "t", "Read it.")))
        assertEquals(ToolStatus.Running, toolStatus(messages))
        assertEquals(ToolStatus.Completed, toolStatus(settleStaleLive(messages, busy = true)))
    }

    @Test
    fun `B-CHAT-117 a tool of the newest step with no words after it keeps running`() {
        val messages = listOf(user("u"), assistant("a", running("a")))
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a tool is finished when the turn has ended`() {
        val messages = listOf(user("u"), assistant("a", running("a")))
        assertEquals(ToolStatus.Completed, settleStaleLive(messages, busy = false)[1].parts.single().tool!!.status)
    }

    @Test
    fun `B-CHAT-117 words of an earlier turn do not finish a later turn tool`() {
        val messages =
            listOf(user("u1", "one"), assistant("a1", textPart("a1", "t", "done")), user("u2", "two"), assistant("a2", running("a2")))
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a background process keeps running`() {
        val messages =
            listOf(
                user("u"),
                assistant("a", running("a", JsonObject(mapOf("background" to JsonPrimitive(true)))), textPart("a", "w", "Started it.")),
            )
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a late running report cannot revive a finished call`() {
        // Paseo folds both reports of call c1 into one part; the engine applies the same rule: the whole part wins, and
        // a finished turn settles whatever arrives late (busy = false).
        val messages = listOf(user("u"), assistant("a", toolPart("a", "c1", ToolStatus.Running)))
        assertEquals(ToolStatus.Completed, toolStatus(settleStaleLive(messages, busy = false)))
    }
}

/** `test/revamp/slice_p4_4_test.dart` "fragments merge the same, in linear time" (B-CHAT-096) + LinearMergeTest. */
class LinearMergeTest {
    /** The merge as it was (quadratic: it read the whole buffer back per fragment), kept as the parity oracle. */
    private fun reference(texts: List<String>): String {
        val buffer = StringBuilder()
        for (text in texts) {
            if (text.isBlank()) continue
            if (buffer.isNotEmpty() && !buffer.toString().endsWith('\n') && !text.startsWith('\n')) buffer.append("\n\n")
            buffer.append(text)
        }
        return buffer.toString()
    }

    private fun parts(texts: List<String>) = texts.mapIndexed { i, t -> textPart("m", "p$i", t) }

    @Test
    fun `B-CHAT-096 fragments merge to the old text in every edge case`() {
        val cases =
            listOf(
                listOf("One.", "Two.", "Three."),
                listOf("One.", "  ", "\n", "Two."),
                listOf("One.\n", "Two.", "Three.\n"),
                listOf("One.", "\nTwo.", "\n\nThree"),
                listOf("One.\r\n", "Two.\r", "\r\nThree"),
                listOf("   ", "One.", "Two."),
                listOf("```dart\nfinal a", " = 1;\n", "```"),
            )
        for (texts in cases) {
            val merged = mergeTextParts(parts(texts))
            assertEquals(reference(texts), merged.text, texts.toString())
            assertEquals("p0", merged.id, "the first part's identity")
        }
    }

    @Test
    fun `B-CHAT-096 a single part is returned as is`() {
        val only = textPart("m", "only", "x")
        assertSame(only, mergeTextParts(listOf(only)))
    }

    @Test
    fun `B-CHAT-096 1,000 and 5,000 fragments merge identically and copy at most twice the text`() {
        for (count in listOf(1_000, 5_000)) {
            for (fragmented in listOf(false, true)) {
                val texts = List(count) { if (fragmented) "Part $it." else "Part $it.\n" }
                var copied = 0L
                val merged = mergeTextParts(parts(texts), CopyProbe { copied += it })
                assertEquals(reference(texts), merged.text)
                val total = texts.sumOf { it.length }
                // Linear: every character is written once, plus separators; never the old ~15× read-back.
                assertTrue(copied <= 2L * total, "copied $copied for $total")
            }
        }
    }
}

/** Fold, display and footer-owner passes (`transcript_settle_test.dart:61,98` context, `kit_message_test.dart:155`). */
class TurnPassesTest {
    private fun prompt(id: String) = msg(info(id, Role.User), textPart(id, "$id-t", "go"))

    private fun step(id: String, vararg parts: Part, error: String? = null, finish: String? = null) =
        MessageWithParts(info(id, Role.Assistant, error = error).copy(finish = finish), kotlinx.collections.immutable.persistentListOf(*parts))

    @Test
    fun `B-CHAT-121 passing words fold under the work line and an explanation stays in view`() {
        val messages =
            listOf(
                prompt("u"),
                step("a1", textPart("a1", "t0", "Now let me run the tests:"), toolPart("a1", "c1", ToolStatus.Completed)),
                step("a2", textPart("a2", "t1", "The flakiness comes from CheckoutBloc.\n\nIt races the timer."), toolPart("a2", "c2", ToolStatus.Completed)),
                step("a3", textPart("a3", "t2", "Fixed.")),
            )
        val marks = markFoldedWork(messages)
        assertTrue(marks.parts.contains("a1/t0"))
        assertFalse(marks.parts.contains("a2/t1"), "an explanation before the last step stays in view")
        assertFalse(marks.parts.contains("a3/t2"), "the answer never folds")
        // The running turn keeps everything in place.
        assertTrue(markFoldedWork(messages, liveTail = true).parts.isEmpty())
        assertTrue(isPassingWords("Looking into it."))
        assertFalse(isPassingWords("One.\nTwo."))
        assertFalse(isPassingWords("Look:\n```\nx\n```"))
    }

    @Test
    fun `B-CHAT-121 a finished turn gathers its work into one stretch at the first place`() {
        val messages =
            listOf(
                prompt("u"),
                step("a1", textPart("a1", "t0", "Reading."), toolPart("a1", "c1", ToolStatus.Completed)),
                step("a2", textPart("a2", "t1", "Line one.\nLine two."), toolPart("a2", "c2", ToolStatus.Completed)),
                step("a3", textPart("a3", "t2", "Done.")),
            )
        val display = timelineDisplay(messages, markFoldedWork(messages))
        val work = display.flatten().filterIsInstance<Display.Work>()
        assertEquals(1, work.size)
        assertEquals(listOf("t0", "c1", "c2"), work.single().parts.map { it.id })
        assertTrue(display[1].first() is Display.Work, "the pool sits at the first stretch's place")
        // Running: work stays where it happened, one stretch each.
        val live = timelineDisplay(messages.subList(0, 3), markFoldedWork(messages.subList(0, 3), liveTail = true), liveTail = true)
        assertEquals(2, live.flatten().count { it is Display.Work })
    }

    @Test
    fun `B-CHAT-121 the footer sits on the step that ends the turn, else the last step that draws, none while running`() {
        val messages =
            listOf(
                prompt("u1"),
                step("a1", textPart("a1", "t", "Answer.")),
                step("a2", Part("f", "a2", Ev.S, PartType.StepFinish, rawType = "step-finish")),
                prompt("u2"),
                step("b1", textPart("b1", "t", "Working")),
            )
        val display = timelineDisplay(messages, markFoldedWork(messages))
        assertEquals(setOf(1, 4), turnActionOwners(messages, display))
        assertEquals(setOf(1), turnActionOwners(messages, display, running = true))
        val failed = listOf(prompt("u"), step("e", error = "boom"))
        assertEquals(setOf(1), turnActionOwners(failed, timelineDisplay(failed, markFoldedWork(failed))))
    }

    @Test
    fun `B-CHAT-121 a notice never starts a turn and an inbox prompt is not a prompt`() {
        val notice = msg(info("n", Role.User), Part("x", "n", Ev.S, PartType.Notice, rawType = "v2:notice"))
        val messages = listOf(prompt("u1"), notice, step("a", textPart("a", "t", "Hi")), prompt("u2"))
        assertEquals(listOf("u1", "u2"), deriveTurns(messages).map { it.id })
        assertEquals(listOf("u1"), deriveTurns(messages, ignore = setOf("u2")).map { it.id })
        assertEquals(listOf("head", "u1"), deriveTurns(listOf(step("a0", textPart("a0", "t", "x"))) + messages.subList(0, 3)).map { it.id })
    }
}
