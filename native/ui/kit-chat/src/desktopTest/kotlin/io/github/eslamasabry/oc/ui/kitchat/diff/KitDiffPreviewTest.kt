package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C-005, the diff preview card under a Write or Edit step (Flutter `KitStepPreview` / `_PreviewCard`,
 * lib/ui/kit/chat/kit_step_timeline.dart:44-176, :1090-1230): at most six lines, a `+N` gutter, one button.
 */
@OptIn(ExperimentalTestApi::class)
class KitDiffPreviewTest {
    private fun numbered(n: Int): String = (1..n).joinToString("\n") { "line $it" }

    @Test
    fun `B-CHAT-058 a new file previews its first six lines as added, numbered from 1, with more after`() {
        val preview = KitDiffPreview.fromText(numbered(9))
        assertEquals(6, preview.lines.size)
        assertTrue(preview.more)
        assertTrue(preview.lines.all { it.kind == DiffLineKind.Added })
        assertEquals(listOf("+1", "+2", "+3", "+4", "+5", "+6"), preview.lines.map(preview::gutter))
        assertFalse(KitDiffPreview.fromText(numbered(6)).more)
    }

    @Test
    fun `B-CHAT-058 a patch previews its first change with one line of context before it`() {
        val patch = "--- a/x.txt\n+++ b/x.txt\n@@ -10,4 +10,4 @@\n one\n two\n-three\n+THREE\n\\ No newline at end of file\n four\n"
        val preview = KitDiffPreview.fromPatch(patch)
        assertEquals(listOf("two", "three", "THREE", "four"), preview.lines.map { it.text })
        assertEquals(listOf("11", "−12", "+12", "13"), preview.lines.map(preview::gutter))
        assertFalse(preview.more)
    }

    @Test
    fun `B-CHAT-058 an old and new pair previews the changed lines with signs and no numbers`() {
        val preview = KitDiffPreview.fromTexts(before = "a\nb\nc", after = "a\nB\nc")
        assertEquals(listOf(DiffLineKind.Context, DiffLineKind.Removed, DiffLineKind.Added, DiffLineKind.Context), preview.lines.map { it.kind })
        assertEquals(listOf("", "−", "+", ""), preview.lines.map(preview::gutter))
    }

    @Test
    fun `B-CHAT-058 the preview card draws a gutter clear of its text, opens on a tap and stays left to right`() =
        runComposeUiTest {
            var opened = 0
            val preview = KitDiffPreview.fromText("# App\nRun it.")
            setContent {
                ChatTestTheme(localeTag = "ar") {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        KitDiffPreviewCard(preview, label = "Write README.md", onOpen = { opened++ })
                    }
                }
            }
            val gutter = onNodeWithText("+1", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val text = onNodeWithText("# App", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(gutter.right < text.left, "the number sits before the line, left to right, without overlap: $gutter vs $text")
            onNodeWithTag("kit-diff-preview").performClick()
            assertEquals(1, opened)
            val names =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                    .fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue("Write README.md" in names, "the card is named as one button: $names")
        }

    @Test
    fun `B-CHAT-058 the preview card never draws a registered secret`() =
        runComposeUiTest {
            val secret = "sk-canary-123456"
            val redactor = Redactor().apply { registerSecret(secret) }
            setContent {
                ChatTestTheme(redactor = redactor) {
                    KitDiffPreviewCard(KitDiffPreview.fromText("KEY=$secret"), label = "Write .env", onOpen = {})
                }
            }
            val texts =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
            assertTrue(texts.none { secret in it }, "$texts")
            assertTrue(texts.any { it.startsWith("KEY=") }, "$texts")
        }
}
