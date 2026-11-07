package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * P-108: the reply's ⋯ opens the chat's one message menu, whose anchor is an empty box in the screen's bottom end. The
 * menu opened from that corner and covered the ⋯ (button y 1620–1746 px, menu 1560–2335 px). An anchor-less menu now
 * opens from the control just pressed: above it when it does not fit below, never over it.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuPressOriginTest {
    // The auditor's emulator: 1080 x 2400 px, the ⋯ at 975 ± 63 across and 1620–1746 down, a 775 px menu.
    private val window = MenuWindow(1080, 2400, margin = 42, top = 84, bottom = 84)
    private val more = IntRect(912, 1620, 1038, 1746)
    private val menu = IntSize(560, 775)

    @Test
    fun `B-KIT-108 an anchor-less menu opens from the pressed control and never covers it`() {
        val corner = IntRect(1080, 2316, 1080, 2316) // the empty anchor box at the screen's bottom end
        // Before: placed from the corner, the panel's span runs over the button.
        val before = MenuPlacement.offset(corner, menu, rtl = false, window)
        assertTrue(before.y < more.bottom && before.y + menu.height > more.top, "the old placement covered the ⋯")
        val at = MenuPlacement.anchorFor(corner, more)
        assertEquals(more, at)
        val after = MenuPlacement.offset(at, menu, rtl = false, window)
        assertTrue(after.y + menu.height <= more.top, "the menu flips above the ⋯ (ends at ${after.y + menu.height})")
        assertEquals(IntOffset(1038 - 560, 1620 - 775), after)
    }

    @Test
    fun `B-KIT-108 a menu with its own anchor keeps it`() {
        val own = IntRect(0, 100, 48, 148)
        assertEquals(own, MenuPlacement.anchorFor(own, more))
        val empty = IntRect(1080, 2316, 1080, 2316)
        assertEquals(empty, MenuPlacement.anchorFor(empty, null))
    }

    @Test
    fun `B-KIT-108 only a press from just before the menu opens counts, and once`() {
        val time = TestTimeSource()
        val origin = KitPressOrigin(time)
        origin.record(more)
        time += 100.milliseconds
        assertEquals(more, origin.take())
        assertNull(origin.take(), "a menu takes the press once")
        origin.record(more)
        time += 900.milliseconds
        assertNull(origin.take(), "a press from long ago did not open this menu")
    }

    @Test
    fun `B-KIT-108 a kit button records where it was pressed for the menu it opens`() =
        runDesktopComposeUiTest(400, 800) {
            val origin = KitPressOrigin()
            setContent {
                CompositionLocalProvider(LocalKitPressOrigin provides origin) {
                    KitTheme {
                        var open by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 200.dp, end = 16.dp).testTag("more")) {
                                KitIconButton(KitIcons.more, "More", { open = true })
                            }
                            // The chat's message menu: an empty anchor in the bottom end corner.
                            KitMenu(
                                expanded = open,
                                items = listOf(KitMenuItem("Copy", onSelect = {})),
                                onDismissRequest = { open = false },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {}
                        }
                    }
                }
            }
            val button = onNodeWithTag("more").fetchSemanticsNode().boundsInWindow
            onNodeWithContentDescription("More").performClick()
            waitForIdle()
            val item = onNodeWithText("Copy").fetchSemanticsNode().boundsInWindow
            assertTrue(
                item.bottom <= button.top || item.top >= button.bottom,
                "the menu item sits beside the ⋯, not over it (item $item, button $button)",
            )
        }
}
