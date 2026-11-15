package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Issue 147: Find's query changes at once and its hits come later from the search lane. Until the new query's own hits
 * arrive, the previous query's hits must not count, mark, scroll or step: the bar says it is searching, then one jump
 * goes to the right hit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FindHitsQueryTest {
    private val world = FakeChatWorld()

    /** The search lane, run only when the test says so: the window between a new query and its hits stays open. */
    private class HeldLane : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun `B-CHAT-115 the previous query's hits never count, mark, scroll or step for the new query`() = runTest {
        world.transcript.value =
            stateOf(*turn(1, answer = "red sky").toTypedArray(), *turn(2, answer = "blue sea").toTypedArray())
        val lane = HeldLane()
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val flow = p.states(backgroundScope, lane, SharingStarted.Eagerly)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        fun find(): FindUi? {
            repeat(4) {
                lane.runAll()
                runCurrent()
            }
            return flow.value.find
        }
        fun findHeld(): FindUi? {
            runCurrent()
            return flow.value.find
        }

        p.find("red")
        val red = assertNotNull(find())
        assertEquals(1, red.count)
        assertEquals("b:a1/p1:0", red.currentKey)

        // The new query is asked; its search has not finished.
        p.find("blue")
        val waiting = assertNotNull(findHeld())
        assertEquals("blue", waiting.query)
        assertTrue(waiting.searching, "the bar says it is looking")
        assertEquals(0, waiting.count, "never the previous query's count")
        assertEquals(null, waiting.currentKey, "nothing marked as the current hit")
        assertEquals(-1, waiting.currentIndex, "nothing to scroll to")
        // Next in the window steps through nothing (it used to step through the previous query's list).
        p.findNext()
        assertEquals(null, findHeld()?.currentKey)

        // The new query's hits land: one jump, to its own hit.
        val blue = assertNotNull(find())
        assertFalse(blue.searching)
        assertEquals(1, blue.count)
        assertEquals(0, blue.current)
        assertEquals("b:a2/p2:0", blue.currentKey)
        assertEquals(world.transcript.value.indexOf("b:a2/p2:0"), blue.currentIndex)
    }
}
