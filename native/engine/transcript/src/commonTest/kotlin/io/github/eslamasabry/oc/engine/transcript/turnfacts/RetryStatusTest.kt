package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Port of test/chat_server_state_ui_test.dart retry banner (:193, :227), a prompt that got no answer (:312) and
// assistant error cards (:529) groups, as pure decisions.
class RetryStatusTest {
    private val now = 1_788_000_000_000L

    @Test
    fun `B-CHAT-016 a retry is only called a rate limit when it is one`() {
        // Dart: "Retrying 2 in 0:42" for a dropped socket.
        assertEquals(
            RetryBannerFacts(rateLimited = false, attempt = 2, secondsLeft = 42),
            retryBannerFacts(2, now + 42_000, "ECONNRESET: The socket connection was closed", now),
        )
        // "Retrying 1…"
        assertEquals(RetryBannerFacts(false, 1, null), retryBannerFacts(1, null, "Overloaded", now))
        // "Rate limited. Retrying 3 in 0:05"
        assertEquals(RetryBannerFacts(true, 3, 5), retryBannerFacts(3, now + 5_000, "429 Too Many Requests", now))
        assertEquals(true, retryReasonIsRateLimit("Rate limit exceeded, backing off"))
        assertEquals(false, retryReasonIsRateLimit("Overloaded"))
    }

    @Test
    fun `B-CHAT-016 headline names the attempt and counts down to next`() {
        val first = retryBannerFacts(2, now + 42_000, null, now)
        assertEquals(RetryBannerFacts(true, 2, 42), first)
        assertEquals("0:42", formatRetryCountdown(first.secondsLeft!!))
        val second = retryBannerFacts(5, now + 65_000, null, now)
        assertEquals("1:05", formatRetryCountdown(second.secondsLeft!!))
        // Past due: no clock stuck at 0:00.
        assertEquals(RetryBannerFacts(true, 1, null), retryBannerFacts(1, now - 3_000, null, now))
        // No attempt: "Rate limited. Retrying…".
        assertEquals(RetryBannerFacts(true, null, null), retryBannerFacts(0, null, null, now))
        assertEquals("1:00:05", formatRetryCountdown(3605))
    }

    private fun user(id: String, text: String = "Hi") =
        MessageWithParts(
            MessageInfo(id = id, sessionId = "s", role = Role.User, time = MsgTime(1, null)),
            listOf(Part("$id-t", id, "s", PartType.Text, "text", text = text)).toImmutableList(),
        )

    private fun assistant(
        id: String,
        errorText: String? = null,
        errorKind: MessageErrorKind? = null,
        parts: List<Part> = emptyList(),
        completed: Long? = 2,
    ) = MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = "s",
            role = Role.Assistant,
            time = MsgTime(1, completed),
            errorText = errorText,
            errorKind = errorKind,
        ),
        parts.toImmutableList(),
    )

    private fun text(id: String, words: String) = Part("$id-t", id, "s", PartType.Text, "text", text = words)

    private val modelMissing =
        "ProviderModelNotFoundError: Model not found: openai/gpt-5.6-sol. Did you mean: gpt-5.6-sol, gpt-5.6-sol-pro?\n" +
            "    at <anonymous> (/\$bunfs/root/chunk.js:439:95275)"

    @Test
    fun `B-CHAT-016 a prompt that ended on an error is not answered`() {
        val messages = listOf(user("u1"), assistant("a1", errorText = modelMissing, errorKind = MessageErrorKind.Unknown))
        assertEquals(UnansweredTurn(0, 1, UnansweredState.NotAnswered), unansweredTurn(messages, refused = false, running = false))
        assertNull(unansweredTurn(messages, refused = false, running = true))
    }

    @Test
    fun `B-CHAT-016 a prompt the server refused before any step is not answered`() {
        assertEquals(UnansweredTurn(0, null, UnansweredState.NotAnswered), unansweredTurn(listOf(user("u1")), true, false))
        // Not refused and nothing came: no reply came back.
        assertEquals(UnansweredTurn(0, null, UnansweredState.NoReply), unansweredTurn(listOf(user("u1")), false, false))
        // A command may do its work without a word back.
        assertNull(unansweredTurn(listOf(user("u1", "/compact")), false, false))
    }

    @Test
    fun `B-CHAT-016 a turn that ended with nothing and no error says no reply came back`() {
        assertEquals(
            UnansweredTurn(0, 1, UnansweredState.NoReply),
            unansweredTurn(listOf(user("u1"), assistant("a1")), refused = false, running = false),
        )
        // A step still unfinished is not over; steps that ran quietly did answer; a stop here is no failure.
        assertNull(unansweredTurn(listOf(user("u1"), assistant("a1", completed = null)), false, false))
        val tool = Part("a1-x", "a1", "s", PartType.Tool, "tool", tool = ToolState(ToolStatus.Completed))
        assertNull(unansweredTurn(listOf(user("u1"), assistant("a1", parts = listOf(tool))), false, false))
        assertNull(unansweredTurn(listOf(user("u1"), assistant("a1")), false, false, stoppedPromptId = "u1"))
    }

    @Test
    fun `B-CHAT-016 an answered turn, an older failure and an abort are not unanswered`() {
        val messages =
            listOf(
                user("u1"),
                assistant("a1", errorText = "Something odd", errorKind = MessageErrorKind.Unknown),
                user("u2"),
                assistant("a2", parts = listOf(text("a2", "Done."))),
            )
        assertNull(unansweredTurn(messages, false, false))
        assertNull(unansweredTurn(listOf(user("u1"), assistant("a1", "aborted", MessageErrorKind.Aborted)), false, false))
        val compacted =
            MessageWithParts(
                MessageInfo(id = "c1", sessionId = "s", role = Role.User),
                listOf(Part("c1-0", "c1", "s", PartType.Compaction, "v2:compaction", text = "x")).toImmutableList(),
            )
        assertNull(unansweredTurn(listOf(user("u1"), compacted), false, false))
    }

    @Test
    fun `B-CHAT-016 assistant error cards are chosen from the typed error`() {
        assertEquals(
            AssistantErrorState.ContextOverflow,
            assistantErrorState(MessageErrorKind.ContextOverflow, "Context window exceeded"),
        )
        assertEquals(
            AssistantErrorState.FreeTier,
            assistantErrorState(
                MessageErrorKind.Unknown,
                "FreeUsageLimitError: Free usage limit reached. Subscribe to OpenCode Go to keep going: https://opencode.ai/go",
            ),
        )
        assertEquals(AssistantErrorState.ProviderAuth, assistantErrorState(MessageErrorKind.ProviderAuth, "Invalid API key"))
        assertEquals(AssistantErrorState.OutputLength, assistantErrorState(MessageErrorKind.OutputLength, "Output too long"))
        assertEquals(AssistantErrorState.Stopped, assistantErrorState(MessageErrorKind.Aborted, "aborted"))
        // The class sits in the text, not the name.
        assertEquals(AssistantErrorState.ModelNotFound, assistantErrorState(MessageErrorKind.Unknown, modelMissing))
        assertEquals(AssistantErrorState.Generic, assistantErrorState(MessageErrorKind.Unknown, "Something odd"))
        assertEquals(
            AssistantErrorState.ContentFilter,
            assistantErrorState(MessageErrorKind.ContentFilter, "Blocked by the content filter."),
        )
        assertEquals(AssistantErrorState.UsageLimit, assistantErrorState(null, "Go usage limit exceeded"))
        // Without a way on, the usage limit is a plain card.
        assertEquals(AssistantErrorState.Generic, assistantErrorState(null, "Go usage limit exceeded", canChooseModel = false))
        // An agent's sign-in gone, in its own words.
        assertEquals(AssistantErrorState.ProviderAuth, assistantErrorState(null, "Not logged in · Please run /login"))
    }
}
