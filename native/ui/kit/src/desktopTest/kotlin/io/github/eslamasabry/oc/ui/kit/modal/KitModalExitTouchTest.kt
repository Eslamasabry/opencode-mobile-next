@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cut audit 2, finding 11: on the home, after any sheet closed, the first tap on the page did nothing for seconds on a
 * busy phone. The leaving sheet's scrim still took taps until its exit ended, and every slow frame of that exit kept it.
 * From the moment a modal is dismissed its exit is picture only: a tap on the page lands on the page within two frames
 * of the dismissal (the kit's own state turning), with the exit animation still running (the clock is held).
 */
class KitModalExitTouchTest {
    private enum class Kind { Sheet, Dialog, Menu }

    private class Page {
        var open by mutableStateOf(false)
        var taps = 0
    }

    @Composable
    private fun Modal(kind: Kind, page: Page) {
        val close = { page.open = false }
        when (kind) {
            Kind.Sheet -> KitSheet(visible = page.open, onDismissRequest = close, title = "Servers") { KitText("This phone") }
            Kind.Dialog -> KitAlert(visible = page.open, title = "Remove this server", onDismissRequest = close)
            Kind.Menu ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    KitMenu(expanded = page.open, items = listOf(KitMenuItem("Rename", {})), onDismissRequest = close) {
                        KitText("More")
                    }
                }
        }
    }

    private fun run(kind: Kind, block: ComposeUiTest.(Page) -> Unit) =
        runComposeUiTest {
            val page = Page()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(412, 915) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            // A page control where only the scrim lies over it (the home's chips).
                            Box(Modifier.size(160.dp, 64.dp).testTag("page-chip").clickable { page.taps++ })
                            Modal(kind, page)
                        }
                    }
                }
            }
            waitForIdle()
            block(page)
        }

    private fun tapsReachThePageAsTheModalLeaves(kind: Kind, label: String) =
        run(kind) { page ->
            mainClock.autoAdvance = false
            runOnUiThread { page.open = true }
            mainClock.advanceTimeBy(2_000)
            onNodeWithText(label).assertExists()
            runOnUiThread { page.open = false }
            // The owner's state turns (one frame), the kit's own state turns (the next): the exit has only begun.
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithText(label).assertExists()
            onNodeWithTag("page-chip").performTouchInput { click() }
            mainClock.advanceTimeByFrame()
            assertEquals(1, page.taps, "$kind: the first tap after the dismissal reached the page")
        }

    @Test
    fun `B-KIT-EXIT-101 a tap on the page while a dismissed sheet is still leaving reaches the page`() =
        tapsReachThePageAsTheModalLeaves(Kind.Sheet, "This phone")

    @Test
    fun `B-KIT-EXIT-102 a tap on the page while a dismissed dialog is still leaving reaches the page`() =
        tapsReachThePageAsTheModalLeaves(Kind.Dialog, "Remove this server")

    @Test
    fun `B-KIT-EXIT-103 a tap on the page while a dismissed menu is still fading reaches the page`() =
        tapsReachThePageAsTheModalLeaves(Kind.Menu, "Rename")
}
