package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouReason
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pixel-parity fixes of the native-vs-Flutter scoreboard, group A (orders 01, 05, 06): each test fails on the code
 * before the fix (docs/design/native-ui-rebuild/evidence/score-A1-A3-A4.md).
 */
@OptIn(ExperimentalTestApi::class)
class S17KitAParityTest {
    @Composable
    private fun Themed(rtl: Boolean = false, ratio: Float? = null, content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
            platform = DesktopKitPlatform(),
            localeTag = if (rtl) "ar" else "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalKitPixelRatio provides ratio) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    @Test
    fun `B-KIT-031 the segmented track is the 48 dp target plus its two hairlines, as Flutter's`() =
        runDesktopComposeUiTest(412, 300) {
            var expected = 0f
            setContent {
                Themed {
                    expected = LocalKitTokens.current.minTarget + 2 * kitHairline().value
                    KitSegmented(
                        segments = listOf(KitSegment("today", "Today"), KitSegment("week", "Week"), KitSegment("month", "Month")),
                        selected = "week",
                        semanticsLabel = "Time range",
                        onChange = {},
                    )
                }
            }
            val h = onNodeWithContentDescription("Time range").fetchSemanticsNode().boundsInRoot.height
            // Before: the 48 dp target sat inside the 2 dp indicator inset, so the track was 4 dp taller.
            assertTrue(abs(h - expected) < 0.75f, "track height $h, Flutter ${expected}")
        }

    @Test
    fun `B-KIT-066 a hairline border at a pixel ratio of three is a third of a pixel, not a whole one`() =
        runDesktopComposeUiTest(40, 40) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.fillMaxSize().background(Color.White).padding(10.dp)) {
                        Box(Modifier.size(20.dp).testTag("box").kitHairlineBorder((1f / 3f).dp, Color.Black, RectangleShape))
                    }
                }
            }
            val img = onNodeWithTag("box").captureToImage().toAwtImage()
            val red = (img.getRGB(10, 0) shr 16) and 0xFF
            // Modifier.border rounds the stroke up to one whole pixel (red 0); a third of a pixel blends with white.
            assertTrue(red in 120..235, "top edge red $red")
        }

    @Test
    fun `B-KIT-014 a choice row is at least the two-line row height, as Flutter floors it`() =
        runDesktopComposeUiTest(412, 300) {
            var floor = 0
            setContent {
                Themed {
                    floor = LocalKitTokens.current.rowHeightTwo
                    Column { KitChoiceRow("Only this conversation", selected = true, modifier = Modifier.testTag("row"), onClick = {}) }
                }
            }
            val h = onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
            assertEquals(64, floor)
            assertTrue(h >= floor - 0.5f, "choice row $h dp, Flutter floor $floor")
        }

    @Test
    fun `B-KIT-048 a divider follows the kit pixel ratio, a third as heavy at ratio three`() {
        fun ink(ratio: Float?): Long {
            var sum = 0L
            runDesktopComposeUiTest(60, 20) {
                setContent {
                    Themed(ratio = ratio) {
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp)) { KitDivider() }
                    }
                }
                val img = onRoot().captureToImage().toAwtImage()
                val ground = img.getRGB(30, img.height - 1) and 0xFF
                for (y in 0 until img.height) sum += abs((img.getRGB(30, y) and 0xFF) - ground)
            }
            return sum
        }
        val one = ink(1f)
        val third = ink(3f)
        // Before: the line was 1 / density thick whatever the ratio, so both were the same.
        assertTrue(one > 0 && third > 0, "line drawn: ratio 1 $one, ratio 3 $third")
        assertTrue(third * 10 < one * 6, "ratio 3 ink $third should be about a third of ratio 1 ink $one")
    }

    @Test
    fun `B-KIT-030 no match starts at the reading edge in both directions, not centred`() {
        for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(1280, 800) {
                var gutter = 0
                setContent {
                    Themed(rtl = rtl) {
                        gutter = LocalKitTokens.current.gutter
                        KitSearchNoMatch("opus", {})
                    }
                }
                val title = onNodeWithText("“⁨opus⁩”", substring = true).fetchSemanticsNode().boundsInRoot
                if (rtl) {
                    assertTrue(abs(1280f - gutter - title.right) < 1f, "Arabic title ends at ${title.right}")
                } else {
                    assertTrue(abs(title.left - gutter) < 1f, "title starts at ${title.left}")
                }
            }
        }
    }

    @Test
    fun `B-KIT-027 a needs-you row's title wraps to a second line before it is cut`() =
        runDesktopComposeUiTest(412, 400) {
            val long = "Approve the migration that renames every profile-scoped storage key across the app"
            setContent {
                Themed {
                    Column {
                        KitNeedsYouRow("Allow microphone access", KitNeedsYouReason.Consent, "Voice input stays off.", {})
                        KitNeedsYouRow(long, KitNeedsYouReason.Blocked, "Drafts stay queued until this is answered.", {})
                    }
                }
            }
            val one = onNodeWithText("Allow microphone access", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            val two = onNodeWithText(long, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            assertTrue(two > one * 1.6f, "long title $two dp vs one line $one dp")
        }

    @Test
    fun `B-KIT-066 a control mark is the font glyph KitIcon draws, with no text node of its own`() =
        runDesktopComposeUiTest(80, 40) {
            setContent {
                Themed {
                    Row {
                        Box(Modifier.testTag("glyph")) { KitGlyph(KitIcons.check, LocalKitColors.current.text1, 20.dp) }
                        Box(Modifier.testTag("icon")) { KitIcon(KitIcons.check, size = KitIconSize.Small) }
                    }
                }
            }
            val a = onNodeWithTag("glyph").captureToImage().toAwtImage()
            val b = onNodeWithTag("icon").captureToImage().toAwtImage()
            var differing = 0
            for (y in 0 until minOf(a.height, b.height)) for (x in 0 until minOf(a.width, b.width)) if (a.getRGB(x, y) != b.getRGB(x, y)) differing++
            // Before: the vector path, a pixel higher and heavier than the font glyph.
            assertEquals(0, differing, "pixels that differ from KitIcon's glyph")
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .filter(hasAnyAncestor(hasTestTag("glyph"))).assertCountEquals(0)
        }

    @Test
    fun `B-KIT-014 the choice mark is the small icon size and grows with the text`() {
        fun ringWidth(scale: Float): Int {
            var w = 0
            runDesktopComposeUiTest(412, 120) {
                setContent {
                    KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off), platform = DesktopKitPlatform(), stillFrame = true) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                KitChoiceRow("Staging", selected = false, onClick = {})
                            }
                        }
                    }
                }
                val img = onRoot().captureToImage().toAwtImage()
                val ground = img.getRGB(2, 2)
                // The ring is the first ink from the start edge up to the words (gutter 16, mark, 12 dp gap).
                fun inked(x: Int) = (0 until img.height).any { y -> img.getRGB(x, y) != ground }
                val first = (0 until 80).firstOrNull { inked(it) } ?: return@runDesktopComposeUiTest
                var last = first
                while (last + 1 < img.width && inked(last + 1)) last++
                w = last - first + 1
            }
            return w
        }
        val one = ringWidth(1f)
        val big = ringWidth(2f)
        // Before: a fixed 22 dp ring at every text size.
        assertTrue(one in 19..21, "ring $one dp at 1.0, Flutter 20")
        assertTrue(big in 29..31, "ring $big dp at 2.0, Flutter 30 (1.5x cap)")
    }
}
