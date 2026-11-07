package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chat rendering parity, batch B (markdown): each test is one row of the 2026-11-07 side-by-side audit (C-0NN), with
 * the Flutter value it proves from `lib/ui/kit/chat/kit_markdown.dart`.
 */
@OptIn(ExperimentalTestApi::class)
class ChatParityMarkdownTest {
    private val actions = KitMarkdownActions(onLink = {})
    private val sink = KitMdLinkSink {}

    private fun near(a: Dp, b: Dp, what: String) = assertTrue(abs((a - b).value) < 0.6f, "$what: $a, expected $b")

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first()
    }

    // C-015 -------------------------------------------------------------------------------------------------------

    @Test
    fun `C-015 a block's direction comes from its first strong letter`() {
        assertEquals(LayoutDirection.Rtl, kitMdDirectionOf("ملخص التغييرات"))
        assertEquals(LayoutDirection.Rtl, kitMdDirectionOf("1. انتهى."), "digits and marks are weak")
        assertEquals(LayoutDirection.Ltr, kitMdDirectionOf("Run انتهى"))
        assertEquals(LayoutDirection.Ltr, kitMdDirectionOf("— Ελληνικά"))
        assertNull(kitMdDirectionOf("42 — 7"), "no strong letter: the ambient direction applies")
    }

    @Test
    fun `C-015 a one-line Arabic heading and paragraph sit on the right edge of a left-to-right column`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    Box(Modifier.width(360.dp)) {
                        KitMarkdown(TestStreamingMarkdown.parse("## ملخص\n\nانتهى.\n\nDone."), actions)
                    }
                }
            }
            for (words in listOf("ملخص", "انتهى.")) {
                val node = onNodeWithText(words)
                val bounds = node.getBoundsInRoot()
                near(bounds.right - bounds.left, 360.dp, "$words fills the column")
                val l = node.layout()
                assertTrue(l.getLineLeft(0) > l.size.width / 2f, "$words starts on the right: ${l.getLineLeft(0)}")
            }
            val latin = onNodeWithText("Done.").layout()
            assertTrue(latin.getLineLeft(0) < 1f, "Latin text stays on the left")
        }

    @Test
    fun `C-015 an English paragraph keeps the left edge in a right-to-left app`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(Modifier.width(360.dp)) { KitMarkdown(TestStreamingMarkdown.parse("Done."), actions) }
                    }
                }
            }
            val l = onNodeWithText("Done.").layout()
            assertTrue(l.getLineLeft(0) < 1f, "left to right by its letters: ${l.getLineLeft(0)}")
        }

    // C-016 / C-017 / C-023 ---------------------------------------------------------------------------------------

    @Test
    fun `C-016 C-017 the inline look is partner links, accent paths, Mono 13 code, from the theme`() =
        runComposeUiTest {
            var colors: KitColors? = null
            var mono: TextStyle? = null
            setContent {
                ChatTestTheme(dark = true) {
                    colors = LocalKitColors.current
                    mono = LocalKitTypography.current.style(TypeRole.Mono)
                }
            }
            val c = assertNotNull(colors)
            val style = kitMdInlineStyle(c, assertNotNull(mono))
            assertEquals(c.partner, style.link, "links are roles.partner (#c7a6ff dark), not the accent")
            assertEquals(Color(0xFFC7A6FF), style.link)
            assertEquals(c.accent, style.pathLink, "a confirmed path is the accent, as Flutter's path chip")
            assertEquals(13.sp, style.monoSize)
        }

    @Test
    fun `C-016 inline code is mono at the Mono size with no tint`() {
        val style = KitMdInlineStyle(Color.Blue, androidx.compose.ui.text.font.FontFamily.Monospace, 13.sp)
        val s = inlineAnnotated(TestStreamingMarkdown.parseInlines("run `make` now"), style, sink)
        val code = s.spanStyles.single { it.item.fontFamily != null }
        assertEquals(13.sp, code.item.fontSize)
        assertEquals(Color.Unspecified, code.item.background)
        assertTrue(s.getStringAnnotations(0, s.length).isEmpty(), "no tint marker for a background pass")
    }

    @Test
    fun `C-023 bold is weight 700`() {
        val style = KitMdInlineStyle(Color.Blue, androidx.compose.ui.text.font.FontFamily.Monospace)
        val s = inlineAnnotated(TestStreamingMarkdown.parseInlines("a **b** c"), style, sink)
        assertEquals(FontWeight.Bold, s.spanStyles.single().item.fontWeight)
        assertEquals(700, FontWeight.Bold.weight)
    }

    // C-018 -------------------------------------------------------------------------------------------------------

    @Test
    fun `C-018 a quote is secondary words 8 dp past a two-pixel bar`() =
        runComposeUiTest {
            var colors: KitColors? = null
            setContent {
                ChatTestTheme {
                    colors = LocalKitColors.current
                    KitMarkdown(TestStreamingMarkdown.parse("> a quote"), actions)
                }
            }
            val node = onNodeWithText("a quote")
            near(node.getBoundsInRoot().left, 8.dp, "quote words start")
            assertEquals(assertNotNull(colors).text2, node.layout().layoutInput.style.color)
            assertEquals(2f, kitMdQuoteBarPx(2.625f))
            assertEquals(2f, kitMdQuoteBarPx(1f), "two pixels, never under one logical pixel")
        }

    // C-022 -------------------------------------------------------------------------------------------------------

    @Test
    fun `C-022 bullets take a 16 dp column, numbers 24 dp, and each nesting level moves in 16 dp`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    KitMarkdown(TestStreamingMarkdown.parse("- top\n  - inner\n\n1. first\n  - under"), actions)
                }
            }
            near(onNodeWithText("top").getBoundsInRoot().left, 16.dp, "bullet words")
            near(onNodeWithText("inner").getBoundsInRoot().left, 32.dp, "nested bullet words")
            near(onNodeWithText("first").getBoundsInRoot().left, 24.dp, "numbered words")
            near(onNodeWithText("under").getBoundsInRoot().left, 32.dp, "a bullet nested under a number stays nested")
        }

    @Test
    fun `C-022 the marker column grows with the font scale`() =
        runComposeUiTest {
            setContent { ChatTestTheme(fontScale = 1.5f) { KitMarkdown(TestStreamingMarkdown.parse("- top"), actions) } }
            near(onNodeWithText("top").getBoundsInRoot().left, 24.dp, "16 dp at 150 %")
        }

    // C-025 -------------------------------------------------------------------------------------------------------

    @Test
    fun `C-025 blocks are 12 dp apart and list items 8 dp`() {
        val blocks = TestStreamingMarkdown.parse("## Head\n\nWords.\n\n- one\n- two\n\nEnd.")
        val gaps = blocks.zipWithNext { a, b -> kitMdGap(a, b) }
        assertEquals(listOf(12, 12, 8, 12), gaps)
    }

    @Test
    fun `C-025 a heading and its paragraph are 12 dp apart on screen`() =
        runComposeUiTest {
            setContent { ChatTestTheme { KitMarkdown(TestStreamingMarkdown.parse("## Head\n\nWords."), actions) } }
            val gap = onNodeWithText("Words.").getBoundsInRoot().top - onNodeWithText("Head").getBoundsInRoot().bottom
            near(gap, 12.dp, "heading to paragraph")
        }

    // C-026 -------------------------------------------------------------------------------------------------------

    @Test
    fun `C-026 a rule is one device pixel with 4 dp above and below`() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    ChatTestTheme { KitMarkdownRule(Modifier.testTag("rule")) }
                }
            }
            val b = onNodeWithTag("rule").getBoundsInRoot()
            near(b.bottom - b.top, 8.5.dp, "4 + 0.5 (one pixel at density 2) + 4")
        }

    // C-021 / C-028 -----------------------------------------------------------------------------------------------

    @Test
    fun `C-021 a reply waiting on the person shows no caret`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme { KitMarkdown(TestStreamingMarkdown.parse("I need to run the tests."), actions, live = true, caret = false) }
            }
            assertEquals(0, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size)
        }

    @Test
    fun `C-021 the caret is steady when ambient loops are off`() =
        runComposeUiTest {
            var alpha: (() -> Float)? = null
            setContent { ChatTestTheme { alpha = rememberCaretBreath() } }
            mainClock.advanceTimeBy(CARET_BREATH_MS.toLong())
            assertEquals(1f, assertNotNull(alpha).invoke())
        }

    @Test
    fun `C-028 the caret stands 2 dp after the last glyph, never on it, and before it in Arabic`() =
        runComposeUiTest {
            var ltr: TextLayoutResult? = null
            var rtl: TextLayoutResult? = null
            setContent {
                val measurer = rememberTextMeasurer()
                ltr = measurer.measure("I am writing", TextStyle(fontSize = 16.sp, lineHeight = 24.sp))
                // An Arabic block is laid out right to left (C-015, MdText sets the direction from the words).
                val arabic = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, textDirection = TextDirection.Rtl)
                rtl = measurer.measure("أنا أكتب", arabic)
            }
            waitForIdle()
            val density = Density(1f)
            val l = assertNotNull(ltr)
            val bar = kitCaretRect(l, l.layoutInput.text.length, density)
            val glyphRight = l.getBoundingBox(l.layoutInput.text.length - 1).right
            assertEquals(glyphRight + 2f, bar.left, 0.6f)
            assertEquals(2f, bar.width, 0.01f)
            assertEquals(16f, bar.height, 0.01f)
            val cursor = l.getCursorRect(l.layoutInput.text.length)
            // Flutter's PlaceholderAlignment.middle centres the caret on the text, not on the line's leading.
            assertEquals((cursor.top + cursor.bottom) / 2f, (bar.top + bar.bottom) / 2f, 0.6f, "centred on the text")
            val r = assertNotNull(rtl)
            val rbar = kitCaretRect(r, r.layoutInput.text.length, density)
            val glyphLeft = r.getBoundingBox(r.layoutInput.text.length - 1).left
            assertTrue(rbar.right <= glyphLeft - 1.5f, "before the last Arabic glyph: ${rbar.right} vs $glyphLeft")
        }
}
