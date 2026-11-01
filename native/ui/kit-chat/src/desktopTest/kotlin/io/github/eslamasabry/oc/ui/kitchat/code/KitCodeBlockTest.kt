package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlinx.collections.immutable.ImmutableList
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
            onNodeWithText("Copy command").performClick()
            assertEquals(source, copied)
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
            onNodeWithText("Copy").performClick()
            assertFalse("sk-canary-123456" in copied.orEmpty())
        }
}
