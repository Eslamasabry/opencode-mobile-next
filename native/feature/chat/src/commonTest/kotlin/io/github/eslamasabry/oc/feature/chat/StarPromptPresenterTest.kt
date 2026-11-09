package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptRules
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptState
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.starprompt.ChatStarPrompt
import io.github.eslamasabry.oc.feature.chat.starprompt.StarPromptEvent
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The one-time GitHub star ask under the live line: when it shows, that it shows once, and what its actions do. */
@OptIn(ExperimentalCoroutinesApi::class)
class StarPromptPresenterTest {
    /** The app's port over the real rules, in memory. */
    private class MemoryStar : ChatStarPrompt {
        var state = StarPromptState()
        val events = mutableListOf<StarPromptEvent>()

        override suspend fun promptStarted(): Boolean {
            val (next, ask) = StarPromptRules.afterPrompt(state)
            state = next
            return ask
        }

        override suspend fun replyEnded(success: Boolean) {
            state = StarPromptRules.afterReply(state, success)
        }

        override fun report(event: StarPromptEvent) {
            events += event
        }
    }

    private class Outside : ChatOutside {
        val ownLinks = mutableListOf<String>()
        val replyLinks = mutableListOf<String>()

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused.also { replyLinks += url }

        override fun openLink(url: String): Boolean = false.also { replyLinks += url }

        override fun openOwnLink(url: String): Boolean = true.also { ownLinks += url }
    }

    private val world = FakeChatWorld()
    private val star = MemoryStar()
    private val outside = Outside()
    private val busy = RunFacts(busy = true)

    private fun TestScope.chat(): Pair<ChatPresenter, StateFlow<io.github.eslamasabry.oc.feature.chat.presentation.ChatUi>> {
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = outside, star = star)
        val flow = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.Eagerly)
        runCurrent()
        return p to flow
    }

    private fun TestScope.running(vararg finished: Int, asking: Int, fail: Boolean = false) {
        val rows = finished.flatMap { turn(it, failed = fail && it == finished.last()) }.toMutableList()
        rows += prompt("u$asking", "ask $asking")
        world.transcript.value = stateOf(*rows.toTypedArray(), run = busy)
        runCurrent()
    }

    private fun TestScope.ended(vararg finished: Int, failedLast: Boolean = false) {
        val rows = finished.flatMap { turn(it, failed = failedLast && it == finished.last()) }
        world.transcript.value = stateOf(*rows.toTypedArray())
        runCurrent()
    }

    @Test
    fun `B-STAR-010 the first prompt shows no ask`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        assertFalse(ui.value.starAsk)
        assertEquals(emptyList(), star.events)
    }

    @Test
    fun `B-STAR-011 the second prompt after a good reply shows the ask while it waits, and the reply folds it away`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        ended(1)
        running(1, asking = 2)
        assertTrue(ui.value.starAsk)
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
        ended(1, 2)
        assertFalse(ui.value.starAsk, "the reply arrived without a tap")
        running(1, 2, asking = 3)
        assertFalse(ui.value.starAsk, "never twice")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
    }

    @Test
    fun `B-STAR-012 a failed first reply means no ask on the second prompt`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        ended(1, failedLast = true)
        running(1, asking = 2)
        assertFalse(ui.value.starAsk)
        assertEquals(emptyList(), star.events)
    }

    @Test
    fun `B-STAR-013 no ask while the link is down`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        ended(1)
        world.link.value = ChatLink.Offline
        running(1, asking = 2)
        assertFalse(ui.value.starAsk)
        assertEquals(emptyList(), star.events)
    }

    @Test
    fun `B-STAR-014 a steer into a running turn is not a second prompt`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        // The person types again while the first turn still runs: the turn is still busy, nothing new starts.
        world.transcript.value = stateOf(prompt("u1", "ask 1"), prompt("u1b", "more"), run = busy)
        runCurrent()
        assertFalse(ui.value.starAsk)
        assertEquals(1, star.state.prompts)
    }

    @Test
    fun `B-STAR-015 Star on GitHub opens the repository through the app's own link path and ends the ask`() = runTest {
        val (p, ui) = chat()
        running(asking = 1)
        ended(1)
        running(1, asking = 2)
        p.starTapped()
        runCurrent()
        assertEquals(listOf(StarPromptRules.URL), outside.ownLinks)
        assertEquals(emptyList(), outside.replyLinks, "not the reply-link path that asks first")
        assertFalse(ui.value.starAsk)
        assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Tapped), star.events)
        p.starTapped()
        assertEquals(1, outside.ownLinks.size, "a second tap does nothing")
    }

    @Test
    fun `B-STAR-016 Not now ends the ask and opens nothing`() = runTest {
        val (p, ui) = chat()
        running(asking = 1)
        ended(1)
        running(1, asking = 2)
        p.starDismissed()
        runCurrent()
        assertFalse(ui.value.starAsk)
        assertEquals(emptyList(), outside.ownLinks)
        assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Dismissed), star.events)
        ended(1, 2)
        running(1, 2, asking = 3)
        assertFalse(ui.value.starAsk)
    }

    @Test
    fun `B-STAR-017 the repository address is the app's own https address`() {
        assertEquals("https://github.com/Eslamasabry/opencode-mobile-next", StarPromptRules.URL)
    }
}
