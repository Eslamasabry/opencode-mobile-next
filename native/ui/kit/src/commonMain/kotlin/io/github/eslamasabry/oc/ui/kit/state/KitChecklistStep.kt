package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitStatusIcon
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

public enum class KitStepState {
    Waiting,
    Working,
    Done,
    Failed,
}

/**
 * Flutter KitChecklist's noninteractive step: one spoken state, a mark, and the step's own facts.
 */
@Composable
public fun KitChecklistStep(
    title: String,
    state: KitStepState,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    require(index in 1..total)
    val word =
        stringResource(
            when (state) {
                KitStepState.Waiting -> Res.string.kitMarkWaiting
                KitStepState.Working -> Res.string.kitMarkWorking
                KitStepState.Done -> Res.string.kitMarkDone
                KitStepState.Failed -> Res.string.kitMarkFailed
            }
        )
    val label =
        listOfNotNull(
                stringResource(Res.string.kitProgressStep, index, total),
                title,
                word,
                supporting,
            )
            .joinToString(", ")
    val tokens = LocalKitTokens.current
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .heightIn(
                min = (if (supporting == null) tokens.rowHeightOne else tokens.rowHeightTwo).dp
            )
            .padding(vertical = Space.S1.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Box(
            Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(state, animationSpec = KitMotion.specs.quick(), label = "step-mark") { shown
                ->
                StepMark(shown)
            }
        }
        Column(Modifier.weight(1f)) {
            KitText(
                title,
                role = TypeRole.RowTitle,
                maxLines = 2,
            ) // F: lib/ui/kit/kit_checklist.dart:554
            if (supporting != null) {
                Spacer(Modifier.height(Space.S1.dp))
                KitText(
                    supporting,
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines = 4,
                ) // F: lib/ui/kit/kit_checklist.dart:556
            }
        }
    }
}

@Composable
private fun StepMark(state: KitStepState) {
    val colors = LocalKitColors.current
    when (state) {
        KitStepState.Done -> KitStatusIcon(StatusTone.Ok)
        KitStepState.Failed -> KitStatusIcon(StatusTone.Failure)
        KitStepState.Waiting -> {
            val ring =
                if (
                    listOf(colors.ground, colors.surface1, colors.surface2, colors.surface3).all {
                        wcagContrast(Argb(colors.text3.toArgb()), Argb(it.toArgb())) >=
                            3 // F: lib/ui/kit/kit_status_mark.dart:83
                    }
                )
                    colors.text3
                else colors.text2
            val stroke = kitHairline()
            Canvas(Modifier.size(FlutterKitTokens.MARK_RING_SIZE.dp)) {
                drawCircle(
                    ring,
                    radius = (size.minDimension - stroke.toPx()) / 2,
                    style = Stroke(stroke.toPx()),
                ) // NV: centre the stroke inside its ring
            }
        }
        KitStepState.Working -> {
            if (!KitMotion.state.ambientLoops) {
                Canvas(Modifier.size(FlutterKitTokens.MARK_DOT_SIZE.dp)) {
                    drawCircle(colors.accent)
                }
            } else {
                val turn by
                    rememberInfiniteTransition(label = "step-working")
                        .animateFloat(
                            0f,
                            360f, // NV: a complete spinner revolution
                            infiniteRepeatable(
                                tween(MotionTokens.SPINNER_LAP, easing = LinearEasing)
                            ),
                            label = "step-turn",
                        )
                Canvas(Modifier.size(LocalKitTokens.current.smallIcon.dp)) {
                    val stroke = FlutterKitTokens.SPINNER_STROKE.dp.toPx()
                    rotate(turn) {
                        drawArc(
                            colors.accent,
                            0f,
                            270f,
                            false, // NV: indeterminate spinner arc
                            Offset(stroke / 2, stroke / 2),
                            Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke),
                        )
                    }
                }
            }
        }
    }
}
