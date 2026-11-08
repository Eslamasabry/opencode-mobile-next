package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTile
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileAction
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTiles
import io.github.eslamasabry.oc.ui.kit.agent.agentMonogram
import io.github.eslamasabry.oc.ui.kit.agent.agentTileColumns
import io.github.eslamasabry.oc.ui.kit.agent.agentTileLabel
import io.github.eslamasabry.oc.ui.kit.agent.agentTileRows
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Agents page's bento tiles (native/agents-bento, owner 2026-11-08). */
@OptIn(ExperimentalTestApi::class)
class KitAgentTileTest {
    @Test
    fun `B-KIT-280 two tiles a row on a phone, three or four on a wide window, one at large text`() {
        assertEquals(2, agentTileColumns(328f, 1f)) // a 360 dp phone less its gutters
        assertEquals(2, agentTileColumns(380f, 1f))
        assertEquals(3, agentTileColumns(688f, 1f)) // the Reading cap less its gutters
        assertEquals(4, agentTileColumns(2000f, 1f))
        assertEquals(1, agentTileColumns(688f, 1.31f))
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), agentTileRows(5, 3))
        assertEquals(listOf(listOf(0, 1), listOf(2)), agentTileRows(3, 2))
    }

    @Test
    fun `B-KIT-281 a missing logo is a monogram letter, and the tile reads its name and state`() {
        assertEquals("F", agentMonogram("fx"))
        assertEquals("C", agentMonogram("  claude"))
        assertEquals("?", agentMonogram("··"))
        assertEquals("Codex. Sign in needed", agentTileLabel("Codex", "Sign in needed"))
        assertEquals("Codex", agentTileLabel("Codex", ""))
    }

    @Test
    fun `B-KIT-282 the tile opens on tap, its one act is a separate small target, no logo shows a letter`() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                    CompositionLocalProvider(LocalKitLogoLoader provides MARKS) {
                        Box(Modifier.width(412.dp)) {
                            KitAgentTiles(
                                listOf(
                                    { KitAgentTile("Claude Code", "Ready", "claude", { calls += "open:claude" }) },
                                    {
                                        KitAgentTile(
                                            "Gemini",
                                            "Not installed",
                                            "gemini",
                                            { calls += "open:gemini" },
                                            action = KitAgentTileAction("Install", "Install Gemini") { calls += "install:gemini" },
                                        )
                                    },
                                    { KitAgentTile("fx", "Installing…", "fx", { calls += "open:fx" }, progress = KitProgress.Known(0.4f)) },
                                )
                            )
                        }
                    }
                }
            }
            onAllNodesWithTag("kit-agent-tile").fetchSemanticsNodes().let { assertEquals(3, it.size) }
            onNode(hasContentDescription("Install Gemini") and hasClickAction()).performClick()
            onNode(hasContentDescription("Claude Code. Ready") and hasClickAction()).performClick()
            assertEquals(listOf("install:gemini", "open:claude"), calls)
            onNodeWithText("F", useUnmergedTree = true).assertExists()
        }
}
