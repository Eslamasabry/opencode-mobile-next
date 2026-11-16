package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertFalse

/** Issue 147: an action chip on a half-width tile at 360 dp wraps its words; it is never cut to "New conver…". */
@OptIn(ExperimentalTestApi::class)
class KitChipActionNotCutTest {
    @Test
    fun `I147-FIRST-RUN an action chip label that does not fit on one line wraps and is not cut`() =
        runDesktopComposeUiTest(360, 400) {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.width(140.dp)) {
                        KitChip("New conversation", kind = KitChipKind.Action, icon = KitIcons.chat, onClick = {})
                    }
                }
            }
            waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithTag("kit-chip-label", useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!(results)
            assertFalse(results.first().hasVisualOverflow, "the label is cut")
        }

    @Test
    fun `COMPACT-028 a wrapping action chip wraps onto as many lines as needed at 1_0 and 1_3 text`() {
        for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(360 * 3, 400 * 3) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
                        KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                            Box(Modifier.width(126.dp)) {
                                KitChip(
                                    "Check Antigravity again",
                                    kind = KitChipKind.Action,
                                    icon = KitIcons.retry,
                                    wraps = true,
                                    onClick = {},
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                onNodeWithTag("kit-chip-label", useUnmergedTree = true).fetchSemanticsNode()
                    .config[SemanticsActions.GetTextLayoutResult].action!!(results)
                val layout = results.single()
                assertFalse(layout.hasVisualOverflow, "scale $scale: visual overflow")
                assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "scale $scale: ellipsized")
            }
        }
    }
}
