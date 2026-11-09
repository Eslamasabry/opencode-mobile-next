package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** Cache-first (CF): a conversation's last page is kept as it read and draws the same rows again. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeptTranscriptTest {
    @Test
    fun `CF-10 the kept page carries streamed words and draws the same rows as the live transcript`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = TranscriptHandle(Ev.S, dispatcher, backgroundScope) { testScheduler.currentTime }
        backgroundScope.launch { live.state.collect {} }
        Ev.prompt("u1", "Fix the login bug").forEach(live::submit)
        live.submit(Ev.assistant("a1", completed = 5))
        live.submit(Ev.text("a1", "a1-t"))
        live.submit(Ev.delta("a1", "a1-t", "Done: the token check "))
        live.submit(Ev.delta("a1", "a1-t", "now runs first."))
        live.awaitIdle()

        val kept = live.keptMessages()
        assertEquals("Done: the token check now runs first.", kept.last().parts.single().text)

        // Through the wire, as the last-known store keeps it.
        val json = Json { encodeDefaults = false }
        val back = json.decodeFromString(KeptTranscript.serializer(), json.encodeToString(KeptTranscript.serializer(), KeptTranscript("Login", kept)))

        val again = TranscriptHandle(Ev.S, StandardTestDispatcher(testScheduler), backgroundScope) { testScheduler.currentTime }
        backgroundScope.launch { again.state.collect {} }
        again.loadHistory(back.messages)
        again.awaitIdle()
        runCurrent()

        assertEquals(live.state.value.rows.map { it.key }, again.state.value.rows.map { it.key }, "same rows, same keys")
        assertEquals(
            "Done: the token check now runs first.",
            again.state.value.rows.filterIsInstance<BlockRow>().single().text,
        )
        live.close()
        again.close()
    }

    @Test
    fun `CF-11 fitting keeps the newest messages under the cap and trims long tool text`() {
        val messages =
            (0 until 40).map { i ->
                MessageWithParts(
                    MessageInfo("m$i", Ev.S, if (i % 2 == 0) Role.User else Role.Assistant),
                    listOf(
                        Part("p$i", "m$i", Ev.S, PartType.Text, text = "message $i ".repeat(20)),
                        Part("t$i", "m$i", Ev.S, PartType.Tool, toolName = "read", toolStatus = ToolStatus.Completed, searchText = "x".repeat(20_000)),
                    ),
                )
            }
        val fitted = KeptTranscript.fit("Title", messages, maxBytes = 64 * 1024)!!
        assertTrue(fitted.messages.isNotEmpty() && fitted.messages.size < messages.size)
        assertEquals("m39", fitted.messages.last().info.id, "the newest message is always kept")
        assertTrue(fitted.messages.all { m -> m.parts.all { it.searchText.length <= KeptTranscript.MAX_TOOL_TEXT } })
        val size = Json.encodeToString(KeptTranscript.serializer(), fitted).encodeToByteArray().size
        assertTrue(size <= 64 * 1024, "fits: $size")

        assertNull(KeptTranscript.fit("Title", messages.takeLast(1), maxBytes = 100), "nothing fits a tiny cap")
    }
}
