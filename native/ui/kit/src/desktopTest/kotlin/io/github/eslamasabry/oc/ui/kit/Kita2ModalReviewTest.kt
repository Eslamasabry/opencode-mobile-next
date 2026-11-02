package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitDialogFrame
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals

/** Counterexamples from the KITA2 review; all input values are synthetic. */
@OptIn(ExperimentalTestApi::class)
class Kita2ModalReviewTest {
    private val back =
        object : NavigationEventInput() {
            fun press() {
                dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
                dispatchOnBackCompleted()
            }
        }
    private var fellThrough = 0
    private val dispatcher =
        NavigationEventDispatcher(onBackCompletedFallback = { fellThrough++ }).also {
            it.addInput(back)
        }
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
            ) {
                content()
            }
        }
    }

    @Test
    fun `B-KIT-032 KITA2-4 Back after Keep editing asks again and releases the closed dialog`() =
        runComposeUiTest {
            var dismissed = 0
            var host: io.github.eslamasabry.oc.ui.kit.modal.KitSheetHostState? = null
            setContent {
                Theme {
                    KitSheetHost {
                        host = LocalKitSheetHost.current
                        KitSheet(true, { dismissed++ }, "Edit", dirty = true) { KitText("Body") }
                    }
                }
            }
            runOnIdle { back.press() }
            onNodeWithText("Keep editing").performClick()
            waitForIdle()
            runOnIdle {
                assertEquals(null, host?.dialog, "a completed exit releases the matching entry")
            }
            runOnIdle { back.press() }
            onNodeWithText("Discard your changes?").assertExists()
            assertEquals(0, dismissed)
            assertEquals(0, fellThrough)
        }

    @Test
    fun `B-KIT-032 KITA2-5 a nondismissible sheet consumes Back`() = runComposeUiTest {
        var dismissed = 0
        setContent {
            Theme {
                KitSheetHost {
                    KitSheet(true, { dismissed++ }, "Busy sheet", dismissible = false) {
                        KitText("Body")
                    }
                }
            }
        }
        runOnIdle { back.press() }
        onNodeWithText("Busy sheet").assertExists()
        assertEquals(0, dismissed)
        assertEquals(0, fellThrough)
    }

    @Test
    fun `B-KIT-016 KITA2-5 a nondismissible dialog consumes Back`() = runComposeUiTest {
        var dismissed = 0
        setContent {
            Theme {
                KitSheetHost {
                    KitDialogFrame(true, "Busy dialog", { dismissed++ }, dismissible = false) {
                        KitText("Busy dialog")
                    }
                }
            }
        }
        runOnIdle { back.press() }
        onNodeWithText("Busy dialog").assertExists()
        assertEquals(0, dismissed)
        assertEquals(0, fellThrough)
    }
}
