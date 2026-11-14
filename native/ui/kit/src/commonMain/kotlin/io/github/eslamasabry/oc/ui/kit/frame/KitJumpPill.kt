package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.layout.offset
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidJoinRest
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidPressLight
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidSwell
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidPress
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
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
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitDressedLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
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
        if (visible) PillFace(label, onClick, modifier.kitScreenFloatSeat(edge), edge, backdrop)
        return
    }
    val specs = KitMotion.specs
    // Joined glass (KitLiquidJoin.kt): over a liquid message box the bottom pill is a drop of it, one body of glass with
    // it. It rests sunk a quarter of its height into the box; it rises out of the box narrow, passes its rest, turns and
    // widens, its words showing once it is 70 % wide; leaving, it narrows and sinks back in.
    val joinBody = io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidJoin.current?.bodies?.get("bottom")
    val joinable = backdrop != null && edge == KitJumpEdge.Bottom && state.travels && kitScreenBottomIsSlab() && !kitScreenBottomIsStacked() && joinBody?.granted == true
    if (joinable && joinBody != null) {
        AnimatedVisibility(
            visible,
            modifier,
            enter = EnterTransition.None,
            exit = fadeOut(specs.hold(io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.CLOSE_MS)),
            label = "kit.jump",
        ) {
            val leaving = transition.targetState == EnterExitState.PostExit
            val drop = io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidJoinDrop("bottom")
            val motion = if (drop != null) kitJumpJoinMotion(drop, leaving) else null
            val outer = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            val sinkPx = with(LocalDensity.current) { io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.PILL_SINK_DP.dp.toPx() }
            // It shows once it is composed joined, so the lone pane never flashes at full width; if the join does not
            // come it shows anyway. Read in composition: the join is made between a composition and its draw, and the
            // frame it is made in still holds the lone pane (owner 2026-11-11: "it shows in full then animations start").
            val joinedNow = drop != null && drop.owner.partnerOf(drop) != null
            val waited = remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(JOIN_WAIT_MS)
                waited.value = true
            }
            PillFace(
                label,
                onClick,
                Modifier.kitLeaving(leaving)
                    .graphicsLayer { alpha = if (drop == null || joinedNow || waited.value) 1f else 0f }
                    .onGloballyPositioned { outer.value = androidx.compose.ui.geometry.Rect(it.positionInWindow(), it.size.toSize()) }
                    .offset {
                        // Rest sunk into the box (shape rule 4): the face's bottom edge PILL_SINK_DP below the box's top.
                        val box = joinBody.rest
                        val face = drop?.rest?.height ?: 0f
                        val o = outer.value
                        if (box.height <= 0f || o.height <= 0f || face <= 0f) IntOffset.Zero
                        else IntOffset(0, kotlin.math.round(box.top + sinkPx - (o.center.y + face / 2f)).toInt())
                    },
                edge,
                backdrop,
                drop,
                labelAlpha = motion?.label ?: { 1f },
            )
        }
        return
    }
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
    // Standing apart above the bottom block, its target sits on the block's first part (kitScreenFloatSeat).
    AnimatedVisibility(visible, modifier.kitScreenFloatSeat(edge), enter = enter, exit = exit, label = "kit.jump") {
        PillFace(label, onClick, Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit), edge, backdrop)
    }
}

/** How long a joined pill waits for its glass to join before it shows as a lone pane, ms. */
private const val JOIN_WAIT_MS: Long = 120

/** A joined pill's motion now: its words' alpha (they show once it is 70 % wide). */
private class JumpJoinMotion(val label: () -> Float)

/**
 * The joined pill's motion (`LiquidJoin`, the note's "Drop, rebound, fill" upwards): it rises [LiquidJoin.PILL_RISE_DP]
 * out of the box, half [LiquidJoin.PILL_HALF_DP] wide, on its rise spring, passes its rest and turns; just after the turn
 * it widens on the calm fill spring. Leaving, it narrows, then sinks back in a beat later (the springs and waits are
 * `LiquidJoin`'s). Written to [drop].
 */
@Composable
private fun kitJumpJoinMotion(drop: io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoinDrop, leaving: Boolean): JumpJoinMotion {
    val specs = KitMotion.specs
    val density = LocalDensity.current
    val j = io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
    val length = remember { androidx.compose.animation.core.Animatable(0f) }
    val fill = remember { androidx.compose.animation.core.Animatable(0f) }
    val calm = remember { androidx.compose.animation.core.Animatable(0f) }
    val risePx = with(density) { j.PILL_RISE_DP.dp.toPx() }
    val halfPx = with(density) { j.PILL_HALF_DP.dp.toPx() }
    // The Glass lab's join curve for the pill (its shipped number is REACH_PILL_DP).
    val tunedReach =
        io.github.eslamasabry.oc.ui.kit.effects.LocalKitGlassTuning.current
            ?.knobs(io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind.Chip, io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.dark)
            ?.joinReachDp
    val reachPx = with(density) { (tunedReach ?: j.REACH_PILL_DP).dp.toPx() }
    drop.reachesCentre = false
    drop.reach = { reachPx * (1f + j.PILL_REACH_SWELL * (1f - calm.value.coerceIn(0f, 1f))) }
    drop.shape = { rest ->
        val cy = rest.center.y + (1f - length.value) * risePx
        val half = halfPx + (rest.width / 2f - halfPx) * fill.value.coerceIn(0f, 1f)
        androidx.compose.ui.geometry.Rect(rest.center.x - half, cy - rest.height / 2f, rest.center.x + half, cy + rest.height / 2f)
    }
    androidx.compose.runtime.LaunchedEffect(leaving) {
        if (!leaving) {
            launch { length.animateTo(1f, specs.liquid(j.DROP_ZETA, j.PILL_RISE_K)) }
            launch { calm.animateTo(1f, specs.liquid(1f, j.PILL_RISE_K)) }
            launch {
                kotlinx.coroutines.delay(specs.delayMs(j.turnMs(j.DROP_ZETA, j.PILL_RISE_K) + j.FILL_AFTER_TURN_MS))
                fill.animateTo(1f, specs.liquid(j.FILL_ZETA, j.FILL_K))
            }
        } else {
            launch { fill.animateTo(0f, specs.liquid(1f, j.EMPTY_K)) }
            launch {
                kotlinx.coroutines.delay(specs.delayMs(j.PILL_DRAW_UP_AFTER_MS.toFloat()))
                launch { calm.animateTo(0f, specs.liquid(1f, j.DRAW_UP_K)) }
                length.animateTo(0f, specs.liquid(1f, j.DRAW_UP_K))
            }
        }
    }
    return remember { JumpJoinMotion { ((fill.value - j.LABEL_FROM) / .25f).coerceIn(0f, 1f) } }
}

/**
 * The standing pill's face where the style dresses every part (audit 2026-11-14: Brutal left "Jump to latest" a soft
 * grey capsule beside its ink-edged box and chips): a control standing on the page, so Brutal's ink edge and hard
 * shadow, Paper's ink box with square corners. Null keeps the pill's own face (Solid, Soft, Glass, Signal).
 */
@Composable
private fun kitJumpPillDressed(): io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceLook? =
    kitDressedLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Pill, level = SurfaceLevel.Surface2))

/** The test handle of the standing pill's visible face. */
public const val JUMP_PILL_FACE_TAG: String = "kit-jump-pill-face"

/** E20-S30: the veiled pill's visible face height (inside its 48 dp target). */
public const val JUMP_PILL_FACE_DP: Int = 32

/** The veiled pill's scale as it arrives and leaves. */
private const val PILL_ENTER_SCALE = 0.9f

@Composable
private fun PillFace(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    edge: KitJumpEdge,
    backdrop: KitFrostState?,
    drop: io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoinDrop? = null,
    labelAlpha: () -> Float = { 1f },
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Pill.shape()
    if (backdrop != null) {
        VeiledPillFace(label, onClick, modifier, edge, backdrop, drop, labelAlpha)
        return
    }
    val dressed = kitJumpPillDressed()
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
                // Its page ends its words before the pill (owner 2026-11-14): the pill never sits on a line.
                .kitScreenFloat(edge)
                .then(dressed?.let { Modifier.kitSurface(it) } ?: Modifier.background(colors.surface3, shape).border(1.dp, colors.hairline, shape))
                .testTag(JUMP_PILL_FACE_TAG)
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
private fun VeiledPillFace(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    edge: KitJumpEdge,
    backdrop: KitFrostState,
    drop: io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoinDrop? = null,
    labelAlpha: () -> Float = { 1f },
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Pill.shape()
    val veil = rememberKitChromeVeil()
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Glass: a drop of liquid glass over the transcript (Android 13+, not held); else the veil.
    val liquid = kitLiquidFace(LiquidRole.Pill, backdrop, shape, join = drop)
    // The drop answers the finger: it swells a little and its edge catches light (KitLiquidTouch.kt).
    val liquidPress = rememberKitLiquidPress(interactions, on = liquid != null)
    // Joined to the message box, its glass is drawn beyond its own capsule (the fillets at its foot, its rise out of
    // the box), so the joined face sits on the outer box, before the tap target's clip: under that clip the fillets
    // were cut off and the page showed through (owner 2026-11-11: "a dark spot right and left from this droplet").
    // The joined pane draws from the drop's own box (kitLiquidJoinRest, on the row), whichever node wears it.
    val hoisted = liquid != null && drop?.composedJoined == true
    val dressed = if (liquid == null) kitJumpPillDressed() else null
    Box(
        modifier.kitLiquidSwell(liquidPress)
            .then(if (hoisted && liquid != null) liquid else Modifier)
            .testTag("kit-jump-pill")
            .heightIn(min = tokens.minTarget.dp)
            .kitTappable(onClick = onClick, shape = KitShapeId.Pill, interactionSource = interactions)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.heightIn(min = JUMP_PILL_FACE_DP.dp)
                // Its page ends its words before the pill, and the pill's glass sees none (owner 2026-11-14: "# 3t
                // tests 0 depen…encies" bent under its lens).
                .kitScreenFloat(edge)
                .kitLiquidJoinRest(drop, shape)
                .then(
                    when {
                        hoisted -> Modifier
                        liquid != null -> liquid
                        dressed != null -> Modifier.kitSurface(dressed)
                        else -> Modifier.kitBackdropVeil(backdrop, veil, colors.surface3, shape).border(1.dp, colors.hairline, shape)
                    }
                )
                .kitLiquidPressLight(liquidPress, shape, drop)
                .padding(horizontal = Space.S3.dp)
                .testTag("kit-jump-pill-veiled"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A joined pill's words show once its glass is wide enough to hold them.
            Row(
                Modifier.graphicsLayer { alpha = labelAlpha() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KitGlyph(if (edge == KitJumpEdge.Bottom) KitIcons.down else KitIcons.send, colors.text2, 14.dp)
                KitText(label, role = TypeRole.Label, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
            }
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
    val gutter = kitPageGutter()
    Box(modifier) {
        content()
        val at = if (edge == KitJumpEdge.Bottom) Alignment.BottomCenter else Alignment.TopCenter
        val pad =
            if (edge == KitJumpEdge.Bottom) Modifier.padding(bottom = bottomInset + Space.S3.dp)
            else Modifier.padding(top = Space.S3.dp)
        Box(Modifier.align(at).then(pad).padding(horizontal = gutter).widthIn(max = Dp.Infinity)) { pill() }
    }
}
