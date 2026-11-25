package io.github.eslamasabry.oc.feature.agents.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.ANTIGRAVITY_SHEET
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PHASE = "before"

private const val W_PX = 1080
private const val H_PX = 2400

/**
 * B-548 (sweep 2026-11-25, finding 5): in an agent's sheet, Cancel installation and Remove never wear the "go" green,
 * and the pictures of both sheets at 360 dp, dark and light, at text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class PolishSheetsColoursTest {
    private var previousLocale: Locale = Locale.getDefault()

    private val out = File("build/polish-render").also { it.mkdirs() }

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private fun ComposeUiTest.show(style: Style, mode: AppearanceMode, fontScale: Float, state: AgentsUi) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(3f, fontScale),
                LocalKitSecretSink provides KitSecretSink {},
                LocalKitLogoLoader provides MARKS,
            ) {
                KitTheme(
                    appearance = KitAppearance(mode = mode, style = style, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        AgentsScreen(state, AgentsBentoTest.Recorder())
                    }
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

    private fun ComposeUiTest.assertNotGreen(name: String, node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val green = greenCount(node)
        assertEquals(0, green, "$name is not green ($green px)")
    }

    private fun shot(scene: String, style: Style, mode: AppearanceMode, scale: Float, state: AgentsUi) {
        val look = if (mode == AppearanceMode.Dark) "dark" else "light"
        val text = if (scale == 1.3f) "text13" else "text10"
        val file = File(out, "5-$PHASE-$scene-$look-$text.png")
        runDesktopComposeUiTest(W_PX, H_PX) {
            show(style, mode, scale, state)
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
        assertTrue(file.isFile, "the picture ${file.name} was written")
    }

    @Test
    fun `B-548 Cancel installation and Remove in an agent sheet are not green`() {
        val kilo = EVERY_STATE.copy(sheetAgent = "kilo")
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            for (style in listOf(Style.Solid, Style.Glass))
                runDesktopComposeUiTest(W_PX, H_PX) {
                    show(style, mode, 1f, kilo)
                    val cancels = onAllNodes(hasText("Cancel installation") and hasClickAction())
                    assertEquals(1, cancels.fetchSemanticsNodes().size, "[$mode $style] Cancel installation: once")
                    assertNotGreen("[$mode $style] Cancel installation", cancels[0])
                }
            runDesktopComposeUiTest(W_PX, H_PX) {
                show(Style.Solid, mode, 1f, ANTIGRAVITY_SHEET)
                val removes = onAllNodesWithText("Remove Antigravity")
                assertEquals(1, removes.fetchSemanticsNodes().size, "[$mode] Remove Antigravity appears once")
                assertNotGreen("[$mode] Remove Antigravity", removes[0])
            }
        }
    }

    @Test
    fun pictures() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            val kilo = EVERY_STATE.copy(sheetAgent = "kilo")
            shot("kilo-sheet", Style.Glass, mode, 1f, kilo)
            shot("kilo-sheet", Style.Glass, mode, 1.3f, kilo)
            shot("antigravity-sheet", Style.Solid, mode, 1f, ANTIGRAVITY_SHEET)
            shot("antigravity-sheet", Style.Solid, mode, 1.3f, ANTIGRAVITY_SHEET)
        }
    }
}
