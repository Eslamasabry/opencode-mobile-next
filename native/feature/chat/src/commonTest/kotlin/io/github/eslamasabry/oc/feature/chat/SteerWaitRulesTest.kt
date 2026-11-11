package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.presentation.SteerWait
import io.github.eslamasabry.oc.feature.chat.presentation.SteerWaitRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SteerWaitRulesTest {
    private val own = PromptRow("p1", "t1", "hello")

    @Test
    fun `no note when no run is going or a send would not join it`() {
        assertNull(SteerWaitRules.wait(listOf(own), emptyMap(), busy = false, joins = true))
        assertNull(SteerWaitRules.wait(listOf(own), emptyMap(), busy = true, joins = false))
    }

    @Test
    fun `no note without an earlier turn that still runs a step`() {
        assertNull(SteerWaitRules.wait(listOf(own), emptyMap(), busy = true, joins = true))
    }

    @Test
    fun `Stop shows on the note once the step has run long, or at once without a send time`() {
        val sent = SteerWait("p2", command = false, text = "go on", sentMs = 1_000)
        assertFalse(SteerWaitRules.showsStop(sent, nowMs = 8_999))
        assertTrue(SteerWaitRules.showsStop(sent, nowMs = 9_000))
        assertTrue(SteerWaitRules.showsStop(SteerWait("p2", command = true), nowMs = 0))
    }

    @Test
    fun `the words go out again only while the agent has not started on the message`() {
        val first = prompt("u1", "first")
        val steered = prompt("u2", "go on")
        val running = footer("u1")
        val answer = block("u2", "a2", "p2", 0, "reading it")
        // Newest first, as the state holds them.
        assertTrue(SteerWaitRules.unreadAfterStop(listOf(steered, running, first), "prompt:u2"))
        assertTrue(SteerWaitRules.unreadAfterStop(listOf(notice("u2", "stopped"), steered, first), "prompt:u2"))
        assertFalse(SteerWaitRules.unreadAfterStop(listOf(answer, steered, first), "prompt:u2"), "too late: already read")
        assertFalse(SteerWaitRules.unreadAfterStop(listOf(first), "prompt:u2"), "the message is gone")
    }
}
