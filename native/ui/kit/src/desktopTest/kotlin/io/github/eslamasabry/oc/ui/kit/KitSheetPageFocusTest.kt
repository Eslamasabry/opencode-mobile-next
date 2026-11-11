@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** The page under a shown sheet does not take focus; the focus it held comes back when the sheet hides. */
class KitSheetPageFocusTest {
    private fun run(block: androidx.compose.ui.test.ComposeUiTest.(show: (Boolean) -> Unit, page: FocusRequester) -> Unit) =
        runComposeUiTest {
            var shown by mutableStateOf(false)
            val request = FocusRequester()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), stillFrame = true) {
                    KitSheetHost {
                        Column {
                            BasicTextField("page", {}, Modifier.testTag("page-field").focusRequester(request))
                        }
                        KitSheet(shown, { shown = false }, "Sheet") {}
                    }
                }
            }
            block({ shown = it }, request)
        }

    @Test
    fun `a focused page field loses focus when a sheet shows, cannot take it back meanwhile, and gets it when the sheet hides`() =
        run { show, request ->
            runOnIdle { request.requestFocus() }
            onNodeWithTag("page-field").assertIsFocused()
            runOnIdle { show(true) }
            waitForIdle()
            onNodeWithTag("page-field").assertIsNotFocused()
            runOnIdle { request.requestFocus() }
            waitForIdle()
            onNodeWithTag("page-field").assertIsNotFocused()
            runOnIdle { show(false) }
            waitForIdle()
            onNodeWithTag("page-field").assertIsFocused()
        }

    @Test
    fun `with nothing focused on the page nothing is focused after the sheet hides`() =
        run { show, _ ->
            onNodeWithTag("page-field").assertIsNotFocused()
            runOnIdle { show(true) }
            waitForIdle()
            runOnIdle { show(false) }
            waitForIdle()
            onNodeWithTag("page-field").assertIsNotFocused()
        }
}
