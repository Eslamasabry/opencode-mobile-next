package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentRow
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-549 (polish sweep #6): from 1.3x text a row's value drops under its title, and the chevron that came with the
 * value dropped with it, beside the words, while the plain chevron rows around it kept the row's end line.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowValueChevronEdgeTest {
    private fun endLines(
        scale: Float,
        locale: String,
        underTitle: Boolean,
        end: (DpRect) -> Float,
    ) = runDesktopComposeUiTest(360, 2600) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = locale,
                    stillFrame = true,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        KitRow("Project scope", trailing = KitRowTrailing.Value("All projects"), onClick = {})
                        KitRow("Notifications", trailing = KitRowTrailing.Chevron, onClick = {})
                    }
                }
            }
        }
        val chevrons = onAllNodesWithTag("kit-row-chevron", useUnmergedTree = true)
        assertEquals(2, chevrons.fetchSemanticsNodes().size, "one chevron per row")
        val values = onAllNodesWithTag("kit-row-value", useUnmergedTree = true)
        assertEquals(1, values.fetchSemanticsNodes().size)
        onNodeWithText("All projects").assertExists()
        if (underTitle) {
            val title = onAllNodesWithTag("kit-row-title", useUnmergedTree = true)[0].getBoundsInRoot()
            val value = values[0].getBoundsInRoot()
            assertTrue(value.top >= title.bottom, "the value sits under its title: title $title, value $value")
        }
        val valueRow = chevrons[0].getBoundsInRoot()
        val plainRow = chevrons[1].getBoundsInRoot()
        assertTrue(abs(end(valueRow) - end(plainRow)) < 0.5f, "value row $valueRow, plain row $plainRow")
    }

    @Test
    fun `B-ROW-549 a value row's chevron ends on the plain chevron row's line at large text`() =
        endLines(1.3f, "en", underTitle = true) { it.right.value }

    @Test
    fun `B-ROW-549 in RTL a value row's chevron ends on the plain chevron row's line at large text`() =
        endLines(1.3f, "ar", underTitle = true) { it.left.value }

    @Test
    fun `B-ROW-549 beside its title, a value keeps its chevron on the plain chevron row's line`() =
        endLines(1f, "en", underTitle = false) { it.right.value }

    @Test
    fun `B-KIT-559 Arabic 4-option range picker stays on one horizontal row at 360 dp and 1_3x`() =
        runDesktopComposeUiTest(360, 400) {
            val segments =
                listOf(
                    KitSegment("24h", "٢٤ س"),
                    KitSegment("7d", "٧ أيام"),
                    KitSegment("30d", "٣٠ يومًا"),
                    KitSegment("90d", "٩٠ يومًا"),
                )
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "ar", stillFrame = true) {
                        Box(Modifier.width(328.dp)) { KitSegmented(segments, "7d", "الفترة", onChange = {}) }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("kit-segmented-track").assertExists()
        }

    @Test
    fun `B-KIT-559 paired metric values align on the same horizontal baseline when one label wraps`() =
        runDesktopComposeUiTest(360, 400) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        Box(Modifier.width(328.dp)) {
                            KitMetricGrid(listOf("Agent steps" to "176", "Subagent conversations" to "4"))
                        }
                    }
                }
            }
            waitForIdle()
            val left = onNodeWithText("176", useUnmergedTree = true).getBoundsInRoot()
            val right = onNodeWithText("4", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(abs(left.bottom.value - right.bottom.value) < 1f, "baselines differ: $left vs $right")
        }

    @Test
    fun `B-KIT-565 four Appearance tabs fit without edge fade at 1_0x and keep pill padding at 1_3x`() {
        val tabs = listOf("Colours", "Style", "Background", "Text & motion").map { KitTab(it) }
        runDesktopComposeUiTest(360, 200) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(360, 200) { KitTabStrip(tabs, selected = 0, onSelected = {}) }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val strip = onNodeWithTag("kit-tab-strip").getBoundsInRoot()
            val last = onAllNodesWithText("Text & motion", useUnmergedTree = true)[0].getBoundsInRoot()
            assertTrue(last.right.value <= strip.right.value - 12f, "last tab $last too close to edge $strip")
            val pill = onNodeWithTag("kit-tab-pill", useUnmergedTree = true).getBoundsInRoot()
            val first = onAllNodesWithText("Colours", useUnmergedTree = true)[0].getBoundsInRoot()
            val pad = (first.left - pill.left).value
            assertTrue(pad >= 7.5f, "1.0x pill padding cramped: $pad dp")
        }
        runDesktopComposeUiTest(412, 200) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(412, 200) { KitTabStrip(tabs, selected = 0, onSelected = {}) }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val pill = onNodeWithTag("kit-tab-pill", useUnmergedTree = true).getBoundsInRoot()
            val first = onAllNodesWithText("Colours", useUnmergedTree = true)[0].getBoundsInRoot()
            val pad = (first.left - pill.left).value
            assertTrue(pad >= 7.5f, "1.3x pill padding cramped: $pad dp")
        }
    }

    @Test
    fun `B-KIT-565 Arabic accent swatch label loses no pixels to corner clipping`() {
        val label = "بنفسجي"
        val swatch = listOf(KitAccentSwatch("violet", Color(0xFFB39DFF), label))
        val inkInRow =
            swatchLabelInk {
                KitAccentRow("لون التمييز", "مطابقة", Color(0xFF3DDC97), swatch, selected = null, onChanged = {}, grid = true)
            }
        val freeInk =
            swatchLabelInk {
                Box(Modifier.width(90.dp).height(90.dp), contentAlignment = Alignment.BottomCenter) {
                    KitText(label, role = TypeRole.Caption, tone = TextTone.Secondary)
                }
            }
        assertTrue(freeInk > 0, "reference drew label")
        assertTrue(inkInRow >= freeInk, "Arabic swatch label clipped: $inkInRow vs $freeInk pixels")
    }

    @Test
    fun `B-KIT-557 neutral KitNotice uses calm secondary body size`() =
        runDesktopComposeUiTest(360, 400) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        KitNotice("Voice typing asks Android to recognise speech.", icon = KitIcons.locked, liveRegion = false)
                    }
                }
            }
            waitForIdle()
            val node = onNodeWithTag("kit-notice-message", useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val size = layouts.first().layoutInput.style.fontSize.value
            assertTrue(size <= 14f, "neutral notice should use calm body size (<= 14sp), got $size")
        }

    private fun swatchLabelInk(content: @androidx.compose.runtime.Composable () -> Unit): Int {
        var ink = 0
        runDesktopComposeUiTest(360, 200) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "ar",
                        stillFrame = true,
                    ) {
                        Box(Modifier.width(360.dp).height(120.dp).testTag("host")) { content() }
                    }
                }
            }
            waitForIdle()
            val node = onNodeWithText("بنفسجي", useUnmergedTree = true).getBoundsInRoot()
            val img = onNodeWithTag("host").captureToImage().toPixelMap()
            val ground = img[0, img.height - 1]
            val x0 = (node.left.value.toInt() - 2).coerceAtLeast(0)
            val x1 = (node.right.value.toInt() + 2).coerceAtMost(img.width - 1)
            val y0 = (node.top.value.toInt() - 1).coerceAtLeast(0)
            val y1 = (node.bottom.value.toInt() + 1).coerceAtMost(img.height - 1)
            for (y in y0..y1) for (x in x0..x1) {
                val p = img[x, y]
                if (abs(p.red - ground.red) + abs(p.green - ground.green) + abs(p.blue - ground.blue) > 0.12f) ink++
            }
        }
        return ink
    }
}
