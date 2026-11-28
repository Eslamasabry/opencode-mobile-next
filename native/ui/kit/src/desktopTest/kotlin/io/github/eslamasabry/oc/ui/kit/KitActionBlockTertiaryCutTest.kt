package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KitActionBlock's default on a compact window: a stack's tertiary line takes its room, and gives it back, in one
 * frame. The block has no motion of its own, so a screen that adds or drops a tertiary action is drawn settled.
 */
@OptIn(ExperimentalTestApi::class)
class KitActionBlockTertiaryCutTest {
    private val skip = listOf(KitAction("Skip these versions", {}))

    @Composable
    private fun Scene(tertiary: List<KitAction>) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KitTheme(
                KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Full),
                FakeKitPlatform(),
                "en",
                stillFrame = true,
            ) {
                InWindow(WIDTH, HEIGHT) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        KitActionBlock(
                            Modifier.testTag(BLOCK),
                            primary = KitAction("Update all", {}),
                            secondary = KitAction("Not now", {}),
                            tertiary = tertiary,
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.blockHeight(): Int = onNodeWithTag(BLOCK).fetchSemanticsNode().size.height

    private fun ComposeUiTest.settle(): Int {
        mainClock.autoAdvance = true
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        return blockHeight()
    }

    @Test
    fun `B-KIT-011 a stack's tertiary line takes its room in one frame at Full`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var tertiary by mutableStateOf(emptyList<KitAction>())
            setContent { Scene(tertiary) }
            waitForIdle()
            val without = blockHeight()

            mainClock.autoAdvance = false
            tertiary = skip
            mainClock.advanceTimeByFrame()
            val afterOne = blockHeight()
            onNodeWithText("Skip these versions").assertExists()

            val settled = settle()
            assertTrue(settled > without, "expected the tertiary line to add room: $without -> $settled")
            assertEquals(settled, afterOne, "expected the tertiary line's whole room after one frame (no fold)")
        }

    @Test
    fun `B-KIT-011 a stack's tertiary line gives its room back in one frame at Full`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var tertiary by mutableStateOf(skip)
            setContent { Scene(tertiary) }
            waitForIdle()
            val with = blockHeight()

            mainClock.autoAdvance = false
            tertiary = emptyList()
            mainClock.advanceTimeByFrame()
            val afterOne = blockHeight()
            onNodeWithText("Skip these versions").assertDoesNotExist()

            val settled = settle()
            assertTrue(settled < with, "expected the block to lose the tertiary line's room: $with -> $settled")
            assertEquals(settled, afterOne, "expected the tertiary line's room given back after one frame (no fold)")
        }

    private companion object {
        const val WIDTH = 412
        const val HEIGHT = 320
        const val SETTLE_MS = 600L
        const val BLOCK = "action-block"
    }
}
