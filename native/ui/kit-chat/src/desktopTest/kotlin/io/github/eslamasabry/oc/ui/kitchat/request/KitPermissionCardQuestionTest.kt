package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C-007, the approval card in a conversation (Flutter `KitRequestLook.question`, lib/ui/kit/kit_request_question.dart):
 * "● Needs you · 3 s" inside the card, the 4 dp attention edge, the ask at the title size, the command block with
 * Details, Reject / Allow once and the underlined "Always allow flutter" link.
 */
@OptIn(ExperimentalTestApi::class)
class KitPermissionCardQuestionTest {
    private val title = "Run flutter test test/chat_test.dart in project?"
    private val command = "flutter test test/chat_test.dart"

    @Test
    fun `B-CHAT-123 the question look holds its needs-you tag, command block, Details and always-allow link`() =
        runComposeUiTest {
            var once = 0
            var always = 0
            var reject = 0
            var details = 0
            setContent {
                ChatTestTheme(dark = true) {
                    KitPermissionCard(
                        title = title, summary = null, subject = null,
                        targets = listOf(KitRequestTarget(command)),
                        allowOnceLabel = "Allow once", alwaysLabel = "Always allow flutter", denyLabel = "Reject",
                        onAllowOnce = { once++ }, onAlways = { always++ }, onDeny = { reject++ },
                        copyLabel = "Copy", copiedLabel = "Copied",
                        look = KitRequestLook.Question, waitingAge = "3 s", onDetails = { details++ },
                    )
                }
            }
            onNodeWithText("Needs you · 3 s", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("kit-request-needs-you-dot", useUnmergedTree = true).assertExists()
            onNodeWithText(command, useUnmergedTree = true).assertIsDisplayed()
            // The command block has no copy mark of its own in this look: Details holds the request.
            onNode(hasText("Copy"), useUnmergedTree = true).assertDoesNotExist()
            val tag = onNodeWithText("Needs you · 3 s", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val ask = onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(tag.bottom <= ask.top, "the tag sits inside the card, above the ask: $tag vs $ask")

            onNodeWithTag("kit-request-details").performClick()
            onNodeWithText("Allow once").performClick()
            onNodeWithText("Reject").performClick()
            onNodeWithTag("permission-card-always").performClick()
            assertEquals(listOf(1, 1, 1, 1), listOf(details, once, reject, always))
        }

    @Test
    fun `B-CHAT-123 the ask reads at the title size, and the headline from 1_3x text`() {
        assertEquals(TypeRole.Title, questionTitleRole(1f))
        assertEquals(TypeRole.Headline, questionTitleRole(1.3f))
    }

    @Test
    fun `B-CHAT-123 the question card carries a 4 dp attention edge at its start`() =
        runComposeUiTest {
            var attention = androidx.compose.ui.graphics.Color.Unspecified
            setContent {
                ChatTestTheme(dark = true) {
                    attention = LocalKitColors.current.attention
                    KitPermissionCard(
                        title = title, summary = null, subject = command,
                        allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Reject",
                        onAllowOnce = {}, onAlways = null, onDeny = {},
                        look = KitRequestLook.Question,
                    )
                }
            }
            val ask = onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val image = onRoot().captureToImage()
            val pixels = image.toPixelMap()
            val root = onRoot().getUnclippedBoundsInRoot()
            val scale = image.width / (root.right - root.left).value
            // The words start 16 dp + the 4 dp edge in; 2 dp in from the card's start is the edge.
            val x = ((ask.left - 18.dp).value * scale).toInt()
            val y = (((ask.top + ask.bottom) / 2).value * scale).toInt()
            val c = pixels[x, y]
            assertTrue(
                abs(c.red - attention.red) < 0.08f && abs(c.green - attention.green) < 0.08f && abs(c.blue - attention.blue) < 0.08f,
                "the edge is the attention colour: $c vs $attention",
            )
        }
}
