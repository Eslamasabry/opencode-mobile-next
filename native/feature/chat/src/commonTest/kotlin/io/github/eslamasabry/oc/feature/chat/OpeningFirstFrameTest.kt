package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Open-transitions (Galaxy S22 recording, build 10050): a page that opens with known content shows it in its first
 * frame. A conversation just started from New conversation opened on a skeleton of made-up turns for a second while
 * the message the person had typed was already on its way; an existing conversation drew a skeleton first even when its
 * rows were in memory, because the page's first frame was a fixed loading state, not what the world held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningFirstFrameTest {
    private val world = FakeChatWorld()

    private fun newConversation() {
        world.transcript.value = stateOf()
        world.load.value = ChatLoad.Loading
        world.link.value = ChatLink.Connecting
        world.title.value = null
        world.pending.value = persistentListOf(PendingSend(1, "Create a file named flag.txt", PendingState.Sending))
    }

    @Test
    fun `OT-01 a brand-new conversation shows the message on its way, never the loading skeleton`() {
        newConversation()
        val ui = ChatPresenter(world, RecordingNavigator(), Immediate).current()
        assertEquals(ChatPhase.Live, ui.phase, "the typed message is known content: no skeleton")
        assertEquals(listOf("Create a file named flag.txt"), ui.pending.map { it.text })
    }

    @Test
    fun `OT-02 the page's first state is what the world holds, before any flow has emitted`() = runTest {
        newConversation()
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        // No collector has run yet: this is the value the screen composes its first frame with.
        val first = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.WhileSubscribed()).value
        assertEquals(ChatPhase.Live, first.phase, "first frame: the bubble, not a skeleton")
        assertEquals(listOf("Create a file named flag.txt"), first.pending.map { it.text })
    }

    @Test
    fun `OT-03 an existing conversation with known rows shows them in the first state too`() = runTest {
        world.transcript.value = stateOf(*(turn(1) + turn(2)).toTypedArray())
        world.load.value = ChatLoad.Loading
        world.title.value = "Fix the build"
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val first = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.WhileSubscribed()).value
        assertEquals(ChatPhase.Live, first.phase)
        assertTrue(first.rows.isNotEmpty(), "the kept rows, at once")
        assertEquals("Fix the build", first.title)
    }

    @Test
    fun `OT-04 nothing known still shows the loading body (the skeleton is for unknown content only)`() = runTest {
        world.transcript.value = stateOf()
        world.load.value = ChatLoad.Loading
        world.pending.value = persistentListOf()
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val first = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.WhileSubscribed()).value
        assertEquals(ChatPhase.Loading, first.phase)
    }
}
