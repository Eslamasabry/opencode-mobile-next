package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusGlyph
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.choice.SegmentedRules
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScrollingHeaderState
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.TopBarTitle
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.MenuPlacement
import io.github.eslamasabry.oc.ui.kit.modal.MenuWindow
import io.github.eslamasabry.oc.ui.kit.row.kitRowGutterDp
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.status.statusGlyphIcon
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.ThemeFade
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Batch B3 of the polish sweep on build a2e7d3fa5 (1080 × 2400 at 420 dpi): the kit-level causes. Each test fails on
 * the code before its fix.
 */
@OptIn(ExperimentalTestApi::class)
class S7PolishKitTest {
    @Composable
    private fun Themed(fontScale: Float = 1f, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textLayout(tag: String, inner: Boolean = false): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val tagged = onNodeWithTag(tag, useUnmergedTree = true)
        val node = (if (inner) tagged.onChild() else tagged).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    // P-006 ------------------------------------------------------------------------------------------------------

    @Test
    fun `P-006 the screen header scrolls away with the body and comes back at its top`() = runComposeUiTest {
        setContent {
            Themed {
                val heading = Modifier.testTag("heading")
                KitScreen(header = { KitText("Conversations", heading, role = TypeRole.LargeTitle) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(60) { KitText("Row $it", Modifier.height(56.dp)) }
                    }
                }
            }
        }
        val rest = onNodeWithTag("heading", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        onNodeWithTag("list").performTouchInput { swipeUp() }
        val moved = onNodeWithTag("heading", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        assertTrue(moved < rest - 10f, "the heading moved up with the list (was $rest, now $moved)")
        repeat(4) { onNodeWithTag("list").performTouchInput { swipeDown() } }
        val back = onNodeWithTag("heading", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        assertEquals(rest, back, 0.5f, "at the list's top the heading is back in place")
    }

    @Test
    fun `P-006 the header leaves before the body moves and returns only from the body's top, the cut fading in`() {
        val s = KitScrollingHeaderState().apply { height = 120 }
        assertEquals(0f, s.fade(12f), "nothing is cut at rest")
        assertEquals(-50f, s.takeAway(-50f))
        assertEquals(70, s.shown(120))
        assertEquals(1f, s.fade(12f), "a cut 50 px deep fades in full")
        assertEquals(-70f, s.takeAway(-200f), "only what is left of the header is taken; the body scrolls the rest")
        assertEquals(0, s.shown(120))
        assertEquals(0f, s.takeAway(30f), "a downward scroll first goes to the body")
        assertEquals(30f, s.bringBack(30f), "what the body leaves over at its top brings the header back")
        assertEquals(30, s.shown(120))
        assertEquals(0.5f, KitScrollingHeaderState().apply { height = 120 }.let { it.takeAway(-6f); it.fade(12f) })
    }

    // P-008 ------------------------------------------------------------------------------------------------------

    private val models =
        (1..9).map { KitModelOption("anthropic", "m$it", "Model $it", providerName = "Anthropic") }

    @Composable
    private fun ModelSheet() {
        KitSheetHost {
            KitModelSheet(
                visible = true,
                onDismissRequest = {},
                options = models,
                current = models[2].key,
                onChoose = {},
                onToggleFavorite = {},
            )
        }
    }

    @Test
    fun `P-008 the model sheet's rows start on the sheet's title edge and its stars line up with Close`() =
        runComposeUiTest {
            setContent { Themed { ModelSheet() } }
            val title = onNodeWithText("Choose a model", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val row = onNodeWithText("Model 5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(title.left, row.left, 1f, "one start edge: title at ${title.left}, row words at ${row.left}")
            val close = onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
            val star = onAllNodesWithContentDescriptionFirst("Add favorite")
            assertEquals(close.center.x, star.center.x, 1f, "the star's centre is the Close glyph's")
        }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithContentDescriptionFirst(label: String) =
        onAllNodesWithContentDescription(label).fetchSemanticsNodes().first().boundsInRoot

    @Test
    fun `P-008 a one-line model row is 64 dp (the two-line floor) with its star, and the list starts a step under the search`() =
        runComposeUiTest {
            setContent { Themed { ModelSheet() } }
            val oneLine =
                onNode(hasTestTag("chat-model-choice") and hasText("Model 5")).fetchSemanticsNode().boundsInRoot
            // 0f235c6f84: choice rows floor at the two-line row height (64 dp, as Dart kit_choice_list), one line or two.
            assertEquals(64f, oneLine.height, 0.5f, "a one-line row keeps the 64 dp two-line floor")
            val search = onNodeWithTag("chat-model-search").fetchSemanticsNode().boundsInRoot
            val first = onNodeWithTag("chat-model-default").fetchSemanticsNode().boundsInRoot
            assertEquals(8f, first.top - search.bottom, 0.5f, "a step (8 dp) between the field and the first row")
        }

    @Test
    fun `P-008 rows on a sheet's rail keep no gutter of their own, rows on a page keep 16 dp`() {
        assertEquals(0, kitRowGutterDp(onRail = true, gutter = 16))
        assertEquals(16, kitRowGutterDp(onRail = false, gutter = 16))
    }

    // P-014 ------------------------------------------------------------------------------------------------------

    @Test
    fun `P-014 a neutral notice or status line shows the info mark, not an empty ring`() {
        assertSame(KitIcons.info, statusGlyphIcon(StatusGlyph.EmptyRing))
        assertSame(KitIcons.warning, statusGlyphIcon(StatusGlyph.Warning))
    }

    @Test
    fun `P-014 A6 a status line's words are the secondary size, an untitled notice's message the body size as Flutter`() = runComposeUiTest {
        var ink = Color.Unspecified
        setContent {
            Themed {
                ink = LocalKitColors.current.text1
                androidx.compose.foundation.layout.Column {
                    KitNotice("Your phone keeps the conversations")
                    KitStatusLine(
                        KitStatus(KitStatusKind.Info, "Updates are paused", id = "info", tone = StatusTone.Neutral)
                    )
                }
            }
        }
        // A6 (scoreboard 2026-11-07, kit_notice_ok/warning/working): Flutter says an untitled notice at the body
        // size (lib/ui/kit/kit_notice.dart:470-472); the status line keeps P-014's secondary size.
        // 23b67875e8: the status line's message is Body (16/24) too, and its tag sits on a Column that clears its
        // semantics, so the text layout is read from the text inside it.
        for ((tag, size, line) in listOf(Triple("kit-notice-message", 16f, 24f), Triple("kit-status-message", 16f, 24f))) {
            val style = textLayout(tag, inner = tag == "kit-status-message").layoutInput.style
            assertEquals(size, style.fontSize.value, 0.01f, "$tag size")
            assertEquals(line, style.lineHeight.value, 0.01f, "$tag line")
            assertEquals(ink, style.color, "$tag keeps primary ink: it is the message, not a note")
        }
    }

    // P-016 ------------------------------------------------------------------------------------------------------

    @Test
    fun `A8 a pushed page and a switcher both name themselves at the headline size, as kit_top_bar_dart 591 does`() {
        // Owner rule 2026-11-07: layout and look follow Flutter; P-016's large bar title is withdrawn.
        assertEquals(TypeRole.Headline, TopBarTitle.role(switcher = false))
        assertEquals(TypeRole.Headline, TopBarTitle.role(switcher = true))
    }

    @Test
    fun `A8 a sub-page bar's title is the 16 sp headline at 1_0 and grows with text at 1_3`() = runComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent { Themed(fontScale = scale) { KitTopBar("Settings") } }
        assertEquals(16f, textLayout("kit-top-bar-title").layoutInput.style.fontSize.value, 0.01f) // F: lib/ui/kit/kit_text.dart:298
        scale = 1.3f
        waitForIdle()
        assertEquals(16f * 1.3f, textLayout("kit-top-bar-title").layoutInput.style.fontSize.value, 0.01f)
    }

    // P-017 ------------------------------------------------------------------------------------------------------

    private val window = MenuWindow(width = 1080, height = 2400, margin = 42, top = 84, bottom = 84)

    @Test
    fun `P-017 a menu from a bottom chip opens above it and never covers it`() {
        val chip = IntRect(478, 2150, 864, 2270)
        val panel = IntSize(802, 500)
        val at = MenuPlacement.offset(chip, panel, rtl = false, window = window)
        assertTrue(at.y + panel.height <= chip.top, "the panel ends above the chip (ends at ${at.y + panel.height})")
        assertEquals(864 - 802, at.x, "too wide to start on the chip, it ends on the chip's end edge")
    }

    @Test
    fun `P-017 a menu starts on the start edge of the control that opened it`() {
        val more = IntRect(150, 1200, 276, 1326)
        val panel = IntSize(600, 400)
        val ltr = MenuPlacement.offset(more, panel, rtl = false, window = window)
        assertEquals(150, ltr.x, "it grows away from the ⋯ it came from, not from the far side")
        assertEquals(1326, ltr.y, "below the control")
        val rtl = MenuPlacement.offset(IntRect(804, 1200, 930, 1326), panel, rtl = true, window = window)
        assertEquals(930 - 600, rtl.x, "in Arabic it starts on the control's right edge")
    }

    @Test
    fun `P-017 a menu too tall for either side takes the roomier side, scrolls and never covers its control`() {
        val more = IntRect(900, 1000, 1026, 1126)
        val room = MenuPlacement.room(more.top, more.bottom, window)
        assertEquals(2400 - 84 - 1126, room, "below is the roomier side")
        val held = IntSize(400, minOf(1800, room))
        val at = MenuPlacement.offset(more, held, rtl = false, window = window)
        val clear = at.y >= more.bottom || at.y + held.height <= more.top
        assertTrue(clear, "the panel (y ${at.y}) does not cover the ⋯")
        assertFalse(MenuPlacement.opensAbove(more.top, more.bottom, held.height, window))
        assertTrue(MenuPlacement.opensAbove(1800, 1926, 900, window), "no room below: above")
    }

    // P-027 ------------------------------------------------------------------------------------------------------

    @Test
    fun `P-027 the tall track parts resting segments only, never beside the chosen one`() {
        // Auto · Light · Dark with Dark chosen: one seam, between Auto and Light (Flutter's design).
        assertTrue(SegmentedRules.seamAfter(0, 3, 2, tall = true))
        assertFalse(SegmentedRules.seamAfter(1, 3, 2, tall = true))
        assertFalse(SegmentedRules.seamAfter(2, 3, 2, tall = true), "never after the last")
        // Light chosen: no seam at all (both seams touch it).
        assertFalse(SegmentedRules.seamAfter(0, 3, 1, tall = true))
        assertFalse(SegmentedRules.seamAfter(1, 3, 1, tall = true))
        assertFalse(SegmentedRules.seamAfter(0, 3, 2, tall = false), "the short track has none")
    }

    // P-029 ------------------------------------------------------------------------------------------------------

    @Test
    fun `P-029 the theme's colours move together, dark-or-light flipping halfway`() {
        fun colors(dark: Boolean) =
            ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null).toKitColors()
        val from = colors(dark = true)
        val to = colors(dark = false)
        assertSame(from, ThemeFade.lerp(from, to, 0f))
        assertSame(to, ThemeFade.lerp(from, to, 1f))
        val mid = ThemeFade.lerp(from, to, 0.5f)
        assertNotEquals(from.ground, mid.ground)
        assertNotEquals(to.ground, mid.ground)
        assertEquals(to.dark, mid.dark)
        assertEquals(from.dark, ThemeFade.lerp(from, to, 0.49f).dark)
    }
    // P-116 and the timed P-029 fade moved to S10MotionKitTest with M-019 (the old screen now fades over the new).
}
