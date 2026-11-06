package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
 * A frameless composer: text and controls sit on the page, with only the working glow around them.
 * The short field shares one row with its controls; growing text keeps its field slot and moves
 * controls below. Accessories remain inside the conversation measure; the glow follows the resolved
 * corners.
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
    KitComposerGlow(
        active = running,
        modifier = modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(),
        choice = KitGlowChoice.of(appearance.activityGlow, appearance.softGlow),
        corner = corner,
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.S1.dp).testTag("kit-composer-box")) {
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
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
                .background(colors.surface3, RoundedCornerShape(50))
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            icon?.let { KitIcon(it, size = KitIconSize.Small, tone = TextTone.Secondary) }
            KitText(
                label,
                Modifier.weight(1f, fill = false),
                role = TypeRole.Label,
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
    KitTappable(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        enabled = on && !working,
        disabledReason = if (working) label else enabled.reason,
        haptic = KitHaptic.Send,
        shape = KitShapeId.Circle,
        ink = if (on) TextTone.OnAccent else TextTone.Tertiary,
    ) {
        Box(
            Modifier.size(FlutterKitTokens.COMPOSER_ACTION_SIZE.dp)
                .then(look.modifier)
                .testTag("kit-composer-send-disc"),
            contentAlignment = Alignment.Center,
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
