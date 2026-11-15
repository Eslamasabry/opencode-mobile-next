package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SEND-STALE-5 (owner 2026-11-15, a message that was not sent): under the failed bubble the "Details" label, its box and
 * Try again started at the screen's edge (x = 0) while the sentences above them kept the page gutter. Every piece of a
 * failed send stands on the sentences' own column.
 */
@OptIn(ExperimentalTestApi::class)
class KitSendFailedColumnTest {
    @Test
    fun `SEND-STALE-5 the details fold and the action start on the sentences' column, never at the screen's edge`() {
        val lefts = HashMap<String, Dp>()
        runDesktopComposeUiTest(400, 400) {
            setContent {
                ChatTestTheme {
                    Box(Modifier.width(360.dp)) {
                        KitSendFailed(
                            lines = listOf("Your message wasn't sent.", "The connection changed while sending."),
                            actionLabel = "Try again",
                            onAction = {},
                            details = KitSendFailedDetails("Details", "Copy details", "Refused · StaleScope"),
                        )
                    }
                }
            }
            waitForIdle()
            lefts["first"] = onNodeWithText("Your message wasn't sent.").getUnclippedBoundsInRoot().left
            lefts["second"] = onNodeWithText("The connection changed while sending.").getUnclippedBoundsInRoot().left
            lefts["details"] = onNodeWithTag("kit-send-failed-details").getUnclippedBoundsInRoot().left
            lefts["label"] = onNodeWithText("Details", useUnmergedTree = true).getUnclippedBoundsInRoot().left
            lefts["action"] = onNodeWithTag("kit-send-failed-action").getUnclippedBoundsInRoot().left
            lefts["actionWords"] = onNodeWithText("Try again", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        }
        val column = lefts.getValue("first")
        assertTrue(column >= 16.dp, "the sentences keep the page gutter ($lefts)")
        assertEquals(column, lefts.getValue("second"), "both sentences on one column ($lefts)")
        assertEquals(column, lefts.getValue("details"), "the Details box on the sentences' column ($lefts)")
        assertTrue(lefts.getValue("label") >= column, "the Details label never left of the sentences ($lefts)")
        // A quiet button bleeds its touch frame past the column (KitButton kitBleed) so its words sit on the edge.
        assertTrue(lefts.getValue("action") > 0.dp, "Try again's frame never starts at the screen's edge ($lefts)")
        assertEquals(column, lefts.getValue("actionWords"), "Try again's words on the sentences' column ($lefts)")
    }
}
