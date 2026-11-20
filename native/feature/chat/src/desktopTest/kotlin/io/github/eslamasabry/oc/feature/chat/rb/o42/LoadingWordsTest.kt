package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.boundsOfTag
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatPane
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 (a saved conversation took about ten seconds to open): "Initially I didn't have any indication that it
 * was loading". A first load that takes longer than a moment says so in words, in the middle of the room over the
 * placeholder turns; before that moment it says nothing, and while the screen's line already says something
 * (connecting, offline) it never says a second thing.
 */
@OptIn(ExperimentalTestApi::class)
class LoadingWordsTest {
    private val words = "Loading the conversation"

    @Test
    fun `LOAD-4 a slow first load says Loading the conversation after a second and not before`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            mainClock.autoAdvance = false
            setContent { E20Scene { scene.Screen(scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())) } }
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS / 2)
            onAllNodesWithTag("chat-loading-words", useUnmergedTree = true).assertCountEquals(0)
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS)
            onAllNodesWithTag("chat-loading-words", useUnmergedTree = true).assertCountEquals(1)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(1)
            val label = boundsOfTag("chat-loading-words")
            val skeleton = boundsOfTag("chat-loading")
            assertTrue(label.top >= skeleton.top && label.bottom <= skeleton.bottom, "the words $label lie in the body $skeleton")
        }

    @Test
    fun `LOAD-5 while the screen's line says something the slow load adds no second line`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            mainClock.autoAdvance = false
            val offline = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf(), status = ChatStatusUi("offline", ChatStatusKind.Offline))
            setContent { E20Scene { scene.Screen(offline) } }
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS * 3)
            onAllNodesWithTag("chat-loading-words", useUnmergedTree = true).assertCountEquals(0)
        }
}
