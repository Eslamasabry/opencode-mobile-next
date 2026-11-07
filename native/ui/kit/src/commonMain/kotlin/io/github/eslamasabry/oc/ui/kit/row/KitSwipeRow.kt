package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitLeaving
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** How far a swipe must travel (share of the row's width) before letting go does the action. */
private const val SWIPE_COMMIT = 0.4f

/**
 * A row that does one action when swiped toward the end of the line (right in left-to-right, left in right-to-left),
 * with the action's word and glyph revealed beneath it. Letting go early springs back; past the threshold the row
 * slides away, folds shut, and then [onAct] runs. The same action is a custom accessibility action, so TalkBack and
 * Switch Access never need the gesture. The row is not removed here: the caller drops it from its list (or brings it
 * back if the act fails), which also resets this part.
 *
 * States: resting, dragging, committed
 * Text: frame (the action word)
 * Motion: 150 ms settle, 150 ms fold; Off = instant
 * Replaces: Flutter Dismissible on the Conversations rows
 */
@Composable
public fun KitSwipeRow(
    actionLabel: String,
    onAct: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = KitIcons.archive,
    content: @Composable () -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    var drag by remember { mutableFloatStateOf(0f) }
    var fold by remember { mutableFloatStateOf(1f) }
    var width by remember { mutableIntStateOf(0) }
    var committed by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(onAct)
    val state = rememberDraggableState { delta ->
        if (committed == 0f) drag = (drag + delta * sign).coerceIn(0f, width.toFloat())
    }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * fold).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics(mergeDescendants = true) {
                if (enabled) customActions = listOf(CustomAccessibilityAction(actionLabel) { act(); true })
            },
    ) {
        if (drag > 0f) {
            val reveal = (drag / (width.coerceAtLeast(1) * 0.2f)).coerceIn(0f, 1f)
            Row(
                Modifier.matchParentSize()
                    .background(colors.surface3)
                    .padding(horizontal = kitRowGutter())
                    .alpha(reveal)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.Start),
            ) {
                Image(icon, null, Modifier.size(tokens.smallIcon.dp), colorFilter = ColorFilter.tint(colors.text1))
                KitText(actionLabel, role = TypeRole.Label, maxLines = 1)
            }
        }
        Box(
            Modifier.heightIn(min = 48.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(IntOffset(drag.roundToInt(), 0))
                    }
                }
                // E20-S24: the face is filled only while it moves, to cover the action beneath. At rest the row lies
                // on the page itself, so a list of swipe rows reads as one surface (an opaque face drew a darker
                // band per row over a live background or a styled ground).
                .then(if (drag > 0f || committed > 0f) Modifier.background(colors.ground) else Modifier)
                .kitLeaving(committed > 0f)
                .draggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && committed == 0f,
                    reverseDirection = false,
                    onDragStopped = {
                        scope.launch {
                            if (drag >= width * SWIPE_COMMIT && width > 0) {
                                committed = 1f
                                animate(drag, width.toFloat(), animationSpec = specs.quick()) { v, _ -> drag = v }
                                animate(1f, 0f, animationSpec = specs.quick()) { v, _ -> fold = v }
                                act()
                            } else {
                                animate(drag, 0f, animationSpec = specs.quick()) { v, _ -> drag = v }
                            }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
