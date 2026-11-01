package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMomentRules
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMomentState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhraseMomentTest {
    private fun user(id: String) = MessageWithParts(MessageInfo(id = id, sessionId = "s", role = Role.User), persistentListOf())

    private fun assistant(
        id: String,
        error: String? = null,
        kind: MessageErrorKind? = null,
        finish: String? = null,
        done: Boolean = true,
        created: Long = 1000,
        tools: List<Pair<String, ToolStatus>> = emptyList(),
    ) = MessageWithParts(
        MessageInfo(
            id = id, sessionId = "s", role = Role.Assistant, time = MsgTime(created, if (done) created + 5 else null),
            errorText = error, errorKind = kind, finish = finish,
        ),
        tools.map { (name, status) ->
            Part(id = "t-$name", messageId = id, sessionId = "s", type = PartType.Tool, rawType = "tool", toolName = name, tool = ToolState(status))
        }.toImmutableList(),
    )

    private fun derive(vararg m: MessageWithParts, busy: Boolean = false, stopped: Boolean = false, offline: Boolean = false, now: Long = 1000 + 120_000): PhraseMomentState =
        PhraseMomentRules.derive(m.toList(), busy, now, stoppedByPerson = stopped, offline = offline)

    @Test
    fun `B-CHAT-085 an empty new chat has no moment`() {
        assertNull(derive().moment)
        assertNull(derive(user("local-1")).moment)
    }

    @Test
    fun `B-CHAT-085 busy is working, even with no history`() {
        assertEquals(PhraseMoment.Working, derive(busy = true).moment)
        assertEquals(PhraseMoment.Working, derive(user("u1"), assistant("a1"), busy = true).moment)
    }

    @Test
    fun `B-CHAT-085 a finished turn is idle without edits and finished-with-changes with them`() {
        assertEquals(PhraseMoment.Idle, derive(user("u1"), assistant("a1", tools = listOf("read" to ToolStatus.Completed))).moment)
        for (tool in listOf("edit", "write", "apply_patch", "multiedit")) {
            assertEquals(PhraseMoment.FinishedWithChanges, derive(user("u1"), assistant("a1", tools = listOf(tool to ToolStatus.Completed))).moment, tool)
        }
    }

    @Test
    fun `B-CHAT-085 only the last turn counts and a failed edit is not a change`() {
        assertEquals(
            PhraseMoment.Idle,
            derive(user("u1"), assistant("a1", tools = listOf("edit" to ToolStatus.Completed)), user("u2"), assistant("a2", tools = listOf("read" to ToolStatus.Completed))).moment,
        )
        assertEquals(PhraseMoment.Idle, derive(user("u1"), assistant("a1", tools = listOf("edit" to ToolStatus.Error))).moment)
    }

    @Test
    fun `B-CHAT-086 cut off by length is stopped-partway with Continue, none offline`() {
        val cut = derive(user("u1"), assistant("a1", error = "Output length limit reached", kind = MessageErrorKind.OutputLength))
        assertEquals(PhraseMoment.StoppedPartway, cut.moment)
        assertTrue(cut.continueShown)
        assertFalse(derive(user("u1"), assistant("a1", error = "x", kind = MessageErrorKind.OutputLength), offline = true).continueShown)
    }

    @Test
    fun `B-CHAT-086 finish length with no error is stopped-partway with no button`() {
        val cut = derive(user("u1"), assistant("a1", finish = "length"))
        assertEquals(PhraseMoment.StoppedPartway, cut.moment)
        assertFalse(cut.continueShown)
    }

    @Test
    fun `B-CHAT-086 stopped by the person, an error and an interruption are classified`() {
        assertEquals(PhraseMoment.StoppedPartway, derive(user("u1"), assistant("a1"), stopped = true).moment)
        assertEquals(PhraseMoment.StoppedPartway, derive(user("u1"), stopped = true).moment)
        assertEquals(PhraseMoment.StoppedPartway, derive(user("u1"), assistant("a1", error = "Provider failed", kind = MessageErrorKind.Unknown)).moment)
        assertEquals(PhraseMoment.StoppedPartway, derive(user("u1"), assistant("a1", done = false)).moment)
    }

    @Test
    fun `B-CHAT-086 an error notice hides the row - a stop or a length cut does not`() {
        assertTrue(derive(user("u1"), assistant("a1", error = "This model's usage limit reached", kind = MessageErrorKind.Unknown)).errorShown)
        assertFalse(derive(user("u1"), assistant("a1", error = "aborted", kind = MessageErrorKind.Aborted)).errorShown)
        assertFalse(derive(user("u1"), assistant("a1", error = "x", kind = MessageErrorKind.OutputLength)).errorShown)
        assertFalse(derive(user("u1"), assistant("a1")).errorShown)
    }

    @Test
    fun `B-CHAT-086 a just-started reply that is not busy yet is not interrupted`() {
        assertEquals(PhraseMoment.Idle, derive(user("u1"), assistant("a1", done = false), now = 1000 + 2000).moment)
    }

    @Test
    fun `B-CHAT-086 an error class named in the text is recovered`() {
        val state = derive(user("u1"), assistant("a1", error = "MessageOutputLengthError: too long", kind = MessageErrorKind.Unknown))
        assertTrue(state.continueShown)
        assertFalse(state.errorShown)
    }
}
