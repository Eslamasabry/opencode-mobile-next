@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/**
 * Cut audit 2, finding 14: on New conversation, closing a sheet with Back raised the keyboard again, and the next tap
 * (meant for the model chip) landed on the keyboard. The page puts the keyboard away before a sheet opens and turns the
 * box's autofocus off while one is up; when the sheet closed, autofocus came back on and the box took focus again. The
 * box takes focus when the page arrives, once: closing a sheet never raises a keyboard the page had put away.
 */
class KitStartBoxSheetFocusTest {
    private val question = "What would you like to build?"

    @Test
    fun `B-KIT-START-201 closing a sheet does not put the cursor back in the box the page put away`() = runComposeUiTest {
        val field = TextFieldState()
        var sheet by mutableStateOf(false)
        lateinit var focus: FocusManager
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        focus = LocalFocusManager.current
                        Column {
                            KitStartBox(field, question, autofocus = !sheet)
                        }
                        KitSheet(visible = sheet, onDismissRequest = { sheet = false }, title = "Choose a model") { KitText("GPT 1") }
                    }
                }
            }
        }
        waitForIdle()
        val box = onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(question)))
        box.assertIsFocused()
        // The model chip: the keyboard goes first, then the sheet opens (NewConversationScreen's open()).
        runOnIdle {
            focus.clearFocus()
            sheet = true
        }
        waitForIdle()
        // Back on the sheet.
        runOnIdle { sheet = false }
        waitForIdle()
        box.assertIsNotFocused()
    }
}
