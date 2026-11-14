package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPress
import io.github.eslamasabry.oc.ui.kit.foundation.KitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.kitMenuHold
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cut audit 2, finding 13: the home row's hold menu opened far from the pressed row and the row was not marked, so
 * nothing said which conversation it acts on; a hold on a message recorded nothing, so its menu opened from the
 * screen's corner. A hold now records the part's box and the finger's point: the menu opens at the part, beside the
 * finger, inside the safe area, and the part keeps a held face while its menu is open.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuHeldTest {
    private val window = MenuWindow(1080, 2400, margin = 42, top = 84, bottom = 84)
    private val menu = IntSize(560, 920)

    @Test
    fun `B-KIT-HOLD-001 a held home row's menu opens beside the finger, next to the row, never over it`() {
        val row = IntRect(0, 1400, 1080, 1600)
        val press = KitPress(row, IntOffset(700, 1500), owner = Any())
        val at = MenuPlacement.anchorFor(row, press, band = 264)
        assertEquals(700, at.left, "across: on the finger")
        assertEquals(700, at.right)
        assertEquals(row.top, at.top)
        assertEquals(row.bottom, at.bottom)
        val placed = MenuPlacement.offset(at, menu, rtl = false, window)
        assertTrue(placed.x == 700 || placed.x + menu.width == 700, "the panel starts or ends on the finger (x ${placed.x})")
        val top = placed.y
        val bottom = placed.y + menu.height
        assertTrue(bottom <= row.top || top >= row.bottom, "the menu sits beside the row ($top..$bottom), never over it")
        assertTrue(abs(bottom - row.top) <= 1 || abs(top - row.bottom) <= 1, "and touches the row's side, not 600 px away")
    }

    @Test
    fun `B-KIT-HOLD-002 a held long message's menu opens at the finger, not from the screen's corner`() {
        val corner = IntRect(1080, 2316, 1080, 2316) // the chat's empty anchor in the bottom end
        val message = IntRect(42, 300, 1038, 2000)
        val press = KitPress(message, IntOffset(400, 1200), owner = Any())
        val at = MenuPlacement.anchorFor(corner, press, band = 264)
        assertEquals(IntRect(400, 1200 - 132, 400, 1200 + 132), at)
        val placed = MenuPlacement.offset(at, IntSize(560, 600), rtl = false, window)
        assertTrue(placed.y >= window.top, "inside the safe area")
        assertTrue(placed.y + 600 <= 1200 - 132 || placed.y >= 1200 + 132, "the finger's spot stays clear")
    }

    @Test
    fun `B-KIT-HOLD-003 a tap without a point and a press outside the anchor keep the old rule`() {
        val own = IntRect(0, 100, 48, 148)
        val tap = KitPress(IntRect(0, 100, 48, 148))
        assertEquals(own, MenuPlacement.anchorFor(own, tap, band = 264))
        val elsewhere = KitPress(IntRect(500, 900, 600, 1000), IntOffset(550, 950), owner = Any())
        assertEquals(own, MenuPlacement.anchorFor(own, elsewhere, band = 264))
        assertEquals(own, MenuPlacement.anchorFor(own, null, band = 264))
    }

    @Test
    fun `B-KIT-HOLD-004 a row held for its menu stays marked while the menu is open, and the menu is beside the finger`() =
        runDesktopComposeUiTest(400, 800) {
            val origin = KitPressOrigin()
            setContent {
                CompositionLocalProvider(LocalKitPressOrigin provides origin) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.height(500.dp))
                            KitRow(
                                "create a file g1.txt",
                                Modifier.testTag("row"),
                                menu = listOf(KitMenuItem("Pin conversation", {}), KitMenuItem("Rename", {})),
                                onClick = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            assertNull(origin.holding)
            val row = onNodeWithTag("row").fetchSemanticsNode().boundsInWindow
            onNodeWithTag("row").performTouchInput { longClick(Offset(300f, height / 2f)) }
            waitForIdle()
            assertNotNull(origin.holding, "the held row keeps its held face while its menu is open")
            val item = onNodeWithText("Pin conversation").fetchSemanticsNode().boundsInWindow
            val finger = row.left + 300f
            assertTrue(item.left >= finger - 1f || item.right <= finger + 1f, "the panel lies on one side of the finger ($item, finger $finger)")
            assertTrue(item.bottom <= row.top || item.top >= row.bottom, "the menu is beside the row, not over it ($item, row $row)")
            onNodeWithText("Pin conversation").performClick()
            waitForIdle()
            assertNull(origin.holding, "the mark goes when the menu closes")
        }

    @Test
    fun `B-KIT-HOLD-005 a held message records its press, so the anchor-less menu opens at it`() =
        runDesktopComposeUiTest(400, 800) {
            val origin = KitPressOrigin()
            setContent {
                CompositionLocalProvider(LocalKitPressOrigin provides origin) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        var open by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(80.dp).testTag("message").kitMenuHold("Message actions", { open = true })) {}
                            KitMenu(
                                expanded = open,
                                items = listOf(KitMenuItem("Copy", onSelect = { open = false })),
                                onDismissRequest = { open = false },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {}
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("message").performTouchInput { longClick(Offset(60f, 40f)) }
            waitForIdle()
            assertNotNull(origin.holding, "the message is marked while its menu is open")
            val item = onNodeWithText("Copy").fetchSemanticsNode().boundsInWindow
            assertTrue(item.top >= 80f - 1f, "the menu opens under the held message, not from the bottom corner ($item)")
            assertTrue(item.top <= 180f, "right under it ($item)")
        }
}
