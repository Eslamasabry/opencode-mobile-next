package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.scene.KitDraw
import io.github.eslamasabry.oc.ui.kit.scene.KitIllustration
import io.github.eslamasabry.oc.ui.kit.scene.KitScene
import io.github.eslamasabry.oc.ui.kit.scene.KitSceneFrame
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonTranscript
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import kotlin.test.Test

/** The matrix rows of the order's parts without a Flutter golden: illustration, transcript skeleton, task mark. */
class KitControlsCGateTest {
    /** A ring with the accent wash: enough of a scene to prove the frame, the scale and the palette. */
    private object Ring : KitScene {
        override fun DrawScope.paint(frame: KitSceneFrame) {
            drawCircle(KitDraw.wash(frame.palette), radius = 40f, center = Offset(60f, 60f))
            drawCircle(frame.palette.accent, radius = 40f * frame.entrance, center = Offset(60f, 60f), style = KitDraw.pen())
        }
    }

    @Test fun illustration() {
        FlutterGoldenGate.matrix("part-kit_illustration", caps = mapOf("KitIllustration" to "reading")) { KitIllustration(Ring) }
    }

    @Test fun skeletonTranscript() {
        FlutterGoldenGate.matrix("part-kit_skeleton_transcript", caps = mapOf("KitSkeletonTranscript" to "conversation")) {
            KitSkeletonTranscript(Modifier.height(400.dp))
        }
    }

    @Test fun taskMark() {
        FlutterGoldenGate.matrix("part-kit_task_mark", caps = mapOf("KitTaskMark" to "glyph")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KitTaskState.entries.forEach { KitTaskMark(it) }
                KitTaskMark(KitTaskState.Working, paused = true, showLabel = true)
            }
        }
    }
}
