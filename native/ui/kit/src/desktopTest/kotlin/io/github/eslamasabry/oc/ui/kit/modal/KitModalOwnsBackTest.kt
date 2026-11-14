@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Cut audit 2, finding 12: on Settings › This app › Language one Back closed the Language sheet AND left This app. All
 * of a window's sheets live in its one host, whose Back was registered when the session's first sheet opened; a page
 * opened later (This app) registered its own Back after it, and the dispatcher asks the newest first, so the page's
 * Back ran and its leaving took the sheet with it. One Back does one thing: with a sheet, dialog, menu or picker open it
 * closes that and nothing else, whatever order the page and the host were composed in. The next Back is the page's.
 */
class KitModalOwnsBackTest {
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

    private enum class Kind { Sheet, Dialog, Menu, Picker, SideSheet }

    private class Page {
        /** A new value composes the page again: its Back is registered anew, after the window host's. */
        var visit by mutableIntStateOf(0)
        var open by mutableStateOf(false)
        var backs = 0
    }

    private val models = (1..3).map { KitModelOption("openai", "gpt-$it", "GPT $it", providerName = "OpenAI") }

    @Composable
    private fun Modal(kind: Kind, page: Page) {
        val close = { page.open = false }
        when (kind) {
            Kind.Sheet -> KitSheet(visible = page.open, onDismissRequest = close, title = "Language") { KitText("English") }
            Kind.Dialog -> KitAlert(visible = page.open, title = "Remove this server", onDismissRequest = close)
            Kind.Menu ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    KitMenu(expanded = page.open, items = listOf(KitMenuItem("Rename", {})), onDismissRequest = close) {
                        KitText("More")
                    }
                }
            Kind.Picker -> KitModelSheet(visible = page.open, onDismissRequest = close, options = models, current = null, onChoose = {})
            Kind.SideSheet -> KitSideSheet(visible = page.open, onDismissRequest = close) { KitText("Details") }
        }
    }

    private fun run(kind: Kind, block: ComposeUiTest.(Page) -> Unit) =
        runComposeUiTest {
            val page = Page()
            setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(412, 915) {
                            // The window's one host (the shell's), as on the phone: every page's modals live here.
                            KitSheetHost(Modifier.fillMaxSize()) {
                                key(page.visit) {
                                    KitBackGuard(true) { page.backs++ }
                                    Modal(kind, page)
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block(page)
        }

    /** The modal opens once (the host registers its Back), closes, the page is opened again, and the modal opens. */
    private fun ComposeUiTest.openAfterThePageCameBack(page: Page) {
        runOnIdle { page.open = true }
        waitForIdle()
        runOnIdle { page.open = false }
        waitForIdle()
        runOnIdle { page.visit++ }
        waitForIdle()
        runOnIdle { page.open = true }
        waitForIdle()
    }

    private fun oneBackClosesOnlyTheModal(kind: Kind) =
        run(kind) { page ->
            openAfterThePageCameBack(page)
            runOnIdle { back.press() }
            waitForIdle()
            assertFalse(runOnIdle { page.open }, "$kind: Back closed it")
            assertEquals(0, page.backs, "$kind: the page under it did not take the same Back")
            assertEquals(0, fellThrough)
            runOnIdle { back.press() }
            waitForIdle()
            assertEquals(1, page.backs, "$kind: with nothing open the next Back is the page's")
        }

    @Test
    fun `B-KIT-BACK-101 one Back with a sheet open closes the sheet and leaves the page where it is`() =
        oneBackClosesOnlyTheModal(Kind.Sheet)

    @Test
    fun `B-KIT-BACK-102 one Back with a dialog open closes the dialog and leaves the page where it is`() =
        oneBackClosesOnlyTheModal(Kind.Dialog)

    @Test
    fun `B-KIT-BACK-103 one Back with a menu open closes the menu and leaves the page where it is`() =
        oneBackClosesOnlyTheModal(Kind.Menu)

    @Test
    fun `B-KIT-BACK-104 one Back with a picker open closes the picker and leaves the page where it is`() =
        oneBackClosesOnlyTheModal(Kind.Picker)

    @Test
    fun `B-KIT-BACK-105 one Back with a side sheet open closes it and leaves the page where it is`() =
        oneBackClosesOnlyTheModal(Kind.SideSheet)
}
