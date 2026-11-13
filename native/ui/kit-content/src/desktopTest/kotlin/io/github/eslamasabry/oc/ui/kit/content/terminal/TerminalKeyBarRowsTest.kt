package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2026-11-13 (terminal): the three-row key panel spent most of its first row empty beside Ctrl-C and Ctrl-D.
 * A row of fewer keys than columns shares the grid's whole width.
 */
@OptIn(ExperimentalTestApi::class)
class TerminalKeyBarRowsTest {
    @Test
    fun `B-TERM-KEYS-030 Ctrl-C and Ctrl-D fill the row above the eight keys, edge to edge`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = ContentFakePlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) {
                    TerminalKeyBar(controller = TerminalKeyBarController(), onKey = { _, _, _ -> }, interruptKeys = true)
                }
            }
        }
        val interrupt = onNodeWithContentDescription("Interrupt, Control C").getUnclippedBoundsInRoot()
        val endOfInput = onNodeWithContentDescription("End of input, Control D").getUnclippedBoundsInRoot()
        val esc = onNodeWithContentDescription("Escape key").getUnclippedBoundsInRoot()
        val pageUp = onNodeWithContentDescription("Page up").getUnclippedBoundsInRoot()
        assertTrue(abs((interrupt.left - esc.left).value) <= 1f, "Ctrl-C starts the row where Esc does: $interrupt / $esc")
        assertTrue(abs((endOfInput.right - pageUp.right).value) <= 1f, "Ctrl-D ends the row where PgUp does: $endOfInput / $pageUp")
        assertTrue(abs((interrupt.width - endOfInput.width).value) <= .5f, "the two keys share the row evenly")
    }

    @Test
    fun `B-TERM-KEYS-031 a short row's keys share the grid's span in whole pixels, a full row keeps its width`() {
        assertEquals(44f, TerminalKeyGrid.shortRowWidth(8, 8, 44f, 4f, 1f))
        // Eight 44 dp keys 4 dp apart span 380 dp; two keys 4 dp apart take 188 dp each.
        assertEquals(188f, TerminalKeyGrid.shortRowWidth(2, 8, 44f, 4f, 1f))
        // At 2.625 px/dp the width snaps down to a whole pixel.
        val w = TerminalKeyGrid.shortRowWidth(3, 8, 44f, 4f, 2.625f)
        assertEquals(w * 2.625f, kotlin.math.floor(w * 2.625f + 1e-3f), 1e-2f)
    }
}
