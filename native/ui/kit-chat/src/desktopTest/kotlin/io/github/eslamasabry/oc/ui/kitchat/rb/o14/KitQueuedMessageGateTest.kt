package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kitchat.transcript.kitBubbleShape
import io.github.eslamasabry.oc.ui.kitchat.transcript.kitPromptBubbleFill
import kotlin.test.Test

/**
 * test/goldens/kit/kit_queued_message_golden_test.dart (every state at 412x915, the default — three waiting, Send now
 * + Edit — at 412x915 and 1280x800, and at 2.0 text) and test/goldens/kit/kit_queued_receipt_golden_test.dart (the
 * receipt states checking, uncertain, uncertain after a check, storage full at 412x915): the bubble at the end of a
 * short transcript above a composer-height inset (`_scene`: 16 dp gutter, the reply's words, 16 dp, the bubble,
 * 96 dp). Frame: `kitGalleryPart` ([FlutterGoldenGate.Frame.Part]).
 *
 * Native has no KitQueuedMessage (nothing in kit-chat draws queued sends; the composer tray and the steer box do
 * not), so [Bubble] stands in: the prompt bubble's fill and corners ([kitPromptBubbleFill], [kitBubbleShape]) at 85 %
 * of the column at the end side, padded 8/12/8/8, the head label, the items split by KitDivider (each 8 dp padded:
 * words in Body (2 lines), the attachment count and the state in Secondary), a receipt's KitDetailsFold, and the
 * actions as tertiary KitButtons in a flow 8 dp apart. Words are Flutter's ARB strings for each state.
 *
 * Mismatch: no native KitQueuedMessage / KitQueuedItem / KitQueuedState; the whole bubble is a stand-in.
 * Mismatch: state words carry no receipt colours (KitReceipt.span: sending, not confirmed, refused tones).
 * Mismatch: an item with a menu is a KitTappable on surface2 with the button shape in Flutter; native draws plain
 *   content (no tappable face, no Edit/Remove menu).
 * Mismatch: the bubble's hairline edge (kitPromptBubbleDecoration(edge: false)) is not drawn.
 * Mismatch: "Last checked 2:02 PM." is typed from DateTime(2026, 10, 2, 14, 2); no native part formats it.
 */
@OptIn(RedactorApi::class, ExperimentalLayoutApi::class)
class KitQueuedMessageGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        FlutterCapture { KitChatEnvironment(Redactor()).Provide { content() } }
    }

    /** One waiting message as the bubble lists it. */
    private class Item(val text: String, val state: String, val attachments: Int = 0, val details: Boolean = false)

    /** One action under the items. */
    private class Action(val label: String, val disabledReason: String? = null, val working: Boolean = false)

    private val details = listOf( // F: test/goldens/kit/kit_queued_receipt_golden_test.dart:29
        KitTechnicalValue("Sent", Redacted("2026-10-02T14:00:12.000")),
        KitTechnicalValue("Command ID", Redacted("queue:queued-1759413612000000")),
        KitTechnicalValue("Receipt ID", Redacted("msg_0a1b2c3d4e5fAbCdEfGhIjKlMn")),
    )

    /** `_scene`: the reply's words, the bubble, the composer's height. */
    @Composable private fun Scene(bubble: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { // F: test/goldens/kit/kit_queued_message_golden_test.dart:59
            KitText(
                "I added the cache interface and a first in-memory version. " +
                    "Tell me which store you want behind it.",
            )
            Spacer(Modifier.height(16.dp)) // F: test/goldens/kit/kit_queued_message_golden_test.dart:68
            bubble()
            Spacer(Modifier.height(96.dp)) // F: test/goldens/kit/kit_queued_message_golden_test.dart:70
        }
    }

    /** KitQueuedMessage stand-in (lib/ui/kit/chat/kit_queued_message.dart:184-276). */
    @Composable private fun Bubble(items: List<Item>, actions: List<Action> = emptyList()) {
        val colors = LocalKitColors.current
        val allWaiting = items.all { it.state == "Waiting to send" }
        val head = listOfNotNull("Waiting to send · ${items.size}", if (allWaiting) "Sends when you're back online" else null).joinToString(" · ")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.fillMaxWidth(0.85f) // F: lib/ui/kit/chat/kit_queued_message.dart:267 (KitLayout.bubbleMaxShare)
                    .background(kitPromptBubbleFill(colors), kitBubbleShape())
                    .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 8.dp), // F: lib/ui/kit/chat/kit_queued_message.dart:218-223
            ) {
                KitText(head, Modifier.padding(horizontal = 8.dp), role = TypeRole.Label) // F: lib/ui/kit/chat/kit_queued_message.dart:229
                Spacer(Modifier.height(8.dp)) // F: lib/ui/kit/chat/kit_queued_message.dart:235
                items.forEachIndexed { i, item ->
                    if (i > 0) KitDivider()
                    Column(Modifier.fillMaxWidth().padding(8.dp)) { // F: lib/ui/kit/chat/kit_queued_message.dart:322-324
                        if (item.text.isNotEmpty()) KitText(item.text, role = TypeRole.Body, tone = TextTone.Primary, maxLines = 2)
                        if (item.attachments > 0) KitText("${item.attachments} attachments", role = TypeRole.Secondary)
                        KitText(item.state, role = TypeRole.Secondary)
                    }
                    if (item.details) {
                        KitDetailsFold(
                            label = "Details",
                            copyLabel = "Copy all",
                            modifier = Modifier.padding(horizontal = 8.dp), // F: lib/ui/kit/chat/kit_queued_message.dart:385
                            values = details,
                            notes = listOf("The connection ended before we could confirm receipt."),
                        )
                    }
                }
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)) // F: lib/ui/kit/chat/kit_queued_message.dart:238
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), // F: lib/ui/kit/chat/kit_queued_message.dart:239
                        verticalArrangement = Arrangement.spacedBy(8.dp), // F: lib/ui/kit/chat/kit_queued_message.dart:240
                    ) {
                        actions.forEach { a ->
                            KitButton(
                                a.label,
                                {},
                                role = KitButtonRole.Tertiary,
                                enabled = if (a.disabledReason == null) KitEnabled.Enabled else KitEnabled.Disabled(a.disabledReason),
                                working = a.working,
                            )
                        }
                    }
                }
            }
        }
    }

    private val waiting = listOf(
        Item("Use SQLite for the cache", "Waiting to send"),
        Item(
            "Then move the settings migration behind a flag so older builds keep " +
                "reading the old file",
            "Waiting to send",
            attachments = 2,
        ),
        Item("And run the tests", "Waiting to send"),
    )

    @Composable private fun Default() = Scene { Bubble(waiting, listOf(Action("Send now"), Action("Edit"))) }

    @Composable private fun Offline() = Scene { Bubble(waiting, listOf(Action("Send now", disabledReason = "You're offline"))) }

    @Composable private fun Sending() = Scene {
        Bubble(listOf(Item("Use SQLite for the cache", "Sending…"), Item("And run the tests", "Waiting to send")))
    }

    @Composable private fun NotConfirmed() = Scene {
        Bubble(listOf(Item("Use SQLite for the cache", "Not confirmed yet")), listOf(Action("Try again")))
    }

    @Composable private fun Failed() = Scene {
        Bubble(
            listOf(Item("Use SQLite for the cache", "Not accepted: the server is out of disk space")),
            listOf(Action("Try again"), Action("Edit")),
        )
    }

    @Composable private fun AfterReply() = Scene {
        Bubble(
            listOf(
                Item("Use SQLite for the cache", "Sends after this reply"),
                Item("Keep the old file as a fallback", "Adds to this turn"),
            ),
            listOf(Action("Add to this turn")),
        )
    }

    @Composable private fun Mixed() = Scene {
        Bubble(
            listOf(
                Item("Use SQLite for the cache", "Reached the server"),
                Item("And run the tests", "Not confirmed yet"),
                Item("Then open a pull request", "Not accepted: the session was closed"),
                Item("", "Update waiting"),
            ),
            listOf(Action("Try again")),
        )
    }

    @Composable private fun ReceiptChecking() = Scene {
        Bubble(
            listOf(Item("Use SQLite for the cache", "Checking whether your message arrived…", details = true)),
            listOf(Action("Try again", disabledReason = "Try again", working = true)),
        )
    }

    @Composable private fun ReceiptUncertain() = Scene {
        Bubble(listOf(Item("Use SQLite for the cache", "We couldn't confirm your message arrived.", details = true)), listOf(Action("Try again")))
    }

    @Composable private fun ReceiptUncertainChecked() = Scene {
        Bubble(
            listOf(Item("Use SQLite for the cache", "We couldn't confirm your message arrived. Last checked 2:02 PM.", details = true)),
            listOf(Action("Try again")),
        )
    }

    @Composable private fun ReceiptStorageFull() = Scene {
        Bubble(
            listOf(
                Item(
                    "Use SQLite for the cache",
                    "This phone can't safely record sends right now, so your message wasn't sent. It is kept here.",
                ),
            ),
        )
    }

    @Test fun kit_queued_message_default() {
        FlutterGoldenGate.check("kit_queued_message_default", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_queued_message_default_1280x800", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_queued_message_default_text2", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_queued_message_default_text2_1280x800", wrap = wrap) { Default() }
    }

    @Test fun kit_queued_message_states() {
        FlutterGoldenGate.check("kit_queued_message_offline", wrap = wrap) { Offline() }
        FlutterGoldenGate.check("kit_queued_message_sending", wrap = wrap) { Sending() }
        FlutterGoldenGate.check("kit_queued_message_not_confirmed", wrap = wrap) { NotConfirmed() }
        FlutterGoldenGate.check("kit_queued_message_failed", wrap = wrap) { Failed() }
        FlutterGoldenGate.check("kit_queued_message_after_reply", wrap = wrap) { AfterReply() }
        FlutterGoldenGate.check("kit_queued_message_mixed", wrap = wrap) { Mixed() }
    }

    @Test fun kit_queued_receipt_states() {
        FlutterGoldenGate.check("kit_queued_receipt_checking", wrap = wrap) { ReceiptChecking() }
        FlutterGoldenGate.check("kit_queued_receipt_uncertain", wrap = wrap) { ReceiptUncertain() }
        FlutterGoldenGate.check("kit_queued_receipt_uncertain_checked", wrap = wrap) { ReceiptUncertainChecked() }
        FlutterGoldenGate.check("kit_queued_receipt_storage_full", wrap = wrap) { ReceiptStorageFull() }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_queued_message", caps = mapOf("KitQueuedMessage" to "conversation"), wrap = wrap) { Default() }
    }
}
