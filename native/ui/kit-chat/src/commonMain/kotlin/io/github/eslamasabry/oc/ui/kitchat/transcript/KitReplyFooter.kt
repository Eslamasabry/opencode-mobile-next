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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** The footer's Copy and More glyphs (Flutter `AppIconography.actionSize`, app_iconography.dart:20). */
internal const val REPLY_ACTION_GLYPH: Int = 24

/** The space between the 48 dp target's edge and its glyph's box. */
internal const val REPLY_ACTION_INSET: Int = (FlutterKitTokens.MIN_TARGET - REPLY_ACTION_GLYPH) / 2

/**
 * How far each glyph's ink stands in from the end of its box, as a share of its size (Flutter `KitOptical._ink`,
 * kit_optical.dart:25-33, measured on the same Phosphor paths: dots-three .1875, copy .125).
 */
internal const val MORE_INK_END: Float = 0.1875f
internal const val COPY_INK_END: Float = 0.125f

/**
 * How far the actions move toward the end edge (Flutter `_footerRow` reach, kit_turn.dart:676-684): the last glyph's
 * ink then ends on the turn's edge, where the prompt bubble's does, not inside its 48 dp target.
 */
internal fun replyActionReach(more: Boolean): Float =
    REPLY_ACTION_INSET + REPLY_ACTION_GLYPH * if (more) MORE_INK_END else COPY_INK_END

/** At this text scale and above the meta words move above the actions (Flutter `_kWrapTextScale`). */
internal const val FOOTER_WRAP_TEXT_SCALE: Float = 1.3f

/**
 * The reply footer (Flutter `KitTurn._footerRow`, kit_turn.dart:667-737; C-003): one row in the reply column (700 dp
 * cap, S4 sides). Copy then More sit at the **end**, 24 dp glyphs in 48 dp targets 8 dp apart, moved toward the edge
 * so the last glyph's ink ends where the prompt bubble's edge does. [metadata] fills the start of the line: a stopped
 * reply's [KitReplyNote], then the newest turn's [KitReplyMeta] ("7:01 PM · model · 150 tok"), then any other line
 * (an end note). From 1.3× text that start column moves above the actions, which stay at the end.
 *
 * Inside a [KitTurn] the row keeps no padding of its own: the turn puts it 4 dp under the reply
 * ([KitTurnNext.Footer]). Outside one, the reply block's own 4 dp does the same.
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
    val stacked = LocalDensity.current.fontScale >= FOOTER_WRAP_TEXT_SCALE
    val frame = modifier.footerEntrance(arriving).fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Space.S4.dp)
    val hasActions = onCopy != null || onMore != null
    if (stacked) {
        Column(frame) {
            metadata()
            if (hasActions)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    ReplyActions(copyLabel, moreLabel, onCopy, onMore)
                }
        }
    } else {
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { metadata() }
            if (hasActions) {
                Spacer(Modifier.width(Space.S2.dp))
                ReplyActions(copyLabel, moreLabel, onCopy, onMore)
            }
        }
    }
}

/**
 * The newest turn's meta words at the start of its footer ("7:01 PM · Parity model · 150 tok"; Flutter
 * kit_turn.dart:719-721): Caption, tertiary. Goes in [KitReplyFooter]'s `metadata`.
 */
@Composable
public fun KitReplyMeta(text: String, modifier: Modifier = Modifier) {
    KitText(text, modifier.testTag(REPLY_META_TAG), role = TypeRole.Caption, tone = TextTone.Tertiary)
}

/**
 * A stopped reply's note leading its footer line ("This reply was stopped before it finished."; Flutter
 * kit_turn.dart:536-546, :697-712): Secondary words, the meta under it, Copy and More at the line's end. Goes in
 * [KitReplyFooter]'s `metadata`, before [KitReplyMeta].
 */
@Composable
public fun KitReplyNote(text: String, modifier: Modifier = Modifier) {
    KitText(text, modifier, role = TypeRole.Secondary, tone = TextTone.Secondary)
}

public const val REPLY_META_TAG: String = "kit-reply-meta"

@Composable
private fun ReplyActions(copyLabel: String, moreLabel: String, onCopy: (() -> Unit)?, onMore: (() -> Unit)?) {
    // Placed (painted and hit-tested) toward the end; the row's layout keeps its width, as Flutter's translate.
    Row(Modifier.offset(x = replyActionReach(more = onMore != null).dp).testTag("kit-reply-actions")) {
        if (onCopy != null)
            KitIconButton(KitIcons.copy, copyLabel, onCopy, tone = TextTone.Secondary, size = REPLY_ACTION_GLYPH)
        if (onCopy != null && onMore != null) Spacer(Modifier.width(Space.S2.dp))
        if (onMore != null)
            KitIconButton(KitIcons.more, moreLabel, onMore, tone = TextTone.Secondary, size = REPLY_ACTION_GLYPH)
    }
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
