package io.github.eslamasabry.oc.ui.kit.frame

import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidPane
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitBackdropVeil
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.spatialFast
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.kitLeaving
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitJumpLatest
import io.github.eslamasabry.oc.ui.words.res.kitJumpNewLatest
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Which edge of its scroll area a pill belongs to (Dart `KitJumpEdge`). */
public enum class KitJumpEdge {
    /** Newer content below: "3 new · Jump to latest". Arrow down. */
    Bottom,

    /** Older content above: "Earlier messages". Arrow up. */
    Top,
}

/** Plan 08 §2.5, §5.1; plan 06 §7.3 (`KitJumpPill`). */
public object JumpPillRules {
    /** The area is "away from latest" past 480 dp (Flutter's 480 logical pixels), compared after `toPx()`. */
    public fun shows(distanceFromEdgePx: Float, density: Float): Boolean =
        distanceFromEdgePx > MotionTokens.AWAY_FROM_LATEST_DP * density

    /** How far the pill starts from its rest, toward its own edge (Full only): a bottom pill rises, a top pill drops. */
    public fun riseDp(edge: KitJumpEdge, state: KitMotionState): Int =
        when {
            !state.travels -> 0
            // Flutter kit_jump_pill.dart: `tokens.space2` (8 dp), not the 6 dp entrance rise.
            edge == KitJumpEdge.Bottom -> Space.S2
            else -> -Space.S2
        }
}

/** The kit words for a bottom pill: "Jump to latest", or "3 new · Jump to latest". The count is the host's, never invented. */
@Composable
public fun kitJumpLatestLabel(newCount: Int = 0): String =
    if (newCount <= 0) stringResource(Res.string.kitJumpLatest)
    else pluralStringResource(Res.plurals.kitJumpNewLatest, newCount, Numbers.format(newCount))

/**
 * The floating "3 new · Jump to latest" pill (and its "Earlier messages" twin at the top) on transcripts, team output
 * and logs (Dart `KitJumpPill`; plan 06 §7.3, plan 08 §5.1). A solid button with words; an arrow alone is never enough.
 * The host decides [visible] (not at the newest end: [JumpPillRules.shows]) and scrolls in [onClick]; the pill moves
 * nothing itself. Hidden: not hittable, out of semantics and focus.
 *
 * States: shown, hidden
 * Text: frame (one line; two from 2.0×)
 * Cap: Chip
 * Motion: fade STANDARD + 8 dp rise toward its edge in, fade QUICK out; Calm = fade; Off = instant (class Travel)
 * Replaces: KitJumpPill, _JumpToLatestButton, _JumpPill (lib/ui/kit/kit_jump_pill.dart)
 */
@Composable
public fun KitJumpPill(
    label: String,
    onClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    edge: KitJumpEdge = KitJumpEdge.Bottom,
    /**
     * E20-S30: what scrolls behind the pill ([io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource]). Given, the
     * pill is the calmer translucent one: a 32 dp face over the content blurred behind it (API 31+; a 92 % tint below),
     * an arrow and the words, scaling in and out on the kit's spring.
     */
    backdrop: KitFrostState? = null,
) {
    kitAssert(label.isNotBlank()) { "KitJumpPill: a pill always carries words" }
    val state = KitMotion.state
    if (state.reduced) {
        if (visible) PillFace(label, onClick, modifier, edge, backdrop)
        return
    }
    val specs = KitMotion.specs
    val risePx = with(LocalDensity.current) { JumpPillRules.riseDp(edge, state).dp.roundToPx() }
    // The veiled pill also grows from 90 % on the kit's fast spring while it may travel (Calm only fades).
    val grows = backdrop != null && state.travels
    val enter: EnterTransition =
        fadeIn(specs.pace(KitPace.Standard)) +
            (if (risePx != 0) slideInVertically(specs.pace<IntOffset>(KitPace.Standard)) { risePx } else EnterTransition.None) +
            if (grows) scaleIn(specs.spatialFast(), initialScale = PILL_ENTER_SCALE) else EnterTransition.None
    val exit: ExitTransition =
        fadeOut(specs.pace(KitPace.Quick)) +
            (if (risePx != 0) slideOutVertically(specs.pace<IntOffset>(KitPace.Quick)) { risePx } else ExitTransition.None) +
            if (grows) scaleOut(specs.spatialFast(), targetScale = PILL_ENTER_SCALE) else ExitTransition.None
    AnimatedVisibility(visible, modifier, enter = enter, exit = exit, label = "kit.jump") {
        PillFace(label, onClick, Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit), edge, backdrop)
    }
}

/** E20-S30: the veiled pill's visible face height (inside its 48 dp target). */
public const val JUMP_PILL_FACE_DP: Int = 32

/** The veiled pill's scale as it arrives and leaves. */
private const val PILL_ENTER_SCALE = 0.9f

@Composable
private fun PillFace(label: String, onClick: () -> Unit, modifier: Modifier, edge: KitJumpEdge, backdrop: KitFrostState?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Pill.shape()
    if (backdrop != null) {
        VeiledPillFace(label, onClick, modifier, edge, backdrop)
        return
    }
    // A small floating pill (36 dp face) inside its 48 dp target: it points the way without covering the reading
    // column (owner 2026-11-06: the full-size button sat on the words).
    Box(
        modifier
            .testTag("kit-jump-pill")
            .heightIn(min = tokens.minTarget.dp)
            .kitTappable(onClick = onClick, shape = KitShapeId.Pill)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp)
                .background(colors.surface3, shape)
                .border(1.dp, colors.hairline, shape)
                .padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            KitGlyph(if (edge == KitJumpEdge.Bottom) KitPartGlyphs.ChevronDown else KitPartGlyphs.ChevronUp, colors.text1, 16.dp)
            KitText(label, role = TypeRole.Secondary, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/** E20-S30: the calmer pill on a translucent, blurred face: arrow and words, 32 dp, in its 48 dp target. */
@Composable
private fun VeiledPillFace(label: String, onClick: () -> Unit, modifier: Modifier, edge: KitJumpEdge, backdrop: KitFrostState) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Pill.shape()
    val veil = rememberKitChromeVeil()
    Box(
        modifier
            .testTag("kit-jump-pill")
            .heightIn(min = tokens.minTarget.dp)
            .kitTappable(onClick = onClick, shape = KitShapeId.Pill)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Glass: a drop of liquid glass over the transcript (Android 13+, not held); else the veil.
        val liquid = kitLiquidBackdrop(backdrop)
        Row(
            Modifier.heightIn(min = JUMP_PILL_FACE_DP.dp)
                .then(
                    liquid?.let { Modifier.kitLiquidPane(LiquidRole.Floating, it, shape) }
                        ?: Modifier.kitBackdropVeil(backdrop, veil, colors.surface3, shape).border(1.dp, colors.hairline, shape)
                )
                .padding(horizontal = Space.S3.dp)
                .testTag("kit-jump-pill-veiled"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KitGlyph(if (edge == KitJumpEdge.Bottom) KitIcons.down else KitIcons.send, colors.text2, 14.dp)
            KitText(label, role = TypeRole.Label, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/**
 * Lays [pill] over [content] (the scroll area it belongs to): centred, `space3` in from the pill's edge, never closer
 * than the gutter to the sides. A bottom pill also clears [bottomInset] (the composer, the pinned primary, the dock)
 * until `KitBottomInset` publishes it.
 *
 * Cap: Fill
 */
@Composable
public fun KitJumpPillLayer(
    modifier: Modifier = Modifier,
    edge: KitJumpEdge = KitJumpEdge.Bottom,
    bottomInset: Dp = 0.dp,
    pill: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    Box(modifier) {
        content()
        val at = if (edge == KitJumpEdge.Bottom) Alignment.BottomCenter else Alignment.TopCenter
        val pad =
            if (edge == KitJumpEdge.Bottom) Modifier.padding(bottom = bottomInset + Space.S3.dp)
            else Modifier.padding(top = Space.S3.dp)
        Box(Modifier.align(at).then(pad).padding(horizontal = gutter).widthIn(max = Dp.Infinity)) { pill() }
    }
}
