package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** E20-S10: Copy and More draw at one glyph size, so the outlined Copy no longer reads heavier than the three dots. */
internal const val REPLY_ACTION_GLYPH: Int = 20

/** The space between the 48 dp target's edge and its glyph: what the row shifts by to put the glyph on the text's edge. */
internal const val REPLY_ACTION_INSET: Int = (FlutterKitTokens.MIN_TARGET - REPLY_ACTION_GLYPH) / 2

/**
 * How far the row rises into the space above it: the glyph then sits one kit step (S1 under the reply's own S1 bottom
 * padding = S2) below the reply's last line instead of a whole target's inset lower.
 */
internal const val REPLY_ACTION_RISE: Int = REPLY_ACTION_INSET - Space.S1

/**
 * The reply footer's visible Copy and More affordances (Flutter kit_turn.dart:667-737), owner's craft pass (E20-S10):
 * one compact row on the reply's own edge — the start edge its text begins on, inside the reply column (700 dp cap,
 * S4 sides), the first glyph lined up with the text — Copy then More at the same glyph size and tone, their 48 dp
 * targets side by side with no extra gap, the row tucked one kit step under the last line. [metadata] (a failed or
 * stopped line, an end note) follows the actions on the same row.
 *
 * [arriving] (M-009): the turn has just ended in front of the person, so the row enters as Flutter's `KitEntrance`
 * does: it fades in and rises [FOOTER_RISE_DP] into place over STANDARD (250 ms) on Enter, paint only (the row is
 * laid out, tappable and read out from the first frame). Calm fades without the rise; reduced motion shows it at once.
 * A footer composed for a turn that ended earlier (history, scrolling back) passes false and is simply there.
 */
@Composable
public fun KitReplyFooter(
    copyLabel: String,
    moreLabel: String,
    onCopy: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    arriving: Boolean = false,
    metadata: @Composable () -> Unit = {},
) {
    Row(
        modifier.footerEntrance(arriving).fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Space.S4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onCopy != null || onMore != null)
            Row(Modifier.replyActionEdge().testTag("kit-reply-actions")) {
                if (onCopy != null)
                    KitIconButton(KitIcons.copy, copyLabel, onCopy, tone = TextTone.Secondary, size = REPLY_ACTION_GLYPH)
                if (onMore != null)
                    KitIconButton(KitIcons.more, moreLabel, onMore, tone = TextTone.Secondary, size = REPLY_ACTION_GLYPH)
            }
        Box(Modifier.weight(1f)) { metadata() }
    }
}

/**
 * Shifts the action row toward the start edge by the target's inset (mirrored under RTL by `placeRelative`) and up by
 * [REPLY_ACTION_RISE], and takes that much less room, so the glyphs line up with the reply text without shrinking a
 * target: the targets keep their full 48 dp and still take taps (nothing above clips them).
 */
private fun Modifier.replyActionEdge(): Modifier = layout { measurable, constraints ->
    val inset = REPLY_ACTION_INSET.dp.roundToPx()
    val rise = REPLY_ACTION_RISE.dp.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = (placeable.width - inset).coerceAtLeast(0)
    val height = (placeable.height - rise).coerceAtLeast(0)
    layout(width, height) { placeable.placeRelative(-inset, -rise) }
}

/** Flutter `KitEntrance.rise` (6 dp; native `MotionTokens.ENTRANCE_RISE_DP` still says 8, a `:ui:design` item). */
internal const val FOOTER_RISE_DP: Int = 6

/** The footer's look at entrance [progress] 0..1: its alpha and how far (dp) it still sits below its place. */
internal fun footerEntranceAt(progress: Float, travels: Boolean): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    return p to if (travels) FOOTER_RISE_DP * (1f - p) else 0f
}

@Composable
private fun Modifier.footerEntrance(arriving: Boolean): Modifier {
    val state = KitMotion.state
    if (!arriving || state.reduced) return this
    val specs = KitMotion.specs
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, specs.pace(KitPace.Standard)) }
    val travels = state.travels
    return this.graphicsLayer {
        val (alpha, below) = footerEntranceAt(progress.value, travels)
        this.alpha = alpha
        translationY = below.dp.toPx()
    }
}
