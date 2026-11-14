package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cut audit 1 (c7, gl1, gl3): the chat's message menu opened up INTO the status bar, its first row drawn under the clock
 * and the menu touching the top of the screen. Two causes: the menu's empty anchor box read as the window's top-left
 * corner (`boundsInWindow` is `Rect.Zero` for an empty box), and a menu opening below its anchor was never held under
 * the top room. A kit menu now always lies inside the safe area with the kit's gutter, flips to the side with room, and
 * is held to the room (it scrolls) when taller.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuSafeAreaTest {
    // The auditor's emulator: 1080 x 2400 px, status bar + step 132 px, navigation bar + step 84 px, gutter 42 px; a
    // landscape-style cutout of 90 px on the left; three rows of 126 px are the least room beside an anchor.
    private val window = MenuWindow(1080, 2400, margin = 42, top = 132, bottom = 84, left = 132, right = 42, minRoom = 378)

    private fun assertInside(at: androidx.compose.ui.unit.IntOffset, size: IntSize, what: String) {
        assertTrue(at.x >= window.left, "$what: starts inside the left room (x ${at.x})")
        assertTrue(at.x + size.width <= window.width - window.right, "$what: ends inside the right room (x ${at.x + size.width})")
        assertTrue(at.y >= window.top, "$what: never under the status bar (y ${at.y})")
        assertTrue(at.y + size.height <= window.height - window.bottom, "$what: never under the navigation bar (ends ${at.y + size.height})")
    }

    @Test
    fun `B-KIT-MSAFE-001 a menu from an anchor at any edge stays inside the safe area`() {
        val anchors =
            mapOf(
                "the window's top-left corner (the old empty anchor)" to IntRect(0, 0, 0, 0),
                "a control under the status bar" to IntRect(400, 20, 520, 100),
                "a control at the top end" to IntRect(950, 140, 1076, 266),
                "a control at the top start, in the cutout" to IntRect(0, 140, 126, 266),
                "a control at the bottom start" to IntRect(0, 2250, 126, 2376),
                "the empty anchor in the bottom end corner" to IntRect(1080, 2400, 1080, 2400),
                "a control at the end, mid height" to IntRect(1000, 1100, 1080, 1226),
                "a message as tall as the screen" to IntRect(42, -300, 1038, 2700),
                "a control below the window" to IntRect(500, 2500, 600, 2600),
            )
        val panels = listOf(IntSize(560, 775), IntSize(320, 300), IntSize(706, 930))
        for ((what, anchor) in anchors) for (panel in panels) {
            // The panel is measured against the room first (it scrolls inside when taller), then placed.
            val held = IntSize(panel.width, minOf(panel.height, MenuPlacement.room(anchor.top, anchor.bottom, window)))
            assertTrue(held.height > 0, "$what: a menu always has room")
            assertInside(MenuPlacement.offset(anchor, held, rtl = false, window), held, what)
            assertInside(MenuPlacement.offset(anchor, held, rtl = true, window), held, "$what, right to left")
        }
    }

    @Test
    fun `B-KIT-MSAFE-002 a menu flips to the side with room and is held to that room when taller`() {
        // Low on the screen: no room below, so above, beside its control.
        val low = IntRect(900, 2150, 1026, 2276)
        val at = MenuPlacement.offset(low, IntSize(400, 600), rtl = false, window)
        assertEquals(2150 - 600, at.y, "above its control, ending on its top edge")
        // High on the screen: below.
        val high = IntRect(900, 140, 1026, 266)
        assertEquals(266, MenuPlacement.offset(high, IntSize(400, 600), rtl = false, window).y)
        // Too tall for either side: the roomier side's room, inside the safe area.
        val mid = IntRect(900, 1000, 1026, 1126)
        val room = MenuPlacement.room(mid.top, mid.bottom, window)
        assertEquals(2400 - 84 - 1126, room, "below is the roomier side (1190 against 868)")
        val placed = MenuPlacement.offset(mid, IntSize(400, room), rtl = false, window)
        assertEquals(1126, placed.y)
        // An anchor under the status bar counts from the safe area's edge: its room never reaches under the bar.
        assertEquals(2400 - 84 - 132, MenuPlacement.room(0, 0, window))
    }

    @Test
    fun `B-KIT-MSAFE-003 an anchor-less menu opens from where its empty anchor lies, not from the window's corner`() =
        runDesktopComposeUiTest(400, 800) {
            var open by mutableStateOf(false)
            setContent {
                // A record of its own: a press another test made a moment ago must not open this menu.
                CompositionLocalProvider(LocalKitPressOrigin provides KitPressOrigin()) {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Chat") }) {
                        Box(Modifier.fillMaxSize()) {
                            KitText("Behind")
                            // The chat's message menu: an empty anchor in the bottom end corner, opened by a hold
                            // that records no pressed control.
                            KitMenu(
                                expanded = open,
                                items = listOf(KitMenuItem("Details", onSelect = {}), KitMenuItem("Copy message text", onSelect = {})),
                                onDismissRequest = { open = false },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {}
                        }
                    }
                }
                }
            }
            open = true
            waitForIdle()
            val panel = onNodeWithTag("kit-menu").fetchSemanticsNode().boundsInWindow
            assertTrue(panel.top > 400f, "the menu opens above the bottom end corner, not from the top (panel $panel)")
            assertTrue(panel.bottom <= 800f, "and ends inside the window (panel $panel)")
            assertTrue(panel.right >= 300f, "on the end side (panel $panel)")
        }
}
