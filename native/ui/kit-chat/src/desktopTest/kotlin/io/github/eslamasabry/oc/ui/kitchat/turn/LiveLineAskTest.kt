package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one-time ask folds into the live line: it adds words and two actions, and nothing else on the transcript. */
@OptIn(ExperimentalTestApi::class)
class LiveLineAskTest {
    private var taps = 0
    private var dismissals = 0

    private fun ask(look: KitLiveAskLook = KitLiveAskLook.Card) =
        KitLiveAsk(
            "Enjoying OpenCode?",
            "A GitHub star helps others find it.",
            "Star on GitHub",
            "Star",
            { taps++ },
            "Not now",
            { dismissals++ },
            look,
        )

    @Test
    fun `B-STAR-020 every look shows the ask under the live line and each action does its own thing`() {
        for (look in KitLiveAskLook.entries) runComposeUiTest {
            taps = 0
            dismissals = 0
            setContent { ChatTestTheme { KitTurnLiveLine("Thinking", ask = ask(look)) } }
            onNodeWithTag(LIVE_ASK_TAG).assertExists()
            onNodeWithTag(LIVE_ASK_ACTION_TAG).performClick()
            assertEquals(1 to 0, taps to dismissals, "$look action")
            // The pill's small x is the chip's own remove target, named by the dismiss label.
            if (look == KitLiveAskLook.Pill) onNodeWithContentDescription("Not now").performClick()
            else onNodeWithTag(LIVE_ASK_DISMISS_TAG).performClick()
            assertEquals(1 to 1, taps to dismissals, "$look dismiss")
        }
    }

    @Test
    fun `B-STAR-021 an ordinary live line has no ask`() = runComposeUiTest {
        setContent { ChatTestTheme { KitTurnLiveLine("Thinking") } }
        onNodeWithTag(LIVE_ASK_TAG).assertDoesNotExist()
    }

    @Test
    fun `B-STAR-022 the ask is announced politely and takes no focus`() {
        for (look in listOf(KitLiveAskLook.Card, KitLiveAskLook.Notice)) runComposeUiTest {
            setContent { ChatTestTheme { KitTurnLiveLine("Thinking", ask = ask(look)) } }
            val words = onNodeWithText("Enjoying OpenCode?")
            assertEquals(LiveRegionMode.Polite, words.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
            onNodeWithTag(LIVE_ASK_ACTION_TAG).assert(isFocused().not())
            onNodeWithTag(LIVE_ASK_DISMISS_TAG).assert(isFocused().not())
        }
    }

    @Test
    fun `B-STAR-023 the ask folds away when it is taken back`() = runComposeUiTest {
        var shown by androidx.compose.runtime.mutableStateOf<KitLiveAsk?>(ask())
        setContent { ChatTestTheme { KitTurnLiveLine("Thinking", ask = shown) } }
        onNodeWithTag(LIVE_ASK_TAG).assertExists()
        shown = null
        waitForIdle()
        onNodeWithTag(LIVE_ASK_TAG).assertDoesNotExist()
    }
}
