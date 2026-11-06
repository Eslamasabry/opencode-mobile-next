package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposerLargeTextTest {
    @Test
    fun `B-CHAT-006 N26 large text wraps model and mode above the action row`() = runComposeUiTest {
        val model = "DeepSeek V4.1 Flash Free with a longer model name"
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        KitComposerBox(
                            running = false,
                            field = { KitText("Message") },
                            chips = {
                                KitComposerChip(model, onClick = {})
                                KitComposerChip("Ask first", onClick = {})
                            },
                            trailing = { KitComposerSend("Send", onClick = {}) },
                        )
                    }
                }
            }
        }
        val modelBounds = onNodeWithText(model, useUnmergedTree = true).getBoundsInRoot()
        val modeBounds = onNodeWithText("Ask first", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(modelBounds.width <= 320.dp)
        assertTrue(
            modeBounds.top >= modelBounds.bottom,
            "The mode wraps below the full-width model",
        )
        assertTrue(
            modelBounds.height > modeBounds.height,
            "The full model label wraps over multiple lines",
        )
    }
}
