@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.walkfix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.status.statusControlsStack
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-094: the "OpenCode Mobile was closed … running again" line keeps its words at full width. */
class KitStatusLineStackTest {
    private val exitWords =
        "OpenCode Mobile was closed 11/6/26, 3:10 PM. The server and its team stopped with it. " +
            "Open the app to start them running again."

    @Test
    fun `B-094 long status words take the full width and the buttons go under them`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    KitStatusLine(
                        KitStatus(
                            KitStatusKind.AppStopped,
                            exitWords,
                            id = "app-exit",
                            action = KitLineAction("Keep running", {}),
                            onDismiss = {},
                            dismissLabel = "Dismiss",
                        )
                    )
                }
            }
        }
        val line = onNodeWithTag("kit-status-line").getBoundsInRoot()
        val words = onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot()
        val action = onNodeWithTag("kit-status-action").getBoundsInRoot()
        val dismiss = onNodeWithTag("kit-status-dismiss").getBoundsInRoot()
        // Squeezed beside two buttons the words got about a third of the line; now they get all of it past the glyph.
        assertTrue(words.right - words.left > (line.right - line.left) * 0.75f, "words $words in line $line")
        assertTrue(action.top >= words.bottom, "action $action under words $words")
        assertTrue(dismiss.top >= words.bottom, "dismiss $dismiss under words $words")
    }

    @Test
    fun `E20-S08 the stacked buttons' words start on the message's edge`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    KitStatusLine(
                        KitStatus(
                            KitStatusKind.AppStopped,
                            exitWords,
                            id = "app-exit",
                            action = KitLineAction("Keep running", {}),
                            onDismiss = {},
                            dismissLabel = "Dismiss",
                        )
                    )
                }
            }
        }
        val words = onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot()
        val keep = onNodeWithText("Keep running", useUnmergedTree = true).getBoundsInRoot()
        // One edge for the words: the glyph's column, then the message and the buttons' words on the same x
        // (the buttons sat 12 dp further in, a third edge).
        assertTrue((keep.left - words.left).value.let { it > -1f && it < 1f }, "button words $keep, message $words")
    }

    @Test
    fun `B-094 a short status keeps its button on the words' line`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    KitStatusLine(
                        KitStatus(KitStatusKind.Connection, "Reconnecting", action = KitLineAction("Try again", {}))
                    )
                }
            }
        }
        val words = onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot()
        val action = onNodeWithTag("kit-status-action").getBoundsInRoot()
        assertTrue(action.left >= words.right - 1.dp, "action $action beside words $words")
    }

    @Test
    fun `B-094 the controls stack only when words and controls overflow one line`() {
        assertFalse(statusControlsStack(available = 400, lead = 30, message = 100, controls = listOf(80), gap = 12))
        assertTrue(statusControlsStack(available = 400, lead = 30, message = 300, controls = listOf(80, 70), gap = 12))
        assertFalse(statusControlsStack(available = 100, lead = 30, message = 900, controls = emptyList(), gap = 12))
    }
}

/** B-102: the transcript's last line rests above the composer's ground fade, not under it. */
class KitScreenChromeRoomTest {
    @Test
    fun `B-102 the body's bottom room covers the composer block and its fade`() = runComposeUiTest {
        var room = 0.dp
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                androidx.compose.foundation.layout.Box(Modifier.width(412.dp).height(800.dp)) {
                    io.github.eslamasabry.oc.ui.kit.frame.KitScreen(
                        overlayChrome = true,
                        page = true,
                        bottom = {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(80.dp))
                        },
                    ) {
                        room = io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding().calculateBottomPadding()
                    }
                }
            }
        }
        waitForIdle()
        val block = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
        val blockHeight = block.bottom - block.top
        // Before B-102 the room was exactly the block, so the fade's 16 dp sat on the last line.
        assertTrue(room >= blockHeight + 16.dp - 0.5.dp, "room $room for block $blockHeight")
    }
}
