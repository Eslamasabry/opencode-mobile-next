package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-403: a reply's headings scale above the body and sit closer to the text below than to the text above. */
@OptIn(ExperimentalTestApi::class)
class HeadingScaleTest {
    private val actions = KitMarkdownActions(onLink = {})

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.first()
    }

    private fun SemanticsNodeInteraction.size() = layout().layoutInput.style.fontSize.value

    private fun SemanticsNodeInteraction.weight() = layout().layoutInput.style.fontWeight!!.weight

    @Composable
    private fun Reply(markdown: String) = ChatTestTheme { KitMarkdown(TestStreamingMarkdown.parse(markdown), actions) }

    @Test
    fun `B-403 h1 and h2 are larger than the body and h3 is heavier`() =
        runComposeUiTest {
            setContent { Reply("# One\n\n## Two\n\n### Three\n\n#### Four\n\nBody words.") }
            val body = onNodeWithText("Body words.")
            assertTrue(onNodeWithText("One").size() > body.size(), "h1 larger than body")
            assertTrue(onNodeWithText("Two").size() > body.size(), "h2 larger than body")
            assertTrue(onNodeWithText("Three").weight() > body.weight(), "h3 heavier than body")
            assertTrue(onNodeWithText("Four").weight() > body.weight(), "h4 heavier than body")
        }

    @Test
    fun `B-403 the gap before a heading is larger than the gap after it`() {
        val blocks = TestStreamingMarkdown.parse("Intro.\n\n## Head\n\nWords.")
        val before = kitMdGap(blocks[0], blocks[1])
        val after = kitMdGap(blocks[1], blocks[2])
        assertTrue(before > after, "before $before > after $after")
        assertEquals(20, before)
    }

    @Test
    fun `B-403 on screen the heading sits nearer its text than the paragraph above`() =
        runComposeUiTest {
            setContent { Reply("Intro.\n\n## Head\n\nWords.") }
            val intro = onNodeWithText("Intro.").getBoundsInRoot()
            val head = onNodeWithText("Head").getBoundsInRoot()
            val words = onNodeWithText("Words.").getBoundsInRoot()
            assertTrue((head.top - intro.bottom) > (words.top - head.bottom), "above is wider than below")
        }

    @Test
    fun `B-403 a heading that opens the reply adds no space above`() =
        runComposeUiTest {
            setContent { Reply("## Head\n\nWords.") }
            val head = onNodeWithText("Head").getBoundsInRoot()
            assertTrue(head.top.value < 8f, "first heading top ${head.top}")
        }
}
