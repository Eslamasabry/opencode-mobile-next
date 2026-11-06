package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-101: a numbered list's words start on one edge whatever each item's number is. */
@OptIn(ExperimentalTestApi::class)
class ListIndentTest {
    private val actions = KitMarkdownActions(onLink = {})

    @Test
    fun `B-101 numbered items 1 to 11 start their words on one edge`() = runComposeUiTest {
        val list = (1..11).joinToString("\n") { "$it. item$it words" }
        setContent { ChatTestTheme { KitMarkdown(TestStreamingMarkdown.parse(list), actions) } }
        val edges = (1..11).map { onNodeWithText("item$it words").getBoundsInRoot().left }
        val first = edges.first()
        for ((i, left) in edges.withIndex()) {
            assertTrue((left - first).value.let { it < 0.5f && it > -0.5f }, "item ${i + 1} at $left, item 1 at $first")
        }
        assertTrue(first > 0.dp)
    }

    @Test
    fun `B-101 the marker column fits the widest number of the same digit count`() {
        assertEquals("00.", kitMdMarkerSample(ordered = true, number = 1))
        assertEquals("00.", kitMdMarkerSample(ordered = true, number = 42))
        assertEquals("000.", kitMdMarkerSample(ordered = true, number = 100))
        assertNull(kitMdMarkerSample(ordered = false, number = null))
    }
}
