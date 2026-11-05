package io.github.eslamasabry.oc.ui.kit.rb.o15

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitSenseAction
import io.github.eslamasabry.oc.ui.kit.input.KitSenseAsk
import io.github.eslamasabry.oc.ui.kit.input.KitSenseItem
import io.github.eslamasabry.oc.ui.kit.input.KitSenseKind
import kotlin.test.Test

/** The default scenes of test/goldens/kit/kit_sense_ask_golden_test.dart: one photo picked of two. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitSenseAskGateTest {
    private val ask: @Composable () -> Unit = {
        Box(Modifier.padding(horizontal = 16.dp)) {
            KitSenseAsk(
                kind = KitSenseKind.Photo,
                purpose = "Show me the screen where the error appears.",
                actions = listOf(KitSenseAction("Take photo", {}), KitSenseAction("Choose photo", {})),
                items = listOf(KitSenseItem("p1", "IMG_0001.jpg", "Remove IMG_0001.jpg", "2.1 MB")),
                sendLabel = "Send photos",
                onSend = {},
                max = 2,
                countLabel = "1 of 2",
                maxReachedLabel = "That is the most it can take",
                sendDisabledReason = "Add a photo first",
                onRemove = {},
            )
        }
    }

    @Test fun kit_sense_ask_default() { FlutterGoldenGate.check("kit_sense_ask_default", content = ask) }

    @Test fun kit_sense_ask_default_1280x800() { FlutterGoldenGate.check("kit_sense_ask_default_1280x800", content = ask) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_sense_ask", caps = mapOf("KitSenseAsk" to "conversation"), content = ask) }
}
