package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The message box (Dart `KitComposer` classic pill, `kit_composer_classic.dart`): one 26 dp pill on `surface2` holding
 * the words on top and one row under them: [leading] (the plus), [chips] (model, approval: never a row of their own
 * above the box), then [trailing] (mic, Send or Stop). While a reply runs ([running]) the running glow travels its
 * edge (the app's signature; [KitComposerGlow] owns the motion, reduced motion and battery saver). Capped at the
 * composer width (760 dp) and centred on wide windows, where the corner drops to 18 dp.
 *
 * States: idle, typing, running (glow), disabled (host passes a disabled field)
 * Text: reading (the field), frame (the chips)
 * Cap: 760 dp
 * Motion: the glow (ambient, see [KitComposerGlow]); Send ⇄ Stop cross-fades through [KitComposerSwap]
 * Replaces: KitComposer's pill (lib/ui/kit/chat/kit_composer.dart, kit_composer_classic.dart)
 */
@Composable
public fun KitComposerBox(
    running: Boolean,
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
    chips: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val appearance = LocalKitAppearance.current
    val corner = if (wide) KitComposerGlowDefaults.WideCorner else KitComposerGlowDefaults.Corner
    val shape = RoundedCornerShape(corner)
    KitComposerGlow(
        active = running,
        modifier = modifier.widthIn(max = COMPOSER_MAX_WIDTH.dp).fillMaxWidth(),
        choice = KitGlowChoice.of(appearance.activityGlow, appearance.softGlow),
        corner = corner,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(colors.surface2, shape)
                .then(if (colors.dark) Modifier else Modifier.background(colors.surface2))
                .padding(horizontal = Space.S1.dp)
                .testTag("kit-composer-box"),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Space.S3.dp).heightIn(min = tokens.minTarget.dp)) { field() }
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.S1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                leading()
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) { chips() }
                trailing()
            }
        }
    }
}

/**
 * A chip inside the message box's row (Dart `KitComposerChips`): the model, the approval mode. 32 dp `surface3` pill in
 * a 48 dp target, an optional leading glyph, the words in the label size, and a caret when it opens a picker.
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
        modifier = modifier.then(if (semanticsLabel != null) Modifier.semantics { contentDescription = semanticsLabel } else Modifier),
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
            KitText(label, Modifier.weight(1f, fill = false), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 1)
            if (opensPicker) KitIcon(KitIcons.chevronDown, Modifier.size(14.dp), tone = TextTone.Secondary)
        }
    }
}

/** The plus at the start of the message box's row (attach, photos, history): a bare glyph in a 48 dp target. */
@Composable
public fun KitComposerAdd(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, open: Boolean = false) {
    KitTappable(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = KitShapeId.Circle,
    ) {
        KitIcon(if (open) KitIcons.close else KitIcons.add, size = KitIconSize.Large, tone = TextTone.Primary)
    }
}

/**
 * Send (Dart `_Circle` send): a 40 dp mint disc with the arrow in a 48 dp target (the audit's P1: the visible disc
 * stays 40, the target is 48). Plays `KitHaptic.Send`. Disabled is a `surface3` disc with a quiet arrow and its reason.
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
    val on = enabled.isEnabled
    KitTappable(
        onClick = { if (!working) onClick() },
        modifier = modifier.semantics { contentDescription = label }.testTag("kit-composer-send"),
        enabled = on,
        disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
        haptic = KitHaptic.Send,
        shape = KitShapeId.Circle,
        ink = TextTone.OnAccent,
    ) {
        Box(
            Modifier.size(DISC.dp).background(if (on) colors.accent else colors.surface3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KitIcon(KitIcons.send, size = KitIconSize.Small, tone = if (on) TextTone.OnAccent else TextTone.Tertiary)
        }
    }
}

/**
 * Stop while a reply runs (owner decisions 11B and 02B, 2026-10-02): Send turns into a neutral disc (`text1`) with the
 * stop square, ringed by a turning arc in the live colour, so "it is working, tap to stop" reads at a glance. The ring
 * holds still under reduced motion and battery saver. Plays `KitHaptic.Commit`. [label] names the target ("Stop
 * Claude Code").
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
            val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(MotionTokens.STOP_RING_LAP, easing = LinearEasing), RepeatMode.Restart), label = "turn")
            v
        }
    KitTappable(
        onClick = { if (!stopping) onClick() },
        modifier = modifier.semantics { contentDescription = label }.testTag("kit-composer-stop"),
        haptic = KitHaptic.Commit,
        shape = KitShapeId.Circle,
    ) {
        Box(Modifier.size(RING.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(RING.dp)) {
                val w = 2.dp.toPx()
                drawArc(
                    color = colors.live.copy(alpha = 0.25f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w),
                )
                drawArc(
                    color = colors.live,
                    startAngle = turn * 360f - 90f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
            Box(Modifier.size((DISC - 6).dp).background(if (stopping) colors.surface3 else colors.text1, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(colors.ground, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** The visible Send disc; the target around it is 48 dp. */
private const val DISC = 40

/** The Stop ring's outer size: the disc plus the ring, inside the 48 dp target. */
private const val RING = 44

/** The message box's hard width cap (direction §5.6). */
private const val COMPOSER_MAX_WIDTH = 760
