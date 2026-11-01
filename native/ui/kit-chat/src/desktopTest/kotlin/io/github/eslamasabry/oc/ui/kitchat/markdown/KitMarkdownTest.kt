package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlinx.collections.immutable.ImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitMarkdownTest {
    private val actions = KitMarkdownActions(onLink = {})

    private val sample =
        """
        ## Plan

        Run **the tests** with `./gradlew test` and read [the guide](https://example.com).

        3. third
        4. fourth
          - nested

        > a quote

        | file | lines |
        | --- | ---: |
        | Main.kt | 40 |

        ---

        ```kotlin
        val a = 1
        ```
        """.trimIndent()

    @Test
    fun `every block kind draws its words`() =
        runComposeUiTest {
            setContent { ChatTestTheme { KitMarkdown(TestStreamingMarkdown.parse(sample), actions) } }
            onNodeWithText("Plan").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            onNodeWithText("Run the tests with", substring = true).assertExists()
            onNodeWithText("3.").assertExists() // an ordered list starts at its first item's number
            onNodeWithText("4.").assertExists()
            onNodeWithText("nested").assertExists()
            onNodeWithText("a quote").assertExists()
            onNodeWithText("Main.kt").assertExists()
            onNodeWithText("40").assertExists()
            onNodeWithText("val a = 1").assertExists()
        }

    @Test
    fun `B-CHAT-060 a streaming reply shows a caret after its last words, never after code or once settled`() =
        runComposeUiTest {
            var blocks: ImmutableList<KitMdBlock> by mutableStateOf(TestStreamingMarkdown.parse("Hello there"))
            var live by mutableStateOf(true)
            setContent { ChatTestTheme { KitMarkdown(blocks, actions, live = live) } }
            assertEquals(1, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size)
            blocks = TestStreamingMarkdown.parse("Hello there\n\n```bash\nls")
            waitForIdle()
            assertEquals(0, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size, "a reply ending in code")
            blocks = TestStreamingMarkdown.parse("Hello there")
            live = false
            waitForIdle()
            assertEquals(0, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size, "a settled reply")
        }

    @Test
    fun `a done checklist item is muted, not struck through`() {
        val item = TestStreamingMarkdown.parse("- [x] done thing").single() as KitMdBlock.ListItem
        assertEquals(true, item.checked)
        assertEquals("done thing", item.inlines.text)
    }
}
