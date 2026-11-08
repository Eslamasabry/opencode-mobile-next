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
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestAnsweredRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test

/** The answered scenes of test/goldens/kit/kit_request_card_golden_test.dart: the collapsed row between its two caption lines. */
class KitRequestCardGateTest {
    private val answered: @Composable () -> Unit = {
        Column {
            KitText("I found the failing test. Running it once more to check the fix.", role = TypeRole.Body, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            KitRequestAnsweredRow(KitIcons.permissions, "Run a shell command") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Success)
                        KitText("Allowed once", role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                    KitButton("Undo", onClick = {}, role = KitButtonRole.Tertiary)
                }
            }
            KitText("The next step waits for your answer.", role = TypeRole.Secondary, tone = TextTone.Secondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }

    private val redacted: @Composable (@Composable () -> Unit) -> Unit = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } }

    /** `_transcript(_permission())` (kit_request_card_golden_test.dart:42-100): the waiting ask between two lines. */
    @Composable
    private fun Waiting(disabled: Boolean = false) {
        Column {
            KitText("I found the failing test. Running it once more to check the fix.", role = TypeRole.Body, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            // kitRequestPlacement (kit_request_shape.dart:13-35): the gutter less the 4 dp ring, space1 above and below.
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                KitPermissionCard(
                    title = "Run a shell command",
                    summary = "To check the fix",
                    subject = "flutter test --concurrency=1 test/offline_queue_test.dart",
                    allowOnceLabel = "Run once",
                    alwaysLabel = null,
                    denyLabel = "Don't run",
                    onAllowOnce = {},
                    onAlways = null,
                    onDeny = {},
                    enabled = !disabled,
                    disabledReason = if (disabled) "Reconnect to the laptop to answer." else null,
                    onDetails = {},
                    needsYou = true,
                )
            }
            KitText("The next step waits for your answer.", role = TypeRole.Secondary, tone = TextTone.Secondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }

    @Test fun permission_waiting() { FlutterGoldenGate.check("kit_request_card_permission_waiting", wrap = redacted) { Waiting() } }
    @Test fun permission_waiting_wide() { FlutterGoldenGate.check("kit_request_card_permission_waiting_1280x800", wrap = redacted) { Waiting() } }
    @Test fun disabled() { FlutterGoldenGate.check("kit_request_card_disabled", wrap = redacted) { Waiting(disabled = true) } }

    @Test fun answered() { FlutterGoldenGate.check("kit_request_card_answered", content = answered) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_request_card", caps = mapOf("KitRequestCard" to "wrapper"), content = answered)
    }
}
