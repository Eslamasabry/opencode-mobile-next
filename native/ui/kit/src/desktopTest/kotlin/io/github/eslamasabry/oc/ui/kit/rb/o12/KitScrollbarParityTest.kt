package io.github.eslamasabry.oc.ui.kit.rb.o12

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitOwnScrollbar
import io.github.eslamasabry.oc.ui.kit.frame.KitScrollArea
import io.github.eslamasabry.oc.ui.kit.frame.KitScrollbar
import io.github.eslamasabry.oc.ui.kit.frame.KitScrollbarRules
import io.github.eslamasabry.oc.ui.kit.frame.kitHasOwnScrollbar
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every rule of lib/ui/kit/kit_scrollbar.dart (part-kit_scrollbar, logic and wiring; the part has no golden). */
@OptIn(ExperimentalTestApi::class)
class KitScrollbarParityTest {
    @Test
    fun `B-KIT-120 a fine pointer always shows the thumb, touch only while scrolling, and never when the content fits`() {
        assertTrue(KitScrollbarRules.thumbShown(finePointer = true, scrolling = false, overflow = true))
        assertFalse(KitScrollbarRules.thumbShown(finePointer = false, scrolling = false, overflow = true))
        assertTrue(KitScrollbarRules.thumbShown(finePointer = false, scrolling = true, overflow = true))
        assertFalse(KitScrollbarRules.thumbShown(finePointer = true, scrolling = true, overflow = false))
    }

    @Test
    fun `B-KIT-120 only a fine pointer drags the thumb`() {
        assertTrue(KitScrollbarRules.draggable(finePointer = true, overflow = true))
        assertFalse(KitScrollbarRules.draggable(finePointer = false, overflow = true))
        assertFalse(KitScrollbarRules.draggable(finePointer = true, overflow = false))
    }

    @Test
    fun `B-KIT-120 the thumb is as long as the visible share of the content, never shorter than its minimum`() {
        assertEquals(400f, KitScrollbarRules.thumbLength(track = 400f, maxScroll = 0, minLength = 24f))
        assertEquals(200f, KitScrollbarRules.thumbLength(track = 400f, maxScroll = 400, minLength = 24f))
        assertEquals(24f, KitScrollbarRules.thumbLength(track = 400f, maxScroll = 100_000, minLength = 24f))
        assertEquals(0f, KitScrollbarRules.thumbOffset(track = 400f, thumb = 200f, value = 0, maxScroll = 400))
        assertEquals(200f, KitScrollbarRules.thumbOffset(track = 400f, thumb = 200f, value = 400, maxScroll = 400))
        assertEquals(200f, KitScrollbarRules.thumbOffset(track = 400f, thumb = 200f, value = 9_999, maxScroll = 400))
        // Dragging the thumb across its free track scrolls the whole hidden length.
        assertEquals(400f, KitScrollbarRules.scrollForDrag(delta = 200f, track = 400f, thumb = 200f, maxScroll = 400))
    }

    @Test
    fun `B-KIT-120 a scroll area gets its state only on a fine pointer`() =
        runDesktopComposeUiTest(200, 200) {
            var fine: Any? = Unit
            var touch: Any? = Unit
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    CompositionLocalProvider(LocalKitWindow provides LocalKitWindow.current.copy(finePointer = true)) {
                        KitScrollArea { fine = it }
                    }
                    CompositionLocalProvider(LocalKitWindow provides LocalKitWindow.current.copy(finePointer = false)) {
                        KitScrollArea { touch = it }
                    }
                }
            }
            waitForIdle()
            assertNotNull(fine)
            assertNull(touch)
        }

    @Test
    fun `B-KIT-120 the box draws its own thumb, marks its subtree, and offers a drag handle only on a fine pointer`() =
        runDesktopComposeUiTest(200, 200) {
            var inside = false
            var fine by mutableStateOf(true)
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    CompositionLocalProvider(LocalKitWindow provides LocalKitWindow.current.copy(finePointer = fine)) {
                        val state = rememberScrollState()
                        KitScrollbar(state, Modifier.size(200.dp)) {
                            Column(Modifier.verticalScroll(state)) {
                                inside = kitHasOwnScrollbar()
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1_000.dp))
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(inside, "KitOwnScrollbar wraps the box's content")
            onAllNodesWithTag("kit-scrollbar-thumb").assertCountEquals(1)
            fine = false
            waitForIdle()
            onAllNodesWithTag("kit-scrollbar-thumb").assertCountEquals(0)
        }

    @Test
    fun `B-KIT-120 own scrollbar marks only its subtree`() =
        runDesktopComposeUiTest(50, 50) {
            var outside = true
            var inside = false
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    outside = kitHasOwnScrollbar()
                    KitOwnScrollbar { inside = kitHasOwnScrollbar() }
                }
            }
            waitForIdle()
            assertFalse(outside)
            assertTrue(inside)
        }

    @Test
    fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_scrollbar",
            caps = mapOf("KitOwnScrollbar" to "wrapper", "KitScrollArea" to "wrapper", "KitScrollbar" to "wrapper"),
        ) {
            KitScrollArea { area ->
                val state = area ?: rememberScrollState()
                KitScrollbar(state, Modifier.size(200.dp)) {
                    Column(Modifier.verticalScroll(state)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(600.dp))
                    }
                }
            }
        }
    }
}
