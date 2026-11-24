package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-KIT-302 (big screens): in a pane beside a list the conversation's column grows to the pane's column cap: every
 * centred cap at least the conversation's width grows by the same amount (so its parts keep one pair of edges), fills
 * a narrower pane and leaves two equal margins in a wider one; small centred parts keep their cap; outside such a pane
 * nothing moves.
 */
@OptIn(ExperimentalTestApi::class)
class KitPaneColumnTest {
    private val tags = listOf("conversation", "reading", "state", "dialog", "chat")

    private fun bounds(pane: Int, column: Dp?, rtl: Boolean = false): Map<String, Pair<Float, Float>> {
        var out: Map<String, Pair<Float, Float>> = emptyMap()
        runDesktopComposeUiTest(WINDOW, WINDOW_HEIGHT) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalKitPaneColumn provides column,
                ) {
                    // Each part fills its slot, as a page's column does, so the cap decides where it sits.
                    Column(Modifier.width(pane.dp)) {
                        Part(Modifier.kitMaxWidth(CapRole.Conversation), "conversation")
                        Part(Modifier.kitMaxWidth(CapRole.Reading), "reading")
                        Part(Modifier.kitMaxWidth(CapRole.State), "state")
                        Part(Modifier.kitMaxWidth(CapRole.Dialog), "dialog")
                        // The chat's own column: the conversation's width with its gutters, as a plain width.
                        Part(Modifier.kitMaxWidth(CHAT_PANE.dp), "chat")
                    }
                }
            }
            out = tags.associateWith { onNodeWithTag(it).getBoundsInRoot().let { b -> b.left.value to b.right.value } }
        }
        return out
    }

    @androidx.compose.runtime.Composable
    private fun Part(cap: Modifier, tag: String) = Box(Modifier.fillMaxWidth().then(cap).height(10.dp).testTag(tag))

    @Test
    fun `B-KIT-302 beside a list the conversation column fills the pane up to its cap, then two equal margins`() {
        val narrow = bounds(pane = 900, column = COLUMN.dp)
        assertEquals(0f to 900f, narrow["conversation"], "under the cap the column fills the pane")
        assertEquals(0f to 900f, narrow["chat"])
        assertEquals(230f to 670f, narrow["state"], "a state keeps its cap, centred")
        assertEquals(210f to 690f, narrow["dialog"], "a dialog keeps its cap, centred")
        val wide = bounds(pane = 1200, column = COLUMN.dp)
        assertEquals(120f to 1080f, wide["conversation"], "past the cap: two equal margins")
        assertEquals(104f to 1096f, wide["chat"], "the chat's column keeps its gutters round the same edges")
        val rtl = bounds(pane = 1200, column = COLUMN.dp, rtl = true)
        assertEquals(120f to 1080f, rtl["conversation"], "right to left: the same two margins")
        val phone = bounds(pane = 1000, column = null)
        assertEquals(150f to 850f, phone["conversation"], "one page (a phone, a window without panes): as before")
        assertEquals(140f to 860f, phone["reading"])
        assertEquals(134f to 866f, phone["chat"])
    }

    private companion object {
        const val COLUMN = 960
        const val CHAT_PANE = 732
        const val WINDOW = 1400
        const val WINDOW_HEIGHT = 400
    }
}
