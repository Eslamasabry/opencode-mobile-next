package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitToastState
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Issue 147, "buttons do not respond": a control drawn disabled is still touched, and the tap answers with the reason it
 * states (the window's toast), so no grey control is silent. It still runs nothing and still reads as disabled.
 */
@OptIn(ExperimentalTestApi::class)
class KitDisabledTapAnswerTest {
    private fun themed(toasts: KitToastState?, content: @androidx.compose.runtime.Composable () -> Unit): @androidx.compose.runtime.Composable () -> Unit = {
        KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitToasts provides toasts) { content() }
        }
    }

    @Test
    fun `B-KIT-051 a tap on a disabled button says why and runs nothing`() = runComposeUiTest {
        val toasts = KitToastState()
        var calls = 0
        setContent(
            themed(toasts) {
                KitButton("Start", { calls++ }, enabled = KitEnabled.Disabled("Getting your page ready"))
            }
        )
        onNodeWithText("Start").performTouchInput { click() }
        waitForIdle()
        assertEquals("Getting your page ready", toasts.current?.message)
        assertEquals(0, calls)
        onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test
    fun `B-KIT-051 a tap on a disabled row says why and runs nothing`() = runComposeUiTest {
        val toasts = KitToastState()
        var calls = 0
        setContent(
            themed(toasts) {
                KitRow("Project", enabled = false, disabledReason = "Choose a server first", onClick = { calls++ })
            }
        )
        onNodeWithText("Project").performTouchInput { click() }
        waitForIdle()
        assertEquals("Choose a server first", toasts.current?.message)
        assertEquals(0, calls)
    }

    @Test
    fun `B-KIT-051 an enabled button acts and says nothing`() = runComposeUiTest {
        val toasts = KitToastState()
        var calls = 0
        setContent(themed(toasts) { KitButton("Start", { calls++ }) })
        onNodeWithText("Start").performTouchInput { click() }
        waitForIdle()
        assertEquals(1, calls)
        assertNull(toasts.current)
    }
}
