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
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** The page under a shown sheet does not take focus; the focus it held comes back when the sheet hides. */
class KitSheetPageFocusTest {
    /** The field's own focus (the page's semantics are cleared under a modal, so the semantic state is not there to read). */
    private var pageFocused = false

    private fun run(block: androidx.compose.ui.test.ComposeUiTest.(show: (Boolean) -> Unit, page: FocusRequester) -> Unit) =
        runComposeUiTest {
            var shown by mutableStateOf(false)
            val request = FocusRequester()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform()) {
                    KitSheetHost {
                        Column {
                            BasicTextField("page", {}, Modifier.testTag("page-field").focusRequester(request).onFocusChanged { pageFocused = it.isFocused })
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
            assertTrue(pageFocused, "focused")
            runOnIdle { show(true) }
            waitForIdle()
            assertTrue(!pageFocused, "not focused")
            runOnIdle { request.requestFocus() }
            waitForIdle()
            assertTrue(!pageFocused, "not focused")
            runOnIdle { show(false) }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(pageFocused, "focused")
        }

    @Test
    fun `with nothing focused on the page nothing is focused after the sheet hides`() =
        run { show, _ ->
            assertTrue(!pageFocused, "not focused")
            runOnIdle { show(true) }
            waitForIdle()
            runOnIdle { show(false) }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(!pageFocused, "not focused")
        }
}
