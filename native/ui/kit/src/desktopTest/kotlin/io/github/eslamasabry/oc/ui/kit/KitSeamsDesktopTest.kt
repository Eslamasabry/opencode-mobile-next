package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * P7, way 1 of 3: the kit seams headless on desktop (`runComposeUiTest`, Skia). Behaviour only; visual sign-off stays
 * on Android renders (plan 06 §15.4).
 */
@OptIn(ExperimentalTestApi::class)
class KitSeamsDesktopTest {
    private val words = SceneWords.En
    private val still = KitAppearance(motion = MotionLevel.Off)

    @Composable
    private fun Themed(
        platform: FakeKitPlatform,
        fontScale: Float = 1f,
        appearance: KitAppearance = still,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true, content = content)
        }
    }

    @Test
    fun `B-KIT-051 a tap plays only the meaning's haptic and a long-press plays LongPress`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var sent = 0
        var longs = 0
        setContent { Themed(platform) { ButtonsScene(words, onSend = { sent++ }, onLong = { longs++ }) } }

        onNodeWithText(words.send).performClick()
        assertEquals(1, sent)
        assertEquals(listOf(KitHaptic.Send), platform.haptics)

        onNodeWithText(words.row).performClick() // a plain tap is silent (MOT-11)
        assertEquals(listOf(KitHaptic.Send), platform.haptics)

        onNodeWithText(words.row).performTouchInput { longClick() }
        assertEquals(1, longs)
        assertEquals(listOf(KitHaptic.Send, KitHaptic.LongPress), platform.haptics)
    }

    @Test
    fun `B-KIT-051 a disabled tappable states why and does not act`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        setContent { Themed(platform) { ButtonsScene(words) } }
        onNodeWithText(words.disabled)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, words.reason))
            .performClick()
        assertTrue(platform.haptics.isEmpty())
    }

    @Test
    fun `B-KIT-059 frame text caps at 1_3x while reading text follows the scale`() = runComposeUiTest {
        // Desktop density is linear: at 2.0 a Button line (20 sp) is 40 dp as reading text, 26 dp (1.3x) as frame.
        setContent { Themed(FakeKitPlatform(), fontScale = 2f) { TextScaleScene(words) } }
        onNodeWithTag("frame").assertHeightIsEqualTo(26.dp)
        // Reading Body: 24 sp line at 2.0 = 48 dp per line.
        val reading = onNodeWithTag("reading").fetchSemanticsNode().size.height
        assertEquals(0, reading % 48, "reading lines are 48 dp, got $reading")
    }

    @Test
    fun `B-KIT-059 the root cap holds reading text at 2_5x`() = runComposeUiTest {
        setContent { Themed(FakeKitPlatform(), fontScale = 3f) { TextScaleScene(words) } }
        onNodeWithTag("frame").assertHeightIsEqualTo(26.dp)
        val reading = onNodeWithTag("reading").fetchSemanticsNode().size.height
        assertEquals(0, reading % 60, "24 sp × 2.5 = 60 dp lines, got $reading")
    }

    @Test
    fun `B-KIT-032 a sheet opens in-window and closes from Close and from the scrim`() = runComposeUiTest {
        var open by mutableStateOf(true)
        var dismissed = 0
        setContent {
            Themed(FakeKitPlatform()) {
                SheetScene(words, visible = open, onDismiss = { dismissed++; open = false })
            }
        }
        onNodeWithText(words.sheetTitle).assertExists()
        onNodeWithText(words.sheetBody).assertExists()
        onNodeWithText(words.close).performClick()
        assertEquals(1, dismissed)
        onNodeWithText(words.sheetTitle).assertDoesNotExist()

        open = true
        waitForIdle()
        onNodeWithText(words.sheetTitle).assertExists()
        onNodeWithText(words.screenTitle).performClick() // the scrim covers the screen: a tap there dismisses
        assertEquals(2, dismissed)
        onNodeWithText(words.sheetBody).assertDoesNotExist()
    }

    @Test
    fun `B-KIT-032 a second sheet replaces the first`() = runComposeUiTest {
        setContent {
            Themed(FakeKitPlatform()) {
                var second by remember { mutableStateOf(false) }
                KitScreen(title = words.screenTitle) {
                    io.github.eslamasabry.oc.ui.kit.modal.KitSheet(
                        visible = true, onDismissRequest = {}, title = "First", closeLabel = words.close,
                    ) { KitButton("Next", onClick = { second = true }) }
                    io.github.eslamasabry.oc.ui.kit.modal.KitSheet(
                        visible = second, onDismissRequest = {}, title = "Second", closeLabel = words.close,
                    ) { KitText("Second body") }
                }
            }
        }
        onNodeWithText("First").assertExists()
        onNodeWithText("Next").performClick()
        onNodeWithText("Second").assertExists()
        onNodeWithText("First").assertDoesNotExist()
    }

    @Test
    fun `B-KIT-018 frost draws solid without a platform effect and blurred through one`() = runComposeUiTest {
        var platform by mutableStateOf(FakeKitPlatform(frost = false))
        var ground = 0
        setContent {
            Themed(platform, appearance = KitAppearance()) {
                ground = argbOf(LocalKitColors.current.ground)
                FrostScene(words)
            }
        }
        val solid = onNodeWithTag("header").captureToImage().toPixelMap()
        // Left edge, above the bottom line: exactly the ground (the header's solid fallback is the page ground).
        assertEquals(ground, argbOf(solid[2, solid.height / 2]))

        platform = FakeKitPlatform(frost = true)
        waitForIdle()
        val frosted = onNodeWithTag("header").captureToImage().toPixelMap()
        assertNotEquals(ground, argbOf(frosted[2, frosted.height / 2]), "the blurred stripes show through the tint")
    }

    private fun argbOf(c: androidx.compose.ui.graphics.Color): Int =
        (c.alpha * 255 + 0.5f).toInt() shl 24 or ((c.red * 255 + 0.5f).toInt() shl 16) or
            ((c.green * 255 + 0.5f).toInt() shl 8) or (c.blue * 255 + 0.5f).toInt()
}
