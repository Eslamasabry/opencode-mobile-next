package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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

    @Test
    fun `E20-S08 a menu mixing items with and without glyphs starts every label on one edge`() = runComposeUiTest {
        setContent {
            Themed {
                io.github.eslamasabry.oc.ui.kit.modal.KitMenuPanel(
                    items =
                        listOf(
                            io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem("Back", {}),
                            io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem(
                                "Notes",
                                {},
                                icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.edit,
                            ),
                        ),
                    onChoose = {},
                )
            }
        }
        val back = onNodeWithText("Back", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val notes = onNodeWithText("Notes", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(notes.left, back.left, "Back $back, Notes $notes")
    }

    @Test
    fun `E20-S08 the page under a search field starts a step below it`() = runComposeUiTest {
        setContent {
            Themed {
                io.github.eslamasabry.oc.ui.kit.frame.KitScreen(
                    search = {
                        Box(
                            Modifier.fillMaxWidth()
                                .height(48.dp)
                                .then(Modifier.testTag("field"))
                        )
                    },
                    edges = KitScreenEdges.Bleed,
                ) {
                    KitText("Nothing matches", Modifier.testTag("heading"))
                }
            }
        }
        val field = onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val heading = onNodeWithTag("heading", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The empty state's heading sat against the field (0 dp).
        assertTrue(heading.top - field.bottom >= 8f, "field $field, heading $heading")
    }
}
