package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-CHAT-131: a long wait on one tool puts Stop on the live line; an ordinary line has none. */
@OptIn(ExperimentalTestApi::class)
class LiveLineStopTest {
    @Test
    fun `B-CHAT-131 the live line offers Stop only when it is given one`() = runComposeUiTest {
        var stops = 0
        setContent {
            ChatTestTheme {
                KitTurnLiveLine("Still waiting for webfetch.", actionLabel = "Stop", onAction = { stops++ })
            }
        }
        onNodeWithTag(LIVE_ACTION_TAG).performClick()
        assertEquals(1, stops)
    }

    @Test
    fun `B-CHAT-131 an ordinary live line has no Stop`() = runComposeUiTest {
        setContent { ChatTestTheme { KitTurnLiveLine("Thinking") } }
        onNodeWithTag(LIVE_ACTION_TAG).assertDoesNotExist()
    }
}
