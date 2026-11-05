package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt

public enum class KitTaskItemState {
    Todo,
    InProgress,
    Done,
    Cancelled,
}

@Immutable
public data class KitTaskItem(val text: String, val state: KitTaskItemState, val label: String)

/** Flutter kit_task_sheet.dart's content: marks and unrestricted task text, separated by rules. */
@Composable
public fun KitTaskLines(
    items: List<KitTaskItem>,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) KitDivider()
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = Space.S1.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("kit-task-line-$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskMark(item, running)
                Spacer(Modifier.width(Space.S2.dp))
                KitText(
                    item.text,
                    Modifier.weight(1f),
                    role =
                        if (item.state == KitTaskItemState.InProgress) TypeRole.Button
                        else TypeRole.Body,
                    tone =
                        if (
                            item.state == KitTaskItemState.Done ||
                                item.state == KitTaskItemState.Cancelled
                        )
                            TextTone.Secondary
                        else TextTone.Primary,
                )
            }
        }
    }
}

@Composable
private fun TaskMark(item: KitTaskItem, running: Boolean) {
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val tokens = LocalKitTokens.current
    val motion = KitMotion.state
    val hairline = kitHairline()
    val ratio = kitPixelRatio()
    val glyphSize =
        ((tokens.smallIcon * minOf(kitTextScale(), tokens.maxIconScale) * ratio).roundToInt() /
                ratio)
            .dp
    val ring =
        if (
            listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3).all {
                wcagContrast(roles.text3, it) >= 3 // F: lib/ui/kit/kit_status_mark.dart:78
            }
        )
            colors.text3
        else colors.text2
    val drawn =
        if (item.state == KitTaskItemState.InProgress && !running) KitTaskItemState.Todo
        else item.state
    Box(
        Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp)
            .clearAndSetSemantics { contentDescription = item.label }
            .testTag("kit-task-mark-${drawn.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(drawn, animationSpec = KitMotion.specs.quick(), label = "kit-task-mark") { state
            ->
            Box(
                Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    KitTaskItemState.Todo ->
                        Canvas(
                            Modifier.size(FlutterKitTokens.MARK_RING_SIZE.dp)
                                .testTag("kit-task-waiting")
                        ) {
                            drawCircle(
                                ring,
                                radius = (size.minDimension - hairline.toPx()) / 2,
                                style = Stroke(hairline.toPx()),
                            ) // NV: inset the centered stroke by half its width
                        }
                    KitTaskItemState.InProgress ->
                        if (!motion.ambientLoops) {
                            Canvas(
                                Modifier.size(FlutterKitTokens.MARK_DOT_SIZE.dp)
                                    .testTag("kit-task-working-still")
                            ) {
                                drawCircle(colors.accent)
                            }
                        } else
                            KitWorkingMark(
                                colors.accent,
                                glyphSize,
                                Modifier.testTag("kit-task-working"),
                                strokeWidth = FlutterKitTokens.SPINNER_STROKE.dp,
                            )
                    KitTaskItemState.Done ->
                        KitIcon(
                            KitIcons.check,
                            size = KitIconSize.Small,
                            tone = TextTone.Success,
                            growsWithText = true,
                        )
                    KitTaskItemState.Cancelled ->
                        KitIcon(
                            KitIcons.stopCircle,
                            size = KitIconSize.Small,
                            tone = TextTone.Secondary,
                            growsWithText = true,
                        )
                }
            }
        }
    }
}
