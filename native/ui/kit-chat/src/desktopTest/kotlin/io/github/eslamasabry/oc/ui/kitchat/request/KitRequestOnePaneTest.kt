package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreview
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14 (c20, c21; Glass and Solid): the permission card read as a box in a box (a 6 % attention ring round
 * the card, about 10 px out, on another radius), and its diff line's green band was a square-ended strip inside the
 * rounded preview with dark slivers above and below it. One card, one outline; the band takes its container's shape.
 */
@OptIn(ExperimentalTestApi::class)
class KitRequestOnePaneTest {
    @Test
    fun `B-CHAT-REQ-PANE-01 the waiting card is one pane that fills its slot, no ring round it`() = runComposeUiTest {
        setContent {
            ChatTestTheme(dark = true) {
                Box(Modifier.width(360.dp).testTag("slot")) {
                    KitPermissionCard(
                        title = "Create a1.txt?", summary = "The agent waits until you answer. Nothing is lost.", subject = "a1.txt",
                        allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                        onAllowOnce = {}, onAlways = null, onDeny = {},
                    )
                }
            }
        }
        val slot = onNodeWithTag("slot").getBoundsInRoot()
        val face = onNodeWithTag("kit-request-face", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(abs((face.left - slot.left).value) < .5f && abs((slot.right - face.right).value) < .5f, "the card's own outline is the slot's: $face in $slot")
    }

    @Test
    fun `B-CHAT-REQ-PANE-02 a one-line change's tint fills the preview to its rounded edge`() = runComposeUiTest {
        setContent {
            ChatTestTheme(dark = true) {
                Box(Modifier.width(360.dp)) {
                    KitPermissionCard(
                        title = "Create a1.txt?", summary = null, subject = "a1.txt",
                        allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                        onAllowOnce = {}, onAlways = null, onDeny = {},
                        change = KitRequestChange(KitDiffPreview.fromText("Hello from a1.txt"), "Show the change", null, {}),
                    )
                }
            }
        }
        val face = onNodeWithTag("kit-diff-preview-face", useUnmergedTree = true).getBoundsInRoot()
        val rows = onAllNodesWithTag("kit-diff-preview-row", useUnmergedTree = true)
        assertEquals(1, rows.fetchSemanticsNodes().size)
        val row = rows[0].getBoundsInRoot()
        // The added line's tinted row is the whole face: no strip of the face above or below it, none beside it.
        assertTrue(abs((row.top - face.top).value) < .5f && abs((face.bottom - row.bottom).value) < .5f, "row $row in face $face")
        assertTrue(abs((row.left - face.left).value) < .5f && abs((face.right - row.right).value) < .5f, "row $row in face $face")
    }
}
