package io.github.eslamasabry.oc.ui.kit.rb.o11

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/** test/goldens/kit/kit_dialog_golden_test.dart: the alert state. */
class showKitAlertGateTest {
    @Test fun kit_dialog_alert() {
        FlutterGoldenGate.check("kit_dialog_alert", Frame.Screen) {
            KitSheetHost {
                KitAlert(
                    visible = true,
                    title = "Could not add file",
                    onDismissRequest = {},
                    body = "The file is larger than the server accepts. Share a smaller file or a link to it.",
                    icon = KitIcons.info,
                )
            }
        }
    }
}
