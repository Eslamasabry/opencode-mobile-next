package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.ChoiceCardRules
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCardItem
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_choice_cards.dart: the line, the tap rule while busy, the disabled reason and the read-aloud name. */
@OptIn(ExperimentalTestApi::class)
class KitChoiceCardTest {
    @Composable
    private fun themed(content: @Composable () -> Unit) =
        KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), stillFrame = true) { content() }

    private val phone = KitChoiceCardItem("phone", "On this phone", supporting = "About 4 minutes")
    private val computer = KitChoiceCardItem("computer", "On my computer", supporting = "Pair with a code")

    @Test
    fun `B-KIT-014 choice card line puts a disabled reason first and joins with a middle dot`() {
        assertEquals("About 4 minutes", ChoiceCardRules.line("About 4 minutes", KitEnabled.Enabled))
        assertEquals("Not on this device · About 4 minutes", ChoiceCardRules.line("About 4 minutes", KitEnabled.Disabled("Not on this device")))
        assertEquals("Not on this device", ChoiceCardRules.line("", KitEnabled.Disabled("Not on this device")))
        assertEquals("", ChoiceCardRules.line(null, KitEnabled.Enabled))
        assertEquals("On this phone. About 4 minutes", ChoiceCardRules.label("On this phone", "About 4 minutes"))
        assertEquals("On this phone", ChoiceCardRules.label("On this phone", ""))
    }

    @Test
    fun `B-KIT-014 choice card takes a tap only when enabled and nothing is busy`() {
        assertTrue(ChoiceCardRules.tappable(KitEnabled.Enabled, waiting = false))
        assertFalse(ChoiceCardRules.tappable(KitEnabled.Enabled, waiting = true))
        assertFalse(ChoiceCardRules.tappable(KitEnabled.Disabled("No"), waiting = false))
        assertTrue(ChoiceCardRules.showsBusy("phone", "phone"))
        assertFalse(ChoiceCardRules.showsBusy("computer", "phone"))
        assertFalse(ChoiceCardRules.showsBusy("phone", null))
    }

    @Test
    fun `B-KIT-014 a tap on a choice card chooses it once`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { themed { KitChoiceCards(listOf(phone, computer), "phone", { calls += it }) } }
        onNodeWithContentDescription("On my computer. Pair with a code").performClick()
        assertEquals(listOf("computer"), calls)
        onNodeWithContentDescription("On this phone. About 4 minutes").assertIsSelected()
    }

    @Test
    fun `B-KIT-014 while one choice is busy every card waits and the busy card shows progress`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { themed { KitChoiceCards(listOf(phone, computer), "phone", { calls += it }, busy = "phone") } }
        onNodeWithContentDescription("On my computer. Pair with a code").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), calls)
        onNodeWithTag("kit-choice-card-busy", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-KIT-014 a disabled choice card states its reason and takes no tap`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val off = phone.copy(enabled = KitEnabled.Disabled("Not on this device"))
        setContent { themed { KitChoiceCards(listOf(off, computer), "computer", { calls += it }) } }
        onNodeWithContentDescription("On this phone. Not on this device · About 4 minutes")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not on this device"))
            .performClick()
        assertEquals(emptyList(), calls)
    }

    private fun near(a: Color, b: Color, tol: Float = 0.03f): Boolean =
        abs(a.red - b.red) < tol && abs(a.green - b.green) < tol && abs(a.blue - b.blue) < tol

    private fun cornerPixels(
        px: PixelMap,
        bounds: Rect,
        density: Float,
        match: (Color) -> Boolean,
    ): Int {
        val left = (bounds.right - 56f * density).roundToInt().coerceIn(0, px.width - 1)
        val right = (bounds.right - 16f * density).roundToInt().coerceIn(0, px.width - 1)
        val top = (bounds.top + 16f * density).roundToInt().coerceIn(0, px.height - 1)
        val bottom = (bounds.top + 56f * density).roundToInt().coerceIn(0, px.height - 1)
        var count = 0
        for (x in left..right) {
            for (y in top..bottom) {
                if (match(px[x, y])) count++
            }
        }
        return count
    }

    @Test
    fun `B-545 chosen card shows the accent check and unchosen card draws no empty ring`() {
        listOf(AppearanceMode.Light, AppearanceMode.Dark).forEach { mode ->
            runDesktopComposeUiTest(412, 500) {
                var accent = Color.Unspecified
                setContent {
                    KitTheme(
                        KitAppearance(mode = mode, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        stillFrame = true,
                    ) {
                        val colors = LocalKitColors.current
                        accent = colors.accent
                        Box(Modifier.fillMaxSize().background(colors.ground)) {
                            KitChoiceCards(listOf(phone, computer), "phone", {})
                        }
                    }
                }
                waitForIdle()
                val px = onRoot().captureToImage().toPixelMap()
                val cards = onAllNodesWithTag("kit-choice-card").fetchSemanticsNodes()
                assertEquals(2, cards.size)
                val chosen = cards[0].boundsInRoot
                val unchosen = cards[1].boundsInRoot
                val d = density.density
                val midX = ((unchosen.left + unchosen.right) / 2f).roundToInt()
                val unchosenFill = px[midX, (unchosen.top + 28f * d).roundToInt()]
                val unchosenMark = cornerPixels(px, unchosen, d) { !near(it, unchosenFill) }
                assertEquals(0, unchosenMark, "unchosen card must draw no empty ring or mark (mode=$mode)")
                val chosenCheck = cornerPixels(px, chosen, d) { near(it, accent, tol = 0.12f) }
                assertTrue(chosenCheck > 0, "chosen card must draw accent check in top-end corner (mode=$mode)")
                onAllNodesWithTag("kit-choice-card-check", useUnmergedTree = true).assertCountEquals(1)
            }
        }
    }

    @Test
    fun `B-545 choice card check matches KitRow chosen check size and follows selection`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    val colors = LocalKitColors.current
                    Column(Modifier.fillMaxSize().background(colors.ground)) {
                        KitRow("Selected row", selected = true, onClick = {})
                        KitChoiceCards(listOf(phone, computer), "computer", {})
                    }
                }
            }
            waitForIdle()
            val rowCheck = onNodeWithTag("kit-row-check", useUnmergedTree = true).fetchSemanticsNode()
            val cardCheck = onNodeWithTag("kit-choice-card-check", useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(rowCheck.size, cardCheck.size, "choice card check must match KitRow check size")
            val cards = onAllNodesWithTag("kit-choice-card").fetchSemanticsNodes()
            val first = cards[0].boundsInRoot
            val px = onRoot().captureToImage().toPixelMap()
            val d = density.density
            val midX = ((first.left + first.right) / 2f).roundToInt()
            val fill = px[midX, (first.top + 28f * d).roundToInt()]
            val mark = cornerPixels(px, first, d) { !near(it, fill) }
            assertEquals(0, mark, "unchosen first card must draw no empty ring or mark")
        }
}
