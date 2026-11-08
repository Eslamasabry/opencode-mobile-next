@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Glass's in-flow panes in the kit (owner 2026-11-08): the Corners knob takes effect on every Glass surface with motion
 * on (the owner saw the last corners' face under the new clip and ring), a chosen ring lies on its own surface's outline,
 * and a chosen row is accent-tinted glass rather than an opaque band. B-KIT-005, B-KIT-037.
 */
class GlassPanesKitTest {
    private fun glass(corners: Corners, dark: Boolean = true) =
        KitAppearance(
            mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
            style = Style.Glass,
            motion = MotionLevel.Full,
            fineTune = FineTuneChoice(corners = corners),
        )

    /** A chosen Glass style card at [corners], composed fresh and captured once idle. */
    private fun fresh(corners: Corners): PixelMap {
        lateinit var image: PixelMap
        runComposeUiTest {
            setContent {
                KitTheme(glass(corners), FakeKitPlatform(), "en") {
                    Box(Modifier.width(180.dp).testTag("card")) { KitStyleOptionCard(Style.Glass, "Glass", true, {}) }
                }
            }
            waitForIdle()
            image = onNodeWithTag("card").captureToImage().toPixelMap()
        }
        return image
    }

    private fun differ(a: PixelMap, b: PixelMap): Int {
        assertEquals(a.width to a.height, b.width to b.height)
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) n++
        return n
    }

    @Test
    fun `B-KIT-005 a chosen Glass card draws the new corners after the knob changes, motion on`() {
        for ((from, to) in listOf(Corners.Sharp to Corners.Round, Corners.Round to Corners.Sharp, Corners.Round to Corners.Soft)) {
            var corners by mutableStateOf(from)
            lateinit var switched: PixelMap
            runComposeUiTest {
                setContent {
                    KitTheme(glass(corners), FakeKitPlatform(), "en") {
                        Box(Modifier.width(180.dp).testTag("card")) { KitStyleOptionCard(Style.Glass, "Glass", true, {}) }
                    }
                }
                waitForIdle()
                corners = to
                // The fade runs on the test clock; nothing else recomposes the card.
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                switched = onNodeWithTag("card").captureToImage().toPixelMap()
            }
            val expected = fresh(to)
            assertEquals(0, differ(expected, switched), "$from -> $to: the card still shows $from's face under $to's clip and ring")
        }
    }

    @Test
    fun `B-KIT-005 the three Corners values give three different cards`() {
        val round = fresh(Corners.Round)
        val soft = fresh(Corners.Soft)
        val sharp = fresh(Corners.Sharp)
        assertNotEquals(0, differ(round, soft))
        assertNotEquals(0, differ(soft, sharp))
        // The top-start corner pixel: Sharp's face reaches it, Round's leaves it to the page.
        assertNotEquals(round[1, 1], sharp[1, 1])
    }

    @Test
    fun `B-KIT-037 a chosen row under Glass is accent-tinted glass with corners, not Solid's opaque surface3 band`() {
        for (dark in listOf(true, false)) {
            val roles = StyleResolver.adjust(ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null), Style.Glass)
            val face = chosenRowFace(roles, Knobs(), clear = false, spots = emptyList(), radius = 12f)
            assertTrue(face.fill!!.alpha < 255, "dark=$dark: translucent")
            assertEquals(roles.accent.withAlpha(1f).v and 0xFFFFFF, face.edgeStart!!.v and 0xFFFFFF, "dark=$dark: an accent rim")
            assertEquals(12f, face.radii.topStart)
            lateinit var image: PixelMap
            runComposeUiTest {
                setContent {
                    KitTheme(glass(Corners.Round, dark).copy(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                        Column(Modifier.width(360.dp)) {
                            KitRowGroup {
                                KitRow("new", Modifier.testTag("row"), leading = KitRowLeading.Glyph(KitIcons.folderOpen), selected = true, trailing = KitRowTrailing.Check, onClick = {})
                            }
                        }
                    }
                }
                waitForIdle()
                image = onNodeWithTag("row").captureToImage().toPixelMap()
            }
            val surface3 = androidx.compose.ui.graphics.Color(roles.surface3.withAlpha(1f).v)
            // Inside the inset face, away from words: not the opaque band; at the row's very corner: no face.
            val inside = image[image.width / 2, 6]
            assertNotEquals(surface3, inside, "dark=$dark: no opaque surface3 band")
            assertNotEquals(inside, image[0, 0], "dark=$dark: the face is inset, its corner rounded off")
        }
    }

    @Test
    fun `B-KIT-018 every liquid part keeps a glass face beyond the window's budget, never its solid one`() {
        val faces = mutableMapOf<Int, Boolean>()
        runComposeUiTest {
            setContent {
                KitTheme(
                    KitAppearance(style = Style.Glass, motion = MotionLevel.Full),
                    FakeKitPlatform(liquidGlass = true),
                    "en",
                    stillFrame = true,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalKitLiquidBudget provides androidx.compose.runtime.remember { KitLiquidBudget() }) {
                        val source = io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState()
                        Column {
                            repeat(LIQUID_PARTS) { i ->
                                val face = kitLiquidFace(io.github.eslamasabry.oc.ui.design.effects.LiquidRole.Pill, source, androidx.compose.foundation.shape.CircleShape)
                                faces[i] = face != null
                                Box(Modifier.width(40.dp).then(face ?: Modifier))
                            }
                        }
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(LIQUID_PARTS, faces.size)
        assertTrue(faces.values.all { it }, "a part beyond the budget drew its own face: $faces")
    }

    private companion object {
        const val LIQUID_PARTS = 8
    }
}
