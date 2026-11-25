package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [KitSwitchRow.supportingMinLines] keeps a row's height steady across line counts without changing default callers.
 */
@OptIn(ExperimentalTestApi::class)
class KitSwitchRowMinLinesTest {
    @Composable
    private fun Harness(fontScale: Float = 1.3f, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Column(Modifier.width(320.dp).background(LocalKitColors.current.ground)) {
                    content()
                }
            }
        }
    }

    @Test
    fun `B-KIT-027 supportingMinLines keeps short and long supporting rows the same height`() = runComposeUiTest {
        val shortText = "Stays on 0.171.0"
        val longText = "Version 0.171.0 → 0.174.0 · 48 MB"
        setContent {
            Harness {
                KitSwitchRow(
                    title = "Codex",
                    checked = false,
                    modifier = Modifier.testTag("short-row"),
                    supporting = shortText,
                    supportingMaxLines = 3,
                    supportingMinLines = 2,
                    onCheckedChange = {},
                )
                KitSwitchRow(
                    title = "Codex",
                    checked = true,
                    modifier = Modifier.testTag("long-row"),
                    supporting = longText,
                    supportingMaxLines = 3,
                    supportingMinLines = 2,
                    onCheckedChange = {},
                )
            }
        }
        val shortLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(shortText, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(shortLayouts) }
        val longLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(longText, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(longLayouts) }
        assertEquals(1, shortLayouts.single().lineCount, "short supporting text should be 1 line")
        assertEquals(2, longLayouts.single().lineCount, "long supporting text should wrap to 2 lines")

        val shortHeight = onNodeWithTag("short-row").fetchSemanticsNode().size.height
        val longHeight = onNodeWithTag("long-row").fetchSemanticsNode().size.height
        assertEquals(longHeight, shortHeight, "short row height must match long row height when supportingMinLines = 2")
    }

    @Test
    fun `B-KIT-027 default supportingMinLines keeps an existing caller unchanged`() = runComposeUiTest {
        val shortText = "Stays on 0.171.0"
        val longText = "Version 0.171.0 → 0.174.0 · 48 MB"
        setContent {
            Harness {
                Box(Modifier.testTag("default-row")) {
                    KitSwitchRow(
                        title = "Codex",
                        checked = false,
                        supporting = shortText,
                        supportingMaxLines = 3,
                        onCheckedChange = {},
                    )
                }
                Box(Modifier.testTag("one-row")) {
                    KitSwitchRow(
                        title = "Codex",
                        checked = false,
                        supporting = shortText,
                        supportingMaxLines = 3,
                        supportingMinLines = 1,
                        onCheckedChange = {},
                    )
                }
                Box(Modifier.testTag("two-line-row")) {
                    KitSwitchRow(
                        title = "Codex",
                        checked = true,
                        supporting = longText,
                        supportingMaxLines = 3,
                        onCheckedChange = {},
                    )
                }
            }
        }
        val defaultHeight = onNodeWithTag("default-row").fetchSemanticsNode().size.height
        val oneHeight = onNodeWithTag("one-row").fetchSemanticsNode().size.height
        val twoLineHeight = onNodeWithTag("two-line-row").fetchSemanticsNode().size.height
        assertEquals(oneHeight, defaultHeight, "default supportingMinLines should equal supportingMinLines = 1")
        assertTrue(
            defaultHeight < twoLineHeight,
            "default row ($defaultHeight) should stay shorter than 2-line ($twoLineHeight)",
        )

        val defaultMap = onNodeWithTag("default-row").captureToImage().toPixelMap()
        val oneMap = onNodeWithTag("one-row").captureToImage().toPixelMap()
        assertEquals(oneMap.width, defaultMap.width)
        assertEquals(oneMap.height, defaultMap.height)
        assertTrue(defaultMap.buffer.contentEquals(oneMap.buffer), "existing caller picture must stay pixel-identical")
    }

    @Test
    fun `B-KIT-027 kitSwitchSupportingLines computes 1 at text 1_0 and 2 at text 1_3 for Codex copy`() =
        runComposeUiTest {
            val texts = listOf("Version 0.171.0 → 0.174.0 · 48 MB", "Stays on 0.171.0")
            var linesAt10 = -1
            var linesAt13 = -1
            setContent {
                Harness(fontScale = 1f) {
                    linesAt10 = kitSwitchSupportingLines(texts, rowWidth = 320.dp, supportingMaxLines = 3)
                }
                Harness(fontScale = 1.3f) {
                    linesAt13 = kitSwitchSupportingLines(texts, rowWidth = 320.dp, supportingMaxLines = 3)
                }
            }
            waitForIdle()
            assertEquals(1, linesAt10, "at 320 dp and text 1.0 both texts fit on 1 line")
            assertEquals(2, linesAt13, "at 320 dp and text 1.3 the taller text needs 2 lines")
        }
}
