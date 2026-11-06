package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.widthIn
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

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
    chips: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    stacked: Boolean = true,
    above: @Composable () -> Unit = {},
    hasLeading: Boolean = false,
) {
    val tokens = LocalKitTokens.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val radius = (if (wide) tokens.composerRadiusWide else tokens.composerRadius).dp
    val corner =
        StyleResolver.corner(radius.value, style.knobs.corners, style.spec.tokens.sharpRadius).dp
    val shadowRoom = io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach()
    val chipScroll = rememberScrollState()
    KitComposerGlow(
        active = running,
        modifier = modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(),
        choice = KitGlowChoice.of(appearance.activityGlow, appearance.softGlow),
        corner = corner,
    ) {
        val colors = LocalKitColors.current
        val look =
            kitSurfaceLook(
                KitSurfaceInput(
                    role = SurfaceRole.Floating,
                    shape = KitShapeId.Panel,
                    level = io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = radius,
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
        Column(
            Modifier.fillMaxWidth()
                .then(shadow)
                .then(look.modifier)
                .then(
                    if (solid) Modifier.border(hairline, colors.hairline, look.shape) else Modifier
                )
                .clip(look.shape)
                .padding(Space.S1.dp)
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
                if (!stacked) trailing()
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
                        Row(
                            // The line scrolls sideways; the chips' style shadows keep their room above and below.
                            // N-52: a chip that does not fit fades out at the edge it scrolls under (the mic, Send)
                            // instead of being cut by it.
                            Modifier.weight(1f)
                                .kitShadowRoom(shadowRoom)
                                .kitComposerChipsFade(chipScroll)
                                .horizontalScroll(chipScroll)
                                .padding(vertical = shadowRoom),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        ) {
                            chips()
                        }
                    trailing()
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
) {
    val colors = LocalKitColors.current
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
                .background(if (active) colors.accent.copy(alpha = .14f) else colors.surface3, RoundedCornerShape(50))
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            icon?.let { KitIcon(it, size = KitIconSize.Small, tone = TextTone.Secondary) }
            KitText(
                label,
                // No weight: inside the sideways chip line a weight measures to zero and left only the chevron.
                Modifier.widthIn(max = COMPOSER_CHIP_WORDS_MAX.dp),
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
 * The plus at the start of the message box's row (attach, photos, history): a bare glyph in a 48 dp
 * target.
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
        KitIcon(
            if (open) KitIcons.close else KitIcons.add,
            size = KitIconSize.Large,
            tone = TextTone.Primary,
        )
    }
}

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
 * Plays `KitHaptic.Commit`. [label] names the target ("Stop Claude Code").
 */
@Composable
public fun KitComposerStopRing(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stopping: Boolean = false,
) {
    val colors = LocalKitColors.current
    val still = KitMotion.state.reduced || LocalKitAppearance.current.saver
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

/** The visible Send disc; the target around it is 48 dp. */
private const val DISC = 40

/** The Stop ring's outer size: the disc plus the ring, inside the 48 dp target. */
private const val RING = 44

/** The longest a composer chip's words grow before they end in "…" (the model's name stays readable). */
private const val COMPOSER_CHIP_WORDS_MAX = 180

/** The chips line's edge fade (as `KitFilterChips`): an edge with more chips past it fades over 24 dp. */
private fun Modifier.kitComposerChipsFade(scroll: androidx.compose.foundation.ScrollState): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val left = if (rtl) scroll.value < scroll.maxValue else scroll.value > 0
            val right = if (rtl) scroll.value > 0 else scroll.value < scroll.maxValue
            if (!left && !right) return@drawWithContent
            val edge = (24.dp.toPx() / size.width).coerceIn(0f, .5f)
            val solid = Color.Black
            val clear = Color.Transparent
            drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to if (left) clear else solid,
                    edge to solid,
                    (1f - edge) to solid,
                    1f to if (right) clear else solid,
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
