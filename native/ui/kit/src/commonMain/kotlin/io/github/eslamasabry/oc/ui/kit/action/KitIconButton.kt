package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * An icon-only button (Dart `KitIconButton`; plan 06 §7.5). [label] is required and is what a screen reader says (and
 * the tooltip on a fine pointer, once `KitTip` lands with S2-KITB); 48 dp target. A toggled glyph ([icon] changes)
 * cross-fades on QUICK and settles in one frame under Off (B-KIT-019). [stateLabel] is the spoken state of a toggle
 * ("On"). [tone] Accent marks the one active icon in a bar.
 *
 * States: default, pressed, disabled, working
 * Text: none (label is semantics only)
 * Cap: none (48 dp square)
 * Motion: glyph cross-fade QUICK; Off = instant
 * Replaces: KitIconButton (lib/ui/kit/kit_icon_button.dart)
 */
@Composable
public fun KitIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Primary,
    stateLabel: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    haptic: KitHaptic? = null,
    shortcut: String? = null,
    destructive: Boolean = false,
    selected: Boolean? = null,
    size: Int = 24, // F: lib/ui/kit/kit_icon_button.dart:47
) {
    kitAssert(label.isNotBlank()) { "KitIconButton: an icon button needs a label (B-KIT-019)" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val on = enabled.isEnabled
    val ink = if (!on) colors.text3 else if (selected == true) colors.accent else colors.tone(tone) // F: lib/ui/kit/kit_icon_button.dart:glyphColor
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (held) {
            delay(TOOLTIP_HOLD_MS)
            held = false
        }
    }
    val reason = if (!on && !working) enabled.reason else null
    val fine = LocalKitWindow.current.finePointer
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    Box(
        modifier
            .zIndex(if (hovered || held) 1f else 0f)
            .onGloballyPositioned { anchor = it.boundsInWindow().let { r -> IntRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()) } },
        contentAlignment = Alignment.Center,
    ) {
    // the tooltip sits outside the tappable's circular clip
    if (hovered || held) KitTooltip(anchor, label, reason, if (reason == null && fine) shortcut else null)
    Box(
        Modifier
            .kitTappable(
                interactionSource = interactions,
                onLongClick = { held = true },
                onClick = { if (!working) onClick() },
                enabled = on,
                disabledReason = enabled.reason,
                haptic = if (working) null else haptic,
                shape = KitShapeId.Circle,
                ink = tone,
            )
            .semantics {
                contentDescription = label
                if (stateLabel != null) stateDescription = stateLabel
                if (working) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            if (LocalKitMotion.current.reduced) {
                KitGlyph(KitIcons.statusDot, colors.accent, size.dp) // F: lib/ui/kit/kit_icon_button.dart:glyph still dot
            } else {
                KitWorkingMark(colors.accent, size = size.dp)
            }
        } else {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
                label = "kit-icon-swap",
            ) { glyph -> KitGlyph(glyph, ink, size.dp) }
        }
    }
    }
}

/**
 * The copy variant (Dart `KitIconButton.copy`): copies [text] and shows a check for the copied hold.
 */
@Composable
public fun KitIconButtonCopy(
    text: () -> String,
    modifier: Modifier = Modifier,
    label: String = "Copy",
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(io.github.eslamasabry.oc.ui.design.motion.MotionTokens.COPIED_HOLD.toLong())
            copied = false
        }
    }
    KitIconButton(
        if (copied) io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check else io.github.eslamasabry.oc.ui.kit.icon.KitIcons.copy,
        label,
        onClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text()))
            copied = true
        },
        modifier = modifier,
    )
}

private const val TOOLTIP_HOLD_MS = 1500L // NV: how long a long-press tooltip stays up

/** The tooltip bubble (Flutter `Tooltip`, kit_icon_button.dart:455-492): below the target, centred, on `surface2`. */
@Composable
private fun KitTooltip(anchor: IntRect, label: String, reason: String?, shortcut: String?) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val gutter = with(LocalDensity.current) { LocalKitTokens.current.gutter.dp.roundToPx() }
    val window = LocalWindowInfo.current.containerSize.width
    val primary = typography.style(TypeRole.Secondary).copy(color = colors.tone(TextTone.Primary))
    val secondary = typography.style(TypeRole.Secondary).copy(color = colors.tone(TextTone.Secondary))
    val mono = typography.style(TypeRole.Mono).copy(color = colors.tone(TextTone.Secondary))
    val text = buildAnnotatedString {
        withStyle(primary.toSpanStyle()) { append(label) }
        if (reason != null) {
            append("\n")
            withStyle(secondary.toSpanStyle()) { append(reason) }
        } else if (shortcut != null) {
            append("  ")
            withStyle(mono.toSpanStyle()) { append(KitBidi.ltr(shortcut)) }
        }
    }
    // An overlay in this composition (a Popup would be a second root): below the target, centred, kept inside the gutter.
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(Constraints(maxWidth = (window - 2 * gutter).coerceAtLeast(0))) // NV: margin on both sides, Flutter Tooltip margin (kit_icon_button.dart tooltip margin = gutter)
                val left = (anchor.center.x - p.width / 2).coerceIn(gutter, maxOf(gutter, window - gutter - p.width)) // NV: centred under the target
                layout(0, 0) { p.place(left - anchor.center.x, anchor.height / 2) } // NV: half the target is below its centre
            }
            .background(colors.surface2, RoundedCornerShape(FlutterKitTokens.POPOVER_RADIUS.dp)) // F: lib/ui/kit/kit_tokens.dart:90
            .heightIn(min = 32.dp) // NV: Flutter Tooltip's default height
            .padding(horizontal = 16.dp, vertical = 4.dp), // NV: Flutter Tooltip's mobile padding
        contentAlignment = Alignment.Center,
    ) { BasicText(text) }
}
