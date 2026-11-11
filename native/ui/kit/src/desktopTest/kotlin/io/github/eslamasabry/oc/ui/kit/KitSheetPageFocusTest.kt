@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitKeyboardShown
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** The page under a shown sheet does not take focus; the focus it held comes back when the sheet hides. */
class KitSheetPageFocusTest {
    /** The field's own focus (the page's semantics are cleared under a modal, so the semantic state is not there to read). */
    private var pageFocused = false

    private var secondFocused = false

    /** The page: one field, and a second that asks for focus once when it appears (as Find's does). */
    private class Page {
        var shown by mutableStateOf(false)
        var second by mutableStateOf(false)
        var keyboard by mutableStateOf(false)
        var dialog by mutableStateOf(false)
        val request = FocusRequester()
    }

    private fun runPage(block: androidx.compose.ui.test.ComposeUiTest.(page: Page) -> Unit) =
        runComposeUiTest {
            val page = Page()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform()) {
                    CompositionLocalProvider(LocalKitKeyboardShown provides page.keyboard) {
                        KitSheetHost {
                            Column {
                                BasicTextField("page", {}, Modifier.testTag("page-field").focusRequester(page.request).onFocusChanged { pageFocused = it.isFocused })
                                if (page.second) {
                                    val second = remember { FocusRequester() }
                                    BasicTextField("second", {}, Modifier.focusRequester(second).onFocusChanged { secondFocused = it.isFocused })
                                    LaunchedEffect(Unit) { second.requestFocus() }
                                }
                            }
                            KitSheet(page.shown, { page.shown = false }, "Sheet") {}
                            KitInputDialog(page.dialog, "Rename", "Name", "Rename", initial = "Old") { page.dialog = false }
                        }
                    }
                }
            }
            block(page)
        }

    private fun run(block: androidx.compose.ui.test.ComposeUiTest.(show: (Boolean) -> Unit, page: FocusRequester) -> Unit) =
        runPage { page -> block({ page.shown = it }, page.request) }

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

    @Test
    fun `a page field that asks for focus as the sheet starts to leave gets it and keeps it`() =
        runPage { page ->
            runOnIdle { page.request.requestFocus() }
            runOnIdle { page.shown = true }
            waitForIdle()
            assertTrue(!pageFocused, "not focused")
            // The sheet is asked to hide and the second field appears in the same frame (Find opened from a sheet's row).
            runOnIdle {
                page.shown = false
                page.second = true
            }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(secondFocused, "the field that asked as the sheet left has focus")
            assertTrue(!pageFocused, "the earlier holder did not take it back")
        }

    @Test
    fun `focus comes back after the sheet when the soft keyboard was up as it showed`() =
        runPage { page ->
            runOnIdle {
                page.keyboard = true
                page.request.requestFocus()
            }
            waitForIdle()
            runOnIdle { page.shown = true }
            waitForIdle()
            assertTrue(!pageFocused, "not focused")
            runOnIdle { page.shown = false }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(pageFocused, "focused")
        }

    @Test
    fun `focus does not come back after the sheet when the person had put the soft keyboard away`() =
        runPage { page ->
            runOnIdle {
                page.keyboard = true
                page.request.requestFocus()
            }
            waitForIdle()
            // Back closes the keyboard; the field keeps its focus.
            runOnIdle { page.keyboard = false }
            waitForIdle()
            assertTrue(pageFocused, "still focused with the keyboard away")
            runOnIdle { page.shown = true }
            waitForIdle()
            runOnIdle { page.shown = false }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(!pageFocused, "focus (and with it the keyboard) did not come back")
        }

    @Test
    fun `a dialog whose field takes focus at once gives the page field its focus back when it closes`() =
        runPage { page ->
            runOnIdle { page.request.requestFocus() }
            waitForIdle()
            assertTrue(pageFocused, "focused")
            runOnIdle { page.dialog = true }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(!pageFocused, "the dialog's field has the focus")
            runOnIdle { page.dialog = false }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertTrue(pageFocused, "focus came back to the page field")
        }
}
