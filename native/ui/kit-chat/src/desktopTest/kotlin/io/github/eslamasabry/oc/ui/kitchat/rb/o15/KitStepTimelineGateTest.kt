package io.github.eslamasabry.oc.ui.kitchat.rb.o15

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepTimeline
import kotlin.test.Test

/** Scenes of test/goldens/kit/kit_step_timeline_*: a sentence, the work line, a sentence, 16 dp from the edge. */
@OptIn(ExperimentalTestApi::class)
class KitStepTimelineGateTest {
    private fun scene(expanded: Boolean): @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp)) { // F: page gutter space4
            KitText("I will add a home screen and check it with a test.", role = TypeRole.Body, tone = TextTone.Primary)
            KitStepTimeline(
                label = "Read 1 file · searched once · edited 1 file · ran 1 command",
                icon = KitIcons.editNote,
                expanded = expanded,
                onPressed = {},
            )
            KitText("The screen is in and the route points to it.", role = TypeRole.Body, tone = TextTone.Primary)
        }
    }

    @Test fun collapsed() {
        FlutterGoldenGate.check("kit_step_timeline_collapsed", frame = FlutterGoldenGate.Frame.Screen, motion = MotionLevel.Off, content = scene(false))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_timeline", frame = FlutterGoldenGate.Frame.Screen, content = scene(false))
    }
}
