package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Typing must not present a new screen state per key: the chat screen recomposes per emission. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatEmissionsTest {
    private val world = FakeChatWorld()

    @Test
    fun `typing a character that changes nothing the screen shows emits nothing`() = runTest {
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val flow = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.Eagerly)
        val seen = mutableListOf<ChatUi>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { seen += it } }
        runCurrent()
        val before = seen.size
        "hello world".indices.forEach { i ->
            world.draft.update { it.copy(text = "hello world".substring(0, i + 1)) }
            runCurrent()
        }
        // The first key turns the Back guard on (one real change); every later key is silent.
        assertEquals(1, seen.size - before, "only the Back guard flipping is a new state")
        assertTrue(seen.last().guardBack)
    }

    @Test
    fun `an unrelated change keeps the pending list instance when its bubbles are equal`() = runTest {
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val flow = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.Eagerly)
        world.pending.value = persistentListOf(PendingSend(1, "a", PendingState.Sending), PendingSend(2, "b", PendingState.Sending))
        runCurrent()
        val first = flow.value.pending
        assertEquals(listOf(2L, 1L), first.map { it.localId })
        world.pending.value = persistentListOf(PendingSend(1, "a", PendingState.Sending), PendingSend(2, "b", PendingState.Sending))
        world.title.value = "Another title"
        runCurrent()
        assertSame(first, flow.value.pending)
    }
}
