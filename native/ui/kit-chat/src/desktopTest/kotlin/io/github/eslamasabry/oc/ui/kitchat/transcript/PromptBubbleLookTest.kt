package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C-012 and C-010 (parity audit 2026-11-07, Flutter kit_message.dart:303-345, :583-613, :1257-1300): the prompt bubble
 * has a 6 dp tail at its bottom end, 12 × 10 padding, a hairline edge and, in light mode, a grey text1-at-7 % fill;
 * a sent photo shows inside it as a 96 dp square tile 8 dp under the words. Native had uniform 20 dp corners,
 * 16 × 12 padding, no edge, white in light mode, and no photo.
 */
@OptIn(ExperimentalTestApi::class)
class PromptBubbleLookTest {
    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) < 0.5f, "$what: expected $expected, was $actual")

    @Test
    fun `C-012 corners are 20 with a 6 dp tail at the bottom end, mirrored in Arabic`() {
        val density = Density(1f)
        val size = Size(200f, 48f)
        val shape = kitBubbleShape()
        fun radii(direction: LayoutDirection) =
            (shape.createOutline(size, direction, density) as Outline.Rounded).roundRect
        val ltr = radii(LayoutDirection.Ltr)
        assertEquals(20f, ltr.topLeftCornerRadius.x)
        assertEquals(20f, ltr.topRightCornerRadius.x)
        assertEquals(20f, ltr.bottomLeftCornerRadius.x)
        assertEquals(6f, ltr.bottomRightCornerRadius.x, "the tail is at the bottom end")
        val rtl = radii(LayoutDirection.Rtl)
        assertEquals(6f, rtl.bottomLeftCornerRadius.x, "in Arabic the tail is at the bottom left")
        assertEquals(20f, rtl.bottomRightCornerRadius.x)
    }

    @Test
    fun `C-012 the fill is surface2 in dark and text1 at 7 percent over the ground in light`() = runComposeUiTest {
        lateinit var dark: KitColors
        lateinit var light: KitColors
        setContent {
            Column {
                ChatTestTheme(dark = true) { dark = LocalKitColors.current }
                ChatTestTheme(dark = false) { light = LocalKitColors.current }
            }
        }
        runOnIdle {
            assertEquals(dark.surface2, kitPromptBubbleFill(dark))
            assertEquals(light.text1.copy(alpha = 0.07f).compositeOver(light.ground), kitPromptBubbleFill(light))
            assertTrue(kitPromptBubbleFill(light) != light.surface2, "light mode is not the white surface2")
        }
    }

    private fun ComposeUiTest.bubble(text: String, photos: List<KitPromptPhoto> = emptyList(), rtl: Boolean = false) =
        setContent {
            ChatTestTheme(dark = true, localeTag = if (rtl) "ar" else "en") {
                val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column(Modifier.width(400.dp)) { KitPromptBubble(text, photos = photos) }
                }
            }
        }

    @Test
    fun `C-012 padding is 12 dp across and 10 dp down, the bubble's end on the gutter`() = runComposeUiTest {
        bubble("Say hello.")
        val box = onNodeWithTag(PROMPT_BUBBLE_TAG).getBoundsInRoot()
        val words = onNodeWithText("Say hello.").getBoundsInRoot()
        near(12.dp, words.left - box.left, "start padding")
        near(12.dp, box.right - words.right, "end padding")
        near(10.dp, words.top - box.top, "top padding")
        near(10.dp, box.bottom - words.bottom, "bottom padding")
        near(400.dp - Space.S4.dp, box.right, "the bubble's end sits on the page gutter")
    }

    @Test
    fun `C-012 a wrapped prompt hugs its longest line instead of the whole share`() = runComposeUiTest {
        val text = "Please read the notes file and tell me which of the listed steps still need a person to check them."
        bubble(text)
        val box = onNodeWithTag(PROMPT_BUBBLE_TAG).getBoundsInRoot()
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val layout = results.first()
        assertTrue(layout.lineCount > 1, "the prompt wraps")
        val longest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
        val longestDp = with(density) { ceil(longest).toDp() }
        assertTrue(
            abs((box.right - box.left).value - (longestDp + 24.dp).value) < 1f,
            "the bubble is its longest line plus 12 + 12 dp (was ${box.right - box.left}, line $longestDp)",
        )
    }

    @Test
    fun `C-010 a sent photo shows as a 96 dp tile 8 dp under the words, inside the bubble`() = runComposeUiTest {
        bubble("What is this?", listOf(KitPromptPhoto("shot.png", ImageBitmap(32, 32))))
        val box = onNodeWithTag(PROMPT_BUBBLE_TAG).getBoundsInRoot()
        val words = onNodeWithText("What is this?").getBoundsInRoot()
        val tile = onNodeWithTag(PROMPT_PHOTO_TAG).getBoundsInRoot()
        near(96.dp, tile.right - tile.left, "tile width")
        near(96.dp, tile.bottom - tile.top, "tile height")
        near(8.dp, tile.top - words.bottom, "8 dp under the words")
        near(10.dp, box.bottom - tile.bottom, "the bubble's padding under the tile")
        near(12.dp, tile.left - box.left, "the tile starts on the words' edge")
        val name = onNodeWithTag(PROMPT_PHOTO_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Image, shot.png"), name, "a photo that only shows is named as an image")
    }

    @Test
    fun `C-010 photos sit 8 dp apart and an openable one opens on tap`() = runComposeUiTest {
        var opened = 0
        bubble(
            "Compare",
            listOf(KitPromptPhoto("a.png", ImageBitmap(8, 8), onOpen = { opened++ }), KitPromptPhoto("b.png", null)),
        )
        val tiles = onAllNodesWithTag(PROMPT_PHOTO_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(2, tiles.size, "a photo still loading keeps its tile")
        val gap = with(density) { (tiles[1].left - tiles[0].right).toDp() }
        near(8.dp, gap, "8 dp between tiles")
        onAllNodesWithTag(PROMPT_PHOTO_TAG)[0].performClick()
        assertEquals(1, opened)
    }
}
