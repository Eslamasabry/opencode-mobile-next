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
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlinx.collections.immutable.ImmutableList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
    fun `N9 QA wave4 Copy says Copied for a moment and a one-line block with Copy is one 48 dp target tall`() =
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
                    }
                }
            }
            mainClock.advanceTimeBy(100)
            // Copy on the first line (R3): space1 above and below one 48 dp target (kit_code_block.dart:614-620, 1006-1016).
            val height = onNode(androidx.compose.ui.test.hasTestTag("one")).fetchSemanticsNode().size.height
            assertEquals(with(density) { (48 + 2 * 4).dp.roundToPx() }, height, "a one-line block is one Copy target tall")
            onNodeWithContentDescription("Copy").performClick()
            mainClock.advanceTimeBy(100)
            onNodeWithContentDescription("Copied").assertExists()
            mainClock.advanceTimeBy(COPIED_MS + 500)
            onNodeWithContentDescription("Copy").assertExists()
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
            // The lines are one paragraph (one selectable text node per block, kit_code_block.dart `rows`).
            onNodeWithText("line 12", substring = true).assertExists()
            onNodeWithText("line 13", substring = true).assertDoesNotExist()
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
            // Flutter colours one line at a time (kit_code_block.dart `_markedLineSpan`): one call per line, once.
            assertEquals(2, counting.calls)
            waitForIdle()
            assertEquals(2, counting.calls)
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
        // KitOptical.endPad(copy, 24, 16 - (48 - 24) / 2) = 4 - 24 * .125 = 1 dp.
        assertEquals(1f, codeCopyEnd())
    }

    @Test
    fun `08 lines split after one trailing newline and know their offsets`() {
        assertEquals(listOf("a", "bc", ""), codeSplitLines("a\nbc\n\n"))
        assertEquals(listOf("a", "bc"), codeSplitLines("a\nbc\n"))
        assertEquals(listOf(0, 2, 5), codeLineStarts(listOf("a", "bc", "")))
    }

    @Test
    fun `08 types are coloured only as class titles and void is a keyword, as highlight-js does`() {
        val t = SyntaxMpCodeTokenizer()
        fun roles(line: String) =
            highlightJsRoles(line, t.tokenize(line, CodeLanguage.Dart)).associate { line.substring(it.start, it.end) to it.role }
        val title = roles("class ConnectionController extends ChangeNotifier {")
        assertEquals(CodeRole.Type, title["ConnectionController"])
        assertEquals(CodeRole.Type, title["ChangeNotifier"])
        val body = roles("  Future<void> connect(Profile profile) async {")
        assertEquals(null, body["Future"])
        assertEquals(null, body["Profile"])
        assertEquals(CodeRole.Keyword, body["void"])
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
}
