package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-CHAT-058, the inline view: the line cap, the caller's words, redaction and left-to-right layout. */
@OptIn(ExperimentalTestApi::class)
class KitDiffViewTest {
    private fun numbered(n: Int, prefix: String = "line"): String = (1..n).joinToString("\n") { "$prefix $it" }

    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    @Test
    fun `B-CHAT-058 the inline view draws at most maxLines lines`() =
        runComposeUiTest {
            // 40 lines all rewritten: 40 removed, then 40 added; the cap keeps the first 12 removed.
            val file = DiffModel.fromBeforeAfter("a.txt", numbered(40), numbered(40, "new"))
            setContent {
                ChatTestTheme {
                    KitDiffView(file, maxLines = 12, hunkLabel = { _, _, _, _ -> "hunk" }, gapLabel = { "$it hidden" })
                }
            }
            assertEquals(12, onAllNodes(hasText("line ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
            onNodeWithText("line 12", useUnmergedTree = true).assertExists()
            onNodeWithText("line 13", useUnmergedTree = true).assertDoesNotExist()
            assertTrue(texts().none { it.startsWith("new ") })
        }

    @Test
    fun `B-CHAT-058 fold lines and hunk headers use the caller's words`() =
        runComposeUiTest {
            val after = numbered(60).split("\n").toMutableList().also { it[30] = "changed line 31" }
            val folded = DiffModel.fromBeforeAfter("notes.txt", numbered(60), after.joinToString("\n"))
            val patch = DiffModel.parseFilePatch("a.txt", "@@ -1,3 +1,4 @@\n a\n-b\n+c\n+d\n e\n")
            setContent {
                ChatTestTheme {
                    Column {
                        KitDiffView(folded, hunkLabel = { _, _, _, _ -> "never" }, gapLabel = { "… $it unchanged" })
                        KitDiffView(patch, hunkLabel = { _, _, s, e -> "Lines $s–$e" }, gapLabel = { "gap $it" })
                    }
                }
            }
            onNodeWithText("… 27 unchanged", useUnmergedTree = true).assertExists()
            onNodeWithText("… 26 unchanged", useUnmergedTree = true).assertExists()
            onNodeWithText("Lines 1–4", useUnmergedTree = true).assertExists()
            onNodeWithText("changed line 31", useUnmergedTree = true).assertExists()
            onNodeWithText("line 27", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `B-CHAT-058 a registered secret never reaches the drawn text`() =
        runComposeUiTest {
            val secret = "sk-canary-123456"
            val redactor = Redactor().apply { registerSecret(secret) }
            val file = DiffModel.fromBeforeAfter("a.env", "print old", "print $secret")
            setContent {
                ChatTestTheme(redactor = redactor) {
                    KitDiffView(file, hunkLabel = { _, _, _, _ -> "hunk" }, gapLabel = { "$it hidden" })
                }
            }
            val texts = texts()
            assertTrue(texts.none { secret in it }, "$texts")
            assertTrue("print old" in texts, "$texts")
            assertTrue(texts.any { it.startsWith("print ") && it != "print old" }, "the added line is drawn, masked: $texts")
        }

    @Test
    fun `B-CHAT-058 the diff stays left to right under a right-to-left layout`() =
        runComposeUiTest {
            val file = DiffModel.fromBeforeAfter("a.txt", "old value", "new value")
            setContent {
                ChatTestTheme(localeTag = "ar") {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        KitDiffView(file, hunkLabel = { _, _, _, _ -> "hunk" }, gapLabel = { "$it hidden" })
                    }
                }
            }
            val sign = onNodeWithText("+", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val text = onNodeWithText("new value", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(sign.left < text.left, "the sign sits before the text on the left: $sign vs $text")
            onNodeWithText("−", useUnmergedTree = true).assertExists()
        }
}
