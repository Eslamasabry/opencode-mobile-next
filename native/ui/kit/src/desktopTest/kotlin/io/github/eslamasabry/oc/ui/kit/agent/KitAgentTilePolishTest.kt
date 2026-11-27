package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.KitWholeWordsRules
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Polish checks for agent tiles: half-width lone tiles, two columns at 1.3x, and whole-word chip wrapping. */
@OptIn(ExperimentalTestApi::class)
class KitAgentTilePolishTest {
    @Test
    fun `B-560 a lone last tile is half-width at the start edge with its check top-right`() =
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
                                                icon = KitIcons.chat,
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
            assertTrue(tile.left <= 1f, "expected lone tile at start edge, left=${tile.left}")
            assertTrue(tile.width in 150f..166f, "expected half-width lone tile (~158 dp), got ${tile.width} dp")
            val rightGap = tile.right - check.right
            val topGap = check.top - tile.top
            assertTrue(rightGap <= 20f, "expected check near right edge (gap $rightGap dp > 20 dp)")
            assertTrue(topGap <= 20f, "expected check near top edge (gap $topGap dp > 20 dp)")
            assertTrue(chip.top >= check.bottom, "expected chip top (${chip.top}) >= check bottom (${check.bottom})")
        }

    @Test
    fun `B-560 two tiles stay side by side at 360 dp and 1_3 with full-width actions and equal height`() =
        runDesktopComposeUiTest(328 * 3, 600 * 3) {
            setContent { twoTilePair(scale = 1.3f) }
            waitForIdle()
            val tiles = onAllNodesWithTag("kit-agent-tile", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(2, tiles.size)
            val left = tiles[0].boundsInRoot
            val right = tiles[1].boundsInRoot
            assertTrue(abs(left.top - right.top) <= 2f, "expected side-by-side row: top ${left.top} vs ${right.top}")
            assertTrue(right.left > left.right, "expected second tile to the right of first")
            assertTrue(
                abs(left.height - right.height) <= 2f,
                "expected equal card height: ${left.height} vs ${right.height}",
            )
            val signIn =
                onNode(hasContentDescription("Sign in to Codex") and hasClickAction())
                    .fetchSemanticsNode()
                    .boundsInRoot
            val minInnerPx = right.width - 38f * 3f
            assertTrue(
                signIn.width >= minInnerPx,
                "expected Sign in action to fill card inner width (>= $minInnerPx px), got ${signIn.width} px",
            )
        }

    @androidx.compose.runtime.Composable
    private fun twoTilePair(scale: Float) {
        CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.width(328.dp)) {
                    KitAgentTiles(
                        listOf(
                            {
                                KitAgentTile(
                                    name = "Claude Code",
                                    state = "Ready",
                                    markId = "claude",
                                    onClick = {},
                                    action =
                                        KitAgentTileAction(
                                            label = "Nouvelle conversation",
                                            spoken = "Nouvelle conversation avec Claude Code",
                                            icon = KitIcons.chat,
                                            onClick = {},
                                        ),
                                    ready = true,
                                )
                            },
                            {
                                KitAgentTile(
                                    name = "Codex",
                                    state = "Sign in needed",
                                    markId = "codex",
                                    onClick = {},
                                    action =
                                        KitAgentTileAction(
                                            label = "Sign in",
                                            spoken = "Sign in to Codex",
                                            icon = KitIcons.login,
                                            prominent = true,
                                            onClick = {},
                                        ),
                                )
                            },
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `B-630 wide 928 dp bento grid spans full 960 dp list cap across four columns`() =
        runDesktopComposeUiTest(928, 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        stillFrame = true,
                    ) {
                        Box(Modifier.width(928.dp)) {
                            KitAgentTiles(
                                List(6) { i ->
                                    {
                                        KitAgentTile(
                                            name = "Agent $i",
                                            state = "Ready",
                                            markId = "claude",
                                            onClick = {},
                                            ready = true,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val tiles = onAllNodesWithTag("kit-agent-tile", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(6, tiles.size)
            val row0 = tiles.filter { abs(it.boundsInRoot.top - tiles[0].boundsInRoot.top) <= 2f }
            assertEquals(4, row0.size, "expected 4 columns at 928 dp, got ${row0.size}")
            val left = row0.first().boundsInRoot.left
            val right = row0.last().boundsInRoot.right
            assertTrue(left <= 1f, "expected row start at 0, got $left")
            assertTrue(right >= 927f, "expected row end at 928 dp, got $right")
        }

    @Test
    fun `B-630 wide 928 dp single-column large-text tile spans full 960 dp list cap`() =
        runDesktopComposeUiTest(928, 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        stillFrame = true,
                    ) {
                        Box(Modifier.width(928.dp)) {
                            KitAgentTiles(
                                listOf {
                                    KitAgentTile(
                                        name = "Claude Code",
                                        state = "Ready",
                                        markId = "claude",
                                        onClick = {},
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
            assertTrue(tile.right >= 927f, "expected single-column tile to fill 928 dp, got ${tile.right}")
        }

    @Test
    fun `B-546 half-width tile action drops icon when needed and wraps whole words at full size`() {
        assertTileChipWrapsCleanly("New conversation", scale = 1f, expectIcon = true)
        assertTileChipWrapsCleanly("New conversation", scale = 1.3f, expectIcon = false)
        assertTileChipWrapsCleanly("Nouvelle conversation", scale = 1f, expectIcon = true)
        assertTileChipWrapsCleanly("Nouvelle conversation", scale = 1.3f, expectIcon = false)
        assertTileChipWrapsCleanly("Sign in", scale = 1.3f, expectIcon = true)
    }

    private fun assertTileChipWrapsCleanly(label: String, scale: Float, expectIcon: Boolean) =
        runDesktopComposeUiTest(328 * 3, 600 * 3) {
            var expectedSize = TextUnit.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        stillFrame = true,
                    ) {
                        expectedSize = LocalKitTypography.current.style(TypeRole.Secondary).fontSize
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
                                                label = label,
                                                spoken = "$label with Claude Code",
                                                icon = KitIcons.chat,
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
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithTag("kit-chip-label", useUnmergedTree = true).performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            val layout = layouts.single()
            assertEquals(expectedSize, layout.layoutInput.style.fontSize, "[$label/$scale] font shrank")
            assertFalse(layout.hasVisualOverflow, "[$label/$scale] visual overflow")
            assertFalse(
                (0 until layout.lineCount).any { layout.isLineEllipsized(it) },
                "[$label/$scale] ellipsized",
            )
            assertTrue(layout.lineCount <= 2, "[$label/$scale] expected <= 2 lines, got ${layout.lineCount}")
            assertTrue(KitWholeWordsRules.whole(layout, label), "[$label/$scale] broke mid-word")
            val icons = onAllNodesWithTag("kit-chip-icon", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(
                if (expectIcon) 1 else 0,
                icons.size,
                "[$label/$scale] expected icon=${if (expectIcon) "kept" else "dropped"}",
            )
        }
}
