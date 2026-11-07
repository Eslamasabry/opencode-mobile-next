package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E20-S08 (craft walk of 3062b719a): kit-level fixes found on the phone. */
@OptIn(ExperimentalTestApi::class)
class E20S08KitCraftTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    @Test
    fun `E20-S08 a chip line in a gutter block scrolls inside the gutter, never off the screen's edge`() =
        runComposeUiTest {
            setContent {
                Themed {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        KitFilterChips(bleed = true) {
                            KitChip("OpenCode", onClick = {})
                            KitChip("GLM-5.3-Flash Extended Thinking Preview", onClick = {})
                            KitChip("shopfront-checkout-service", onClick = {})
                        }
                    }
                }
            }
            val line = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
            // The line's window is the block's: it starts and ends on the gutter, so the last chip fades out at the
            // gutter instead of running off the screen.
            assertEquals(16f, line.left)
            assertEquals(412f - 16f, line.right)
            val first = onNodeWithText("OpenCode", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(first.left >= 16f, "the first chip's words start inside the gutter: $first")
        }
}
