package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.engine.agents.handoff.HandoffMessage
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffPart
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffRecordBuilder
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffRecordResult
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffSession
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffToolState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** AG5 (sweep-b 2026-11-11): a conversation record reads as the conversation, with no ids or wire labels as copy. */
class HandoffReadingTest {
    private val record =
        (HandoffRecordBuilder().build(
                HandoffSession(
                    id = "ses_e3477280fffe1UFTUl2SW5rj2r",
                    title = "List what's in this folder",
                    directory = "/srv/project",
                    model = "opencode/big-pickle",
                ),
                listOf(
                    HandoffMessage("m1", "ses_e3477280fffe1UFTUl2SW5rj2r", "user", listOf(HandoffPart("text", "List what's in this folder"))),
                    HandoffMessage(
                        "m2",
                        "ses_e3477280fffe1UFTUl2SW5rj2r",
                        "assistant",
                        listOf(
                            HandoffPart("tool", toolName = "bash", tool = HandoffToolState("completed", input = mapOf("command" to "ls"))),
                            HandoffPart("text", "The folder holds **two** files."),
                        ),
                        errorText = "ProviderError: upstream 502",
                    ),
                ),
                lastState = "observed",
            ) as HandoffRecordResult.Built)
            .record
            .markdown

    @Test
    fun `AG5 the reading is the title and what was said, never ids or the record's labels`() {
        val reading = HandoffReading.of(record, you = "You", agent = "Agent")
        assertEquals("List what's in this folder", reading.title)
        assertTrue("#### You" in reading.body, reading.body)
        assertTrue("#### Agent" in reading.body, reading.body)
        assertTrue("The folder holds **two** files." in reading.body, reading.body)
        for (copy in listOf("ses_", "big-pickle", "Source:", "Last state", "observed", "# Conversation handoff", "- Tool", "```", "ProviderError"))
            assertFalse(copy in reading.body, "\"$copy\" in: ${reading.body}")
    }

    @Test
    fun `AG5 the record itself is left whole for Details and the copy`() {
        assertTrue("ses_e3477280fffe1UFTUl2SW5rj2r" in record)
        assertIs<String>(HandoffReading.of(record, "You", "Agent").body)
    }

    @Test
    fun `AG5 text that is not a record reads as nothing rather than as the raw source`() {
        val reading = HandoffReading.of("# Something else\nSource: x", "You", "Agent")
        assertEquals(null, reading.title)
        assertEquals("", reading.body)
    }
}
