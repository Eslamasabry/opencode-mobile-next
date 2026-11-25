package io.github.eslamasabry.oc.ui.kit.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PHASE = "before"

private const val W_PX = 1080
private const val H_PX = 2400

/**
 * B-548 (sweep 2026-11-25, finding 5): a destructive text action never wears the "go" green, and the pictures of the
 * kit's two text actions at 360 dp, in Solid, dark and light, at text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class PolishSheetsColoursTest {
    @Composable
    private fun TextActions() {
        Column(
            Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KitButton("Download this pack again", {}, Modifier.testTag("plain"), role = KitButtonRole.Tertiary)
            KitButton(
                "Delete this pack",
                {},
                Modifier.testTag("destructive"),
                role = KitButtonRole.Tertiary,
                icon = KitIcons.delete,
                destructive = true,
            )
        }
    }

    private fun ComposeUiTest.show(mode: AppearanceMode, fontScale: Float) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KitTheme(
                    appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    InWindow(360, 800) { TextActions() }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.greenCount(node: SemanticsNodeInteraction): Int {
        val map = node.captureToImage().toPixelMap()
        var green = 0
        for (x in 0 until map.width)
            for (y in 0 until map.height) {
                val p = map[x, y].toArgb()
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                if (g - r >= 40 && g - b >= 40) green++
            }
        return green
    }

    private fun shot(out: File, mode: AppearanceMode, fontScale: Float, suffix: String) {
        val look = if (mode == AppearanceMode.Dark) "dark" else "light"
        val file = File(out, "5-$PHASE-kit-text-actions-$look-$suffix.png")
        runDesktopComposeUiTest(W_PX, H_PX) {
            show(mode, fontScale)
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
        assertTrue(file.isFile, "the picture ${file.name} was written")
    }

    @Test
    fun `B-548 a destructive text action never wears the positive green`() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light))
            runDesktopComposeUiTest(W_PX, H_PX) {
                show(mode, 1f)
                val control = greenCount(onNodeWithTag("plain"))
                val destructive = greenCount(onNodeWithTag("destructive"))
                assertTrue(control > 0, "[$mode] the control, a plain text action, wears the accent ink ($control px)")
                assertEquals(0, destructive, "[$mode] a destructive text action wears the green ($destructive px)")
            }
    }

    @Test
    fun pictures() {
        val out = File("build/polish-render").also { it.mkdirs() }
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            shot(out, mode, 1f, "text10")
            shot(out, mode, 1.3f, "text13")
        }
    }
}
