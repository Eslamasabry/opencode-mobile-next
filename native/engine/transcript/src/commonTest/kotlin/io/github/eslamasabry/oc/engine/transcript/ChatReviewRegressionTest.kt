package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReviewRegressionTest {
    @Test
    fun `chat finding 4 stale and reconnect signal without visible rows`() = runTest {
        val handle = TranscriptHandle(Ev.S, StandardTestDispatcher(testScheduler), backgroundScope) { testScheduler.currentTime }
        handle.submit(GatewayEvent.Connected)
        handle.submit(GatewayEvent.TranscriptStale(Ev.S))
        runCurrent()
        assertEquals(1, handle.refreshes.value)
        handle.submit(GatewayEvent.Connected)
        runCurrent()
        assertEquals(2, handle.refreshes.value)
        handle.close()
    }

    @Test
    fun `chat finding 5 delayed history preserves newer streamed words`() = runTest {
        val handle = TranscriptHandle(Ev.S, StandardTestDispatcher(testScheduler), backgroundScope) { testScheduler.currentTime }
        backgroundScope.launch { handle.state.collect {} }
        runCurrent()
        val snapshot = listOf(MessageWithParts(MessageInfo("a", Ev.S, Role.Assistant), listOf(Part("p", "a", Ev.S, PartType.Text, "Hel"))))
        handle.loadHistory(snapshot)
        val version = handle.historyVersion()
        handle.submit(Ev.delta("a", "p", "lo"))
        runCurrent()
        handle.loadHistory(snapshot, version)
        assertEquals("Hello", handle.state.value.rows.filterIsInstance<BlockRow>().single().text)
        handle.close()
    }

    @Test
    fun `B-CHAT-097 chat finding 9 unexpected notice reconciles without phantom message`() {
        val model = TranscriptModel(Ev.S, TranscriptCounters())
        model.apply(GatewayEvent.Notice(Ev.S, "unknown", "notice", NoticeKind.InstructionsUpdated))
        assertTrue(model.derive(1).rows.isEmpty())
        assertEquals(1, model.refreshRequests)
    }
}
