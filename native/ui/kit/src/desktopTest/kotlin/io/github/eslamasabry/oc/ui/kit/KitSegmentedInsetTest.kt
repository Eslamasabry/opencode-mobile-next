package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.SegmentedGeometry
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-09 (Settings > Appearance, Glass): the chosen thumb of every segmented control touched the track's
 * edge and its corner did not follow the track's; and the first letter of a theme card's name was cut by the card's
 * corner clip. Both are fixed in the kit, so every screen gains them.
 */
@OptIn(ExperimentalTestApi::class)
class KitSegmentedInsetTest {
    private fun look(style: Style, corners: Corners) =
        KitAppearance(
            mode = AppearanceMode.Dark,
            style = style,
            motion = MotionLevel.Off,
            fineTune = FineTuneChoice(corners = corners),
        )

    private val three = listOf(KitSegment("a", "Round"), KitSegment("b", "Soft"), KitSegment("c", "Sharp"))

    @Test
    fun `thumb is inset by the token on all four sides in every style and corner choice`() {
        for (style in Style.entries) for (corners in Corners.entries) {
            runComposeUiTest {
                setContent {
                    KitTheme(look(style, corners), FakeKitPlatform(), "en", stillFrame = true) {
                        Box(Modifier.width(360.dp)) { KitSegmented(three, "a", "Corners", onChange = {}) }
                    }
                }
                waitForIdle()
                val track = onNodeWithTag("kit-segmented-track").getBoundsInRoot()
                val thumb = onNodeWithTag("kit-segmented-thumb").getBoundsInRoot()
                val inset = SegmentedGeometry.INSET
                val what = "$style/$corners"
                assertNear(inset, (thumb.left - track.left).value, "$what left")
                assertNear(inset, (thumb.top - track.top).value, "$what top")
                assertNear(inset, (track.bottom - thumb.bottom).value, "$what bottom")
                // The first segment is chosen: it is flush to the start, so only the start/top/bottom are the track's.
            }
        }
    }

    @Test
    fun `the last segment is inset from the track's end edge too`() = runComposeUiTest {
        setContent {
            KitTheme(look(Style.Glass, Corners.Round), FakeKitPlatform(), "en", stillFrame = true) {
                Box(Modifier.width(360.dp)) { KitSegmented(three, "c", "Corners", onChange = {}) }
            }
        }
        waitForIdle()
        val track = onNodeWithTag("kit-segmented-track").getBoundsInRoot()
        val thumb = onNodeWithTag("kit-segmented-thumb").getBoundsInRoot()
        assertNear(SegmentedGeometry.INSET, (track.right - thumb.right).value, "end")
    }

    @Test
    fun `thumb corner is the track corner minus the inset for Round Soft and Sharp`() {
        for (sharp in listOf(2f, 4f)) {
            val round = SegmentedGeometry.trackRadiusDp(Corners.Round, sharp)
            assertEquals(round - SegmentedGeometry.INSET, SegmentedGeometry.thumbRadiusDp(Corners.Round, sharp))
            // Round at the usual 48 dp height is a stadium: track radius is half the height, the thumb's is half of its own.
            assertEquals(24f, round)
            assertEquals((48f - 2 * SegmentedGeometry.INSET) / 2f, SegmentedGeometry.thumbRadiusDp(Corners.Round, sharp))
            val soft = SegmentedGeometry.trackRadiusDp(Corners.Soft, sharp)
            assertTrue(soft in 1f..round, "Soft is softer than Round")
            assertEquals(soft - SegmentedGeometry.INSET, SegmentedGeometry.thumbRadiusDp(Corners.Soft, sharp))
            // Sharp is a near-square track; the thumb never goes below square.
            assertEquals(maxOf(0f, SegmentedGeometry.trackRadiusDp(Corners.Sharp, sharp) - SegmentedGeometry.INSET), SegmentedGeometry.thumbRadiusDp(Corners.Sharp, sharp))
        }
    }

    @Test
    fun `Round draws a curved pill thumb inside the pill track (corner pixel is the track's, edge middle is the thumb's)`() {
        lateinit var image: PixelMap
        lateinit var thumbBox: androidx.compose.ui.geometry.Rect
        runComposeUiTest {
            setContent {
                KitTheme(look(Style.Solid, Corners.Round), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.width(360.dp).testTag("host")) { KitSegmented(three, "a", "Corners", onChange = {}) }
                }
            }
            waitForIdle()
            image = onNodeWithTag("host").captureToImage().toPixelMap()
            val b = onNodeWithTag("kit-segmented-thumb").getBoundsInRoot()
            thumbBox = androidx.compose.ui.geometry.Rect(b.left.value, b.top.value, b.right.value, b.bottom.value)
        }
        val midY = ((thumbBox.top + thumbBox.bottom) / 2).toInt()
        val edgeMid = image[(thumbBox.left + 3).toInt(), midY]
        val corner = image[(thumbBox.left + 1).toInt(), (thumbBox.top + 1).toInt()]
        val centre = image[((thumbBox.left + thumbBox.right) / 2).toInt(), (thumbBox.top + 3).toInt()]
        assertTrue(differs(edgeMid, corner), "the thumb's corner is rounded away (pixel $corner vs thumb $edgeMid)")
        assertTrue(!differs(edgeMid, centre), "the thumb's top edge is thumb-coloured")
    }

    @Test
    fun `the mode control's block is inset by the token and follows the same corners`() {
        for (corners in Corners.entries) {
            runComposeUiTest {
                setContent {
                    KitTheme(look(Style.Glass, corners), FakeKitPlatform(), "en", stillFrame = true) {
                        Box(Modifier.width(360.dp)) {
                            KitModeSegmented(
                                listOf(
                                    KitModeSegment("a", "Auto", KitIcons.systemTheme),
                                    KitModeSegment("b", "Light", KitIcons.lightMode),
                                    KitModeSegment("c", "Dark", KitIcons.darkMode),
                                    KitModeSegment("d", "Saver", KitIcons.leaf),
                                ),
                                "a", "Light or dark", onChange = {},
                            )
                        }
                    }
                }
                waitForIdle()
                val track = onNodeWithTag("kit-mode-track").getBoundsInRoot()
                val block = onNodeWithTag("kit-mode-block").getBoundsInRoot()
                assertNear(SegmentedGeometry.INSET, (block.left - track.left).value, "$corners left")
                assertNear(SegmentedGeometry.INSET, (block.top - track.top).value, "$corners top")
                assertNear(SegmentedGeometry.INSET, (track.bottom - block.bottom).value, "$corners bottom")
            }
        }
    }

    @Test
    fun `a theme name starting with a round letter loses no pixels to the card's corner clip, in LTR and RTL`() {
        for (dir in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) for (label in listOf("Catppuccin", "Graphite", "Dracula", "Gruvbox")) {
            val card = labelInk(dir, label) { KitThemeOptionCard(PackId.Opencode, label, selected = false, onSelect = {}) }
            val reference = labelInk(dir, label) {
                // The same name, in the same place, with nothing around it to clip it.
                Column(Modifier.fillMaxWidth().height(120.dp)) {
                    Box(Modifier.weight(1f))
                    Box(Modifier.height(Space.S2.dp))
                    KitText(label, Modifier.padding(start = Space.S1.dp), role = TypeRole.RowTitle, tone = TextTone.Primary, maxLines = 1)
                }
            }
            assertTrue(reference > 0, "$label: reference drew its name")
            assertEquals(reference, card, "$dir $label: the card's name must draw every pixel its free-standing twin draws")
        }
    }

    /** Pixels in the name strip (the bottom 28 dp) that differ from the page ground. */
    private fun labelInk(dir: LayoutDirection, label: String, content: @androidx.compose.runtime.Composable () -> Unit): Int {
        var ink = 0
        runComposeUiTest {
            setContent {
                KitTheme(look(Style.Solid, Corners.Round), FakeKitPlatform(), "en", stillFrame = true) {
                    CompositionLocalProvider(LocalLayoutDirection provides dir) {
                        Box(Modifier.width(160.dp).height(120.dp).testTag("host"), contentAlignment = Alignment.TopStart) { content() }
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("host").captureToImage().toPixelMap()
            val ground = img[img.width - 1, img.height - 1]
            for (y in img.height - 28 until img.height) for (x in 0 until img.width) if (differs(img[x, y], ground)) ink++
        }
        return ink
    }

    private fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > .12f

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= .75f, "$what: expected $expected dp, got $actual dp")
}
