package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlinx.collections.immutable.ImmutableList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitCodeBlockTest {
    private class Counting : CodeTokenizer {
        var calls = 0

        override fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken> {
            calls++
            return SyntaxMpCodeTokenizer().tokenize(code, language)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.box(): androidx.compose.ui.geometry.Rect =
        getUnclippedBoundsInRoot().let {
            androidx.compose.ui.geometry.Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
        }

    private fun near(expected: Float, actual: Float, slack: Float = 1f, what: String) =
        assertTrue(kotlin.math.abs(expected - actual) <= slack, "$what: expected $expected, was $actual")

    private fun SemanticsNodeInteractionsProvider.allText(): String =
        onRoot(useUnmergedTree = true).fetchSemanticsNode().let { root ->
            buildString {
                fun visit(n: androidx.compose.ui.semantics.SemanticsNode) {
                    n.config.getOrNull(SemanticsProperties.Text)?.forEach { append(it.text).append('\n') }
                    n.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { append(it).append('\n') }
                    n.children.forEach(::visit)
                }
                visit(root)
            }
        }

    @Test
    fun `B-CHAT-055 a heredoc command shows one prompt per command`() =
        runComposeUiTest {
            val script = "cat > /tmp/a.py <<'EOF'\nimport os\nprint(os.getcwd())\nEOF\npython3 /tmp/a.py"
            setContent { ChatTestTheme { KitCodeBlock(source = script, kind = KitCodeKind.Command) } }
            onAllNodesWithText("$").fetchSemanticsNodes().let { assertEquals(2, it.size) }
        }

    @Test
    fun `B-CHAT-056 copy hands over the source, never the prompts or the line cap`() =
        runComposeUiTest {
            val source = (1..20).joinToString("\n") { "echo $it" }
            var copied: String? = null
            setContent {
                ChatTestTheme {
                    KitCodeBlock(
                        source = source, kind = KitCodeKind.Command, maxLines = 12,
                        copyLabel = "Copy command", onCopy = { copied = it },
                        moreLabel = { "Show all, $it more lines" }, onMore = {},
                    )
                }
            }
            onNodeWithContentDescription("Copy command").performClick()
            assertEquals(source, copied)
        }

    @Test
    fun `N9 C-006 Copy says Copied for a moment and a one-line block with Copy is one target tall plus 4 dp`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                ChatTestTheme {
                    androidx.compose.foundation.layout.Column {
                        KitCodeBlock(
                            source = "print(1)", kind = KitCodeKind.Code,
                            modifier = androidx.compose.ui.Modifier.testTag("one"),
                            copyLabel = "Copy", onCopy = {}, copiedLabel = "Copied",
                        )
                        KitCodeBlock(
                            source = "print(1)", kind = KitCodeKind.Code,
                            modifier = androidx.compose.ui.Modifier.testTag("plain"),
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(100)
            // Flutter (`kit_code_block.dart:596-633`, `:1003-1013`): with Copy on line one the block is S1 + 48 + S1;
            // without controls there is nothing to touch, so it is S4 + the line (19 dp) + S4, not a 48 dp minimum.
            val withCopy = onNode(androidx.compose.ui.test.hasTestTag("one")).box()
            val without = onNode(androidx.compose.ui.test.hasTestTag("plain")).box()
            near(56f, withCopy.height, what = "one line with Copy")
            near(51f, without.height, what = "one line without controls")
            onNodeWithContentDescription("Copy").performClick()
            mainClock.advanceTimeBy(100)
            onNodeWithContentDescription("Copied").assertExists()
            mainClock.advanceTimeBy(COPIED_MS + 500)
            onNodeWithContentDescription("Copy").assertExists()
        }

    @Test
    fun `a one-line wrapped output block with no controls is one line plus its padding`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitCodeBlock(
                        source = "c2.txt", kind = KitCodeKind.Output,
                        modifier = androidx.compose.ui.Modifier.testTag("one"),
                        wrap = true, showWrapToggle = false,
                    )
                }
            }
            val one = onNode(androidx.compose.ui.test.hasTestTag("one")).box()
            assertTrue(one.height < 60f, "one line plus 16 dp above and below, not a 48 dp minimum: ${one.height}")
        }

    @Test
    fun `C-006 text starts 16 dp in, Copy ends the first line and Wrap sits under it`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitCodeBlock(
                        "x = 1\n" + "y".repeat(300), KitCodeKind.Code,
                        androidx.compose.ui.Modifier.width(360.dp).testTag("block"),
                        copyLabel = "Copy", onCopy = {},
                    )
                }
            }
            val block = onNode(androidx.compose.ui.test.hasTestTag("block")).box()
            val first = onNodeWithText("x = 1", useUnmergedTree = true).box()
            val copy = onNodeWithContentDescription("Copy").box()
            val wrap = onNodeWithContentDescription("Wrap lines").box()
            near(block.left + 16f, first.left, what = "text start (16 dp, was 12)")
            near(48f, copy.width, what = "Copy target")
            near(block.right - CODE_COPY_END, copy.right, what = "Copy's target 1 dp from the end")
            near(block.top + 4f, copy.top, what = "Copy 4 dp under the top")
            near((first.top + first.bottom) / 2, (copy.top + copy.bottom) / 2, what = "line one centred on Copy")
            near(copy.left, wrap.left, what = "Wrap under Copy, same column")
            near(copy.bottom, wrap.top, what = "Wrap right under Copy")
        }

    @Test
    fun `C-006 a capped block draws Open full output under a hairline and hands over`() =
        runComposeUiTest {
            val source = (1..43).joinToString("\n") { "line $it" }
            var more = 0
            setContent {
                ChatTestTheme {
                    KitCodeBlock(source = source, kind = KitCodeKind.Code, maxLines = 12, onMore = { more++ })
                }
            }
            onNodeWithText("line 12").assertExists()
            onNodeWithText("line 13").assertDoesNotExist()
            onNodeWithText("Open full output").performClick()
            assertEquals(1, more)
        }

    @Test
    fun `C-006 without a hand-over a capped block says Show all N lines and unfolds in place`() =
        runComposeUiTest {
            val source = (1..20).joinToString("\n") { "line $it" }
            setContent { ChatTestTheme { KitCodeBlock(source = source, kind = KitCodeKind.Output) } }
            onNodeWithText("line 13").assertDoesNotExist()
            onNodeWithText("Show all 20 lines").performClick()
            waitForIdle()
            onNodeWithText("line 13").assertExists()
            onNodeWithText("line 20").assertExists()
            onNodeWithText("Show all 20 lines").assertDoesNotExist()
        }

    @Test
    fun `C-006 a command never offers Wrap (Flutter scrolls commands sideways)`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitCodeBlock(
                        "echo " + "a".repeat(300), KitCodeKind.Command, androidx.compose.ui.Modifier.width(360.dp),
                        copyLabel = "Copy", onCopy = {},
                    )
                }
            }
            onNodeWithContentDescription("Copy").assertExists()
            onNodeWithContentDescription("Wrap lines").assertDoesNotExist()
        }

    @Test
    fun `C-006 a long fence's chunks are coloured in their language, an open one stays plain`() =
        runComposeUiTest {
            val counting = Counting()
            var open by mutableStateOf(true)
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalKitCodeTokenizer provides counting) {
                        KitCodeChunk(
                            "val a = 1\nval b = 2", continued = false, continues = true,
                            language = CodeLanguage.Kotlin, open = open,
                        )
                    }
                }
            }
            waitForIdle()
            assertEquals(0, counting.calls, "a streaming fence is never coloured")
            open = false
            waitForIdle()
            assertEquals(1, counting.calls)
            val text =
                onNodeWithText("val a = 1\nval b = 2", useUnmergedTree = true)
                    .fetchSemanticsNode().config[SemanticsProperties.Text].first()
            val bold = androidx.compose.ui.text.font.FontWeight.W600
            assertTrue(text.spanStyles.any { it.item.fontWeight == bold }, "keywords drawn")
        }

    @Test
    fun `B-CHAT-056 output over twelve lines shows twelve and says how many more`() =
        runComposeUiTest {
            val source = (1..20).joinToString("\n") { "line $it" }
            var more = 0
            setContent {
                ChatTestTheme {
                    KitCodeBlock(source = source, kind = KitCodeKind.Output, moreLabel = { "Open full output ($it more)" }, onMore = { more++ })
                }
            }
            onNodeWithText("line 12").assertExists()
            onNodeWithText("line 13").assertDoesNotExist()
            onNodeWithText("Open full output (8 more)").performClick()
            assertEquals(1, more)
        }

    @Test
    fun `B-CHAT-056 an open fence is never highlighted and a closed one is tokenised once`() =
        runComposeUiTest {
            val counting = Counting()
            var open by mutableStateOf(true)
            var text by mutableStateOf("val a = 1")
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalKitCodeTokenizer provides counting) {
                        KitCodeBlock(source = text, kind = KitCodeKind.Code, language = CodeLanguage.Kotlin, open = open)
                    }
                }
            }
            text = "val a = 1\nval b = 2"
            waitForIdle()
            assertEquals(0, counting.calls)
            open = false
            waitForIdle()
            assertEquals(1, counting.calls)
            waitForIdle()
            assertEquals(1, counting.calls)
        }

    @Test
    fun `B-SEC-094 a registered secret in a command never reaches the screen or the copy`() =
        runComposeUiTest {
            val redactor = Redactor().apply { registerSecret("sk-canary-123456") }
            var copied: String? = null
            setContent {
                ChatTestTheme(redactor = redactor) {
                    KitCodeBlock(
                        source = "export KEY=sk-canary-123456\ncurl -H \"Authorization: Bearer sk-canary-123456\" x",
                        kind = KitCodeKind.Command, copyLabel = "Copy", onCopy = { copied = it },
                    )
                }
            }
            val shown = allText()
            assertFalse("sk-canary-123456" in shown, shown)
            assertTrue("export KEY=" in shown, shown)
            onNodeWithContentDescription("Copy").performClick()
            assertFalse("sk-canary-123456" in copied.orEmpty())
        }

    // ---- order 08: the Flutter rules of kit_code_block.dart, as pure functions ----

    @Test
    fun `08 the plan puts Copy on the first line only when the block has no words for a header`() {
        // A 380 dp block, no words, Copy: inline; a line wider than 380 - 16 - 4 - 48 - 12 = 300 dp offers Wrap.
        assertEquals(CodePlan(inline = true, wrapToggle = false), codePlan(380f, false, false, true, true, 300f, null))
        assertEquals(CodePlan(inline = true, wrapToggle = true), codePlan(380f, false, false, true, true, 300.6f, null))
        // Words (a file name): Copy goes to the header; the room is the block less space4 on each side.
        assertEquals(CodePlan(inline = false, wrapToggle = false), codePlan(380f, true, false, true, true, 348f, null))
        assertEquals(CodePlan(inline = false, wrapToggle = true), codePlan(380f, true, false, true, true, 349f, null))
        // A command never offers Wrap; a gutter takes its width and space2 from the room.
        assertEquals(CodePlan(inline = true, wrapToggle = false), codePlan(380f, false, false, true, false, 999f, 8f))
        assertEquals(CodePlan(inline = false, wrapToggle = false), codePlan(Float.POSITIVE_INFINITY, false, false, true, true, 999f, null))
        // No Copy and nothing overflowing: no inline controls at all.
        assertEquals(CodePlan(inline = false, wrapToggle = false), codePlan(380f, false, false, false, true, 10f, null))
    }

    @Test
    fun `08 the Copy glyph's ink ends on the text inset`() {
        // KitOptical.endPad(copy, 24, 16 - (48 - 24) / 2) = 4 - 24 * .125 = 1 dp, the chat block's CODE_COPY_END.
        assertEquals(1f, codeCopyEnd())
        assertEquals(CODE_COPY_END.toFloat(), codeCopyEnd())
    }

    @Test
    fun `08 lines split after one trailing newline and know their offsets`() {
        assertEquals(listOf("a", "bc", ""), codeSplitLines("a\nbc\n\n"))
        assertEquals(listOf("a", "bc"), codeSplitLines("a\nbc\n"))
        assertEquals(listOf(0, 2, 5), codeLineStarts(listOf("a", "bc", "")))
    }

    @Test
    fun `08 find marks paint accent at 18 percent and the active one at 38 percent`() {
        val accent = Color(0xFF00AA55)
        // Line 2 starts at 4 in "abc\ndefg"; mark 0 covers "ef" (5 until 7), mark 1 (the active one) "g".
        val marked = markLine(AnnotatedString("defg"), 4, listOf(5 until 7, 7 until 8), active = 1, accent = accent)
        val styles = marked.spanStyles.map { Triple(it.start, it.end, it.item.background.alpha) }
        assertEquals(2, styles.size)
        assertEquals(1 to 3, styles[0].first to styles[0].second)
        assertTrue(kotlin.math.abs(styles[0].third - .18f) < .01f)
        assertEquals(3 to 4, styles[1].first to styles[1].second)
        assertTrue(kotlin.math.abs(styles[1].third - .38f) < .01f)
        assertEquals(AnnotatedString("abc"), markLine(AnnotatedString("abc"), 0, listOf(5 until 7), null, accent))
    }

    @Test
    fun `08 words give the block a header with the file name, the counts and a labelled Copy`() =
        runComposeUiTest {
            var copied: String? = null
            setContent {
                ChatTestTheme {
                    androidx.compose.foundation.layout.Column {
                        KitCodeBlock(
                            "val a = 1", KitCodeKind.Code, language = CodeLanguage.Kotlin, onCopy = { copied = it },
                            fileName = "lib/a.kt", added = 4, removed = 1,
                        )
                        KitCodeBlock("adb one\nadb two", KitCodeKind.Command, onCopy = {}, copyWords = "Copy commands")
                        KitCodeBlock("", KitCodeKind.Code)
                    }
                }
            }
            onNodeWithText("lib/a.kt", useUnmergedTree = true).assertExists()
            onNodeWithContentDescription("4 added, 1 removed", useUnmergedTree = true).assertExists()
            onNodeWithText("Copy commands", useUnmergedTree = true).assertExists()
            onNodeWithText("Empty", useUnmergedTree = true).assertExists()
            onNodeWithContentDescription("Copy code").performClick()
            assertEquals("val a = 1", copied)
        }
}
