package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import kotlin.test.Test

/** The default scene of test/goldens/kit/kit_notice_golden_test.dart: a titled notice with two notes, one action and a close. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitNoticeGateTest {
    private val scene: @Composable () -> Unit = {
        KitSurface(Modifier.padding(16.dp)) { // the golden harness's card around the notice
        KitNotice(
            message = "The provider said the key has expired. Paste a new one.",
            title = "The key was not accepted",
            notes = listOf("Nothing was saved", "The old key stays on the server until you replace it"),
            actions = listOf(KitLineAction("Paste a key", onClick = {})),
            dismiss = KitLineAction("Dismiss", onClick = {}),
        )
        }
    }

    @Test fun kit_notice_default() { FlutterGoldenGate.check("kit_notice_default", content = scene) }
    @Test fun kit_notice_default_360x800() { FlutterGoldenGate.check("kit_notice_default_360x800", content = scene) }
    @Test fun kit_notice_default_800x1280() { FlutterGoldenGate.check("kit_notice_default_800x1280", content = scene) }
    @Test fun kit_notice_default_915x412() { FlutterGoldenGate.check("kit_notice_default_915x412", content = scene) }
    @Test fun kit_notice_default_1280x800() { FlutterGoldenGate.check("kit_notice_default_1280x800", content = scene) }
    @Test fun kit_notice_default_1600x1000() { FlutterGoldenGate.check("kit_notice_default_1600x1000", content = scene) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_notice", caps = mapOf("KitNotice" to "list"), content = scene) }
}
