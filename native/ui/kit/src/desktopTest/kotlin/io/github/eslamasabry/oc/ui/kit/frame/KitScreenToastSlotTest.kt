@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitToastHost
import io.github.eslamasabry.oc.ui.kit.modal.KitToastState
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import kotlin.test.Test

/**
 * Toasts in every window: only the app's shell provided the toast slot, so a kit page in another window (the widget
 * panels, Android's Manage space page) showed nothing for every `show`. The page frame now provides a slot when none
 * is above it, and adds none under one (one toast, never two).
 */
class KitScreenToastSlotTest {
    @Test
    fun `B-FRAME-TOAST a page in a window with no toast slot shows its toast`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                Box(Modifier.size(400.dp, 700.dp)) {
                    KitScreen(page = true, topBar = { KitText("Manage space") }) {
                        val toasts = LocalKitToasts.current
                        KitButton("Copy it", onClick = { toasts?.show("Copied") })
                    }
                }
            }
        }
        onNodeWithText("Copied").assertDoesNotExist()
        mainClock.autoAdvance = false
        onNodeWithText("Copy it").performClick()
        mainClock.advanceTimeBy(300)
        onNodeWithText("Copied").assertExists()
    }

    @Test
    fun `B-FRAME-TOAST under the window's own slot a page adds no second toast`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                val outer = remember { KitToastState() }
                CompositionLocalProvider(LocalKitToasts provides outer) {
                    Box(Modifier.size(400.dp, 700.dp)) {
                        KitScreen(page = true, topBar = { KitText("Page") }) {
                            val toasts = LocalKitToasts.current
                            KitButton("Copy it", onClick = { toasts?.show("Copied") })
                        }
                        KitToastHost(outer)
                    }
                }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithText("Copy it").performClick()
        mainClock.advanceTimeBy(300)
        onAllNodesWithTag("kit-toast", useUnmergedTree = true).assertCountEquals(1)
    }
}
