package io.github.eslamasabry.oc.ui.kitchat.rb.o11

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestAnsweredRow
import kotlin.test.Test

/** The answered scenes of test/goldens/kit/kit_request_card_golden_test.dart: the collapsed row between its two caption lines. */
class KitRequestCardGateTest {
    private val answered: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KitText("I found the failing test. Running it once more to check the fix.", role = TypeRole.Body)
            KitRequestAnsweredRow(KitIcons.permissions, "Run a shell command") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitReceipt(KitReceiptState.Confirmed)
                    KitButton("Undo", onClick = {}, role = KitButtonRole.Tertiary)
                }
            }
            KitText("The next step waits for your answer.", role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }

    @Test fun answered() = FlutterGoldenGate.check("kit_request_card_answered", content = answered)

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_request_card", caps = mapOf("KitRequestCard" to "wrapper"), content = answered)
    }
}
