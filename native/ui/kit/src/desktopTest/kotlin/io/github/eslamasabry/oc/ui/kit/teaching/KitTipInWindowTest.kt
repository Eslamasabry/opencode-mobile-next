@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.teaching

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tip shown inside an app screen is drawn in the window's own layer, never in a window of its own (owner
 * 2026-11-10, a new install's first chat: "steals focus from composer whenever there's a tool tip"). A tip that
 * arrives as the person starts to type must leave the field typing: no popup window is made, the field keeps its
 * focus and the words keep going in, and the tip leaves when its moment ends.
 */
class KitTipInWindowTest {
    @Test
    fun `a tip arriving while the person types makes no window and takes no focus`() = runComposeUiTest {
        var tip by mutableStateOf(false)
        val words = TextFieldState()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                KitSheetHost {
                    Column(Modifier.size(412.dp, 720.dp)) {
                        KitField(state = words, label = "Message", labelShown = false)
                        KitTip("Hold Send to send later or repeat", visible = tip, onDismissed = { tip = false }) {
                            Box(Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
        onNode(hasSetTextAction()).performTouchInput { click() }
        onNode(hasSetTextAction()).performTextInput("he")

        tip = true
        waitForIdle()

        onNodeWithTag("kit-tip").assertExists()
        onAllNodes(isPopup()).assertCountEquals(0)
        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).performTextInput("llo")
        runOnIdle { assertEquals("hello", words.text.toString()) }

        tip = false
        waitForIdle()
        onNodeWithTag("kit-tip").assertDoesNotExist()
        onNode(hasSetTextAction()).assertIsFocused()
    }
}
