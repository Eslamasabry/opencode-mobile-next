package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-106: home's "OpenCode Mobile was closed …" line stood about 200 dp tall, pinned under the bar while the list
 * scrolled under it. Flutter pins the line too, but holds it to 40 % of the room under the bar and scrolls its words
 * beyond that; the native line had no cap.
 */
@OptIn(ExperimentalTestApi::class)
class KitScreenStatusRoomTest {
    @Test
    fun `B-KIT-106 the status line may take at most 40 percent of the room under the bar`() {
        assertEquals(400, statusRoomCap(1000))
        assertEquals(0, statusRoomCap(0))
        assertEquals(Constraints.Infinity, statusRoomCap(Constraints.Infinity))
    }

    @Test
    fun `B-KIT-106 a long status line is held to its share and the list keeps the rest`() =
        runDesktopComposeUiTest(360, 480) {
            val long =
                List(12) { "OpenCode Mobile was closed at 3:10 PM. Your phone's OpenCode stopped with it." }
                    .joinToString(" ")
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(
                        topBar = { KitTopBar("Conversations") },
                        status =
                            KitStatus(
                                KitStatusKind.AppStopped,
                                long,
                                id = "app-exit",
                                action = KitLineAction("Keep it running", {}),
                            ),
                    ) {
                        LazyColumn { items(40) { KitText("Row $it") } }
                    }
                }
            }
            waitForIdle()
            val screen = onNodeWithTag("kit-screen").getBoundsInRoot()
            val line = onNodeWithTag("kit-screen-status").getBoundsInRoot()
            val room = screen.bottom - line.top
            val height = line.bottom - line.top
            assertTrue(height.value > 0f, "the line shows")
            assertTrue(height.value <= room.value * 0.4f + 1f, "line $height of room $room")
        }
}
