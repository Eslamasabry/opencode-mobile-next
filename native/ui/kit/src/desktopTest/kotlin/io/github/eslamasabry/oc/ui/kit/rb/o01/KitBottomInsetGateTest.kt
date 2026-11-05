package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitBottomInsetAdd
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import kotlin.test.Test
import kotlin.test.assertEquals

/** KitBottomInset has no golden; the matrix renders it in every style, mode, scale and direction. */
class KitBottomInsetGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_bottom_inset", caps = mapOf("KitBottomInset" to "wrapper")) {
            KitBottomInsetAdd(extraBottom = 8.dp) {
                Box(Modifier.padding(bottom = kitBottomInset().bottom)) { KitText("Above the keyboard", role = TypeRole.Body) }
            }
        }
    }
}
