@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-A11Y-STACK (UX-25): a menu opened over a sheet, and a menu opened over a page, with the keyboard (Back and Escape reach
 * the one dispatcher a hardware key and the system gesture share). Back closes the top layer only, the layer under it stays
 * open, and when the last layer is gone the focus returns to the field that held it when the first one opened: the person
 * who typed, opened a sheet, opened its menu and backed out lands where they were.
 */
class KitStackedModalFocusTest {
    private val back =
        object : NavigationEventInput() {
            fun press() {
                dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
                dispatchOnBackCompleted()
            }
        }
    private var fellThrough = 0
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher =
                NavigationEventDispatcher(onBackCompletedFallback = { fellThrough++ }).also { it.addInput(back) }
        }

    private var pageFocused = false

    private class Page {
        var sheet by mutableStateOf(false)
        var menu by mutableStateOf(false)
        var backs = 0
        val request = FocusRequester()
    }

    private fun run(block: ComposeUiTest.(Page) -> Unit) =
        runComposeUiTest {
            val page = Page()
            setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(412, 915) {
                            KitSheetHost(Modifier.fillMaxSize()) {
                                KitBackGuard(true) { page.backs++ }
                                Column {
                                    BasicTextField("page", {}, Modifier.focusRequester(page.request).onFocusChanged { pageFocused = it.isFocused })
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                                        KitMenu(expanded = page.menu, items = listOf(KitMenuItem("Rename", {})), onDismissRequest = { page.menu = false }) {
                                            KitText("More")
                                        }
                                    }
                                }
                                KitSheet(page.sheet, { page.sheet = false }, "Sheet") { KitText("Body") }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block(page)
        }

    private fun ComposeUiTest.press() {
        runOnIdle { back.press() }
        mainClock.advanceTimeBy(2000)
        waitForIdle()
    }

    @Test
    fun `B-A11Y-STACK a menu over a sheet closes first, the sheet next, then focus is back on the field that held it`() =
        run { page ->
            runOnIdle { page.request.requestFocus() }
            waitForIdle()
            assertTrue(pageFocused, "the field holds focus")
            runOnIdle { page.sheet = true }
            waitForIdle()
            runOnIdle { page.menu = true }
            waitForIdle()
            assertFalse(pageFocused, "nothing on the page has focus under the stack")
            press()
            assertFalse(runOnIdle { page.menu }, "the first Back closed the menu, the top layer")
            assertTrue(runOnIdle { page.sheet }, "the sheet under it stayed open")
            assertFalse(pageFocused, "and the page still has no focus under the sheet")
            press()
            assertFalse(runOnIdle { page.sheet }, "the second Back closed the sheet")
            assertTrue(pageFocused, "focus is back on the field that held it when the first layer opened")
            assertEquals(0, page.backs, "the page's own Back was not taken meanwhile")
            press()
            assertEquals(1, page.backs, "with no layer left the next Back is the page's")
            assertEquals(0, fellThrough)
        }

    @Test
    fun `B-A11Y-STACK a menu over the page closes on Back and the field that held focus still holds it`() =
        run { page ->
            runOnIdle { page.request.requestFocus() }
            waitForIdle()
            runOnIdle { page.menu = true }
            waitForIdle()
            press()
            assertFalse(runOnIdle { page.menu }, "Back closed the menu")
            assertEquals(0, page.backs, "the page did not take the same Back")
            assertTrue(pageFocused, "the field that opened the menu has focus again")
        }
}
