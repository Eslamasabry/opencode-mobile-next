package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** One segment (Dart `KitSegment`): a value, its word, an optional icon, and its own disabled reason. */
@Immutable
public data class KitSegment<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: KitEnabled = KitEnabled.Enabled,
    val count: Int? = null,
)

/** Pure rules of [KitSegmented] (B-KIT-031). */
public object SegmentedRules {
    public const val MIN: Int = 2
    public const val MAX: Int = 4

    /** At 2× text the row always stacks (Dart `KitSegmented`). */
    public const val STACK_AT_SCALE: Float = 2.0f

    public fun validCount(count: Int): Boolean = count in MIN..MAX

    /** The row stacks into choice rows when one word does not fit its equal share, or at 2× text. */
    public fun stacks(labelWidthsDp: List<Float>, availableDp: Float, paddingDp: Float, fontScale: Float): Boolean {
        if (fontScale >= STACK_AT_SCALE) return true
        if (labelWidthsDp.isEmpty()) return false
        val share = availableDp / labelWidthsDp.size - paddingDp
        return labelWidthsDp.any { it > share }
    }

    /** What a tap on [tapped] does: the new value, or null when it is the selected one (no callback) or disabled. */
    public fun <T> onTap(tapped: KitSegment<T>, selected: T, groupEnabled: Boolean): T? =
        if (!groupEnabled || !tapped.enabled.isEnabled || tapped.value == selected) null else tapped.value
}

/**
 * Two to four mutually exclusive values on one line (Dart `KitSegmented`; plan 06 §7.5). The chosen one is the filled
 * block (accent, its words on the accent), so it is told apart by shape, not colour alone. A tap on another enabled
 * segment calls [onChange] once and plays `Select` through the kit seam; a tap on the chosen one does nothing. When a
 * word does not fit its share, or at 2× text, the control stacks into choice rows. A disabled control shows its reason
 * under it; a disabled segment says its own.
 *
 * States: default, selected, disabled, stacked
 * Text: frame
 * Cap: Reading
 * Motion: fill cross-fades QUICK; Off = instant
 * Replaces: KitSegmented, KitSegment (lib/ui/kit/kit_segmented.dart)
 */
@Composable
public fun <T> KitSegmented(
    segments: List<KitSegment<T>>,
    selected: T,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    tall: Boolean = false,
    onChange: (T) -> Unit,
) {
    kitAssert(SegmentedRules.validCount(segments.size)) { "KitSegmented needs 2 to 4 segments (B-KIT-031)" }
    kitAssert(segments.any { it.value == selected }) { "KitSegmented.selected must be one of its segments" }
    kitAssert(segments.all { it.enabled !is KitEnabled.Disabled || it.enabled.reason.isNotBlank() }) {
        "KitSegmented: a disabled segment must say why (B-KIT-031)"
    }
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val appDensity = LocalKitAppDensity.current ?: LocalDensity.current
    val fontScale = appDensity.fontScale
    val reasons =
        if (!enabled.isEnabled) listOfNotNull(enabled.reason)
        else segments.mapNotNull { it.enabled.reason }.distinct()
    Column(modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unscaled = Density(appDensity.density, 1f)
            fun width(text: String, role: TypeRole): Float =
                measurer.measure(text, typography.style(role), density = unscaled).size.width / appDensity.density *
                    minOf(fontScale, TextScalePolicy.FRAME_CAP)
            val smallIcon = tokens.smallIcon.toFloat()
            val widths =
                segments.map { s ->
                    if (tall) {
                        2f * Space.S1 + maxOf(width(s.label, TypeRole.Caption), smallIcon) // F: lib/ui/kit/kit_segmented.dart:488
                    } else {
                        2f * Space.S2 + smallIcon + Space.S1 + width(s.label, TypeRole.Label) + // F: lib/ui/kit/kit_segmented.dart:496
                            (if (s.icon != null) smallIcon + Space.S1 else 0f) +
                            (s.count?.let { Space.S1 + width(Numbers.format(it), TypeRole.Caption) } ?: 0f)
                    }
                }
            val room = maxWidth.value - 2f * kitHairline().value // F: lib/ui/kit/kit_segmented.dart:452-453
            val stacked = fontScale >= SegmentedRules.STACK_AT_SCALE || widths.any { it > room / segments.size }
            if (stacked) {
                StackedSegments(segments, selected, enabled, onChange)
            } else {
                RowSegments(segments, selected, enabled, tall, onChange)
            }
        }
        // In the stack a disabled segment's reason is on its row; only the whole control's reason stays under it.
        // (The stack decision lives inside the constraints, so the segment reasons are shown here for both forms.)
        if (reasons.isNotEmpty()) {
            Column(Modifier.padding(start = Space.S2.dp, top = Space.S1.dp)) {
                reasons.forEach { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame) }
            }
        }
    }
}

@Composable
private fun <T> RowSegments(segments: List<KitSegment<T>>, selected: T, enabled: KitEnabled, tall: Boolean, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val solid = LocalKitStyle.current.style == Style.Solid
    val hairline = kitHairline()
    val inset = (Space.S1 / 2f).dp // F: lib/ui/kit/kit_segmented.dart:629
    val density = LocalDensity.current
    val captionLine = with(density) { LocalKitTypography.current.style(TypeRole.Caption).fontSize.toDp() } * 1.3f * density.fontScale // F: lib/ui/kit/kit_segmented.dart:781
    val tallHeight = maxOf(tokens.minTarget.dp, (tokens.smallIcon * 1.2f).dp + Space.S1.dp + captionLine + (Space.S2 * 2).dp) // F: lib/ui/kit/kit_segmented.dart:783-786
    val selectedIndex = segments.indexOfFirst { it.value == selected }
    val step = if (colors.dark) colors.surface3 else colors.surface2
    val track =
        kitSurfaceLook(KitSurfaceInput(SurfaceRole.Track, KitShapeId.Button, SurfaceLevel.Surface1, ownFill = colors.surface1))
    Row(
        Modifier.fillMaxWidth()
            .then(track.modifier)
            .then(if (solid) Modifier.border(hairline, colors.hairline, track.shape) else Modifier)
            .padding(hairline)
            .selectableGroup(),
    ) {
        segments.forEachIndexed { i, s ->
            val chosen = s.value == selected
            val segEnabled = if (!enabled.isEnabled) enabled else s.enabled
            val on = segEnabled.isEnabled
            val fill by animateColorAsState(if (chosen) (if (solid) step else colors.accent) else Color.Transparent, specs.quick(), label = "segment")
            val words = when {
                !on -> TextTone.Tertiary
                chosen -> if (solid) TextTone.Primary else TextTone.OnAccent
                else -> TextTone.Secondary
            }
            val thumb =
                kitSurfaceLook(
                    KitSurfaceInput(
                        role = SurfaceRole.Control,
                        shape = KitShapeId.Tile,
                        level = SurfaceLevel.Surface3,
                        ownFill = fill,
                        color = if (chosen) (if (solid) step else colors.accent) else null,
                        selected = chosen,
                        disabled = !on,
                    ),
                )
            val inkColors = thumb.words?.takeIf { chosen && !solid }?.let { w -> colors.copy(onAccent = w) } ?: colors
            val ink = inkColors.tone(words)
            // Solid's chosen glyph and check take the accent, its one accent mark.
            val glyph = if (solid && chosen && on) colors.accent else ink
            val seam = i < segments.size - 1 && tall && i != selectedIndex && i + 1 != selectedIndex
            val seamColor = colors.hairline
            Box(
                Modifier.weight(1f)
                    .let { if (tall) it.height(tallHeight) else it.heightIn(min = tokens.minTarget.dp) }
                    .drawBehind {
                        if (seam) drawLine(seamColor, Offset(size.width, 0f), Offset(size.width, size.height), hairline.toPx())
                    }
                    .padding(inset)
                    .then(if (chosen) thumb.modifier else Modifier)
                    .kitSelectTap(chosen, Role.RadioButton, segEnabled, haptic = null, KitShapeId.Tile) {
                        SegmentedRules.onTap(s, selected, enabled.isEnabled)?.let {
                            haptics(KitHaptic.Select)
                            onChange(it)
                        }
                    }
                    .padding(horizontal = (Space.S2.toFloat() - Space.S1 / 2f).dp), // F: lib/ui/kit/kit_segmented.dart:629
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalKitColors provides inkColors) {
                    val count = s.count?.let { Numbers.format(it) }
                    if (tall) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            s.icon?.let { KitGlyph(it, glyph, (tokens.smallIcon * 1.2f).dp) } // F: lib/ui/kit/kit_segmented.dart:851
                            Spacer(Modifier.height(Space.S1.dp))
                            KitText(s.label, role = TypeRole.Caption, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.padding(end = Space.S1.dp).alpha(if (chosen) 1f else 0f)) {
                                KitGlyph(KitIcons.check, if (!on) colors.text3 else if (solid) colors.accent else inkColors.onAccent, tokens.smallIcon.dp)
                            }
                            s.icon?.let {
                                KitGlyph(it, glyph, tokens.smallIcon.dp)
                                Spacer(Modifier.width(Space.S1.dp))
                            }
                            KitText(s.label, role = TypeRole.Label, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                            if (count != null) {
                                Spacer(Modifier.width(Space.S1.dp))
                                KitText(count, role = TypeRole.Caption, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> StackedSegments(segments: List<KitSegment<T>>, selected: T, enabled: KitEnabled, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val panel = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    Column(Modifier.fillMaxWidth().clip(panel.shape).then(panel.modifier).selectableGroup()) {
        segments.forEachIndexed { i, s ->
            val segEnabled = if (!enabled.isEnabled) enabled else s.enabled
            if (i > 0) {
                Box(
                    Modifier.padding(start = (tokens.gutter + tokens.smallIcon + Space.S3).dp)
                        .fillMaxWidth().height(kitHairline()).background(colors.hairline),
                )
            }
            KitChoiceRow(
                title = s.count?.let { "${s.label} · ${Numbers.format(it)}" } ?: s.label,
                selected = s.value == selected,
                mark = KitChoiceMark.Radio,
                enabled = segEnabled,
                leading = s.icon?.let { icon -> { KitGlyph(icon, if (segEnabled.isEnabled) colors.text2 else colors.text3, tokens.smallIcon.dp) } },
                onClick = {
                    SegmentedRules.onTap(s, selected, enabled.isEnabled)?.let {
                        haptics(KitHaptic.Select)
                        onChange(it)
                    }
                },
            )
        }
    }
}
