package io.github.eslamasabry.oc.ui.kitchat.rb.o25

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import kotlin.test.Test

/**
 * test/goldens/kit/kit_frost_golden_test.dart `_scene(role: KitFrostRole.header)`: the header band, shown, over words
 * and a code block, 140 dp down inside the gutter (the shot `kit_frost_header`).
 */
class KitFrostHeaderGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() } }

    private val header: @Composable () -> Unit = {
        val state = rememberKitFrostState()
        Box(Modifier.padding(FlutterKitTokens.GUTTER.dp)) {
            // Scenery: what lies under the glass is not read.
            Column(Modifier.kitFrostSource(state).clearAndSetSemantics {}) {
                for (i in 0 until 4) {
                    KitText("The focus ring on the TV grid is cut off, line $i.", Modifier.padding(vertical = FlutterKitTokens.SPACE2.dp))
                }
                KitCodeBlock("- padding: EdgeInsets.all(12),\n+ padding: EdgeInsets.all(15),", KitCodeKind.Code)
                for (i in 4 until 8) {
                    KitText("The focus ring on the TV grid is cut off, line $i.", Modifier.padding(vertical = FlutterKitTokens.SPACE2.dp))
                }
            }
            Box(Modifier.offset(y = 140.dp).fillMaxWidth().kitFrost(FrostRole.ChatHeader, state, shown = true)) {
                KitText("Fix the TV focus ring", Modifier.padding(FlutterKitTokens.SPACE4.dp), role = TypeRole.Title)
            }
        }
    }

    @Test fun kit_frost_header() { FlutterGoldenGate.check("kit_frost_header", motion = MotionLevel.Full, wrap = wrap, content = header) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_frost_header", wrap = wrap, content = header) }
}
