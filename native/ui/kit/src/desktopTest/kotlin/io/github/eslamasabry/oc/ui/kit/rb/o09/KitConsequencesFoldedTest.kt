package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitMarkedConsequences
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * Verification only (part-kit_consequences is folded into order 12's KitConfirm.kt): test/goldens/kit/
 * kit_consequences_golden_test.dart `_open`, the panel 20 dp from the window's edges, against the owner's native part.
 */
class KitConsequencesFoldedTest {
    private val items = listOf(
        KitConsequence("3 queued prompts will be deleted", KitConsequenceMark.Lost),
        KitConsequence("The exported transcript is kept", KitConsequenceMark.Kept),
        KitConsequence("This runs in the background", KitConsequenceMark.Info),
    )

    private fun shot(name: String) {
        FlutterGoldenGate.check(name, Frame.Screen, wrap = { FlutterCapture(it) }) {
            Box(Modifier.padding(20.dp)) { KitMarkedConsequences(items) }
        }
    }

    @Test fun kit_consequences_default() { shot("kit_consequences_default") }
    @Test fun kit_consequences_default_1280x800() { shot("kit_consequences_default_1280x800") }
    @Test fun kit_consequences_default_text2() { shot("kit_consequences_default_text2") }
    @Test fun kit_consequences_default_text2_1280x800() { shot("kit_consequences_default_text2_1280x800") }
}
