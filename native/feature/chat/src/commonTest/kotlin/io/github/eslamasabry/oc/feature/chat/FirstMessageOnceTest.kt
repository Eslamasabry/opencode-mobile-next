package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F2 (build 10113, journey 2): the server's row of the first message reached the screen one frame before the bubble
 * left it, so that frame drew the message twice. One state carries the swap: a bubble whose server row is drawn is not.
 */
class FirstMessageOnceTest {
    private fun drawn(ui: ChatUi): List<String> =
        ui.rows.filterIsInstance<PromptRow>().map { it.text } + ui.pending.map { it.text }

    @Test
    fun `B-CHAT-ONCE-003 the state that draws the server row leaves its bubble out, even before the bubble settles`() {
        val world = FakeChatWorld()
        world.pending.value =
            persistentListOf(PendingSend(1, "hello d8", PendingState.Sending, serverRowKey = "prompt:u1"))
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        assertEquals(listOf("hello d8"), drawn(p.current()), "the bubble while the server has nothing")
        // The device's order: the row arrives while the world still holds the bubble.
        world.transcript.value = stateOf(prompt("u1", "hello d8"))
        assertEquals(listOf("hello d8"), drawn(p.current()), "one copy in the frame the row arrives")
    }

    @Test
    fun `B-CHAT-ONCE-004 another row of the same words does not take the bubble`() {
        val world = FakeChatWorld()
        world.pending.value =
            persistentListOf(PendingSend(1, "yes", PendingState.Sending, serverRowKey = "prompt:u2"))
        world.transcript.value = stateOf(prompt("u1", "yes"))
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        assertEquals(listOf("yes", "yes"), drawn(p.current()), "the earlier yes and the one on its way")
    }

    @Test
    fun `B-CHAT-ONCE-005 a failed bubble keeps its place and its Try again`() {
        val world = FakeChatWorld()
        world.pending.value =
            persistentListOf(
                PendingSend(
                    1,
                    "hello",
                    PendingState.Failed,
                    Failure.Network(NetworkWhy.Offline),
                    serverRowKey = "prompt:u1",
                )
            )
        world.transcript.value = stateOf(prompt("u1", "hello"))
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        assertEquals(listOf(1L), p.current().pending.map { it.localId })
    }
}
