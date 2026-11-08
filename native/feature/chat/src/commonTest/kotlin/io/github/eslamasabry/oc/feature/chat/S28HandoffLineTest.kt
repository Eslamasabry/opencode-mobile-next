package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.agents.handoff.HandoffLink
import io.github.eslamasabry.oc.feature.chat.handoff.HandoffLineRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S28 story 8 (Flutter `chat_handoff_link.dart`, B-STATE-001): a conversation with a handoff link shows one quiet
 * line: "Continued in <agent>" where this work went on, "Continued from <title>" where it came from.
 */
class S28HandoffLineTest {
    private val link =
        HandoffLink(
            id = "h1",
            sourceProfileId = "p",
            sourceSessionId = "s-source",
            sourceTitle = "Design review",
            targetProfileId = "p",
            targetSessionId = "s-target",
            targetAgent = "Claude Code",
            projectDirectory = "/work",
            handoffPath = "/work/.opencode/handoff/h1.md",
        )

    @Test
    fun `B-STATE-S28-01 the source says where it went on, the target where it came from`() {
        val out = HandoffLineRules.of(listOf(link), "s-source")!!
        assertTrue(out.outgoing)
        assertEquals("Claude Code", out.words)
        val back = HandoffLineRules.of(listOf(link), "s-target")!!
        assertFalse(back.outgoing)
        assertEquals("Design review", back.words)
        assertFalse(back.gone)
    }

    @Test
    fun `B-STATE-S28-02 a conversation no link names has no line`() {
        assertNull(HandoffLineRules.of(listOf(link), "other"))
        assertNull(HandoffLineRules.of(emptyList(), "s-source"))
    }
}
