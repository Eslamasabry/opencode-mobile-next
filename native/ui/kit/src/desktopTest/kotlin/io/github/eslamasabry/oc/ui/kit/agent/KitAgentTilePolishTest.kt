package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** Polish checks for full-width ready agent tiles (B-560). */
@OptIn(ExperimentalTestApi::class)
class KitAgentTilePolishTest {
    @Test
    fun `B-560 a full-width ready card keeps its check in the top corner`() =
        runDesktopComposeUiTest(328, 600) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        stillFrame = true,
                    ) {
                        Box(Modifier.width(328.dp)) {
                            KitAgentTiles(
                                listOf {
                                    KitAgentTile(
                                        name = "Claude Code",
                                        state = "Ready",
                                        markId = "claude",
                                        onClick = {},
                                        action =
                                            KitAgentTileAction(
                                                label = "New conversation",
                                                spoken = "New conversation with Claude Code",
                                                onClick = {},
                                            ),
                                        ready = true,
                                    )
                                }
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val tile = onNodeWithTag("kit-agent-tile", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val check = onNodeWithTag("kit-agent-tile-ready", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val chip = onNodeWithTag("kit-chip-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val rightGap = tile.right - check.right
            val topGap = check.top - tile.top
            assertTrue(rightGap <= 20f, "expected check near right edge (gap $rightGap dp > 20 dp)")
            assertTrue(topGap <= 20f, "expected check near top edge (gap $topGap dp > 20 dp)")
            assertTrue(chip.top >= check.bottom, "expected chip top (${chip.top}) >= check bottom (${check.bottom})")
        }
}
