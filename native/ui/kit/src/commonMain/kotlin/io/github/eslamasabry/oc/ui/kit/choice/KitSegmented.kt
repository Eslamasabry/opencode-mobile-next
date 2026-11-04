package io.github.eslamasabry.oc.ui.kit.choice
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
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
    Column(modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val style = typography.style(TypeRole.Label)
            val frameScale = minOf(fontScale, TextScalePolicy.FRAME_CAP)
            val unscaled = Density(appDensity.density, 1f)
            val widths =
                segments.map { s ->
                    val px = measurer.measure(s.label, style, density = unscaled).size.width
                    px / appDensity.density * frameScale + (if (s.icon != null) tokens.smallIcon + Space.S1 else 0)
                }
            val stacked = SegmentedRules.stacks(widths, maxWidth.value, (Space.S3 * 2).toFloat(), fontScale)
            if (stacked) {
                StackedSegments(segments, selected, enabled, onChange)
            } else {
                RowSegments(segments, selected, enabled, onChange)
            }
        }
        enabled.reason?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame) }
    }
}

@Composable
private fun <T> RowSegments(segments: List<KitSegment<T>>, selected: T, enabled: KitEnabled, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    Row(
        Modifier.fillMaxWidth()
            .kitSurface(kitSurfaceLook(SurfaceRequest(SurfaceRole.Track), colors.surface3), KitShapeId.Button.toShape())
            .padding(Space.S1.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        segments.forEach { s ->
            val chosen = s.value == selected
            val segEnabled = if (!enabled.isEnabled) enabled else s.enabled
            val fill by animateColorAsState(if (chosen) colors.accent else colors.surface3, specs.quick(), label = "segment")
            val look = kitSurfaceLook(SurfaceRequest(SurfaceRole.Control, fill = if (chosen) Argb(colors.accent.toArgb()) else null, selected = chosen, disabled = !segEnabled.isEnabled), fill)
            val styledColors = if (look.words == null) colors else colors.copy(onAccent = Color(look.words!!.v), text1 = Color(look.words!!.v))
            val words = when {
                !segEnabled.isEnabled -> TextTone.Tertiary
                chosen -> TextTone.OnAccent
                else -> TextTone.Primary
            }
            Row(
                Modifier.weight(1f)
                    .heightIn(min = (tokens.minTarget - Space.S2).dp)
                    .kitSelectTap(chosen, Role.RadioButton, segEnabled, haptic = null, KitShapeId.Tile) {
                        SegmentedRules.onTap(s, selected, enabled.isEnabled)?.let {
                            haptics(KitHaptic.Select)
                            onChange(it)
                        }
                    }
                    .kitSurface(look, KitShapeId.Tile.toShape())
                    .padding(horizontal = Space.S2.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalKitColors provides styledColors) {
                    s.icon?.let { KitGlyph(it, styledColors.tone(words), tokens.smallIcon.dp) }
                    KitText(s.label, role = TypeRole.Label, tone = words, kind = KitTextKind.Frame, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun <T> StackedSegments(segments: List<KitSegment<T>>, selected: T, enabled: KitEnabled, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        segments.forEach { s ->
            val segEnabled = if (!enabled.isEnabled) enabled else s.enabled
            KitChoiceRow(
                title = s.label,
                selected = s.value == selected,
                mark = KitChoiceMark.Radio,
                enabled = segEnabled,
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
