package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.kitReceiptSpan
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_receipt_golden_test.dart: every state under a card stub, and the span in rows. */
class KitReceiptGateTest {
    /** A card stub: what was written, and the receipt under it (`_card`). */
    private fun card(title: String, receipt: @Composable () -> Unit): @Composable () -> Unit = {
        KitSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column {
                KitText(title, role = TypeRole.RowTitle)
                Spacer(Modifier.height(8.dp))
                receipt()
            }
        }
    }

    private val sending = card("Use SQLite for the cache") { KitReceipt(KitReceiptState.Sending) }
    private val sent = card("Use SQLite for the cache") { KitReceipt(KitReceiptState.Sent, onTap = {}) }
    private val notConfirmed = card("Use SQLite for the cache") {
        KitReceipt(KitReceiptState.NotConfirmed, onRetry = {}, onTap = {})
    }
    private val confirmed = card("Allow the migration to run") {
        KitReceipt(KitReceiptState.Confirmed, label = "Allowed once", onUndo = {})
    }
    private val refused = card("Stop the run") { KitReceipt(KitReceiptState.Refused("the run already finished")) }
    private val answered = card("Allow the migration to run") { KitReceipt(KitReceiptState.AnsweredElsewhere("the laptop")) }
    private val automatic = card("The phone's server stopped answering") {
        KitReceipt(KitReceiptState.Confirmed, automatic = true, label = "Restarted the phone's server", at = "10:42 AM", onUndo = {})
    }
    private val spanInRows: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            listOf(
                "Use SQLite for the cache" to KitReceiptState.Sent,
                "Merge the parser refactor" to KitReceiptState.NotConfirmed,
                "Stop the run" to KitReceiptState.Refused("the run already finished"),
                "Allow the migration to run" to KitReceiptState.AnsweredElsewhere("the laptop"),
                "Approve the release notes" to KitReceiptState.Confirmed,
            ).forEach { (title, state) ->
                val span = kitReceiptSpan(state, label = if (state == KitReceiptState.Confirmed) "Approved" else null)
                KitRow(
                    title,
                    supporting =
                        buildAnnotatedString {
                            append(span)
                            append("2 min ago")
                        },
                )
            }
        }
    }

    @Test fun kit_receipt_sending() { FlutterGoldenGate.check("kit_receipt_sending", content = sending) }
    @Test fun kit_receipt_sent() { FlutterGoldenGate.check("kit_receipt_sent", content = sent) }
    @Test fun kit_receipt_not_confirmed() { FlutterGoldenGate.check("kit_receipt_not_confirmed", content = notConfirmed) }
    @Test fun kit_receipt_confirmed() { FlutterGoldenGate.check("kit_receipt_confirmed", content = confirmed) }
    @Test fun kit_receipt_refused() { FlutterGoldenGate.check("kit_receipt_refused", content = refused) }
    @Test fun kit_receipt_answered_elsewhere() { FlutterGoldenGate.check("kit_receipt_answered_elsewhere", content = answered) }
    @Test fun kit_receipt_automatic() { FlutterGoldenGate.check("kit_receipt_automatic", content = automatic) }
    @Test fun kit_receipt_span_in_rows() { FlutterGoldenGate.check("kit_receipt_span_in_rows", content = spanInRows) }
    @Test fun kit_receipt_confirmed_1280x800() { FlutterGoldenGate.check("kit_receipt_confirmed_1280x800", content = confirmed) }
    @Test fun kit_receipt_not_confirmed_text2() { FlutterGoldenGate.check("kit_receipt_not_confirmed_text2", content = notConfirmed) }
    @Test fun kit_receipt_not_confirmed_text2_1280x800() {
        FlutterGoldenGate.check("kit_receipt_not_confirmed_text2_1280x800", content = notConfirmed)
    }
    @Test fun kit_receipt_answered_elsewhere_text2() { FlutterGoldenGate.check("kit_receipt_answered_elsewhere_text2", content = answered) }
    @Test fun kit_receipt_answered_elsewhere_text2_1280x800() {
        FlutterGoldenGate.check("kit_receipt_answered_elsewhere_text2_1280x800", content = answered)
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_receipt", caps = mapOf("KitReceipt" to "reading"), content = confirmed) }
}
