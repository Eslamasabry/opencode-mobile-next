package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.feature.chat.presentation.BackOrder
import io.github.eslamasabry.oc.feature.chat.presentation.BackStep
import io.github.eslamasabry.oc.feature.chat.presentation.ChatCapabilitiesUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.FindHit
import io.github.eslamasabry.oc.feature.chat.presentation.Landing
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageActions
import io.github.eslamasabry.oc.feature.chat.presentation.MessageOrder
import io.github.eslamasabry.oc.feature.chat.presentation.ReadAck
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyText
import io.github.eslamasabry.oc.feature.chat.presentation.RowIds
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.presentation.TargetKind
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptFind
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The chat screen's pure rules (plan 09 §6–§7), one table per rule. */
class ChatRulesTest {
    // ---- Find (B-CHAT-115; Flutter TranscriptSearchIndex) ----

    @Test
    fun `B-CHAT-115 find counts literal occurrences in reading order across prompts, replies and thoughts`() {
        val s =
            stateOf(
                prompt("u1", "Fix the Build"),
                thought("u1", "a1", "r1", "the build is red"),
                block("u1", "a1", "p1", 0, "build build"),
                notice("u1", "n1"), // app words: never searched
                footer("u1"),
                prompt("u2", "a.b+c? (literal)"),
            )
        val hits = TranscriptFind.search(s.rows, " build ")
        assertEquals(
            listOf(
                FindHit("prompt:u1", 8, 13),
                FindHit("thought:a1/r1", 4, 9),
                FindHit("b:a1/p1:0", 0, 5),
                FindHit("b:a1/p1:0", 6, 11),
            ),
            hits,
        )
        assertEquals(listOf(FindHit("prompt:u2", 3, 6)), TranscriptFind.search(s.rows, "+c?"), "literal, never a pattern")
        assertTrue(TranscriptFind.search(s.rows, "   ").isEmpty(), "a blank query finds nothing")
        assertEquals(1, TranscriptFind.search(stateOf(block("t", "m", "p", 0, "aaaa")).rows, "aaa").size, "non-overlapping")
    }

    @Test
    fun `B-CHAT-115 find keeps the current hit across streaming, an older page and a deletion`() {
        val before = TranscriptFind.search(stateOf(*turn(1, answer = "x").toTypedArray(), *turn(2, answer = "x").toTypedArray()).rows, "x")
        val current = before[1] // the second turn's hit
        // Streaming adds a newer hit: the current one keeps its place by identity.
        val streamed = TranscriptFind.search(stateOf(*turn(1, answer = "x").toTypedArray(), *turn(2, answer = "x").toTypedArray(), *turn(3, answer = "x").toTypedArray()).rows, "x")
        assertEquals(1, TranscriptFind.keep(streamed, current, 1))
        // An older page lands above: the same hit moves to a new number.
        val older = TranscriptFind.search(stateOf(*turn(0, answer = "x").toTypedArray(), *turn(1, answer = "x").toTypedArray(), *turn(2, answer = "x").toTypedArray()).rows, "x")
        assertEquals(2, TranscriptFind.keep(older, current, 1))
        // Its message was deleted: the cursor keeps its number, clamped.
        val deleted = TranscriptFind.search(stateOf(*turn(1, answer = "x").toTypedArray()).rows, "x")
        assertEquals(0, TranscriptFind.keep(deleted, current, 1))
        assertEquals(-1, TranscriptFind.keep(emptyList(), current, 1))
    }

    @Test
    fun `09-6_6 find steps forward and back and wraps both ways`() {
        assertEquals(1, TranscriptFind.step(3, 0, +1))
        assertEquals(0, TranscriptFind.step(3, 2, +1))
        assertEquals(2, TranscriptFind.step(3, 0, -1))
        assertEquals(-1, TranscriptFind.step(0, 0, +1))
    }

    // ---- The one status line (plan 09 §6.5, 07 §10.1) ----

    private val failedSend = PendingSend(2, "hi", PendingState.Failed, Failure.Network(NetworkWhy.Offline))
    private val olderFailedSend = PendingSend(1, "older", PendingState.Failed)
    private val unconfirmed = PendingSend(3, "maybe", PendingState.Unconfirmed)

    @Test
    fun `07-10_1 this backend's connection outranks every line of the chat`() {
        val all = listOf(olderFailedSend, failedSend)
        val failedRun = RunFacts(busy = false, lastOutcome = RunOutcome.Failed(null))
        assertEquals(ChatStatusKind.Offline, StatusPick.pick(ChatLink.Offline, all, failedRun, emptyList(), Failure.Unknown())?.kind)
        assertEquals(ChatStatusKind.Connecting, StatusPick.pick(ChatLink.Connecting, all, failedRun, emptyList(), null)?.kind)
        assertNull(StatusPick.pick(ChatLink.Live, emptyList(), RunFacts(), emptyList(), null), "nothing to say: no line")
    }

    @Test
    fun `09-6_5 a failed send outranks a lost answer and a failed reply, and the newest bubble speaks`() {
        val failedRun = RunFacts(lastOutcome = RunOutcome.Failed(null))
        val line = StatusPick.pick(ChatLink.Live, listOf(olderFailedSend, unconfirmed, failedSend), failedRun, emptyList(), null)
        assertEquals(ChatStatusKind.SendFailed, line?.kind)
        assertEquals(2L, line?.localId)
        assertEquals("send-2", line?.id)
        assertEquals(ChatStatusKind.SendUnconfirmed, StatusPick.pick(ChatLink.Live, listOf(unconfirmed), failedRun, emptyList(), null)?.kind)
        assertEquals(ChatStatusKind.RunFailed, StatusPick.pick(ChatLink.Live, emptyList(), failedRun, emptyList(), Failure.Unknown())?.kind)
        assertEquals(ChatStatusKind.ActionFailed, StatusPick.pick(ChatLink.Live, emptyList(), RunFacts(), emptyList(), Failure.Unknown())?.kind)
    }

    @Test
    fun `B-CHAT-071 a failed reply that already says why gets no second error line`() {
        val failedRun = RunFacts(busy = false, lastOutcome = RunOutcome.Failed("ProviderAuth"))
        val saysWhy = stateOf(*turn(1).toTypedArray(), *turn(2, failed = true).toTypedArray())
        assertNull(StatusPick.pick(ChatLink.Live, emptyList(), failedRun, saysWhy.rows, null))
        // The prompt got no answer at all: nothing in the transcript says so, the line does.
        val noAnswer = stateOf(*turn(1).toTypedArray(), prompt("u2", "hello"))
        assertEquals(ChatStatusKind.RunFailed, StatusPick.pick(ChatLink.Live, emptyList(), failedRun, noAnswer.rows, null)?.kind)
        // An older failed turn does not hide the newest turn's failure.
        val olderFailed = stateOf(*turn(1, failed = true).toTypedArray(), prompt("u2", "hello"))
        assertEquals(ChatStatusKind.RunFailed, StatusPick.pick(ChatLink.Live, emptyList(), failedRun, olderFailed.rows, null)?.kind)
        // While the next reply runs, the last failure is not news.
        assertNull(StatusPick.pick(ChatLink.Live, emptyList(), failedRun.copy(busy = true), noAnswer.rows, null))
    }

    // ---- Message actions (plan 09 §6.6) ----

    @Test
    fun `B-CHAT-061 a prompt's menu offers copy, send again, fork and delete, delete last, each only when it can run`() {
        val all = ChatCapabilitiesUi(fork = true, deleteMessage = true)
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.SendAgain, MessageAction.Fork, MessageAction.Delete),
            MessageActions.of(TargetKind.Prompt, all, busy = false, messageId = "u1", hasText = true),
        )
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.Fork, MessageAction.Delete),
            MessageActions.of(TargetKind.Prompt, all, busy = true, messageId = "u1", hasText = true),
            "no Send again while a reply runs",
        )
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.SendAgain),
            MessageActions.of(TargetKind.Prompt, ChatCapabilitiesUi(), busy = false, messageId = "u1", hasText = true),
            "absent, never disabled: no fork or delete without the capability",
        )
        assertEquals(listOf(MessageAction.Copy, MessageAction.ReadAloud, MessageAction.Delete), MessageActions.of(TargetKind.Reply, all, false, "a1", hasText = true))
        assertEquals(listOf(MessageAction.Copy, MessageAction.ReadAloud), MessageActions.of(TargetKind.Reply, all, false, messageId = null, hasText = true))
        assertTrue(MessageActions.of(TargetKind.Reply, ChatCapabilitiesUi(), false, null, hasText = false).isEmpty())
    }

    @Test
    fun `B-CHAT-120 undo from here is on a prompt only where the server undoes, never while a reply runs`() {
        val review = ChatCapabilitiesUi(fork = true, deleteMessage = true, undo = true, changes = true)
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.SendAgain, MessageAction.Fork, MessageAction.UndoFromHere, MessageAction.Delete),
            MessageActions.of(TargetKind.Prompt, review, busy = false, messageId = "u1", hasText = true),
        )
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.Fork, MessageAction.Delete),
            MessageActions.of(TargetKind.Prompt, review, busy = true, messageId = "u1", hasText = true),
        )
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.ReadAloud, MessageAction.Changes, MessageAction.Delete),
            MessageActions.of(TargetKind.Reply, review, busy = false, messageId = "a1", hasText = true),
            "a reply's turn opens its changed files (P12-REVIEW), it can be read aloud (P12-VOICE); delete stays last",
        )
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.ReadAloud),
            MessageActions.of(TargetKind.Reply, review, false, messageId = null, hasText = true),
        )
    }

    @Test
    fun `09-6_6 copy reply is the turn's prose in order, thoughts, notices and other turns skipped`() {
        val s =
            stateOf(
                *turn(1, answer = "old").toTypedArray(),
                prompt("u2", "go"),
                thought("u2", "a2", "r", "thinking"),
                block("u2", "a2", "p1", 0, "# Title"),
                block("u2", "a2", "p1", 9, "First paragraph."),
                notice("u2", "n"),
                block("u2", "a3", "p2", 0, "Second step."),
                footer("u2"),
            )
        assertEquals("# Title\n\nFirst paragraph.\n\nSecond step.", ReplyText.of(s.rows, "u2"))
        assertNull(ReplyText.of(stateOf(prompt("u9", "only a prompt")).rows, "u9"))
    }

    // ---- The earlier-messages pill (plan 09 §7.3 rule 6) ----

    @Test
    fun `09-7_3 the earlier-messages count is the whole messages above the oldest visible row`() {
        val rows = (1..20).flatMap { turn(it) } // 20 prompts + 20 replies = 40 messages, 60 rows
        val s = stateOf(*rows.toTypedArray())
        val order = MessageOrder(s.rows)
        assertEquals(40, order.messageCount)
        assertEquals(0, order.earlierCount(s.rows.size - 1), "the oldest row: nothing above it")
        assertEquals(39, order.earlierCount(0), "the newest footer belongs to the newest reply: 39 messages above")
        val reply5 = s.indexOf("b:a5/p5:0")
        assertEquals(9, order.earlierCount(reply5), "u1,a1 … u4,a4 and u5 sit above reply a5")
        assertEquals(9, order.earlierCount(s.indexOf("footer:u5")), "a footer counts as its reply")
        assertEquals(0, order.earlierCount(-1))
        assertTrue(MessageOrder.pillVisible(awayFromLatest = true, messageCount = 40, earlier = 9))
        assertFalse(MessageOrder.pillVisible(awayFromLatest = false, messageCount = 40, earlier = 9), "only while away")
        assertFalse(MessageOrder.pillVisible(awayFromLatest = true, messageCount = 30, earlier = 9), "only past 30 messages")
        assertFalse(MessageOrder.pillVisible(awayFromLatest = true, messageCount = 40, earlier = 0))
    }

    @Test
    fun `09-6_1 a row names its message from the engine's key, and app rows name none`() {
        assertEquals("u1", RowIds.messageIdOf(prompt("u1", "x")))
        assertEquals("a1", RowIds.messageIdOf(block("u1", "a1", "p", 3, "x")))
        assertEquals("a1", RowIds.messageIdOf(thought("u1", "a1", "r", "x")))
        assertNull(RowIds.messageIdOf(notice("u1", "n")))
        assertNull(RowIds.messageIdOf(footer("u1")))
    }

    // ---- Back, reading, arrivals ----

    @Test
    fun `07-3_3 Back closes the menu, then a dialog, then find, then guards an unsaved draft, then leaves`() {
        assertEquals(BackStep.CloseMenu, BackOrder.next(menuOpen = true, confirmOpen = true, findOpen = true, draftUnsaved = true))
        assertEquals(BackStep.CloseConfirm, BackOrder.next(false, true, true, true))
        assertEquals(BackStep.CloseFind, BackOrder.next(false, false, true, true))
        assertEquals(BackStep.SaveDraftThenLeave, BackOrder.next(false, false, false, true))
        assertEquals(BackStep.Leave, BackOrder.next(false, false, false, false))
        assertFalse(BackOrder.handles(false, false, false, false), "nothing to protect: the predictive preview plays")
        assertTrue(DraftFacts(text = "half a thought").unsaved)
        assertFalse(DraftFacts(text = "  \n").unsaved, "whitespace is nothing to lose")
        assertFalse(DraftFacts(text = "kept", saved = "kept").unsaved)
    }

    @Test
    fun `B-CHAT-012 the chat counts as read only when loaded and at the live edge`() {
        assertTrue(ReadAck.allowed(ChatLoad.Live, atLiveEdge = true))
        assertFalse(ReadAck.allowed(ChatLoad.Live, atLiveEdge = false))
        assertFalse(ReadAck.allowed(ChatLoad.Loading, atLiveEdge = true))
        assertFalse(ReadAck.allowed(ChatLoad.Failed(Failure.Unknown()), atLiveEdge = true))
    }

    @Test
    fun `09-6_7 landing on a failure goes to the newest failed turn`() {
        val s = stateOf(*turn(1, failed = true).toTypedArray(), *turn(2, failed = true).toTypedArray(), *turn(3).toTypedArray())
        assertEquals("footer:u2", Landing.newestFailedKey(s.rows))
        assertNull(Landing.newestFailedKey(stateOf(*turn(1).toTypedArray()).rows))
    }
}
