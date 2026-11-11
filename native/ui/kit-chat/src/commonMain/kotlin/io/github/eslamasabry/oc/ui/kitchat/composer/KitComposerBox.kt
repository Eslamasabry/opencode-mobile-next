package io.github.eslamasabry.oc.ui.kitchat.composer

import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidJoinRest
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidJoinBody
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidTouchLight
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidTouchWatch
import androidx.compose.foundation.layout.widthIn
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import io.github.eslamasabry.oc.ui.kit.foundation.kitMayTruncate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import io.github.eslamasabry.oc.ui.kit.foundation.kitDecorated
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.ChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSpin
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Whether the message box here is the chat's one slab of glass (owner 2026-11-10: "merge the model picker and approval
 * mode with the input box so we get a slab of glass"): Glass, on the chat's overlaid bottom block. The words sit on
 * top; under them one row holds the plus, the approval mode, the model, Stop while a reply runs, and the mic or Send.
 * Nothing rides above the box, so the transcript scrolls sharp up to the glass and bends under its edge. Every other
 * style keeps Flutter's strip above a one-row box.
 */
@Composable
public fun kitComposerSlab(): Boolean =
    LocalKitLiquidChrome.current != null && LocalKitStyle.current.style == io.github.eslamasabry.oc.ui.design.style.Style.Glass

/** True inside the slab ([kitComposerSlab]): its chips are quiet parts of the one pane, never glass of their own. */
public val LocalKitComposerSlab: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Flutter's composer: the input box on its own school-resolved floating surface (surface2 with the token phone/wide
 * radius; Solid adds KitGlass's hairline and floating shadow), inside the running glow. The short field shares one
 * row with its controls; growing text keeps its field slot and moves the controls below. Accessories stay inside the
 * same face, capped at the conversation measure; the glow follows the resolved corners. (Owner 2026-11-06: the
 * frameless try read as stray words on the page, not as the box it is.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitComposerBox(
    running: Boolean,
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
    chips: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    stacked: Boolean = true,
    above: @Composable () -> Unit = {},
    hasLeading: Boolean = false,
    /** At the end of the words' own row when [stacked] (the slab's Stop while a reply runs): the row under keeps still. */
    fieldEnd: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalKitTokens.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val radius = (if (wide) tokens.composerRadiusWide else tokens.composerRadius).dp
    val corner =
        // The box's corner grows with the style's pane inset (Glass), as its face's does (kitSurfaceLook).
        StyleResolver.paneCorner(radius.value, style.knobs.corners, style.spec.tokens).dp
    val shadowRoom = io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach()
    KitComposerGlow(
        active = running,
        modifier = modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(),
        choice = KitGlowChoice.of(appearance.activityGlow, appearance.softGlow),
        corner = corner,
    ) {
        val colors = LocalKitColors.current
        // E20-S30: on the chat's translucent bottom veil the face is translucent too (the surface colour at the veil's
        // alpha), so the transcript scrolling under it shows through, blurred by the veil. Glass keeps its own pane.
        val veil = LocalKitChromeVeil.current
        val look =
            kitSurfaceLook(
                KitSurfaceInput(
                    role = SurfaceRole.Floating,
                    shape = KitShapeId.Panel,
                    level = io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = radius,
                    faceAlpha = composerFaceAlpha(veil, style.style),
                )
            )
        val solid = style.style == io.github.eslamasabry.oc.ui.design.style.Style.Solid
        val shadow =
            if (!solid) Modifier
            else
                Modifier.kitDecorated(
                    io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration(
                        radii = io.github.eslamasabry.oc.ui.design.style.Radii.all(corner.value),
                        shadows =
                            listOf(
                                io.github.eslamasabry.oc.ui.design.style.Shadow(
                                    dxDp = FlutterKitTokens.FLOATING_SHADOWS_DX.toFloat(),
                                    dyDp = FlutterKitTokens.FLOATING_SHADOWS_DY.toFloat(),
                                    blurDp = FlutterKitTokens.FLOATING_SHADOWS_BLUR.toFloat(),
                                    spreadDp = FlutterKitTokens.FLOATING_SHADOWS_SPREAD.toFloat(),
                                    color =
                                        io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current.elevationShadow
                                            .withAlpha(FlutterKitTokens.FLOATING_SHADOWS_ALPHA),
                                )
                            ),
                    )
                )
        val hairline = io.github.eslamasabry.oc.ui.kit.foundation.kitHairline()
        // Glass: on the chat's overlaid bottom block the box is liquid glass over the transcript scrolling behind it
        // (Android 13+, where not held); null keeps the style's own face.
        // Joined glass: the box is the body the jump pill joins ("bottom"), one body of glass while both are liquid.
        val joinBody = rememberKitLiquidJoinBody("bottom")
        val liquid = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, look.shape, join = joinBody)
        val slab = kitComposerSlab()
        // Nothing inside the box is glass of its own: its chips and Stop are parts of this one pane.
        CompositionLocalProvider(LocalKitLiquidChrome provides null, LocalKitComposerSlab provides slab) {
        // The slab answers a finger on its controls: a soft light under it, a brighter edge.
        val touch = io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidTouch(on = liquid != null)
        Column(
            Modifier.fillMaxWidth()
                .then(shadow)
                .kitLiquidTouchWatch(touch)
                .then(if (liquid != null) Modifier.kitLiquidJoinRest(joinBody, look.shape) else Modifier)
                .then(liquid ?: look.modifier)
                .kitLiquidTouchLight(touch, look.shape, join = joinBody)
                .then(
                    if (solid) Modifier.border(hairline, colors.hairline, look.shape) else Modifier
                )
                .clip(look.shape)
                // Glass keeps its pane inset inside the box (owner 2026-11-09): room for the rim, sheen and lens.
                .padding(Space.S1.dp + io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset())
                .testTag("kit-composer-box")
        ) {
            above()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (!stacked) leading()
                Box(
                    Modifier.weight(1f)
                        .padding(
                            start = if (!stacked && hasLeading) 0.dp else Space.S3.dp,
                            end = Space.S2.dp,
                        )
                        .heightIn(min = tokens.minTarget.dp)
                ) {
                    field()
                }
                if (!stacked) trailing() else fieldEnd()
            }
            if (stacked) {
                // At large text sizes choices get the full measure above the action targets.
                // Flow wraps the model and approval mode instead of squeezing either into a sliver.
                if (largeText)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        chips()
                    }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading()
                    if (largeText) Spacer(Modifier.weight(1f))
                    else
                        // E20-S08: the line never scrolls a chip under the mic or Send. E20-S11: the first chip
                        // (the model) shows its name; the later ones (the approval mode) shorten first.
                        KitComposerChipLine(
                            Modifier.weight(1f).kitShadowRoom(shadowRoom).padding(vertical = shadowRoom)
                        ) {
                            chips()
                        }
                    trailing()
                }
            }
        }
        }
    }
}

/**
 * A chip inside the message box's row (Dart `KitComposerChips`): the model, the approval mode. 32
 * dp `surface3` pill in a 48 dp target, an optional leading glyph, the words in the label size, and
 * a caret when it opens a picker.
 */
@Composable
public fun KitComposerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    opensPicker: Boolean = true,
    semanticsLabel: String? = null,
    /** A choice that is on (auto-approve): the accent's soft tint and accent words, same size and shape. */
    active: Boolean = false,
    /**
     * This chip's words may end in "…" when the line is full (E20-S11: the approval mode shortens first; the model's
     * name, the line's first chip, gives way only when even that does not fit).
     */
    shrinks: Boolean = false,
    /**
     * Only the glyph, in a round face (the slab's approval mode on a phone, where the model's name needs the room).
     * It needs an [icon] and a [semanticsLabel]: the words are still what a screen reader hears.
     */
    wordless: Boolean = false,
) {
    val colors = LocalKitColors.current
    // On the slab the chip is a quiet tint of the glass it lies in, never an opaque plate.
    val face =
        when {
            active -> colors.accent.copy(alpha = if (LocalKitComposerSlab.current) .18f else .14f)
            LocalKitComposerSlab.current -> colors.text1.copy(alpha = SLAB_CHIP_TINT)
            else -> colors.surface3
        }
    if (wordless && icon != null) {
        KitTappable(
            onClick = onClick,
            modifier = modifier.semantics { contentDescription = semanticsLabel ?: label },
            shape = KitShapeId.Circle,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(face, CircleShape), contentAlignment = Alignment.Center) {
                KitIcon(icon, size = KitIconSize.Small, tone = if (active) TextTone.Accent else TextTone.Secondary)
            }
        }
        return
    }
    KitTappable(
        onClick = onClick,
        modifier =
            modifier.then(
                if (semanticsLabel != null)
                    Modifier.semantics { contentDescription = semanticsLabel }
                else Modifier
            ),
        shape = KitShapeId.Pill,
    ) {
        Row(
            Modifier.heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(face, RoundedCornerShape(50))
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            icon?.let { KitIcon(it, size = KitIconSize.Small, tone = if (active && LocalKitComposerSlab.current) TextTone.Accent else TextTone.Secondary) }
            KitText(
                label,
                // The chip line gives a bounded width (it no longer scrolls), so the words take what the glyphs
                // leave and the caret stays. Only a chip that [shrinks] may end in "…".
                Modifier.weight(1f, fill = false).widthIn(max = COMPOSER_CHIP_WORDS_MAX.dp)
                    .then(if (shrinks) Modifier.kitMayTruncate("composer-chip") else Modifier),
                role = TypeRole.Label,
                tone = if (active) TextTone.Accent else TextTone.Primary,
                kind = KitTextKind.Frame,
                maxLines = if (LocalDensity.current.fontScale >= 1.5f) Int.MAX_VALUE else 1,
            )
            if (opensPicker)
                Image(
                    KitIcons.chevronDown,
                    null,
                    Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(colors.text2),
                )
        }
    }
}

/**
 * The plus at the start of the message box's row: a bare glyph in a 48 dp target. While the tray is [open] it turns
 * into "×" by an eighth of a turn (Flutter `AnimatedRotation(turns: 1/8)`, QUICK on Enter) through the kit's [KitSpin]:
 * Calm cross-fades the two glyphs instead of turning, reduced motion is instant (M-002).
 */
@Composable
public fun KitComposerAdd(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
) {
    KitTappable(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = KitShapeId.Circle,
    ) {
        KitSpin(turns = if (open) ADD_OPEN_TURNS else 0f, pace = KitPace.Quick) {
            KitIcon(
                KitIcons.add,
                modifier = Modifier.testTag(if (open) "kit-composer-add-open" else "kit-composer-add"),
                size = KitIconSize.Large,
                tone = TextTone.Primary,
            )
        }
    }
}

/** An eighth of a turn: "+" reads as "×". */
internal const val ADD_OPEN_TURNS: Float = 1f / 8f

/**
 * Send (Dart `_Circle` send): a 40 dp mint disc with the arrow in a 48 dp target (the audit's P1:
 * the visible disc stays 40, the target is 48). Plays `KitHaptic.Send`. Disabled is a `surface3`
 * disc with a quiet arrow and its reason.
 */
@Composable
public fun KitComposerSend(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    /** Hold Send (Flutter send later / repeat). Hidden gesture: the caller gives it a visible twin. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val on = enabled.isEnabled
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Control,
                shape = KitShapeId.Circle,
                level = SurfaceLevel.Surface3,
                ownFill = if (on) colors.accent else colors.surface3,
                color = if (on) colors.accent else null,
                action = true,
            )
        )
    val ink = if (on) look.words ?: colors.onAccent else colors.text3
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The disc sits under the press target, outside its clip, so the style's shadow is whole (as the mic's).
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(FlutterKitTokens.COMPOSER_ACTION_SIZE.dp)
                .then(look.modifier)
                .testTag("kit-composer-send-disc")
        )
        KitTappable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            modifier = Modifier.semantics { contentDescription = label },
            enabled = on && !working,
            disabledReason = if (working) label else enabled.reason,
            haptic = KitHaptic.Send,
            shape = KitShapeId.Circle,
            ink = if (on) TextTone.OnAccent else TextTone.Tertiary,
        ) {
            if (working)
                Box(
                    Modifier.size(tokens.smallIcon.dp).testTag("kit-composer-send-working"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (KitMotion.state.ambientLoops)
                        KitWorkingMark(ink, size = tokens.smallIcon.dp)
                    else
                        Image(
                            KitIcons.statusDot,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            colorFilter = ColorFilter.tint(ink),
                        )
                }
            else
                Image(
                    KitIcons.send,
                    contentDescription = null,
                    modifier =
                        Modifier.size(tokens.smallIcon.dp).graphicsLayer {
                            scaleX = if (rtl) -1f else 1f
                        },
                    colorFilter = ColorFilter.tint(ink),
                )
        }
    }
}

/**
 * Stop while a reply runs (owner decisions 11B and 02B, 2026-10-02): Send turns into a neutral disc
 * (`text1`) with the stop square, ringed by a turning arc in the live colour, so "it is working,
 * tap to stop" reads at a glance. The ring holds still under reduced motion and battery saver.
 * Plays `KitHaptic.Commit`. [label] names the target ("Stop Claude Code"). [onLongClick] (S24-CHAT, Flutter
 * `_stopMenu`): the hold, named [onLongClickLabel] for a screen reader; null = no hold. Ignored while stopping.
 */
@Composable
public fun KitComposerStopRing(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stopping: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val still = !KitMotion.state.ambientLoops || LocalKitAppearance.current.saver
    val turn =
        if (still) {
            0.15f
        } else {
            val t = rememberInfiniteTransition(label = "kit-stop-ring")
            val v by
                t.animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(
                        tween(MotionTokens.STOP_RING_LAP, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
                    label = "turn",
                )
            v
        }
    KitTappable(
        onClick = { if (!stopping) onClick() },
        modifier = modifier.semantics { contentDescription = label },
        onLongClick = onLongClick?.takeUnless { stopping },
        onLongClickLabel = onLongClickLabel,
        haptic = KitHaptic.Commit,
        shape = KitShapeId.Circle,
    ) {
        Box(Modifier.size(RING.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(RING.dp)) {
                val w = 2.dp.toPx()
                drawArc(
                    color = colors.live.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w),
                )
                drawArc(
                    color = colors.live,
                    startAngle = turn * 360f - 90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier.size((DISC - 6).dp)
                    .background(if (stopping) colors.surface3 else colors.text1, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).background(colors.ground, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** E20-S30: the composer face's alpha on [veil] under [style]: the veil's, except Glass (its own pane) and no veil. */
internal fun composerFaceAlpha(veil: ChromeVeil?, style: io.github.eslamasabry.oc.ui.design.style.Style): Float =
    if (veil == null || style == io.github.eslamasabry.oc.ui.design.style.Style.Glass) 1f else veil.faceAlpha

/** The visible Send disc; the target around it is 48 dp. */
private const val DISC = 40

/** The Stop ring's outer size: the disc plus the ring, inside the 48 dp target. */
private const val RING = 44

/** The longest a composer chip's words grow before they end in "…" (the model's name stays readable). */
private const val COMPOSER_CHIP_WORDS_MAX = 180

/** A slab chip's face: the words' ink at this alpha over the glass. */
private const val SLAB_CHIP_TINT = .08f

/** E20-S11: the least a later chip (the approval mode) keeps beside the model: its words start, then "…". */
private const val COMPOSER_CHIP_TAIL_MIN = 96

/**
 * The message box's chip line (E20-S08, E20-S11): one row that ends before the action button. The first chip (the
 * model) has priority: it shows its whole name, up to the chip's own cap, as long as every later chip (the approval
 * mode) keeps [COMPOSER_CHIP_TAIL_MIN] (or its whole width when smaller); the later chips take what is left and end in
 * "…" first. Only when even that does not fit does the model's name give way too. Nothing ever draws past the line's
 * end. Right to left, the line starts on the right.
 */
@Composable
private fun KitComposerChipLine(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = Space.S2.dp.roundToPx()
        val max = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val first = measurables.firstOrNull()
        val rest = measurables.drop(1)
        val tailMin = COMPOSER_CHIP_TAIL_MIN.dp.roundToPx()
        val reserved =
            rest.map { minOf(it.maxIntrinsicWidth(Constraints.Infinity), tailMin) }
                .filter { it > 0 }
                .let { widths -> widths.sum() + gap * widths.size }
        val headCap = (max - reserved).coerceAtLeast(0)
        val head = first?.measure(loose.copy(maxWidth = headCap))
        var used = head?.width?.let { if (it > 0) it + gap else 0 } ?: 0
        val tail =
            rest.map { m ->
                m.measure(loose.copy(maxWidth = (max - used).coerceAtLeast(0))).also { if (it.width > 0) used += it.width + gap }
            }
        val placed = listOfNotNull(head) + tail
        val shown = placed.filter { it.width > 0 }
        val width = (shown.sumOf { it.width } + gap * (shown.size - 1).coerceAtLeast(0)).coerceIn(constraints.minWidth, max)
        val height = (placed.maxOfOrNull { it.height } ?: 0).coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            var x = 0
            placed.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                if (p.width > 0) x += p.width + gap
            }
        }
    }
}
